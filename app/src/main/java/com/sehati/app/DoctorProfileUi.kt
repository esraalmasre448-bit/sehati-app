package com.sehati.app

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private fun sxHm(min: Int): String = "%02d:%02d".format(min / 60, min % 60)

private fun openWorkPlaceRoute(ctx: android.content.Context, place: Map<*, *>?, fallbackName: String): String? {
    if (place == null) return "لم يضف الطبيب موقع $fallbackName بعد"
    val lat = (place["lat"] as? Number)?.toDouble()
    val lng = (place["lng"] as? Number)?.toDouble()
    val link = place["mapLink"]?.toString().orEmpty()
    val name = place["name"]?.toString().orEmpty().ifBlank { fallbackName }
    val uri = when {
        lat != null && lng != null -> Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$lat,$lng")
        link.startsWith("https://") -> Uri.parse(link)
        name.isNotBlank() -> Uri.parse("https://www.google.com/maps/search/?api=1&query=" + Uri.encode(name))
        else -> return "لم يحدد الطبيب موقع $fallbackName بعد"
    }
    return try {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        null
    } catch (_: Exception) { "تعذر فتح الخرائط على هذا الجهاز" }
}

@Composable
private fun SxInfoRow(label: String, value: Any?, icon: ImageVector) {
    val v = value?.toString().orEmpty()
    if (v.isNotBlank()) {
        Row(
            Modifier.fillMaxWidth().background(BCard, RoundedCornerShape(12.dp))
                .border(1.dp, BLine, RoundedCornerShape(12.dp)).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(icon, contentDescription = null, tint = BRed, modifier = Modifier.size(22.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(label, color = BMuted, fontSize = 13.sp)
                Text(v, color = Color.White, fontSize = 16.sp)
            }
        }
    }
}

@Composable
private fun SxProfTab(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .background(if (selected) BRed else BCard, RoundedCornerShape(12.dp))
            .border(1.dp, if (selected) BRed else BLine, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) { Text(label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
}

@Composable
private fun SxStat(label: String, value: String, modifier: Modifier) {
    Column(
        modifier.background(BCard, RoundedCornerShape(12.dp))
            .border(1.dp, BLine, RoundedCornerShape(12.dp)).padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(label, color = BMuted, fontSize = 12.sp, textAlign = TextAlign.Center)
    }
}

/**
 * صفحة الطبيب بتصميم المرجع: صورة واسم وتخصص، تبويبان (معلومات عامة / الموقع)، وأرقام اليوم الحقيقية من الطابور.
 * لا تقييمات ولا حالة توفر هنا لأنه لا يوجد مصدر بيانات مؤكد لهما في هذا الملف.
 * رقم الهاتف يؤخذ من ملف الطبيب إن وُجد، وإلا من doctorPrivate إن سمحت القواعد (وإلا لا يظهر).
 */
@Composable
fun DoctorDetailFull(d: Map<String, Any?>, onBack: () -> Unit, onBook: () -> Unit) {
    val ctx = LocalContext.current
    val doctorId = d["id"]?.toString().orEmpty()
    var msg by remember { mutableStateOf("") }
    var tab by remember { mutableIntStateOf(0) }
    var phone by remember { mutableStateOf(d["phone"]?.toString().orEmpty()) }
    var last by remember { mutableLongStateOf(0L) }
    var current by remember { mutableLongStateOf(0L) }
    var clinicBranches by remember { mutableStateOf<List<Map<String, Any?>>>(emptyList()) }

    LaunchedEffect(doctorId) {
        if (phone.isBlank() && doctorId.isNotBlank()) {
            bDb.collection("doctorPrivate").document(doctorId).get()
                .addOnSuccessListener { s ->
                    val p = s.getString("phone").orEmpty()
                    if (p.isNotBlank()) phone = p
                }
        }
    }

    DisposableEffect(doctorId) {
        val reg = if (doctorId.isBlank()) null else
            bDb.collection("queues").document("${doctorId}_${todayKey()}")
                .addSnapshotListener { s, _ ->
                    last = s?.getLong("last") ?: 0L
                    current = s?.getLong("current") ?: 0L
                }
        onDispose { reg?.remove() }
    }

    DisposableEffect(doctorId) {
        if (doctorId.isBlank()) onDispose { }
        else {
            val reg = bDb.collection("doctorBranches").document(doctorId).collection("items")
                .whereEqualTo("active", true)
                .addSnapshotListener { snapshot, _ ->
                    clinicBranches = snapshot?.documents?.map { it.data.orEmpty() }.orEmpty()
                }
            onDispose { reg.remove() }
        }
    }

    val startMin = ((d["startHour"] as? Number)?.toInt() ?: 9) * 60
    val endMin = ((d["endHour"] as? Number)?.toInt() ?: 17) * 60

    Column(
        Modifier.fillMaxSize().background(BBg).padding(20.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TextButton(onClick = onBack) { Text("رجوع", color = BRed) }
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            DoctorAvatar(d["photo"]?.toString(), "${d["name"] ?: ""}", 110.dp)
            Spacer(Modifier.height(8.dp))
            Text("${d["name"] ?: ""}", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text("${d["specialty"] ?: ""}", fontSize = 16.sp, color = BRed)
            val city = "${d["city"] ?: ""}"
            if (city.isNotBlank()) Text(city, fontSize = 13.sp, color = BMuted)
        }

        // الحجز هو الإجراء الأساسي، لذلك يظهر مباشرة بعد هوية الطبيب.
        RedButton("احجز موعد", onClick = onBook)

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SxProfTab("معلومات عامة", tab == 0, Modifier.weight(1f)) { tab = 0 }
            SxProfTab("الموقع", tab == 1, Modifier.weight(1f)) { tab = 1 }
        }

        if (tab == 0) {
            SxInfoRow("نبذة", d["bio"], Icons.Filled.Info)
            SxInfoRow("العيادة", d["clinicName"], Icons.Filled.Business)
            SxInfoRow("سعر الكشفية", d["fee"], Icons.Filled.Payments)
            SxInfoRow("ساعات استقبال الحجوزات بالعيادة", "${sxHm(startMin)} - ${sxHm(endMin)}", Icons.Filled.Schedule)
            if (hasWorkDays(d)) SxInfoRow("أيام الدوام", workDaysLabel(workDaysOf(d)), Icons.Filled.CalendarMonth)
            SxInfoRow("أيام وساعات الدوام بالعيادة", d["hours"], Icons.Filled.CalendarMonth)
            SxInfoRow("المشفى", d["hospitalName"], Icons.Filled.LocalHospital)
            SxInfoRow("أيام وساعات الدوام بالمشفى", d["hospitalHours"], Icons.Filled.Schedule)
            SxInfoRow("رقم التواصل", phone, Icons.Filled.Phone)
        } else {
            SxInfoRow("المدينة", d["city"], Icons.Filled.LocationOn)
            SxInfoRow("العنوان", d["address"], Icons.Filled.LocationOn)
            val places = d["workLocations"] as? Map<*, *>
            val clinicPlace = places?.get("clinic") as? Map<*, *>
            val hospitalPlace = places?.get("hospital") as? Map<*, *>
            if (clinicPlace != null) {
                SxInfoRow("موقع العيادة", clinicPlace["name"], Icons.Filled.Business)
                SxInfoRow("دوام العيادة", "${clinicPlace["startHour"]}:00 - ${clinicPlace["endHour"]}:00", Icons.Filled.Schedule)
                val days = (clinicPlace["workDays"] as? List<*>)?.mapNotNull { (it as? Number)?.toInt() }?.toSet().orEmpty()
                if (days.isNotEmpty()) SxInfoRow("أيام العيادة", workDaysLabel(days), Icons.Filled.CalendarMonth)
                OutlinedButton(onClick = { msg = openWorkPlaceRoute(ctx, clinicPlace, "العيادة") ?: "" },
                    modifier = Modifier.fillMaxWidth().height(50.dp)) {
                    Text("الاتجاهات إلى العيادة", color = Color.White)
                }
            } else {
                OutlinedButton(onClick = { msg = openRoute(ctx, d) ?: "" },
                    modifier = Modifier.fillMaxWidth().height(50.dp)) {
                    Text("الطريق إلى العيادة", color = Color.White)
                }
            }
            if (hospitalPlace != null) {
                SxInfoRow("موقع المشفى", hospitalPlace["name"], Icons.Filled.LocalHospital)
                SxInfoRow("دوام المشفى", "${hospitalPlace["startHour"]}:00 - ${hospitalPlace["endHour"]}:00", Icons.Filled.Schedule)
                val days = (hospitalPlace["workDays"] as? List<*>)?.mapNotNull { (it as? Number)?.toInt() }?.toSet().orEmpty()
                if (days.isNotEmpty()) SxInfoRow("أيام المشفى", workDaysLabel(days), Icons.Filled.CalendarMonth)
                OutlinedButton(onClick = { msg = openWorkPlaceRoute(ctx, hospitalPlace, "المشفى") ?: "" },
                    modifier = Modifier.fillMaxWidth().height(50.dp)) {
                    Text("الاتجاهات إلى المشفى", color = Color.White)
                }
            }
            if (clinicBranches.isNotEmpty()) {
                Text("فروع العيادة", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                clinicBranches.forEachIndexed { index, branch ->
                    SxInfoRow("الفرع ${index + 1}", branch["name"], Icons.Filled.Business)
                    SxInfoRow("المدينة", branch["city"], Icons.Filled.LocationOn)
                    SxInfoRow("العنوان", branch["address"], Icons.Filled.LocationOn)
                    OutlinedButton(
                        onClick = { msg = openWorkPlaceRoute(ctx, branch, "الفرع") ?: "" },
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) { Text("الاتجاهات إلى ${branch["name"] ?: "الفرع"}", color = Color.White) }
                }
            }
        }

        // أرقام اليوم الحقيقية من وثيقة الطابور
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SxStat("حجوزات اليوم", "$last", Modifier.weight(1f))
            SxStat("الدور الحالي", "$current", Modifier.weight(1f))
            SxStat("بالانتظار", "${(last - current).coerceAtLeast(0L)}", Modifier.weight(1f))
        }

        if (msg.isNotEmpty()) Text(msg, color = BDanger)
        if (phone.isNotBlank()) {
            OutlinedButton(
                onClick = {
                    try {
                        ctx.startActivity(
                            Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    } catch (e: Exception) { msg = "تعذر فتح الاتصال" }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) { Text("اتصال بالعيادة", color = Color.White) }
        }
    }
}

package com.sehati.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions

private data class WorkPlaceDraft(
    val name: String = "",
    val start: String = "9",
    val end: String = "17",
    val days: Set<Int> = (1..7).toSet(),
    val step: Int = 30,
    val location: String = ""
)

@Composable
fun ClinicSettingsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val uid = bAuth.currentUser?.uid ?: ""
    var clinic by remember { mutableStateOf(WorkPlaceDraft()) }
    var hospital by remember { mutableStateOf(WorkPlaceDraft(name = "")) }
    var bio by remember { mutableStateOf("") }
    var gpsTarget by remember { mutableStateOf("clinic") }
    var msg by remember { mutableStateOf("") }
    var ok by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(uid) {
        if (uid.isBlank()) return@LaunchedEffect
        bDb.collection("doctors").document(uid).get().addOnSuccessListener { d ->
            if (!d.exists()) return@addOnSuccessListener
            bio = d.getString("bio") ?: ""
            val locations = d.get("workLocations") as? Map<*, *>
            fun readPlace(key: String, legacy: Boolean): WorkPlaceDraft {
                val p = locations?.get(key) as? Map<*, *>
                if (p != null) return WorkPlaceDraft(
                    name = p["name"]?.toString().orEmpty(),
                    start = (p["startHour"] as? Number)?.toInt()?.toString() ?: "9",
                    end = (p["endHour"] as? Number)?.toInt()?.toString() ?: "17",
                    days = (p["workDays"] as? List<*>)?.mapNotNull { (it as? Number)?.toInt() }?.toSet()?.takeIf { it.isNotEmpty() } ?: (1..7).toSet(),
                    step = ((p["slotMinutes"] as? Number)?.toInt() ?: 30).coerceIn(10, 180),
                    location = when {
                        p["lat"] is Number && p["lng"] is Number -> "${p["lat"]},${p["lng"]}"
                        else -> p["mapLink"]?.toString().orEmpty()
                    }
                )
                if (legacy) {
                    val lat = d.getDouble("lat")
                    val lng = d.getDouble("lng")
                    return WorkPlaceDraft(
                        name = d.getString("clinicName").orEmpty(),
                        start = (d.getLong("startHour") ?: 9L).toString(),
                        end = (d.getLong("endHour") ?: 17L).toString(),
                        days = workDaysOf(d.data),
                        step = (d.getLong("slotMinutes") ?: 30L).toInt(),
                        location = if (lat != null && lng != null) "$lat,$lng" else d.getString("mapLink").orEmpty()
                    )
                }
                return WorkPlaceDraft()
            }
            clinic = readPlace("clinic", true)
            hospital = readPlace("hospital", false)
        }
    }

    fun useGps() {
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val loc = try {
            lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        } catch (_: SecurityException) { null }
        if (loc == null) {
            ok = false
            msg = "لم يصلنا موقع GPS حديث. شغّل تحديد الموقع أو أدخل رابط الخرائط يدوياً."
            return
        }
        val value = "${loc.latitude},${loc.longitude}"
        if (gpsTarget == "clinic") clinic = clinic.copy(location = value)
        else hospital = hospital.copy(location = value)
        ok = true
        msg = "تم إدخال إحداثيات ${if (gpsTarget == "clinic") "العيادة" else "المشفى"}؛ اضغط حفظ."
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) useGps() else { ok = false; msg = "لم يتم منح إذن الموقع" }
    }

    fun validPlace(p: WorkPlaceDraft, label: String): String? {
        if (p.name.isBlank()) return "اكتب اسم $label"
        val sh = p.start.trim().toIntOrNull()
        val eh = p.end.trim().toIntOrNull()
        if (sh == null || eh == null || sh !in 0..23 || eh !in 1..24 || sh >= eh) return "راجع ساعات $label: البداية من 0 إلى 23 والنهاية من 1 إلى 24"
        if (p.days.isEmpty()) return "اختر يوماً واحداً على الأقل لدوام $label"
        if (p.location.isNotBlank() && parseLatLng(p.location) == null && !isMapsLink(p.location)) return "موقع $label يجب أن يكون رابط خرائط https أو إحداثيات مثل 33.51,36.29"
        return null
    }

    Column(
        Modifier.fillMaxSize().background(BBg).padding(20.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TextButton(onClick = onBack) { Text("رجوع", color = BRed) }
        Text("مواقع العمل والدوام", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text("أدخل بيانات العيادة والمشفى كلٌّ على حدة. يمكنك كتابة الموقع يدوياً أو التقاط آخر موقع معروف من GPS.", color = BMuted)
        WorkPlaceEditor("العيادة", clinic, { clinic = it }, onGps = {
            gpsTarget = "clinic"
            val granted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            if (granted) useGps() else launcher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        })
        Divider(color = BLine)
        WorkPlaceEditor("المشفى", hospital, { hospital = it }, onGps = {
            gpsTarget = "hospital"
            val granted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            if (granted) useGps() else launcher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        })
        Field(bio, { bio = it }, "نبذة عنك (تظهر للمريض)")
        if (msg.isNotEmpty()) Text(msg, color = if (ok) BGreen else BDanger)
        RedButton("حفظ مواقع العمل والدوام", enabled = !busy) {
            val error = validPlace(clinic, "العيادة") ?: validPlace(hospital, "المشفى")
            if (error != null) {
                ok = false
                msg = error
                return@RedButton
            }
            fun placeData(p: WorkPlaceDraft): Map<String, Any> {
                val m = mutableMapOf<String, Any>(
                    "name" to p.name.trim(),
                    "startHour" to p.start.trim().toInt(),
                    "endHour" to p.end.trim().toInt(),
                    "workDays" to p.days.sorted(),
                    "slotMinutes" to p.step
                )
                val ll = parseLatLng(p.location)
                if (ll != null) {
                    m["lat"] = ll.first
                    m["lng"] = ll.second
                } else if (p.location.isNotBlank()) m["mapLink"] = p.location.trim()
                return m
            }
            val clinicData = placeData(clinic)
            val data = mutableMapOf<String, Any>(
                "workLocations" to mapOf("clinic" to clinicData, "hospital" to placeData(hospital)),
                "bio" to bio.trim(),
                "clinicName" to clinic.name.trim(),
                "startHour" to clinic.start.trim().toInt(),
                "endHour" to clinic.end.trim().toInt(),
                "slotMinutes" to clinic.step,
                "workDays" to clinic.days.sorted(),
                "locationUpdatedAt" to FieldValue.serverTimestamp()
            )
            val clinicLL = parseLatLng(clinic.location)
            if (clinicLL != null) {
                data["lat"] = clinicLL.first
                data["lng"] = clinicLL.second
                data["mapLink"] = FieldValue.delete()
            } else if (clinic.location.isNotBlank()) {
                data["mapLink"] = clinic.location.trim()
                data["lat"] = FieldValue.delete()
                data["lng"] = FieldValue.delete()
            }
            busy = true
            msg = ""
            bDb.collection("doctors").document(uid).get().addOnSuccessListener { d ->
                if (!d.exists()) {
                    busy = false; ok = false
                    msg = "احفظ ملفك المهني أولاً (الاسم والاختصاص والمدينة)"
                } else {
                    bDb.collection("doctors").document(uid).set(data, SetOptions.merge())
                        .addOnSuccessListener { busy = false; ok = true; msg = "تم حفظ موقعي العيادة والمشفى ودوامهما" }
                        .addOnFailureListener { busy = false; ok = false; msg = "تعذر الحفظ؛ تحقق من صلاحيات Firestore وحاول مجدداً" }
                }
            }.addOnFailureListener { busy = false; ok = false; msg = "تعذر تحميل ملف الطبيب" }
        }
    }
}

@Composable
private fun WorkPlaceEditor(
    title: String,
    value: WorkPlaceDraft,
    onChange: (WorkPlaceDraft) -> Unit,
    onGps: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text("إعدادات $title", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Field(value.name, { onChange(value.copy(name = it)) }, "اسم $title")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) { Field(value.start, { onChange(value.copy(start = it)) }, "من الساعة", keyboard = KeyboardType.Number) }
            Box(Modifier.weight(1f)) { Field(value.end, { onChange(value.copy(end = it)) }, "إلى الساعة", keyboard = KeyboardType.Number) }
        }
        Text("أيام الدوام", color = BMuted)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            WEEK_DAYS.forEach { (n, label) ->
                FilterChip(selected = n in value.days, onClick = {
                    onChange(value.copy(days = if (n in value.days) value.days - n else value.days + n))
                }, label = { Text(label) })
            }
        }
        Text("مدة الموعد", color = BMuted)
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            listOf(15, 20, 30, 45, 60).forEach { m ->
                FilterChip(selected = value.step == m, onClick = { onChange(value.copy(step = m)) }, label = { Text("$m د") })
            }
        }
        Field(value.location, { onChange(value.copy(location = it)) }, "رابط خرائط Google أو إحداثيات")
        OutlinedButton(onClick = onGps, modifier = Modifier.fillMaxWidth().height(48.dp)) {
            Text("التقاط موقع $title من GPS", color = Color.White)
        }
    }
}

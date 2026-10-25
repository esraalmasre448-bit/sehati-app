package com.sehati.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import java.util.Date

/** الصفحة الرئيسية للطبيب (التصميم المرجعي). الصفحات الفرعية تُفتح داخلها بحالة محلية. */
@Composable
fun DoctorHome(name: String, unread: Int, onGo: (String) -> Unit, onTab: (Int) -> Unit) {
    var page by remember { mutableStateOf("home") }
    BackHandler(enabled = page != "home") { page = "home" }
    when (page) {
        "patients" -> DoctorPatientsScreen(onBack = { page = "home" })
        else -> DoctorHomeContent(name, unread, onGo, onTab, onPatients = { page = "patients" })
    }
}

@Composable
private fun DoctorHomeContent(
    name: String, unread: Int,
    onGo: (String) -> Unit, onTab: (Int) -> Unit, onPatients: () -> Unit
) {
    val uid = bAuth.currentUser?.uid ?: ""
    val today = todayKey()
    var docName by remember { mutableStateOf("") }
    var specialty by remember { mutableStateOf("") }
    var clinic by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }
    var photo by remember { mutableStateOf<String?>(null) }
    var todayN by remember { mutableStateOf<Int?>(null) }
    var newN by remember { mutableStateOf<Int?>(null) }
    var waiting by remember { mutableStateOf<Long?>(null) }
    var failed by remember { mutableStateOf(false) }
    var available by remember { mutableStateOf<Boolean?>(null) }
    var toggleMsg by remember { mutableStateOf("") }

    LaunchedEffect(uid) {
        if (uid.isEmpty()) return@LaunchedEffect
        bDb.collection("doctors").document(uid).get().addOnSuccessListener { d ->
            docName = d.getString("name") ?: ""
            specialty = d.getString("specialty") ?: ""
            clinic = d.getString("clinicName") ?: ""
            city = d.getString("city") ?: ""
            photo = d.getString("photo")
            available = d.getBoolean("available")
        }
    }

    DisposableEffect(uid, today) {
        val r1 = bDb.collection("appointments").whereEqualTo("doctorId", uid).limit(300)
            .addSnapshotListener { s, e ->
                if (e != null || s == null) {
                    failed = true
                } else {
                    failed = false
                    val dayAgo = Date(System.currentTimeMillis() - 24L * 3600 * 1000)
                    val list = s.documents.map { it.data.orEmpty() }
                    todayN = list.count { it["date"] == today && it["status"] != "CANCELLED" }
                    newN = list.count {
                        it["status"] == "CONFIRMED" && (it["createdAt"] as? Timestamp)?.toDate()?.after(dayAgo) == true
                    }
                }
            }
        // الرقم الحالي = آخر رقم نُودي، آخر رقم صدر = آخر رقم أُعطي. المنتظرون = الفرق بينهما.
        val r2 = bDb.collection("queues").document("${uid}_$today").addSnapshotListener { s, _ ->
            val cur = s?.getLong("current") ?: 0L
            val lst = s?.getLong("last") ?: 0L
            waiting = (lst - cur).coerceAtLeast(0L)
        }
        onDispose { r1.remove(); r2.remove() }
    }

    val shownName = docName.ifBlank { name }
    val countText: (Long?) -> String = { n -> if (failed) "غير متاح" else n?.toString() ?: "..." }

    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // الرأس: في RTL العنصر الأول يظهر على اليمين
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DoctorAvatar(photo, shownName.ifBlank { "د" }, 44.dp)
            Box {
                IconButton(onClick = { onTab(2) }) { Icon(Icons.Filled.Notifications, null, tint = Color.White) }
                if (unread > 0) Badge(Modifier.align(Alignment.TopEnd)) { Text("$unread") }
            }
            Spacer(Modifier.weight(1f))
            Column(Modifier.weight(2f)) {
                Text("الطريق إلى صحتي", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("مريضك أخوك، فارحم وجعه وقدّر ظروفه.", fontSize = 12.sp, color = BMuted)
            }
            Image(painterResource(R.drawable.ic_launcher), null, Modifier.size(48.dp))
        }

        // بطاقة الطبيب
        Column(
            Modifier.fillMaxWidth()
                .background(BCard, RoundedCornerShape(18.dp))
                .border(1.dp, BLine, RoundedCornerShape(18.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                DoctorAvatar(photo, shownName.ifBlank { "د" }, 84.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(shownName, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    if (specialty.isNotBlank()) Text(specialty, color = BRed, fontSize = 14.sp)
                    val place = listOf(clinic, city).filter { it.isNotBlank() }.joinToString(" - ")
                    if (place.isNotBlank()) Text(place, color = BMuted, fontSize = 12.sp)
                }
            }
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    when (available) {
                        true -> "متاح لاستقبال المرضى"
                        false -> "غير متاح حالياً"
                        null -> "لم تحددي حالتك بعد"
                    },
                    color = if (available == true) BGreen else BMuted,
                    fontWeight = FontWeight.Bold
                )
                Switch(
                    checked = available == true,
                    onCheckedChange = { v ->
                        toggleMsg = ""
                        bDb.collection("doctors").document(uid)
                            .update(mapOf("available" to v, "availableUpdatedAt" to FieldValue.serverTimestamp()))
                            .addOnSuccessListener { available = v }
                            .addOnFailureListener { toggleMsg = "تعذر تغيير الحالة، تحقق من الإنترنت" }
                    }
                )
            }
            if (toggleMsg.isNotEmpty()) Text(toggleMsg, color = BDanger, fontSize = 12.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DoctorStat("مواعيد اليوم", countText(todayN?.toLong()), Modifier.weight(1f))
                DoctorStat("المرضى المنتظرون", countText(waiting), Modifier.weight(1f))
            }
        }

        Text("الأدوات", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HubTile(Icons.Filled.CalendarMonth, "المواعيد", "عرض وإدارة المواعيد", true, Modifier.weight(1f)) { onGo("dash") }
            HubTile(Icons.Filled.Group, "المرضى", "قائمة المرضى وملفاتهم", false, Modifier.weight(1f)) { onPatients() }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HubTile(Icons.Filled.Assignment, "الطلبات الجديدة", "حجوزات آخر 24 ساعة (${newN ?: "..."})", false, Modifier.weight(1f)) { onGo("dash") }
            HubTile(Icons.Filled.ManageAccounts, "إعدادات الحساب", "الملف الشخصي والتخصص", false, Modifier.weight(1f)) { onTab(3) }
        }

        Text("خدمات", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Tile(Icons.Filled.Person, "ملفي المهني", true, Modifier.fillMaxWidth()) { onGo("profile") }
        Tile(Icons.Filled.LocationOn, "إعدادات العيادة (الدوام والموقع)", false, Modifier.fillMaxWidth()) { onGo("settings") }
        Tile(Icons.Filled.Info, "تجديد الاشتراك والتواصل مع الإدارة", false, Modifier.fillMaxWidth()) { onGo("renew") }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Tile(Icons.Filled.Search, stringResource(R.string.find_doctor), false, Modifier.weight(1f)) { onGo("search") }
            Tile(Icons.Filled.Warning, "الطوارئ", false, Modifier.weight(1f)) { onGo("emergency") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Tile(Icons.Filled.Person, stringResource(R.string.my_turn), false, Modifier.weight(1f)) { onGo("queue") }
            Tile(Icons.Filled.LocationOn, stringResource(R.string.way_to_clinic), false, Modifier.weight(1f)) { onGo("route") }
        }
    }
}

@Composable
private fun DoctorStat(label: String, value: String, modifier: Modifier) {
    Column(
        modifier.background(BBg, RoundedCornerShape(14.dp))
            .border(1.dp, BLine, RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text(label, color = BMuted, fontSize = 12.sp)
    }
}

@Composable
private fun HubTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, sub: String,
    red: Boolean, modifier: Modifier, onClick: () -> Unit
) {
    Column(
        modifier.height(118.dp)
            .background(if (red) BRed else BCard, RoundedCornerShape(16.dp))
            .border(1.dp, if (red) BRed else BLine, RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, null, tint = if (red) BOnAccent else Color.White, modifier = Modifier.size(30.dp))
        Spacer(Modifier.height(6.dp))
        Text(title, color = if (red) BOnAccent else Color.White, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(sub, color = if (red) BOnAccent else BMuted, fontSize = 11.sp, textAlign = TextAlign.Center, maxLines = 2)
    }
}

/** قائمة مرضى الطبيب الحاليّ، مبنية من مواعيده، بدون تكرار حسب معرّف المريض. */
@Composable
fun DoctorPatientsScreen(onBack: () -> Unit) {
    val uid = bAuth.currentUser?.uid ?: ""
    var patients by remember { mutableStateOf<List<Map<String, Any?>>?>(null) }
    var failed by remember { mutableStateOf(false) }

    DisposableEffect(uid) {
        val reg = bDb.collection("appointments").whereEqualTo("doctorId", uid).limit(300)
            .addSnapshotListener { s, e ->
                if (e != null || s == null) {
                    failed = true
                } else {
                    failed = false
                    val rows = s.documents.map { it.data.orEmpty() }
                        .filter { it["status"] != "CANCELLED" && !it["patientId"].toString().isBlank() }
                    patients = rows.groupBy { it["patientId"].toString() }.map { (pid, list) ->
                        val latest = list.maxByOrNull { it["date"].toString() } ?: list.first()
                        mapOf(
                            "id" to pid,
                            "name" to (latest["patientName"]?.toString() ?: ""),
                            "phone" to (latest["patientPhone"]?.toString() ?: ""),
                            "count" to list.size,
                            "last" to (latest["date"]?.toString() ?: "")
                        )
                    }.sortedBy { it["name"].toString() }
                }
            }
        onDispose { reg.remove() }
    }

    Column(Modifier.fillMaxSize().background(BBg).padding(16.dp)) {
        TextButton(onClick = onBack) { Text("رجوع", color = BRed) }
        Text("المرضى", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(Modifier.height(10.dp))
        val list = patients
        when {
            failed -> Text("تعذر تحميل المرضى، تحقق من الإنترنت", color = BDanger)
            list == null -> Text("جارٍ التحميل...", color = BMuted)
            list.isEmpty() -> Text("لا يوجد مرضى مرتبطون بحجوزاتك بعد", color = BMuted)
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(list, key = { it["id"].toString() }) { p ->
                    Column(
                        Modifier.fillMaxWidth()
                            .background(BCard, RoundedCornerShape(14.dp))
                            .border(1.dp, BLine, RoundedCornerShape(14.dp))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("${p["name"]}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        val phone = p["phone"].toString()
                        if (phone.isNotBlank()) Text(phone, color = BMuted, fontSize = 13.sp)
                        Text("عدد الحجوزات: ${p["count"]}  •  آخر موعد: ${p["last"]}", color = BMuted, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

package com.sehati.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Calendar

@Composable
fun QueueScreen(onBack: () -> Unit, onFind: () -> Unit) {
    val uid = bAuth.currentUser?.uid ?: ""
    val today = remember { todayKey() }
    var items by remember { mutableStateOf(listOf<Map<String, Any?>>()) }
    var loaded by remember { mutableStateOf(false) }

    DisposableEffect(uid) {
        val reg = bDb.collection("appointments").whereEqualTo("patientId", uid).limit(100)
            .addSnapshotListener { s, e ->
                if (e == null) {
                    items = s?.documents?.map { it.data.orEmpty() + ("id" to it.id) }.orEmpty()
                        .filter { it["date"] == today && it["status"] == "CONFIRMED" }
                        .sortedBy { (it["slot"] as? Number)?.toInt() ?: 0 }
                    loaded = true
                }
            }
        onDispose { reg.remove() }
    }

    LazyColumn(
        Modifier.fillMaxSize().background(BBg).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        item { TextButton(onClick = onBack) { Text("رجوع", color = BRed) } }
        item { Text("دوري الآن", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White) }
        if (loaded && items.isEmpty()) {
            item { Text("ما عندك حجز لليوم. احجز موعدًا ليظهر دورك هنا.", color = BMuted) }
            item { RedButton("ابحث عن طبيب", onClick = onFind) }
        }
        items(items, key = { it["id"] as String }) { a -> QueueCard(a) }
    }
}

@Composable
private fun QueueCard(a: Map<String, Any?>) {
    val ctx = LocalContext.current
    val doctorId = a["doctorId"].toString()
    val date = a["date"].toString()
    val number = (a["number"] as? Number)?.toLong() ?: 0L
    var current by remember { mutableLongStateOf(0L) }
    var msg by remember { mutableStateOf("") }

    DisposableEffect(doctorId, date) {
        val reg = bDb.collection("queues").document("${doctorId}_$date")
            .addSnapshotListener { s, _ -> current = s?.getLong("current") ?: 0L }
        onDispose { reg.remove() }
    }

    val ahead = (number - current - 1).coerceAtLeast(0)
    val (statusText, color) = when {
        current == number -> "حان دورك! تفضّل بالدخول" to BGreen
        current > number -> "انتهى دورك" to BMuted
        else -> "بانتظار دورك" to Color.White
    }
    Column(
        Modifier.fillMaxWidth().background(BCard, RoundedCornerShape(14.dp))
            .border(1.dp, if (current == number) BGreen else BLine, RoundedCornerShape(14.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text("${a["doctorName"]}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text("الساعة ${a["time"]}", color = BMuted)
        Text("رقمك: $number", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text("الرقم الحالي: $current", color = Color.White, fontSize = 16.sp)
        if (current < number) Text("عدد الأشخاص قبلك: $ahead", color = Color.White, fontSize = 16.sp)
        Text(statusText, color = color, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        if (msg.isNotEmpty()) Text(msg, color = BRed)
        Button(
            onClick = { routeToDoctor(ctx, doctorId) { msg = it } },
            colors = ButtonDefaults.buttonColors(containerColor = BRed, contentColor = Color.White)
        ) { Text("الطريق إلى العيادة") }
    }
}

@Composable
fun RouteScreen(onBack: () -> Unit, onFind: () -> Unit) {
    val ctx = LocalContext.current
    val uid = bAuth.currentUser?.uid ?: ""
    val today = remember { todayKey() }
    var items by remember { mutableStateOf(listOf<Map<String, Any?>>()) }
    var loaded by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }

    DisposableEffect(uid) {
        val reg = bDb.collection("appointments").whereEqualTo("patientId", uid).limit(100)
            .addSnapshotListener { s, e ->
                if (e == null) {
                    items = s?.documents?.map { it.data.orEmpty() + ("id" to it.id) }.orEmpty()
                        .filter { it["status"] == "CONFIRMED" && it["date"].toString() >= today }
                        .sortedBy { it["date"].toString() }
                    loaded = true
                }
            }
        onDispose { reg.remove() }
    }

    LazyColumn(
        Modifier.fillMaxSize().background(BBg).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        item { TextButton(onClick = onBack) { Text("رجوع", color = BRed) } }
        item { Text("الطريق إلى العيادة", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White) }
        if (msg.isNotEmpty()) item { Text(msg, color = BRed) }
        if (loaded && items.isEmpty()) {
            item { Text("ما عندك مواعيد قادمة. احجز موعدًا لتظهر عيادتك هنا.", color = BMuted) }
            item { RedButton("ابحث عن طبيب", onClick = onFind) }
        }
        items(items, key = { it["id"] as String }) { a ->
            Column(
                Modifier.fillMaxWidth().background(BCard, RoundedCornerShape(14.dp))
                    .border(1.dp, BLine, RoundedCornerShape(14.dp)).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("${a["doctorName"]}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("${a["clinicName"] ?: ""}  ${a["city"] ?: ""}", color = BMuted)
                Text("${a["date"]}  •  ${a["time"]}", color = Color.White)
                Button(
                    onClick = { routeToDoctor(ctx, a["doctorId"].toString()) { msg = it } },
                    colors = ButtonDefaults.buttonColors(containerColor = BRed, contentColor = Color.White)
                ) { Text("افتح الطريق في خرائط جوجل") }
            }
        }
    }
}

@Composable
fun DoctorDashboardContent(onBack: (() -> Unit)? = null) {
    val uid = bAuth.currentUser?.uid ?: ""
    val st = subState(uid)
    val active = st == "active"
    val days = remember { (0..6).map { i -> Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, i) } } }
    var idx by remember { mutableIntStateOf(0) }
    val date = dateKey(days[idx])
    var items by remember { mutableStateOf(listOf<Map<String, Any?>>()) }
    var current by remember { mutableLongStateOf(0L) }
    var last by remember { mutableLongStateOf(0L) }
    var msg by remember { mutableStateOf("") }

    DisposableEffect(uid, date) {
        val r1 = bDb.collection("appointments")
            .whereEqualTo("doctorId", uid).whereEqualTo("date", date)
            .addSnapshotListener { s, e ->
                if (e == null) {
                    items = s?.documents?.map { it.data.orEmpty() + ("id" to it.id) }.orEmpty()
                        .sortedBy { (it["number"] as? Number)?.toLong() ?: 0L }
                }
            }
        val r2 = bDb.collection("queues").document("${uid}_$date").addSnapshotListener { s, _ ->
            current = s?.getLong("current") ?: 0L
            last = s?.getLong("last") ?: 0L
        }
        onDispose { r1.remove(); r2.remove() }
    }

    LazyColumn(
        Modifier.fillMaxSize().background(BBg).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        if (onBack != null) item { TextButton(onClick = onBack) { Text("رجوع", color = BRed) } }
        item { Text("لوحتي — الحجوزات", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White) }
        item { LockNote(st) }
        item { DayChips(days, idx) { idx = it } }
        item {
            Column(
                Modifier.fillMaxWidth().background(BCard, RoundedCornerShape(14.dp))
                    .border(1.dp, BLine, RoundedCornerShape(14.dp)).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("الرقم الحالي: $current", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("آخر رقم صدر: $last", color = BMuted)
                Button(
                    enabled = active && current < last,
                    onClick = {
                        bDb.collection("queues").document("${uid}_$date")
                            .update("current", current + 1)
                            .addOnFailureListener { msg = "تعذر تحديث الدور، تحقق من الإنترنت واشتراكك" }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BRed, contentColor = Color.White),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("نادِ التالي") }
            }
        }
        if (msg.isNotEmpty()) item { Text(msg, color = BRed) }
        if (items.isEmpty()) item { Text("لا توجد حجوزات لهذا اليوم", color = BMuted) }
        items(items, key = { it["id"] as String }) { a ->
            val (label, color) = statusLabel(a["status"])
            val id = a["id"] as String
            Column(
                Modifier.fillMaxWidth().background(BCard, RoundedCornerShape(14.dp))
                    .border(1.dp, BLine, RoundedCornerShape(14.dp)).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("رقم ${a["number"]} — ${a["patientName"]}", fontWeight = FontWeight.Bold, color = Color.White)
                Text("${a["patientPhone"] ?: ""}  •  الساعة ${a["time"]}", color = BMuted)
                val note = a["note"]?.toString().orEmpty()
                if (note.isNotBlank()) Text("ملاحظة: $note", color = BMuted)
                Text(label, color = color, fontWeight = FontWeight.Bold)
                if (a["status"] == "CONFIRMED") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            enabled = active,
                            onClick = {
                                setStatus(id, "DONE", false) { ok ->
                                    msg = if (ok) "" else "تعذر التحديث، تحقق من الإنترنت واشتراكك"
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = BRed, contentColor = Color.White)
                        ) { Text("تم") }
                        OutlinedButton(
                            enabled = active,
                            onClick = {
                                setStatus(id, "CANCELLED", true) { ok ->
                                    msg = if (ok) "" else "تعذر الإلغاء، تحقق من الإنترنت واشتراكك"
                                }
                            }
                        ) { Text("إلغاء الحجز", color = Color.White) }
                    }
                }
            }
        }
    }
}

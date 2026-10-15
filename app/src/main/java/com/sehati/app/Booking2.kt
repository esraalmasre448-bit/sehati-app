package com.sehati.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.firestore.FieldValue
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

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
        if (msg.isNotEmpty()) Text(msg, color = BDanger)
        Button(
            onClick = { routeToDoctor(ctx, doctorId) { msg = it } },
            colors = ButtonDefaults.buttonColors(containerColor = BRed, contentColor = BOnAccent)
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
        if (msg.isNotEmpty()) item { Text(msg, color = BDanger) }
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
                    colors = ButtonDefaults.buttonColors(containerColor = BRed, contentColor = BOnAccent)
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
    var renew by remember { mutableStateOf(false) }
    var notes by remember { mutableStateOf(listOf<Map<String, Any?>>()) }
    var myName by remember { mutableStateOf("") }
    var noteFor by remember { mutableStateOf<Map<String, Any?>?>(null) }
    var noteKind by remember { mutableStateOf("rx") }
    var noteText by remember { mutableStateOf("") }
    var noteBusy by remember { mutableStateOf(false) }
    var noteMsg by remember { mutableStateOf("") }

    LaunchedEffect(uid) {
        if (uid.isNotEmpty()) {
            bDb.collection("doctors").document(uid).get().addOnSuccessListener { myName = it.getString("name") ?: "" }
        }
    }
    DisposableEffect(uid) {
        val regN = bDb.collection("prescriptions").whereEqualTo("doctorId", uid).limit(300)
            .addSnapshotListener { s, e ->
                if (e == null) notes = s?.documents?.map { it.data.orEmpty() + ("id" to it.id) }.orEmpty()
            }
        onDispose { regN.remove() }
    }

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

    if (renew) {
        RenewalScreen(onBack = { renew = false })
        return
    }

    LazyColumn(
        Modifier.fillMaxSize().background(BBg).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        if (onBack != null) item { TextButton(onClick = onBack) { Text("رجوع", color = BRed) } }
        item { Text("لوحتي — الحجوزات", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White) }
        if (st == "expired" || st == "cancelled") {
            item { ExpiredCard { renew = true } }
        } else {
            item { LockNote(st) }
            if (st != "active" && st != "loading") {
                item { RedButton("تجديد الاشتراك والتواصل مع الإدارة") { renew = true } }
            }
        }
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
                    colors = ButtonDefaults.buttonColors(containerColor = BRed, contentColor = BOnAccent),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("نادِ التالي") }
            }
        }
        if (msg.isNotEmpty()) item { Text(msg, color = BDanger) }
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
                notes.filter { it["appointmentId"] == id }
                    .sortedBy { (it["createdAt"] as? com.google.firebase.Timestamp)?.seconds ?: 0L }
                    .forEach { n ->
                        NoteBox(n) {
                            bDb.collection("prescriptions").document(n["id"] as String).delete()
                                .addOnFailureListener { msg = "تعذر الحذف، تحقق من الإنترنت" }
                        }
                    }
                if (a["status"] != "CANCELLED") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            enabled = active,
                            onClick = { noteKind = "rx"; noteText = ""; noteMsg = ""; noteFor = a }
                        ) { Text("اكتب راشيتة", color = Color.White) }
                        OutlinedButton(
                            enabled = active,
                            onClick = { noteKind = "lab"; noteText = ""; noteMsg = ""; noteFor = a }
                        ) { Text("اطلب تحاليل", color = Color.White) }
                    }
                }
                if (a["status"] == "CONFIRMED") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            enabled = active,
                            onClick = {
                                setStatus(id, "DONE", false) { ok ->
                                    msg = if (ok) "" else "تعذر التحديث، تحقق من الإنترنت واشتراكك"
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = BRed, contentColor = BOnAccent)
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

    val nf = noteFor
    if (nf != null) {
        AlertDialog(
            onDismissRequest = { if (!noteBusy) noteFor = null },
            title = { Text(if (noteKind == "rx") "راشيتة للمريض ${nf["patientName"]}" else "طلب تحاليل للمريض ${nf["patientName"]}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("سيظهر هذا النص للمريض داخل التطبيق تحت موعده.", fontSize = 13.sp)
                    OutlinedTextField(
                        value = noteText, onValueChange = { if (it.length <= 2000) noteText = it },
                        minLines = 4, maxLines = 8,
                        label = { Text(if (noteKind == "rx") "الأدوية والجرعات والتعليمات" else "التحاليل المطلوبة") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (noteMsg.isNotEmpty()) Text(noteMsg, color = BDanger)
                }
            },
            confirmButton = {
                TextButton(enabled = !noteBusy, onClick = {
                    if (noteText.isBlank()) { noteMsg = "اكتب النص أولاً"; return@TextButton }
                    noteBusy = true; noteMsg = ""
                    bDb.collection("prescriptions").add(
                        mapOf(
                            "doctorId" to uid, "doctorName" to myName,
                            "patientId" to (nf["patientId"] ?: ""), "patientName" to (nf["patientName"] ?: ""),
                            "appointmentId" to (nf["id"] ?: ""), "kind" to noteKind,
                            "text" to noteText.trim(), "createdAt" to FieldValue.serverTimestamp()
                        )
                    ).addOnSuccessListener { noteBusy = false; noteFor = null }
                        .addOnFailureListener { noteBusy = false; noteMsg = "تعذر الحفظ، تحقق من الإنترنت واشتراكك" }
                }) { Text("حفظ وإرسال للمريض") }
            },
            dismissButton = { TextButton(enabled = !noteBusy, onClick = { noteFor = null }) { Text("إلغاء") } }
        )
    }
}

@Composable
fun NoteBox(n: Map<String, Any?>, onDelete: (() -> Unit)?) {
    val rx = n["kind"] == "rx"
    val ts = (n["createdAt"] as? com.google.firebase.Timestamp)?.toDate()
    val dateTxt = if (ts != null) SimpleDateFormat("d/M/yyyy", Locale.US).format(ts) else ""
    Column(
        Modifier.fillMaxWidth()
            .background(BBg, RoundedCornerShape(10.dp))
            .border(1.dp, BRed, RoundedCornerShape(10.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            (if (rx) "راشيتة" else "طلب تحاليل") + (if (dateTxt.isNotEmpty()) " • $dateTxt" else "") +
                (if (onDelete == null && "${n["doctorName"] ?: ""}".isNotBlank()) " • من د. ${n["doctorName"]}" else ""),
            color = BRed, fontWeight = FontWeight.Bold, fontSize = 13.sp
        )
        Text("${n["text"] ?: ""}", color = Color.White)
        if (onDelete == null) Text("ملاحظة طبية من الطبيب عبر التطبيق، وليست وصفة رسمية.", color = BMuted, fontSize = 11.sp)
        if (onDelete != null) TextButton(onClick = onDelete) { Text("حذف", color = BDanger) }
    }
}

@Composable
fun ExpiredCard(onRenew: () -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .background(BCard, RoundedCornerShape(16.dp))
            .border(1.dp, BRed, RoundedCornerShape(16.dp))
            .padding(20.dp),
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        androidx.compose.material3.Icon(
            Icons.Filled.CalendarMonth, null,
            tint = BRed, modifier = Modifier.size(56.dp)
        )
        Text("انتهى اشتراكك", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text(
            "عزيزي الطبيب، انتهى اشتراكك في التطبيق. لتجديد الاشتراك والتمتع بجميع المزايا، تواصل مع الإدارة.",
            color = BMuted, textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        RedButton("تجديد الاشتراك") { onRenew() }
    }
}

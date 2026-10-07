package com.sehati.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val BBg = Color(0xFF0A0C11)
private val BCard = Color(0xFF14171D)
private val BLine = Color(0xFF2A2F3A)
private val BRed = Color(0xFFE5171F)
private val BMuted = Color(0xFF9AA0AB)
private val BGreen = Color(0xFF3DDC84)

private val bAuth get() = FirebaseAuth.getInstance()
private val bDb get() = FirebaseFirestore.getInstance()

private fun num(v: Any?): Int? = (v as? Number)?.toInt()
private fun dbl(v: Any?): Double? = (v as? Number)?.toDouble()
private fun dateKey(c: Calendar): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(c.time)
private fun todayKey(): String = dateKey(Calendar.getInstance())
private fun slotLabel(minutes: Int): String = "%02d:%02d".format(minutes / 60, minutes % 60)

private fun slotsOf(d: Map<String, Any?>): List<Int> {
    val s = num(d["startHour"]) ?: 9
    val e = num(d["endHour"]) ?: 17
    val step = (num(d["slotMinutes"]) ?: 30).coerceAtLeast(10)
    val out = mutableListOf<Int>()
    var m = s * 60
    while (m + step <= e * 60) { out.add(m); m += step }
    return out
}

fun openRoute(ctx: Context, d: Map<String, Any?>): String? {
    val lat = dbl(d["lat"])
    val lng = dbl(d["lng"])
    val link = d["mapLink"]?.toString().orEmpty()
    val addr = listOf(d["address"], d["city"])
        .mapNotNull { it?.toString()?.takeIf { s -> s.isNotBlank() } }
        .joinToString("، ")
    val uri = when {
        lat != null && lng != null ->
            Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$lat,$lng")
        link.startsWith("http") -> Uri.parse(link)
        addr.isNotBlank() ->
            Uri.parse("https://www.google.com/maps/dir/?api=1&destination=" + Uri.encode(addr))
        else -> return "لم يحدد الطبيب موقع العيادة بعد"
    }
    return try {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        null
    } catch (e: Exception) {
        "تعذر فتح الخرائط على هذا الجهاز"
    }
}

private fun routeToDoctor(ctx: Context, doctorId: String, onMsg: (String) -> Unit) {
    bDb.collection("doctors").document(doctorId).get()
        .addOnSuccessListener { s -> onMsg(openRoute(ctx, s.data.orEmpty()) ?: "") }
        .addOnFailureListener { onMsg("تعذر تحميل موقع العيادة، تحقق من الإنترنت") }
}

private fun parseLatLng(s: String): Pair<Double, Double>? {
    val m = Regex("""(-?\d{1,3}\.\d+)\s*[, ]\s*(-?\d{1,3}\.\d+)""").find(s) ?: return null
    val a = m.groupValues[1].toDoubleOrNull() ?: return null
    val b = m.groupValues[2].toDoubleOrNull() ?: return null
    return a to b
}

private fun statusLabel(s: Any?): Pair<String, Color> = when (s) {
    "CONFIRMED" -> "مؤكد" to BGreen
    "DONE" -> "منتهي" to BMuted
    "CANCELLED" -> "ملغى" to BRed
    else -> "-" to BMuted
}

private fun book(
    d: Map<String, Any?>, date: String, slot: Int, note: String,
    onResult: (Boolean, String) -> Unit
) {
    val me = bAuth.currentUser
    if (me == null) { onResult(false, "سجّل الدخول أولًا"); return }
    val doctorId = d["id"] as String
    val id = "${doctorId}_${date}_$slot"
    val slotRef = bDb.collection("slots").document(id)
    val apptRef = bDb.collection("appointments").document(id)
    val queueRef = bDb.collection("queues").document("${doctorId}_$date")
    bDb.collection("users").document(me.uid).get().addOnSuccessListener { u ->
        val pName = u.getString("name") ?: ""
        val pPhone = u.getString("phone") ?: ""
        bDb.runTransaction<Long> { tx ->
            if (tx.get(slotRef).exists()) return@runTransaction -1L
            val q = tx.get(queueRef)
            val number: Long
            if (q.exists()) {
                number = (q.getLong("last") ?: 0L) + 1L
                tx.update(queueRef, "last", number)
            } else {
                number = 1L
                tx.set(
                    queueRef,
                    mapOf("doctorId" to doctorId, "date" to date, "last" to 1L, "current" to 0L)
                )
            }
            tx.set(
                slotRef,
                mapOf("doctorId" to doctorId, "date" to date, "slot" to slot, "patientId" to me.uid)
            )
            tx.set(
                apptRef,
                mapOf(
                    "patientId" to me.uid, "patientName" to pName, "patientPhone" to pPhone,
                    "doctorId" to doctorId,
                    "doctorName" to (d["name"]?.toString() ?: ""),
                    "specialty" to (d["specialty"]?.toString() ?: ""),
                    "clinicName" to (d["clinicName"]?.toString() ?: ""),
                    "address" to (d["address"]?.toString() ?: ""),
                    "city" to (d["city"]?.toString() ?: ""),
                    "date" to date, "slot" to slot, "time" to slotLabel(slot),
                    "number" to number, "status" to "CONFIRMED", "note" to note,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            number
        }.addOnSuccessListener { n ->
            if (n < 0) onResult(false, "هذا الموعد انحجز للتو، اختر وقتًا آخر")
            else onResult(true, "تم حجز موعدك الساعة ${slotLabel(slot)} بتاريخ $date. رقمك بالدور: $n")
        }.addOnFailureListener {
            onResult(false, "تعذر الحجز. تحقق من الإنترنت، أو أن الطبيب غير متاح للحجز حاليًا")
        }
    }.addOnFailureListener { onResult(false, "تعذر تحميل بياناتك، تحقق من الإنترنت") }
}

private fun setStatus(id: String, status: String, freeSlot: Boolean, onDone: (Boolean) -> Unit) {
    val batch = bDb.batch()
    batch.update(bDb.collection("appointments").document(id), "status", status)
    if (freeSlot) batch.delete(bDb.collection("slots").document(id))
    batch.commit().addOnSuccessListener { onDone(true) }.addOnFailureListener { onDone(false) }
}

@Composable
private fun subState(uid: String): String {
    var st by remember { mutableStateOf("loading") }
    DisposableEffect(uid) {
        val reg = bDb.collection("subscriptions").document(uid).addSnapshotListener { s, e ->
            st = when {
                e != null -> "error"
                s == null || !s.exists() -> "none"
                s.getString("status") == "CANCELLED" -> "cancelled"
                s.getString("status") == "ACTIVE" &&
                    (s.getTimestamp("expiresAt")?.toDate()?.after(Date()) == true) -> "active"
                else -> "expired"
            }
        }
        onDispose { reg.remove() }
    }
    return st
}

@Composable
private fun LockNote(st: String) {
    val text = when (st) {
        "none" -> "لا يوجد اشتراك فعّال لحسابك. تواصل مع إدارة التطبيق لتفعيل اشتراكك."
        "expired", "cancelled" -> "انتهى اشتراكك.\nقم بتجديد الاشتراك لاستعادة جميع مزايا حسابك."
        else -> ""
    }
    if (text.isNotEmpty()) {
        Text(
            text, color = BRed,
            modifier = Modifier.fillMaxWidth()
                .background(Color(0xFF2A1215), RoundedCornerShape(12.dp))
                .border(1.dp, BRed, RoundedCornerShape(12.dp)).padding(12.dp)
        )
    }
}

@Composable
private fun DayChips(days: List<Calendar>, idx: Int, onSelect: (Int) -> Unit) {
    val fmt = remember { SimpleDateFormat("EEE d/M", Locale("ar")) }
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        days.forEachIndexed { i, c ->
            FilterChip(
                selected = idx == i, onClick = { onSelect(i) },
                label = { Text(if (i == 0) "اليوم" else fmt.format(c.time)) }
            )
        }
    }
}

@Composable
private fun BInfo(label: String, value: Any?) {
    val v = value?.toString().orEmpty()
    if (v.isNotBlank()) {
        Column(
            Modifier.fillMaxWidth().background(BCard, RoundedCornerShape(12.dp))
                .border(1.dp, BLine, RoundedCornerShape(12.dp)).padding(12.dp)
        ) {
            Text(label, color = BMuted, fontSize = 13.sp)
            Text(v, color = Color.White, fontSize = 16.sp)
        }
    }
}

@Composable
fun DoctorDetailFull(d: Map<String, Any?>, onBack: () -> Unit, onBook: () -> Unit) {
    val ctx = LocalContext.current
    var msg by remember { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().background(BBg).padding(20.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TextButton(onClick = onBack) { Text("رجوع", color = BRed) }
        Text("${d["name"]}", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text("${d["specialty"]}", fontSize = 16.sp, color = BRed)
        BInfo("نبذة", d["bio"])
        BInfo("العيادة", d["clinicName"])
        BInfo("المدينة", d["city"])
        BInfo("العنوان", d["address"])
        BInfo("رقم التواصل", d["phone"])
        BInfo(
            "ساعات الدوام",
            "${slotLabel((num(d["startHour"]) ?: 9) * 60)} - ${slotLabel((num(d["endHour"]) ?: 17) * 60)}"
        )
        BInfo("سعر الكشفية", d["fee"])
        if (msg.isNotEmpty()) Text(msg, color = BRed)
        RedButton("احجز موعد", onClick = onBook)
        OutlinedButton(
            onClick = { msg = openRoute(ctx, d) ?: "" },
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) { Text("الطريق إلى العيادة", color = Color.White) }
        val phone = d["phone"]?.toString().orEmpty()
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

@Composable
fun BookingScreen(d: Map<String, Any?>, onBack: () -> Unit, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val doctorId = d["id"] as String
    val days = remember { (0..6).map { i -> Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, i) } } }
    var dayIdx by remember { mutableIntStateOf(0) }
    var taken by remember { mutableStateOf(setOf<Int>()) }
    var selected by remember { mutableStateOf<Int?>(null) }
    var note by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    var done by remember { mutableStateOf(false) }
    var resultText by remember { mutableStateOf("") }
    val date = dateKey(days[dayIdx])
    val slots = remember(d) { slotsOf(d) }
    val nowMin = Calendar.getInstance().let { it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE) }

    DisposableEffect(doctorId, date) {
        selected = null
        val reg = bDb.collection("slots")
            .whereEqualTo("doctorId", doctorId).whereEqualTo("date", date)
            .addSnapshotListener { s, _ ->
                taken = s?.documents?.mapNotNull { (it.get("slot") as? Number)?.toInt() }?.toSet().orEmpty()
            }
        onDispose { reg.remove() }
    }

    Column(
        Modifier.fillMaxSize().background(BBg).padding(20.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TextButton(onClick = onBack) { Text("رجوع", color = BRed) }
        Text("حجز موعد", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text("${d["name"]} — ${d["specialty"]}", color = BRed)
        if (done) {
            Text(
                resultText, color = BGreen, fontSize = 16.sp,
                modifier = Modifier.fillMaxWidth()
                    .background(BCard, RoundedCornerShape(12.dp))
                    .border(1.dp, BGreen, RoundedCornerShape(12.dp)).padding(14.dp)
            )
            if (msg.isNotEmpty()) Text(msg, color = BRed)
            RedButton("عرض موقع العيادة") { msg = openRoute(ctx, d) ?: "" }
            OutlinedButton(
                onClick = onDone, modifier = Modifier.fillMaxWidth().height(52.dp)
            ) { Text("مواعيدي", color = Color.White) }
        } else {
            Text("اختر اليوم", color = BMuted)
            DayChips(days, dayIdx) { dayIdx = it }
            Text("اختر الوقت", color = BMuted)
            if (slots.isEmpty()) Text("لا توجد أوقات متاحة", color = BMuted)
            slots.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { s ->
                        FilterChip(
                            selected = selected == s,
                            onClick = { selected = s },
                            enabled = s !in taken && !(dayIdx == 0 && s <= nowMin),
                            label = { Text(slotLabel(s)) }
                        )
                    }
                }
            }
            Field(note, { note = it }, "ملاحظة للطبيب (اختياري)")
            if (msg.isNotEmpty()) Text(msg, color = BRed)
            RedButton("تأكيد الحجز", enabled = !busy && selected != null) {
                val s = selected ?: return@RedButton
                busy = true
                msg = ""
                book(d, date, s, note.trim()) { ok, text ->
                    busy = false
                    if (ok) { done = true; resultText = text } else msg = text
                }
            }
        }
    }
}

@Composable
fun MyAppointmentsContent(onFind: () -> Unit, onBack: (() -> Unit)? = null) {
    val ctx = LocalContext.current
    val uid = bAuth.currentUser?.uid ?: ""
    var items by remember { mutableStateOf(listOf<Map<String, Any?>>()) }
    var loaded by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    var cancelId by remember { mutableStateOf<String?>(null) }

    DisposableEffect(uid) {
        val reg = bDb.collection("appointments").whereEqualTo("patientId", uid).limit(100)
            .addSnapshotListener { s, e ->
                if (e != null) msg = "تعذر تحميل المواعيد، تحقق من الإنترنت"
                else {
                    items = s?.documents?.map { it.data.orEmpty() + ("id" to it.id) }.orEmpty()
                        .sortedWith(
                            compareBy<Map<String, Any?>>(
                                { it["date"].toString() },
                                { (it["slot"] as? Number)?.toInt() ?: 0 }
                            )
                        )
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
        if (onBack != null) item { TextButton(onClick = onBack) { Text("رجوع", color = BRed) } }
        item { Text("مواعيدي", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White) }
        if (msg.isNotEmpty()) item { Text(msg, color = BRed) }
        if (loaded && items.isEmpty()) {
            item { Text("ما عندك مواعيد بعد.", color = BMuted) }
            item { RedButton("ابحث عن طبيب واحجز موعد", onClick = onFind) }
        }
        items(items, key = { it["id"] as String }) { a ->
            val (label, color) = statusLabel(a["status"])
            Column(
                Modifier.fillMaxWidth().background(BCard, RoundedCornerShape(14.dp))
                    .border(1.dp, BLine, RoundedCornerShape(14.dp)).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("${a["doctorName"]}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("${a["specialty"] ?: ""}", color = BRed)
                Text("${a["clinicName"] ?: ""}  ${a["city"] ?: ""}", color = BMuted)
                Text("التاريخ: ${a["date"]}  •  الساعة: ${a["time"]}", color = Color.White)
                Text("رقمك بالدور: ${a["number"]}", color = Color.White, fontWeight = FontWeight.Bold)
                Text(label, color = color, fontWeight = FontWeight.Bold)
                if (a["status"] == "CONFIRMED") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { routeToDoctor(ctx, a["doctorId"].toString()) { msg = it } },
                            colors = ButtonDefaults.buttonColors(containerColor = BRed, contentColor = Color.White)
                        ) { Text("الطريق إلى العيادة") }
                        OutlinedButton(onClick = { cancelId = a["id"] as String }) {
                            Text("إلغاء الموعد", color = Color.White)
                        }
                    }
                }
            }
        }
    }

    val cid = cancelId
    if (cid != null) {
        AlertDialog(
            onDismissRequest = { cancelId = null },
            title = { Text("إلغاء الموعد") },
            text = { Text("هل تريد إلغاء هذا الموعد؟") },
            confirmButton = {
                TextButton(onClick = {
                    setStatus(cid, "CANCELLED", true) { ok ->
                        msg = if (ok) "تم إلغاء الموعد" else "تعذر الإلغاء، حاول مرة أخرى"
                        cancelId = null
                    }
                }) { Text("تأكيد الإلغاء", color = BRed) }
            },
            dismissButton = { TextButton(onClick = { cancelId = null }) { Text("رجوع") } }
        )
    }
}

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
        Modifier.

package com.sehati.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

val BBg = Color(0xFF0A0C11)
val BCard = Color(0xFF14171D)
val BLine = Color(0xFF30343D)
val BRed = Color(0xFFE5171F)
val BDanger = Color(0xFFFF5A5F)
val BOnAccent = Color(0xFFFFFFFF)
val BMuted = Color(0xFF9AA7BD)
val BGreen = Color(0xFF3DDC84)

val bAuth get() = FirebaseAuth.getInstance()
val bDb get() = FirebaseFirestore.getInstance()

private fun num(v: Any?): Int? = (v as? Number)?.toInt()
private fun dbl(v: Any?): Double? = (v as? Number)?.toDouble()
fun dateKey(c: Calendar): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(c.time)
fun todayKey(): String = dateKey(Calendar.getInstance())
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

fun routeToDoctor(ctx: Context, doctorId: String, onMsg: (String) -> Unit) {
    bDb.collection("doctors").document(doctorId).get()
        .addOnSuccessListener { s -> onMsg(openRoute(ctx, s.data.orEmpty()) ?: "") }
        .addOnFailureListener { onMsg("تعذر تحميل موقع العيادة، تحقق من الإنترنت") }
}

fun parseLatLng(s: String): Pair<Double, Double>? {
    val m = Regex("""(-?\d{1,3}\.\d+)\s*[, ]\s*(-?\d{1,3}\.\d+)""").find(s) ?: return null
    val a = m.groupValues[1].toDoubleOrNull() ?: return null
    val b = m.groupValues[2].toDoubleOrNull() ?: return null
    return a to b
}

fun statusLabel(s: Any?): Pair<String, Color> = when (s) {
    "PENDING" -> "بانتظار موافقة الطبيب" to Color(0xFFFFC857)
    "CONFIRMED" -> "مؤكد" to BGreen
    "DONE" -> "منتهي" to BMuted
    "CANCELLED" -> "ملغى" to BDanger
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
            else {
                PushSender.notifyAppointment(id, "booking_created")
                onResult(true, "تم حجز موعدك الساعة ${slotLabel(slot)} بتاريخ $date. رقمك بالدور: $n")
            }
        }.addOnFailureListener {
            onResult(false, "تعذر الحجز. تحقق من الإنترنت، أو أن الطبيب غير متاح للحجز حاليًا")
        }
    }.addOnFailureListener { onResult(false, "تعذر تحميل بياناتك، تحقق من الإنترنت") }
}

@Suppress("UNUSED_PARAMETER")
fun setStatus(id: String, status: String, freeSlot: Boolean, onDone: (Boolean) -> Unit) {
    // لا نحذف وثيقة slots هنا: مسار الحجز القديم معطّل بقاعدة create،
    // والحذف كان يُفشل الإلغاء كله إذا لم تكن الوثيقة موجودة.
    // الإلغاء يحدّث حالة الموعد فقط.
    bDb.collection("appointments").document(id)
        .update("status", status)
        .addOnSuccessListener {
            val event = when (status) {
                "CONFIRMED" -> "booking_approved"
                "CANCELLED" -> "appointment_cancelled"
                "DONE" -> "appointment_done"
                else -> null
            }
            if (event != null) PushSender.notifyAppointment(id, event)
            onDone(true)
        }
        .addOnFailureListener { onDone(false) }
}

@Composable
fun subState(uid: String): String {
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
fun LockNote(st: String) {
    val text = when (st) {
        "none" -> "لا يوجد اشتراك فعّال لحسابك. تواصل مع إدارة التطبيق لتفعيل اشتراكك."
        "expired", "cancelled" -> "انتهى اشتراكك.\nقم بتجديد الاشتراك لاستعادة جميع مزايا حسابك."
        else -> ""
    }
    if (text.isNotEmpty()) {
        Text(
            text, color = BDanger,
            modifier = Modifier.fillMaxWidth()
                .background(Color(0xFF2A1215), RoundedCornerShape(12.dp))
                .border(1.dp, BDanger, RoundedCornerShape(12.dp)).padding(12.dp)
        )
    }
}

@Composable
fun DayChips(days: List<Calendar>, idx: Int, onSelect: (Int) -> Unit) {
    val fmt = remember { SimpleDateFormat("EEE d/M", Locale("ar")) }
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        days.forEachIndexed { i, c ->
            FilterChip(
                selected = idx == i, onClick = { onSelect(i) },
                label = { Text(if (dateKey(c) == todayKey()) "اليوم" else fmt.format(c.time)) }
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
fun DoctorDetailFullOld(d: Map<String, Any?>, onBack: () -> Unit, onBook: () -> Unit) {
    val ctx = LocalContext.current
    val doctorId = d["id"]?.toString().orEmpty()
    var privatePhone by remember(doctorId) { mutableStateOf("") }
    var tab by remember { mutableIntStateOf(0) }
    var msg by remember { mutableStateOf("") }

    LaunchedEffect(doctorId) {
        if (doctorId.isNotBlank()) {
            bDb.collection("doctorPrivate").document(doctorId).get()
                .addOnSuccessListener { privatePhone = it.getString("phone").orEmpty() }
        }
    }

    Column(
        Modifier.fillMaxSize().background(BBg).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) { Text("رجوع", color = BRed) }
            Spacer(Modifier.weight(1f))
            Text("الملف الطبي", color = BMuted, fontSize = 13.sp)
        }

        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                .background(BCard, RoundedCornerShape(22.dp))
                .border(1.dp, BLine, RoundedCornerShape(22.dp))
                .padding(18.dp),
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box {
                DoctorAvatar(d["photo"]?.toString(), "${d["name"] ?: ""}", 104.dp)
                if (d["available"] == true) {
                    Text(
                        "متاح",
                        Modifier.align(androidx.compose.ui.Alignment.BottomEnd)
                            .background(BGreen, RoundedCornerShape(12.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold
                    )
                }
            }
            Text("${d["name"] ?: "الطبيب"}", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text("${d["specialty"] ?: ""}", fontSize = 15.sp, color = BRed, fontWeight = FontWeight.SemiBold)
            val place = listOf(d["clinicName"]?.toString(), d["city"]?.toString())
                .filter { !it.isNullOrBlank() }.joinToString(" • ")
            if (place.isNotBlank()) Text(place, color = BMuted, fontSize = 13.sp)
            RedButton("احجز موعد", onClick = onBook)
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                .background(BCard, RoundedCornerShape(14.dp))
                .border(1.dp, BLine, RoundedCornerShape(14.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf("معلومات عامة", "الموقع والتواصل").forEachIndexed { index, label ->
                Box(
                    Modifier.weight(1f).height(42.dp)
                        .background(if (tab == index) BRed else Color.Transparent, RoundedCornerShape(11.dp))
                        .clickable { tab = index },
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    Text(label, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
            }
        }

        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (tab == 0) {
                BInfo("نبذة عن الطبيب", d["bio"])
                BInfo("سعر الكشفية", d["fee"])
                BInfo(
                    "ساعات استقبال الحجوزات",
                    "${slotLabel((num(d["startHour"]) ?: 9) * 60)} - ${slotLabel((num(d["endHour"]) ?: 17) * 60)}"
                )
                if (hasWorkDays(d)) BInfo("أيام الدوام", workDaysLabel(workDaysOf(d)))
                BInfo("أيام وساعات الدوام بالعيادة", d["hours"])
                BInfo("المشفى", d["hospitalName"])
                BInfo("أيام وساعات الدوام بالمشفى", d["hospitalHours"])
            } else {
                BInfo("اسم العيادة", d["clinicName"])
                BInfo("المدينة", d["city"])
                BInfo("العنوان", d["address"])
                BInfo("رقم التواصل", privatePhone)
                if (msg.isNotEmpty()) Text(msg, color = BDanger, fontSize = 12.sp)
                OutlinedButton(
                    onClick = { msg = openRoute(ctx, d) ?: "" },
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) { Text("الطريق إلى العيادة", color = Color.White) }
                if (privatePhone.isNotBlank()) {
                    OutlinedButton(
                        onClick = {
                            try {
                                ctx.startActivity(
                                    Intent(Intent.ACTION_DIAL, Uri.parse("tel:$privatePhone"))
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            } catch (e: Exception) { msg = "تعذر فتح الاتصال" }
                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp)
                    ) { Text("اتصال بالعيادة", color = Color.White) }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
fun BookingScreen(d: Map<String, Any?>, onBack: () -> Unit, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val doctorId = d["id"] as String
    val days = remember(d) { workDayCalendars(workDaysOf(d)) }
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
            if (msg.isNotEmpty()) Text(msg, color = BDanger)
            RedButton("عرض موقع العيادة") { msg = openRoute(ctx, d) ?: "" }
            OutlinedButton(
                onClick = onDone, modifier = Modifier.fillMaxWidth().height(52.dp)
            ) { Text("مواعيدي", color = Color.White) }
        } else {
            Text("اختر اليوم", color = BMuted)
            if (hasWorkDays(d)) Text("أيام دوام الطبيب: " + workDaysLabel(workDaysOf(d)), color = BGreen, fontSize = 13.sp)
            DayChips(days, dayIdx) { dayIdx = it }
            Text("اختر الوقت", color = BMuted)
            if (slots.isEmpty()) Text("لا توجد أوقات متاحة", color = BMuted)
            slots.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { s ->
                        FilterChip(
                            selected = selected == s,
                            onClick = { selected = s },
                            enabled = s !in taken && !(date == todayKey() && s <= nowMin),
                            label = { Text(slotLabel(s)) }
                        )
                    }
                }
            }
            Field(note, { note = it }, "ملاحظة للطبيب (اختياري)")
            if (msg.isNotEmpty()) Text(msg, color = BDanger)
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

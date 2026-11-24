package com.sehati.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
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
import com.google.firebase.firestore.FieldValue
import java.util.Calendar

private fun qNum(v: Any?): Int? = (v as? Number)?.toInt()
private fun qTime(m: Int): String = "%02d:%02d".format(m / 60, m % 60)

private fun nowMinutes(): Int {
    val c = Calendar.getInstance()
    return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
}

private fun estimate(last: Long, current: Long, today: Boolean, startMin: Int, step: Int): Pair<Long, Int> {
    val next = last + 1
    val sched = startMin + ((next - 1) * step).toInt()
    val est = if (today) {
        val ahead = (next - 1 - current).coerceAtLeast(0)
        maxOf(sched, nowMinutes() + (ahead * step).toInt())
    } else sched
    return next to est
}

private fun bookByQueue(
    d: Map<String, Any?>, date: String, today: Boolean, note: String, visitType: String,
    beneficiaryName: String, beneficiaryRelation: String,
    onResult: (Boolean, String) -> Unit
) {
    val me = bAuth.currentUser
    if (me == null) { onResult(false, "سجّل الدخول أولًا"); return }
    val doctorId = d["id"] as String
    val startMin = (qNum(d["startHour"]) ?: 9) * 60
    val endMin = (qNum(d["endHour"]) ?: 17) * 60
    val step = (qNum(d["slotMinutes"]) ?: 30).coerceAtLeast(10)
    val queueRef = bDb.collection("queues").document("${doctorId}_$date")
    val apptRef = bDb.collection("appointments").document("${doctorId}_${date}_${me.uid}_${visitType}")
    val legacyApptRef = bDb.collection("appointments").document("${doctorId}_${date}_${me.uid}")
    bDb.collection("users").document(me.uid).get().addOnSuccessListener { u ->
        val pName = beneficiaryName.ifBlank { u.getString("name") ?: "" }
        val pPhone = u.getString("phone") ?: ""
        bDb.runTransaction<String> { tx ->
            val a = tx.get(apptRef)
            val legacy = tx.get(legacyApptRef)
            val q = tx.get(queueRef)
            if (legacy.exists() && legacy.getString("status")?.let { it == "PENDING" || it == "CONFIRMED" } == true) {
                return@runTransaction "LEGACY_EXIST|${legacy.getLong("number") ?: 0L}|${legacy.getString("time") ?: ""}"
            }
            if (a.exists()) {
                val st = a.getString("status")
                if (st == "CONFIRMED") {
                    return@runTransaction "EXIST|${a.getLong("number") ?: 0L}|${a.getString("time") ?: ""}"
                }
                if (st == "PENDING") return@runTransaction "PENDING_EXIST|${a.getLong("number") ?: 0L}|${a.getString("time") ?: ""}"
                if (st != "CANCELLED") return@runTransaction "BLOCKED"
            }
            val last = if (q.exists()) (q.getLong("last") ?: 0L) else 0L
            val current = if (q.exists()) (q.getLong("current") ?: 0L) else 0L
            val (number, est) = estimate(last, current, today, startMin, step)
            if (est + step > endMin) return@runTransaction "FULL"
            if (q.exists()) {
                tx.update(queueRef, mapOf("last" to number, "lastAppointmentId" to apptRef.id))
            } else {
                tx.set(
                    queueRef,
                    mapOf(
                        "doctorId" to doctorId, "date" to date, "last" to 1L, "current" to 0L,
                        "lastAppointmentId" to apptRef.id
                    )
                )
            }
            tx.set(
                apptRef,
                mapOf(
                    "patientId" to me.uid, "patientName" to pName, "patientPhone" to pPhone,
                    "beneficiaryRelation" to beneficiaryRelation,
                    "doctorId" to doctorId,
                    "doctorName" to (d["name"]?.toString() ?: ""),
                    "specialty" to (d["specialty"]?.toString() ?: ""),
                    "clinicName" to (d["clinicName"]?.toString() ?: ""),
                    "address" to (d["address"]?.toString() ?: ""),
                    "city" to (d["city"]?.toString() ?: ""),
                    "date" to date, "slot" to est, "time" to qTime(est),
                    "number" to number, "status" to "PENDING", "note" to note,
                    "visitType" to visitType,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            "OK|$number|${qTime(est)}"
        }.addOnSuccessListener { r ->
            val p = r.split("|")
            when (p[0]) {
                "FULL" -> onResult(false, "الأدوار ممتلئة لهذا اليوم، اختر يومًا آخر")
                "BLOCKED" -> onResult(false, "تعذر إنشاء الحجز بسبب حالة موعد سابق.")
                "LEGACY_EXIST" -> onResult(
                    false,
                    "لديك موعد قديم غير مصنف عند هذا الطبيب في هذا اليوم. لتجنب تكرار الحجز، لا يمكن إضافة زيارة أخرى قبل انتهاء الموعد أو إلغائه."
                )
                "PENDING_EXIST" -> onResult(true, "طلبك السابق بانتظار موافقة الطبيب. رقم الدور: ${p.getOrElse(1) { "" }} — الوقت التقديري: ${p.getOrElse(2) { "" }}")
                "EXIST" -> onResult(
                    true,
                    "لديك حجز مسبق عند هذا الطبيب بهذا اليوم. رقمك: ${p.getOrElse(1) { "" }} — الوقت التقريبي: ${p.getOrElse(2) { "" }}"
                )
                "OK" -> {
                    PushSender.notifyAppointment(apptRef.id, "booking_requested")
                    onResult(
                        true,
                        "تم إرسال طلب الحجز للطبيب. سيصبح موعدك مؤكداً بعد موافقته. رقم الدور المبدئي: ${p.getOrElse(1) { "" }} — الوقت التقديري: ${p.getOrElse(2) { "" }} بتاريخ $date"
                    )
                }
                else -> onResult(false, "تعذر تحديد نتيجة الحجز. افتح مواعيدك للتحقق قبل إعادة المحاولة.")
            }
        }.addOnFailureListener { e ->
            val code = (e as? com.google.firebase.firestore.FirebaseFirestoreException)?.code
            val text = when (code) {
                com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                    "لم يتم الحجز. قد يكون اشتراك الطبيب منتهيًا أو تغيّر رقم الدور، حاول مرة أخرى"
                com.google.firebase.firestore.FirebaseFirestoreException.Code.UNAVAILABLE ->
                    "تعذر الحجز، تحقق من الإنترنت وحاول مجددًا"
                else -> "تعذر الحجز، حاول مرة أخرى"
            }
            onResult(false, text)
        }
    }.addOnFailureListener { onResult(false, "تعذر تحميل بياناتك، تحقق من الإنترنت") }
}

@Composable
fun QueueBookingScreen(d: Map<String, Any?>, onBack: () -> Unit, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val doctorId = d["id"] as String
    val days = remember(d) { workDayCalendars(workDaysOf(d)) }
    var dayIdx by remember { mutableIntStateOf(0) }
    var last by remember { mutableLongStateOf(0L) }
    var current by remember { mutableLongStateOf(0L) }
    var note by remember { mutableStateOf("") }
    var visitType by remember { mutableStateOf("CONSULTATION") }
    var familyMembers by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var selectedFamily by remember { mutableStateOf<Pair<String, String>?>(null) }
    var familyMenu by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    var done by remember { mutableStateOf(false) }
    var resultText by remember { mutableStateOf("") }
    val date = dateKey(days[dayIdx])
    val isToday = date == todayKey()
    val patientUid = bAuth.currentUser?.uid.orEmpty()
    DisposableEffect(patientUid) {
        if (patientUid.isBlank()) onDispose { }
        else {
            val registration = bDb.collection("familyProfiles").document(patientUid).collection("members")
                .addSnapshotListener { snapshot, _ ->
                    familyMembers = snapshot?.documents.orEmpty().map {
                        it.getString("name").orEmpty() to it.getString("relation").orEmpty()
                    }.filter { it.first.isNotBlank() }
                }
            onDispose { registration.remove() }
        }
    }
    val startMin = (qNum(d["startHour"]) ?: 9) * 60
    val endMin = (qNum(d["endHour"]) ?: 17) * 60
    val step = (qNum(d["slotMinutes"]) ?: 30).coerceAtLeast(10)

    DisposableEffect(doctorId, date) {
        last = 0L
        current = 0L
        val reg = bDb.collection("queues").document("${doctorId}_$date")
            .addSnapshotListener { s, _ ->
                last = s?.getLong("last") ?: 0L
                current = s?.getLong("current") ?: 0L
            }
        onDispose { reg.remove() }
    }

    val (nextNo, estMin) = estimate(last, current, isToday, startMin, step)
    val full = estMin + step > endMin

    Column(
        Modifier.fillMaxSize().background(BBg).padding(20.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TextButton(onClick = onBack) { Text("رجوع", color = BRed) }
        Text("حجز دور", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
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
            Text("نوع الزيارة", color = BMuted, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilterChip(
                    selected = visitType == "CONSULTATION",
                    onClick = { visitType = "CONSULTATION" },
                    label = { Text("معاينة") }
                )
                FilterChip(
                    selected = visitType == "FOLLOW_UP",
                    onClick = { visitType = "FOLLOW_UP" },
                    label = { Text("مراجعة") }
                )
            }
            Box {
                OutlinedButton(onClick = { familyMenu = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        selectedFamily?.let { "الحجز لـ: ${it.first} (${it.second})" } ?: "الحجز لي شخصياً",
                        color = Color.White
                    )
                }
                DropdownMenu(expanded = familyMenu, onDismissRequest = { familyMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("أنا") },
                        onClick = { selectedFamily = null; familyMenu = false }
                    )
                    familyMembers.forEach { member ->
                        DropdownMenuItem(
                            text = { Text("${member.first} — ${member.second}") },
                            onClick = { selectedFamily = member; familyMenu = false }
                        )
                    }
                }
            }
            Text("اختر اليوم", color = BMuted)
            if (hasWorkDays(d)) Text("أيام دوام الطبيب: " + workDaysLabel(workDaysOf(d)), color = BGreen, fontSize = 13.sp)
            DayChips(days, dayIdx) { dayIdx = it }
            Column(
                Modifier.fillMaxWidth().background(BCard, RoundedCornerShape(14.dp))
                    .border(1.dp, BLine, RoundedCornerShape(14.dp)).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("يحدد التطبيق وقت موعدك تلقائيًا حسب رقم دورك.", color = BMuted)
                Text("آخر رقم صدر: $last", color = Color.White, fontSize = 16.sp)
                if (full) {
                    Text(
                        "الأدوار ممتلئة لهذا اليوم، اختر يومًا آخر",
                        color = BDanger, fontWeight = FontWeight.Bold
                    )
                } else {
                    Text("رقمك: $nextNo", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(
                        "الوقت التقريبي: ${qTime(estMin)}",
                        fontSize = 18.sp, fontWeight = FontWeight.Bold, color = BGreen
                    )
                    Text("الوقت تقريبي ويتغير حسب سرعة الطبيب.", color = BMuted, fontSize = 13.sp)
                }
            }
            Field(note, { note = it }, "ملاحظة للطبيب (اختياري)")
            if (msg.isNotEmpty()) Text(msg, color = BDanger)
            RedButton("إرسال طلب الحجز", enabled = !busy && !full) {
                busy = true
                msg = ""
                bookByQueue(
                    d, date, isToday, note.trim().take(300), visitType,
                    selectedFamily?.first.orEmpty(), selectedFamily?.second.orEmpty()
                ) { ok, text ->
                    busy = false
                    if (ok) { done = true; resultText = text } else msg = text
                }
            }
        }
    }
}

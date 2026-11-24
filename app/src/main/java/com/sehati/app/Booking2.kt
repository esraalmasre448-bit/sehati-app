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
                        .filter { it["date"] == today && it["status"] in listOf("PENDING", "CONFIRMED") }
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
    var slotMinutes by remember { mutableIntStateOf(30) }
    var msg by remember { mutableStateOf("") }

    LaunchedEffect(doctorId) {
        bDb.collection("doctors").document(doctorId).get()
            .addOnSuccessListener { doctor ->
                slotMinutes = (doctor.getLong("slotMinutes") ?: 30L).toInt().coerceIn(10, 180)
            }
    }

    DisposableEffect(doctorId, date) {
        val reg = bDb.collection("queues").document("${doctorId}_$date")
            .addSnapshotListener { s, _ -> current = s?.getLong("current") ?: 0L }
        onDispose { reg.remove() }
    }

    val ahead = (number - current - 1).coerceAtLeast(0)
    val pending = a["status"] == "PENDING"
    val (statusText, color) = when {
        pending -> "طلبك بانتظار موافقة الطبيب" to Color(0xFFFFC857)
        current == number -> "حان دورك! تفضّل بالدخول" to BGreen
        current > number -> "انتهى دورك" to BMuted
        else -> "بانتظار دورك" to Color.White
    }
    Column(
        Modifier.fillMaxWidth().background(BCard, RoundedCornerShape(14.dp))
            .border(1.dp, if (!pending && current == number) BGreen else BLine, RoundedCornerShape(14.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text("${a["doctorName"]}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
        val visitLabel = when (a["visitType"]) {
            "CONSULTATION" -> "معاينة"
            "FOLLOW_UP" -> "مراجعة"
            else -> "زيارة"
        }
        Text("نوع الزيارة: $visitLabel", color = BRed, fontWeight = FontWeight.SemiBold)
        Text("الساعة ${a["time"]}", color = BMuted)
        Text("رقمك: $number", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text("الرقم الحالي: $current", color = Color.White, fontSize = 16.sp)
        if (!pending && current < number) {
            val initialAhead = (number - 1L).coerceAtLeast(0L)
            val progressPercent = if (initialAhead == 0L) 100
                else (((initialAhead - ahead).coerceAtLeast(0L) * 100L) / initialAhead).toInt().coerceIn(0, 100)
            val etaMinutes = ahead * slotMinutes
            val etaText = if (etaMinutes < 60L) "$etaMinutes دقيقة تقريباً"
                else "${etaMinutes / 60L} ساعة و ${etaMinutes % 60L} دقيقة تقريباً"
            Text("عدد الأشخاص قبلك: $ahead", color = Color.White, fontSize = 16.sp)
            Text("نسبة التقدم نحو دورك: $progressPercent%", color = BMuted, fontSize = 13.sp)
            Box(
                Modifier.fillMaxWidth().height(9.dp)
                    .background(BBg, RoundedCornerShape(50))
            ) {
                Box(
                    Modifier.fillMaxWidth(progressPercent / 100f).height(9.dp)
                        .background(BRed, RoundedCornerShape(50))
                )
            }
            Text("الوقت المتوقع: $etaText", color = BGreen, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text("تقدير تقريبي حسب متوسط مدة المعاينة، وقد يتغير حسب سير العيادة.", color = BMuted, fontSize = 11.sp)
        }
        Text(statusText, color = color, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        if (msg.isNotEmpty()) Text(msg, color = BDanger)
        if (!pending) {
            Button(
                onClick = { routeToDoctor(ctx, doctorId) { msg = it } },
                colors = ButtonDefaults.buttonColors(containerColor = BRed, contentColor = BOnAccent)
            ) { Text("الطريق إلى العيادة") }
        }
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
                Text(
                    "نوع الزيارة: " + when (a["visitType"]) {
                        "CONSULTATION" -> "معاينة"
                        "FOLLOW_UP" -> "مراجعة"
                        else -> "زيارة"
                    },
                    color = BRed,
                    fontWeight = FontWeight.SemiBold
                )
                Button(
                    onClick = { routeToDoctor(ctx, a["doctorId"].toString()) { msg = it } },
                    colors = ButtonDefaults.buttonColors(containerColor = BRed, contentColor = BOnAccent)
                ) { Text("افتح الطريق في خرائط جوجل") }
            }
        }
    }
}

@Composable
fun DoctorDashboardContent(onBack: (() -> Unit)? = null, onOpenHealthRecord: (String) -> Unit = {}) {
    val uid = bAuth.currentUser?.uid ?: ""
    val st = subState(uid)
    val active = st == "active"
    var wd by remember { mutableStateOf((1..7).toSet()) }
    val days = remember(wd) { workDayCalendars(wd) }
    var idx by remember { mutableIntStateOf(0) }
    val date = dateKey(days[idx.coerceIn(0, days.size - 1)])
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
    var followUpTarget by remember { mutableStateOf<Map<String, Any?>?>(null) }
    var followUpDate by remember { mutableStateOf("") }
    var followUpTime by remember { mutableStateOf("09:00") }
    var followUpBusy by remember { mutableStateOf(false) }
    var followUpMsg by remember { mutableStateOf("") }
    var paymentTarget by remember { mutableStateOf<Map<String, Any?>?>(null) }
    var paymentAmount by remember { mutableStateOf("") }
    var paymentCurrency by remember { mutableStateOf("SYP") }
    var paymentBusy by remember { mutableStateOf(false) }
    var paymentMsg by remember { mutableStateOf("") }

    LaunchedEffect(uid) {
        if (uid.isNotEmpty()) {
            bDb.collection("doctors").document(uid).get().addOnSuccessListener {
                myName = it.getString("name") ?: ""
                wd = workDaysOf(it.data)
                idx = 0
            }
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

    val nextAppointment = items.firstOrNull {
        (it["number"] as? Number)?.toLong() == current + 1L
    }
    val canAdvance = nextAppointment?.get("status") in listOf("CONFIRMED", "CANCELLED", "DONE")

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
        item { SehatiHero(eyebrow = "إدارة العيادة", title = "لوحتي — الحجوزات", subtitle = "طلبات المرضى، الموافقات، وإدارة الدور اليومي.", icon = Icons.Filled.CalendarMonth) }
        if (st == "expired" || st == "cancelled") {
            item { ExpiredCard { renew = true } }
        } else {
            item { LockNote(st) }
            if (st != "active" && st != "loading") {
                item { RedButton("تجديد الاشتراك والتواصل مع الإدارة") { renew = true } }
            }
        }
        item { DayChips(days, idx.coerceIn(0, days.size - 1)) { idx = it } }
        item {
            Column(
                Modifier.fillMaxWidth().background(BCard, RoundedCornerShape(14.dp))
                    .border(1.dp, BLine, RoundedCornerShape(14.dp)).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("الرقم الحالي: $current", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("آخر رقم صدر: $last", color = BMuted)
                if (current < last && !canAdvance) {
                    Text("لا يمكن تجاوز طلب بانتظار موافقة الطبيب.", color = Color(0xFFFFC857), fontSize = 12.sp)
                }
                Button(
                    enabled = active && current < last && canAdvance,
                    onClick = {
                        bDb.collection("queues").document("${uid}_$date")
                            .update("current", current + 1)
                            .addOnSuccessListener { PushSender.notifyQueueAdvanced(date) }
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
                OutlinedButton(
                    onClick = { onOpenHealthRecord(a["patientId"]?.toString().orEmpty()) },
                    enabled = !a["patientId"]?.toString().isNullOrBlank()
                ) { Text("السجل الصحي بموافقة المريض", color = Color.White) }
                notes.filter { it["appointmentId"] == id }
                    .sortedBy { (it["createdAt"] as? com.google.firebase.Timestamp)?.seconds ?: 0L }
                    .forEach { n ->
                        NoteBox(n) { reason ->
                            bDb.collection("prescriptions").document(n["id"] as String).update(
                                mapOf(
                                    "status" to "VOIDED",
                                    "voidedAt" to FieldValue.serverTimestamp(),
                                    "voidedBy" to uid,
                                    "voidReason" to reason
                                )
                            ).addOnSuccessListener {
                                msg = "تم إلغاء المستند مع توثيق السبب والاحتفاظ به في السجل"
                            }.addOnFailureListener {
                                msg = "تعذر إلغاء المستند، تحقق من الصلاحيات والاتصال"
                            }
                        }
                    }
                if (a["status"] == "CONFIRMED") {
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
                if (a["status"] == "PENDING") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            enabled = active,
                            onClick = {
                                setStatus(id, "CONFIRMED", false) { ok ->
                                    msg = if (ok) "تمت الموافقة على الحجز" else "تعذرت الموافقة، تحقق من الإنترنت واشتراكك"
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = BRed, contentColor = BOnAccent)
                        ) { Text("قبول الحجز") }
                        OutlinedButton(
                            enabled = active,
                            onClick = {
                                setStatus(id, "CANCELLED", false) { ok ->
                                    msg = if (ok) "تم رفض طلب الحجز" else "تعذر رفض الطلب، تحقق من الإنترنت واشتراكك"
                                }
                            }
                        ) { Text("رفض الطلب", color = Color.White) }
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
                                setStatus(id, "CANCELLED", false) { ok ->
                                    msg = if (ok) "" else "تعذر الإلغاء، تحقق من الإنترنت واشتراكك"
                                }
                            }
                        ) { Text("إلغاء الحجز", color = Color.White) }
                    }
                }
                if (a["status"] == "CONFIRMED" || a["status"] == "DONE") {
                    OutlinedButton(
                        enabled = active,
                        onClick = {
                            followUpTarget = a
                            followUpDate = ""
                            followUpTime = "09:00"
                            followUpMsg = ""
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("تحديد موعد متابعة", color = Color.White) }
                }
                if (a["status"] == "DONE") {
                    val paid = a["paidAmount"] as? Number
                    if (paid != null) {
                        Text("المبلغ المسجل: " + paid.toDouble() + " " + (a["paidCurrency"] ?: ""), color = BGreen, fontSize = 13.sp)
                    } else {
                        OutlinedButton(
                            enabled = active,
                            onClick = {
                                paymentTarget = a
                                paymentAmount = ""
                                paymentCurrency = "SYP"
                                paymentMsg = ""
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("تسجيل المبلغ المدفوع", color = Color.White) }
                    }
                }
            }
        }
    }

    val followUp = followUpTarget
    if (followUp != null) {
        AlertDialog(
            onDismissRequest = { if (!followUpBusy) followUpTarget = null },
            title = { Text("تحديد موعد متابعة") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("سيصل المريض إشعاراً فور حفظ موعد المتابعة. أدخل التاريخ والوقت المحليين للعيادة.")
                    OutlinedTextField(value = followUpDate, onValueChange = { followUpDate = it.take(10) }, label = { Text("التاريخ YYYY-MM-DD") }, singleLine = true)
                    OutlinedTextField(value = followUpTime, onValueChange = { followUpTime = it.take(5) }, label = { Text("الوقت HH:MM") }, singleLine = true)
                    if (followUpMsg.isNotBlank()) Text(followUpMsg, color = BDanger)
                }
            },
            confirmButton = {
                TextButton(enabled = !followUpBusy, onClick = {
                    val dateOk = Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(followUpDate) && followUpDate >= todayKey()
                    val timeOk = Regex("^([01]\\d|2[0-3]):[0-5]\\d$").matches(followUpTime)
                    if (!dateOk || !timeOk) {
                        followUpMsg = "تحقق من التاريخ والوقت بصيغتهما الصحيحة."
                        return@TextButton
                    }
                    followUpBusy = true
                    val appointmentId = followUp["id"]?.toString().orEmpty()
                    bDb.collection("appointments").document(appointmentId).update(
                        mapOf(
                            "followUpDate" to followUpDate,
                            "followUpTime" to followUpTime,
                            "followUpSetAt" to FieldValue.serverTimestamp()
                        )
                    ).addOnSuccessListener {
                        followUpBusy = false
                        followUpTarget = null
                        PushSender.notifyFollowUp(appointmentId)
                        msg = "تم حفظ موعد المتابعة وإرسال إشعار للمريض."
                    }.addOnFailureListener {
                        followUpBusy = false
                        followUpMsg = "تعذر حفظ المتابعة. تحقق من الصلاحيات والاشتراك."
                    }
                }) { Text("حفظ وإشعار المريض") }
            },
            dismissButton = { TextButton(enabled = !followUpBusy, onClick = { followUpTarget = null }) { Text("إلغاء") } }
        )
    }

    val payment = paymentTarget
    if (payment != null) {
        AlertDialog(
            onDismissRequest = { if (!paymentBusy) paymentTarget = null },
            title = { Text("تسجيل المبلغ المدفوع") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("سجّل المبلغ الذي استلمه الطبيب فعلياً. هذا إدخال يدوي وليس إثبات دفع إلكتروني.")
                    OutlinedTextField(
                        value = paymentAmount, onValueChange = { paymentAmount = it.take(14) },
                        label = { Text("المبلغ") }, singleLine = true
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("SYP", "EUR", "USD").forEach { currency ->
                            FilterChip(
                                selected = paymentCurrency == currency,
                                onClick = { paymentCurrency = currency },
                                label = { Text(currency) }
                            )
                        }
                    }
                    if (paymentMsg.isNotBlank()) Text(paymentMsg, color = BDanger)
                }
            },
            confirmButton = {
                TextButton(enabled = !paymentBusy, onClick = {
                    val amount = paymentAmount.replace(",", ".").toDoubleOrNull()
                    if (amount == null || amount <= 0.0 || amount > 100000000000.0) {
                        paymentMsg = "أدخل مبلغاً صحيحاً أكبر من صفر."
                        return@TextButton
                    }
                    paymentBusy = true
                    val appointmentId = payment["id"]?.toString().orEmpty()
                    bDb.collection("appointments").document(appointmentId).update(
                        mapOf(
                            "paidAmount" to amount,
                            "paidCurrency" to paymentCurrency,
                            "paidRecordedAt" to FieldValue.serverTimestamp()
                        )
                    ).addOnSuccessListener {
                        paymentBusy = false
                        paymentTarget = null
                        msg = "تم تسجيل المبلغ المدفوع."
                    }.addOnFailureListener {
                        paymentBusy = false
                        paymentMsg = "تعذر تسجيل المبلغ. قد يكون قد سُجل مسبقاً."
                    }
                }) { Text("حفظ المبلغ") }
            },
            dismissButton = { TextButton(enabled = !paymentBusy, onClick = { paymentTarget = null }) { Text("إلغاء") } }
        )
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
                    ).addOnSuccessListener { saved ->
                        noteBusy = false
                        noteFor = null
                        PushSender.notifyPrescription(saved.id)
                    }.addOnFailureListener { noteBusy = false; noteMsg = "تعذر الحفظ، تحقق من الإنترنت واشتراكك" }
                }) { Text("حفظ وإرسال للمريض") }
            },
            dismissButton = { TextButton(enabled = !noteBusy, onClick = { noteFor = null }) { Text("إلغاء") } }
        )
    }
}

@Composable
fun NoteBox(n: Map<String, Any?>, onVoid: ((String) -> Unit)?) {
    val rx = n["kind"] == "rx"
    val isVoided = n["status"] == "VOIDED"
    var showVoidConfirmation by remember(n["id"]) { mutableStateOf(false) }
    var voidReason by remember(n["id"]) { mutableStateOf("") }
    val ts = (n["createdAt"] as? com.google.firebase.Timestamp)?.toDate()
    val dateTxt = if (ts != null) SimpleDateFormat("d/M/yyyy", Locale.US).format(ts) else ""
    Column(
        Modifier.fillMaxWidth().background(BBg, RoundedCornerShape(10.dp))
            .border(1.dp, BRed, RoundedCornerShape(10.dp)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            (if (rx) "راشيتة" else "طلب تحاليل") +
                (if (dateTxt.isNotEmpty()) " • $dateTxt" else "") +
                (if (onVoid == null && "${n["doctorName"] ?: ""}".isNotBlank()) " • من د. ${n["doctorName"]}" else "") +
                (if (isVoided) " • ملغاة" else ""),
            color = if (isVoided) BDanger else BRed, fontWeight = FontWeight.Bold, fontSize = 13.sp
        )
        Text("${n["text"] ?: ""}", color = Color.White)
        if (isVoided) {
            Text("أُلغي هذا المستند مع الاحتفاظ به في السجل الطبي.", color = BDanger, fontSize = 11.sp)
            val reason = n["voidReason"]?.toString().orEmpty()
            if (reason.isNotBlank()) Text("سبب الإلغاء: $reason", color = BMuted, fontSize = 12.sp)
        } else if (onVoid == null) {
            Text("ملاحظة طبية من الطبيب عبر التطبيق، وليست وصفة رسمية.", color = BMuted, fontSize = 11.sp)
        }
        if (onVoid != null && !isVoided) {
            TextButton(onClick = { showVoidConfirmation = true }) { Text("إلغاء المستند", color = BDanger) }
        }
    }
    if (showVoidConfirmation && onVoid != null && !isVoided) {
        AlertDialog(
            onDismissRequest = { showVoidConfirmation = false },
            title = { Text("إلغاء المستند الطبي؟") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("سيبقى المستند محفوظاً في سجل المريض. اكتب سبب الإلغاء لتوثيقه.")
                    OutlinedTextField(
                        value = voidReason, onValueChange = { voidReason = it.take(300) },
                        label = { Text("سبب الإلغاء (إلزامي)") }, minLines = 2
                    )
                }
            },
            confirmButton = {
                TextButton(enabled = voidReason.isNotBlank(), onClick = {
                    showVoidConfirmation = false
                    onVoid(voidReason.trim())
                }) { Text("تأكيد الإلغاء", color = BDanger) }
            },
            dismissButton = { TextButton(onClick = { showVoidConfirmation = false }) { Text("رجوع") } }
        )
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

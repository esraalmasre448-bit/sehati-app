package com.sehati.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val SxPending = Color(0xFFF5A524)
private val SxDone = Color(0xFF3B82F6)

private val SX_TABS = listOf(
    "ALL" to "الكل",
    "PENDING" to "قيد الانتظار",
    "CONFIRMED" to "مؤكد",
    "DONE" to "منتهي",
    "CANCELLED" to "ملغى"
)

/** نص ولون الحالة. النص يوضح الحالة، واللون للتمييز فقط (لا نعتمد على اللون وحده). */
private fun sxApptStatus(s: Any?): Pair<String, Color> = when (s) {
    "PENDING" -> "قيد الانتظار" to SxPending
    "CONFIRMED" -> "مؤكد" to BGreen
    "DONE" -> "منتهي" to SxDone
    "CANCELLED" -> "ملغى" to BDanger
    else -> "-" to BMuted
}

@Composable
private fun SxApptTab(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .background(if (selected) BRed else BCard, RoundedCornerShape(20.dp))
            .border(1.dp, if (selected) BRed else BLine, RoundedCornerShape(20.dp))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) { Text(label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
}

@Composable
private fun SxStatusChip(label: String, color: Color) {
    Box(
        Modifier
            .background(color.copy(alpha = 0.18f), RoundedCornerShape(10.dp))
            .border(1.dp, color, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) { Text(label, color = color, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
}

/**
 * شاشة «مواعيدي» للمريض بتصميم المرجع: تبويبات حسب الحالة، وبطاقة لكل موعد مع زر تفاصيل.
 * كل البيانات حقيقية من appointments وprescriptions. تدعم PENDING وCONFIRMED وDONE وCANCELLED.
 * الإلغاء يستدعي setStatus بدون حذف slots.
 */
@Composable
fun MyAppointmentsContent(onFind: () -> Unit, onBack: (() -> Unit)? = null) {
    val ctx = LocalContext.current
    val uid = bAuth.currentUser?.uid ?: ""
    var items by remember { mutableStateOf(listOf<Map<String, Any?>>()) }
    var loaded by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    var cancelId by remember { mutableStateOf<String?>(null) }
    var notes by remember { mutableStateOf(listOf<Map<String, Any?>>()) }
    var tab by remember { mutableStateOf("ALL") }
    var open by remember { mutableStateOf(setOf<String>()) }

    DisposableEffect(uid) {
        val regN = bDb.collection("prescriptions").whereEqualTo("patientId", uid).limit(200)
            .addSnapshotListener { s, e ->
                if (e == null) notes = s?.documents?.map { it.data.orEmpty() + ("id" to it.id) }.orEmpty()
            }
        onDispose { regN.remove() }
    }

    DisposableEffect(uid) {
        val reg = bDb.collection("appointments").whereEqualTo("patientId", uid).limit(100)
            .addSnapshotListener { s, e ->
                if (e != null) msg = "تعذر تحميل المواعيد، تحقق من الإنترنت"
                else {
                    items = s?.documents?.map { it.data.orEmpty() + ("id" to it.id) }.orEmpty()
                        .sortedWith(
                            compareByDescending<Map<String, Any?>> { it["date"].toString() }
                                .thenByDescending { (it["slot"] as? Number)?.toInt() ?: 0 }
                        )
                    loaded = true
                }
            }
        onDispose { reg.remove() }
    }

    val shown = if (tab == "ALL") items else items.filter { it["status"] == tab }

    LazyColumn(
        Modifier.fillMaxSize().background(BBg).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        if (onBack != null) item { TextButton(onClick = onBack) { Text("رجوع", color = BRed) } }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("مواعيدي", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("تابع حالة حجوزاتك ومواعيدك القادمة", fontSize = 12.sp, color = BMuted)
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    Triple("الكل", items.size, BRed),
                    Triple("مؤكد", items.count { it["status"] == "CONFIRMED" }, BGreen),
                    Triple("قيد الانتظار", items.count { it["status"] == "PENDING" }, SxPending)
                ).forEach { (label, value, tint) ->
                    Column(
                        Modifier.weight(1f).background(BCard, RoundedCornerShape(13.dp))
                            .border(1.dp, BLine, RoundedCornerShape(13.dp)).padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(value.toString(), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text(label, color = tint, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SX_TABS.forEach { (k, l) -> SxApptTab(l, tab == k) { tab = k } }
            }
        }
        if (msg.isNotEmpty()) item { Text(msg, color = BDanger) }
        if (loaded && items.isEmpty()) {
            item { Text("ما عندك مواعيد بعد.", color = BMuted) }
            item { RedButton("ابحث عن طبيب واحجز موعد", onClick = onFind) }
        } else if (loaded && shown.isEmpty()) {
            item { Text("لا توجد مواعيد في هذا التصنيف.", color = BMuted) }
        }
        items(shown, key = { it["id"] as String }) { a ->
            val id = a["id"] as String
            val (label, color) = sxApptStatus(a["status"])
            val isOpen = id in open
            val canCancel = a["status"] == "PENDING" || a["status"] == "CONFIRMED"
            Column(
                Modifier.fillMaxWidth().background(BCard, RoundedCornerShape(14.dp))
                    .border(1.dp, BLine, RoundedCornerShape(14.dp)).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    DoctorAvatar(null, "${a["doctorName"] ?: ""}", 52.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("${a["doctorName"] ?: ""}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("${a["specialty"] ?: ""}", color = BMuted, fontSize = 13.sp)
                        Text(
                            "${a["date"] ?: ""}".replace("-", "/") + " - " + "${a["time"] ?: ""}",
                            color = Color.White, fontSize = 13.sp
                        )
                    }
                    SxStatusChip(label, color)
                }
                TextButton(onClick = { open = if (isOpen) open - id else open + id }) {
                    Text(if (isOpen) "إخفاء التفاصيل" else "التفاصيل", color = BMuted)
                }
                if (isOpen) {
                    val place = "${a["clinicName"] ?: ""}  ${a["city"] ?: ""}".trim()
                    if (place.isNotBlank()) Text(place, color = BMuted, fontSize = 13.sp)
                    Text("رقمك بالدور: ${a["number"] ?: "-"}", color = Color.White, fontWeight = FontWeight.Bold)
                    val note = "${a["note"] ?: ""}"
                    if (note.isNotBlank()) Text("ملاحظتك: $note", color = BMuted, fontSize = 13.sp)
                    notes.filter { it["appointmentId"] == a["id"] }
                        .sortedBy { (it["createdAt"] as? com.google.firebase.Timestamp)?.seconds ?: 0L }
                        .forEach { NoteBox(it, null) }
                    if (canCancel) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { routeToDoctor(ctx, a["doctorId"].toString()) { msg = it } },
                                colors = ButtonDefaults.buttonColors(containerColor = BRed, contentColor = BOnAccent)
                            ) { Text("الطريق إلى العيادة") }
                            OutlinedButton(onClick = { cancelId = id }) {
                                Text(if (a["status"] == "PENDING") "سحب الطلب" else "إلغاء الموعد", color = Color.White)
                            }
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
                    setStatus(cid, "CANCELLED", false) { ok ->
                        msg = if (ok) "تم إلغاء الموعد" else "تعذر الإلغاء، حاول مرة أخرى"
                        cancelId = null
                    }
                }) { Text("تأكيد الإلغاء", color = BDanger) }
            },
            dismissButton = { TextButton(onClick = { cancelId = null }) { Text("رجوع") } }
        )
    }
}

package com.sehati.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val ABg = Color(0xFF0A0C11)
private val ACard = Color(0xFF14171D)
private val ALine = Color(0xFF2A2F3A)
private val ARed = Color(0xFFE5171F)
private val AMuted = Color(0xFF9AA0AB)
private val AGreen = Color(0xFF3DDC84)
private val AExpiredBg = Color(0xFF2A1215)

private const val CODE_HASH = "3dacdbccd5c182f2ea0f8496ae6f09c1a3a66676aba26fc8b911b87131af35fd"

private val aAuth get() = FirebaseAuth.getInstance()
private val aDb get() = FirebaseFirestore.getInstance()

private fun sha256(s: String): String =
    java.security.MessageDigest.getInstance("SHA-256").digest(s.toByteArray())
        .joinToString("") { "%02x".format(it) }

private fun fmt(ts: Any?): String =
    (ts as? Timestamp)?.toDate()?.let { SimpleDateFormat("yyyy/MM/dd", Locale.US).format(it) } ?: "-"

private fun statusOf(s: Map<String, Any?>?): String {
    if (s == null) return "NONE"
    val exp = (s["expiresAt"] as? Timestamp)?.toDate()
    return when (s["status"]) {
        "CANCELLED" -> "CANCELLED"
        "ACTIVE" -> if (exp != null && exp.after(Date())) "ACTIVE" else "EXPIRED"
        else -> "EXPIRED"
    }
}

private fun logHistory(uid: String, name: String, action: String, start: Date?, end: Date?, months: Int) {
    val paid = action == "تفعيل" || action == "تجديد"
    aDb.collection("subscriptionHistory").add(
        mapOf(
            "uid" to uid, "name" to name, "action" to action,
            "paymentMethod" to (if (paid) "Cash" else "-"),
            "startAt" to start?.let { Timestamp(it) },
            "expiresAt" to end?.let { Timestamp(it) },
            "months" to months,
            "adminUid" to (aAuth.currentUser?.uid ?: ""),
            "createdAt" to FieldValue.serverTimestamp()
        )
    )
}

private fun grant(user: Map<String, Any?>, sub: Map<String, Any?>?, months: Int, done: (Boolean) -> Unit) {
    val uid = user["id"] as String
    val now = Date()
    val curExp = (sub?.get("expiresAt") as? Timestamp)?.toDate()
    val active = curExp != null && statusOf(sub) == "ACTIVE"
    val base: Date = if (active && curExp != null) curExp else now
    val cal = Calendar.getInstance().apply { time = base; add(Calendar.MONTH, months) }
    val newExp = cal.time
    val start: Date = if (active) ((sub?.get("startedAt") as? Timestamp)?.toDate() ?: now) else now
    aDb.collection("subscriptions").document(uid).set(
        mapOf(
            "uid" to uid, "status" to "ACTIVE",
            "startedAt" to Timestamp(start), "expiresAt" to Timestamp(newExp),
            "paymentMethod" to "Cash", "updatedAt" to FieldValue.serverTimestamp()
        )
    ).addOnSuccessListener {
        logHistory(uid, user["name"]?.toString().orEmpty(), if (sub == null) "تفعيل" else "تجديد", start, newExp, months)
        done(true)
    }.addOnFailureListener { done(false) }
}

private fun cancelSub(user: Map<String, Any?>, sub: Map<String, Any?>?, done: (Boolean) -> Unit) {
    val uid = user["id"] as String
    aDb.collection("subscriptions").document(uid).update(
        mapOf("status" to "CANCELLED", "updatedAt" to FieldValue.serverTimestamp())
    ).addOnSuccessListener {
        logHistory(
            uid, user["name"]?.toString().orEmpty(), "إلغاء",
            (sub?.get("startedAt") as? Timestamp)?.toDate(),
            (sub?.get("expiresAt") as? Timestamp)?.toDate(), 0
        )
        done(true)
    }.addOnFailureListener { done(false) }
}

@Composable
fun AdminScreen(onBack: () -> Unit) {
    val myUid = aAuth.currentUser?.uid ?: ""
    var state by remember { mutableStateOf("loading") }
    LaunchedEffect(Unit) {
        aDb.collection("config").document("admin").get()
            .addOnSuccessListener { d ->
                state = if (!d.exists()) "claim" else if (d.getString("uid") == myUid) "panel" else "denied"
            }
            .addOnFailureListener { state = "error" }
    }
    Box(Modifier.fillMaxSize().background(ABg)) {
        when (state) {
            "loading" -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = ARed)
            "claim" -> ClaimScreen(myUid, onBack) { state = "panel" }
            "panel" -> AdminPanel(onBack)
            "denied" -> AMessage("لا يمكن فتح هذه الصفحة", onBack)
            else -> AMessage("تعذر التحميل، تحقق من الإنترنت وحاول مرة أخرى", onBack)
        }
    }
}

@Composable
private fun AMessage(text: String, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = onBack) { Text("رجوع", color = ARed) }
        Text(text, color = Color.White)
    }
}

@Composable
private fun ClaimScreen(uid: String, onBack: () -> Unit, onDone: () -> Unit) {
    var code by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = onBack) { Text("رجوع", color = ARed) }
        Text("الرمز", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Field(code, { code = it }, "الرمز")
        if (msg.isNotEmpty()) Text(msg, color = ARed)
        RedButton("اجعلني أدمن", enabled = !busy) {
            if (sha256(code.trim()) != CODE_HASH) {
                msg = "الرمز غير صحيح"
                return@RedButton
            }
            busy = true
            aDb.collection("config").document("admin")
                .set(mapOf("uid" to uid, "createdAt" to FieldValue.serverTimestamp()))
                .addOnSuccessListener { onDone() }
                .addOnFailureListener { busy = false; msg = "تعذر التنفيذ، تحقق من الإنترنت وحاول مرة أخرى" }
        }
    }
}

@Composable
private fun StatBox(label: String, value: Int, modifier: Modifier) {
    Column(
        modifier.background(ACard, RoundedCornerShape(12.dp))
            .border(1.dp, ALine, RoundedCornerShape(12.dp)).padding(12.dp)
    ) {
        Text("$value", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text(label, color = AMuted, fontSize = 13.sp)
    }
}

@Composable
private fun TableHeader() {
    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp)) {
        Text("الاسم", Modifier.weight(1.3f), color = AMuted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text("الاختصاص", Modifier.weight(0.9f), color = AMuted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text("الإجراءات", Modifier.weight(1f), color = AMuted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun DoctorRow(
    u: Map<String, Any?>, sub: Map<String, Any?>?, specialty: String,
    onGrant: () -> Unit, onCancel: () -> Unit
) {
    val st = statusOf(sub)
    val expiredLike = st == "EXPIRED" || st == "CANCELLED"
    val (label, color) = when (st) {
        "ACTIVE" -> "فعّال" to AGreen
        "EXPIRED" -> "منتهي" to ARed
        "CANCELLED" -> "ملغى" to ARed
        else -> "بدون اشتراك" to AMuted
    }
    val detail = when (st) {
        "ACTIVE" -> "حتى ${fmt(sub?.get("expiresAt"))}"
        "EXPIRED" -> "انتهى بتاريخ ${fmt(sub?.get("expiresAt"))}"
        "CANCELLED" -> "تم إلغاء الاشتراك"
        else -> ""
    }
    Row(
        Modifier.fillMaxWidth()
            .background(if (expiredLike) AExpiredBg else ACard, RoundedCornerShape(12.dp))
            .border(1.dp, if (expiredLike) ARed else ALine, RoundedCornerShape(12.dp))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1.3f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("${u["name"]}", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text(if (u["accountType"] == "clinic") "عيادة" else "طبيب", color = AMuted, fontSize = 12.sp)
            Text(label, color = color, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            if (detail.isNotEmpty()) Text(detail, color = color, fontSize = 11.sp)
        }
        Text(
            specialty.ifBlank { "—" }, Modifier.weight(0.9f),
            color = Color.White, fontSize = 14.sp
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(
                onClick = onGrant,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ARed, contentColor = Color.White),
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (st == "ACTIVE") "تجديد" else "اشتراك", fontSize = 13.sp) }
            OutlinedButton(
                onClick = onCancel,
                enabled = st == "ACTIVE",
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                modifier = Modifier.fillMaxWidth()
            ) { Text("إلغاء الاشتراك", fontSize = 12.sp) }
        }
    }
}

@Composable
private fun HistoryCard(h: Map<String, Any?>) {
    Column(
        Modifier.fillMaxWidth().background(ACard, RoundedCornerShape(12.dp))
            .border(1.dp, ALine, RoundedCornerShape(12.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text("${h["name"]}  •  ${h["action"]}", fontWeight = FontWeight.Bold, color = Color.White)
        Text("الدفع: ${h["paymentMethod"]}", color = AMuted, fontSize = 13.sp)
        Text("من ${fmt(h["startAt"])} إلى ${fmt(h["expiresAt"])}", color = AMuted, fontSize = 13.sp)
        Text("المدة: ${h["months"]} شهر  •  التاريخ: ${fmt(h["createdAt"])}", color = AMuted, fontSize = 13.sp)
        Text("المنفذ: الأدمن", color = AMuted, fontSize = 13.sp)
    }
}

@Composable
private fun AdminPanel(onBack: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    var users by remember { mutableStateOf(listOf<Map<String, Any?>>()) }
    var subs by remember { mutableStateOf(mapOf<String, Map<String, Any?>>()) }
    var specs by remember { mutableStateOf(mapOf<String, String>()) }
    var history by remember { mutableStateOf(listOf<Map<String, Any?>>()) }
    var error by remember { mutableStateOf("") }
    var info by remember { mutableStateOf("") }
    var grantUser by remember { mutableStateOf<Map<String, Any?>?>(null) }
    var cancelUser by remember { mutableStateOf<Map<String, Any?>?>(null) }
    var busy by remember { mutableStateOf(false) }
    val handled = remember { mutableSetOf<String>() }

    DisposableEffect(Unit) {
        val r1 = aDb.collection("users").whereIn("accountType", listOf("doctor", "clinic")).limit(200)
            .addSnapshotListener { s, e ->
                if (e != null) error = "تعذر تحميل الحسابات، تحقق من الإنترنت"
                else {
                    error = ""
                    users = s?.documents?.map { it.data.orEmpty() + ("id" to it.id) }.orEmpty()
                }
            }
        val r2 = aDb.collection("subscriptions").limit(500).addSnapshotListener { s, e ->
            if (e == null) subs = s?.documents?.associate { it.id to it.data.orEmpty() }.orEmpty()
        }
        val r3 = aDb.collection("subscriptionHistory")
            .orderBy("createdAt", Query.Direction.DESCENDING).limit(100)
            .addSnapshotListener { s, e ->
                if (e == null) history = s?.documents?.map { it.data.orEmpty() + ("id" to it.id) }.orEmpty()
            }
        val r4 = aDb.collection("doctors").limit(500).addSnapshotListener { s, e ->
            if (e == null) specs = s?.documents?.associate { it.id to (it.getString("specialty") ?: "") }.orEmpty()
        }
        onDispose { r1.remove(); r2.remove(); r3.remove(); r4.remove() }
    }

    LaunchedEffect(subs, users) {
        val now = Date()
        subs.forEach { (uid, s) ->
            val exp = (s["expiresAt"] as? Timestamp)?.toDate()
            if (s["status"] == "ACTIVE" && exp != null && exp.before(now) && handled.add(uid)) {
                aDb.collection("subscriptions").document(uid).update(
                    mapOf("status" to "EXPIRED", "updatedAt" to FieldValue.serverTimestamp())
                )
                val name = users.firstOrNull { it["id"] == uid }?.get("name")?.toString().orEmpty()
                logHistory(uid, name, "انتهاء", (s["startedAt"] as? Timestamp)?.toDate(), exp, 0)
            }
        }
    }

    val doctors = users.count { it["accountType"] == "doctor" }
    val clinics = users.count { it["accountType"] == "clinic" }
    val activeN = users.count { statusOf(subs[it["id"] as String]) == "ACTIVE" }
    val expiredN = users.count { statusOf(subs[it["id"] as String]) == "EXPIRED" }
    val weekAhead = Date(System.currentTimeMillis() + 7L * 24 * 3600 * 1000)
    val soonN = users.count {
        val s = subs[it["id"] as String]
        statusOf(s) == "ACTIVE" && ((s?.get("expiresAt") as? Timestamp)?.toDate()?.before(weekAhead) == true)
    }
    val renewals = history.count { it["action"] == "تجديد" }
    val cashN = history.count { it["action"] == "تفعيل" || it["action"] == "تجديد" }

    LazyColumn(
        Modifier.fillMaxSize().background(ABg).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        item { TextButton(onClick = onBack) { Text("رجوع", color = ARed) } }
        item { Text("لوحة الاشتراكات", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White) }
        if (error.isNotEmpty()) item { Text(error, color = ARed) }
        if (info.isNotEmpty()) item { Text(info, color = AGreen) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatBox("الأطباء", doctors, Modifier.weight(1f))
                StatBox("العيادات", clinics, Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatBox("اشتراكات فعالة", activeN, Modifier.weight(1f))
                StatBox("اشتراكات منتهية", expiredN, Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatBox("تنتهي خلال 7 أيام", soonN, Modifier.weight(1f))
                StatBox("عمليات التجديد", renewals, Modifier.weight(1f))
            }
        }
        item { StatBox("عمليات الدفع النقدي المسجلة (آخر 100 عملية)", cashN, Modifier.fillMaxWidth()) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = tab == 0, onClick = { tab = 0 }, label = { Text("الأطباء والعيادات") })
                FilterChip(selected = tab == 1, onClick = { tab = 1 }, label = { Text("سجل الاشتراكات") })
            }
        }
        if (tab == 0) {
            item { TableHeader() }
            if (users.isEmpty()) item { Text("لا توجد حسابات أطباء أو عيادات بعد", color = AMuted) }
            items(users, key = { it["id"] as String }) { u ->
                val id = u["id"] as String
                DoctorRow(
                    u, subs[id], specs[id].orEmpty(),
                    onGrant = { grantUser = u },
                    onCancel = { cancelUser = u }
                )
            }
        } else {
            if (history.isEmpty()) item { Text("لا توجد عمليات بعد", color = AMuted) }
            items(history, key = { it["id"] as String }) { h -> HistoryCard(h) }
        }
    }

    val gu = grantUser
    if (gu != null) {
        var months by remember(gu) { mutableIntStateOf(1) }
        AlertDialog(
            onDismissRequest = { grantUser = null },
            title = { Text("مدة الاشتراك (دفع نقدًا)") },
            text = {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(1, 3, 6, 12).forEach { m ->
                        FilterChip(selected = months == m, onClick = { months = m }, label = { Text("$m") })
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    busy = true
                    grant(gu, subs[gu["id"] as String], months) { ok ->
                        busy = false
                        if (ok) { info = "تم حفظ الاشتراك"; error = "" } else error = "تعذر حفظ الاشتراك، حاول مرة أخرى"
                        grantUser = null
                    }
                }) { Text("تأكيد", color = ARed) }
            },
            dismissButton = { TextButton(onClick = { grantUser = null }) { Text("إلغاء") } }
        )
    }

    val cu = cancelUser
    if (cu != null) {
        AlertDialog(
            onDismissRequest = { cancelUser = null },
            title = { Text("إلغاء الاشتراك") },
            text = { Text("بعد التأكيد تُقفل المزايا المدفوعة، والحساب والبيانات تبقى محفوظة.") },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    busy = true
                    cancelSub(cu, subs[cu["id"] as String]) { ok ->
                        busy = false
                        if (ok) { info = "تم إلغاء الاشتراك"; error = "" } else error = "تعذر الإلغاء، حاول مرة أخرى"
                        cancelUser = null
                    }
                }) { Text("تأكيد الإلغاء", color = ARed) }
            },
            dismissButton = { TextButton(onClick = { cancelUser = null }) { Text("رجوع") } }
        )
    }
}

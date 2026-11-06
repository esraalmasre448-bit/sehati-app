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

private val ABg = Color(0xFF0B1220)
private val ACard = Color(0xFF131C2E)
private val ALine = Color(0xFF24304A)
private val ARed = Color(0xFFE5171F)
private val ADanger = Color(0xFFFF5A5F)
private val AOnAccent = Color(0xFFFFFFFF)
private val AMuted = Color(0xFF9AA7BD)
private val AGreen = Color(0xFF3DDC84)
private val AExpiredBg = Color(0xFF2A1215)

private val aAuth get() = FirebaseAuth.getInstance()
private val aDb get() = FirebaseFirestore.getInstance()

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
                state = if (!d.exists()) "denied" else if (d.getString("uid") == myUid) "panel" else "denied"
            }
            .addOnFailureListener { state = "error" }
    }
    Box(Modifier.fillMaxSize().background(ABg)) {
        when (state) {
            "loading" -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = ARed)
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
    approved: Boolean, profileExists: Boolean, onToggleApproval: () -> Unit,
    onGrant: () -> Unit, onCancel: () -> Unit
) {
    val st = statusOf(sub)
    val expiredLike = st == "EXPIRED" || st == "CANCELLED"
    val (label, color) = when (st) {
        "ACTIVE" -> "فعّال" to AGreen
        "EXPIRED" -> "منتهي" to ADanger
        "CANCELLED" -> "ملغى" to ADanger
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
            .border(1.dp, if (expiredLike) ADanger else ALine, RoundedCornerShape(12.dp))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1.3f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("${u["name"]}", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text(if (u["accountType"] == "clinic") "عيادة" else "طبيب", color = AMuted, fontSize = 12.sp)
            Text("المعرّف: ${u["id"]}", color = AMuted, fontSize = 10.sp)
            Text(label, color = color, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            if (detail.isNotEmpty()) Text(detail, color = color, fontSize = 11.sp)
            Text(if (approved) "الملف معتمد" else "بانتظار اعتماد الملف", color = if (approved) AGreen else Color(0xFFFFC857), fontSize = 11.sp)
        }
        Text(
            specialty.ifBlank { "—" }, Modifier.weight(0.9f),
            color = Color.White, fontSize = 14.sp
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(
                onClick = onToggleApproval,
                enabled = profileExists,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                modifier = Modifier.fillMaxWidth()
            ) { Text(
                when {
                    !profileExists -> "بانتظار الملف"
                    approved -> "إلغاء الاعتماد"
                    else -> "اعتماد الملف"
                },
                fontSize = 12.sp
            ) }
            Button(
                onClick = onGrant,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ARed, contentColor = AOnAccent),
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
private fun RenewalRequestCard(
    request: Map<String, Any?>,
    busy: Boolean,
    onApprove: () -> Unit,
    onReject: () -> Unit
) {
    val status = request["status"]?.toString() ?: "PENDING"
    val statusLabel = when (status) {
        "APPROVED" -> "تم التفعيل"
        "REJECTED" -> "مرفوض"
        else -> "بانتظار المراجعة"
    }
    Column(
        Modifier.fillMaxWidth().background(ACard, RoundedCornerShape(12.dp))
            .border(1.dp, ALine, RoundedCornerShape(12.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Text(request["doctorName"]?.toString().orEmpty().ifBlank { "طبيب / عيادة" },
            color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text("معرّف الحساب: ${request["uid"] ?: "—"}", color = AMuted, fontSize = 11.sp)
        Text("المدة: ${request["planLabel"] ?: request["months"] ?: "—"}", color = Color.White)
        val amount = request["amount"]?.toString().orEmpty()
        if (amount.isNotBlank()) Text("المبلغ المعلن: $amount", color = AMuted)
        Text(statusLabel, color = when (status) {
            "APPROVED" -> AGreen
            "REJECTED" -> ADanger
            else -> Color(0xFFFFC857)
        }, fontWeight = FontWeight.Bold)
        if (status == "PENDING") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onApprove, enabled = !busy, modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = AGreen, contentColor = Color.Black)
                ) { Text("تأكيد الدفع والتفعيل") }
                OutlinedButton(onClick = onReject, enabled = !busy, modifier = Modifier.weight(1f)) {
                    Text("رفض الطلب", color = ADanger)
                }
            }
        }
    }
}

@Composable
private fun AdminPanel(onBack: () -> Unit) {
    val adminUid = aAuth.currentUser?.uid ?: ""
    var tab by remember { mutableIntStateOf(0) }
    var users by remember { mutableStateOf(listOf<Map<String, Any?>>()) }
    var patientCount by remember { mutableIntStateOf(0) }
    var systemStatus by remember { mutableStateOf("جارٍ التحقق من الاتصال") }
    var subs by remember { mutableStateOf(mapOf<String, Map<String, Any?>>()) }
    var specs by remember { mutableStateOf(mapOf<String, String>()) }
    var approvals by remember { mutableStateOf(mapOf<String, Boolean>()) }
    var profileExists by remember { mutableStateOf(emptySet<String>()) }
    var history by remember { mutableStateOf(listOf<Map<String, Any?>>()) }
    var renewalRequests by remember { mutableStateOf(listOf<Map<String, Any?>>()) }
    var error by remember { mutableStateOf("") }
    var info by remember { mutableStateOf("") }
    var grantUser by remember { mutableStateOf<Map<String, Any?>?>(null) }
    var cancelUser by remember { mutableStateOf<Map<String, Any?>?>(null) }
    var busy by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
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
            if (e == null) {
                specs = s?.documents?.associate { it.id to (it.getString("specialty") ?: "") }.orEmpty()
                approvals = s?.documents?.associate { it.id to (it.getBoolean("approved") == true) }.orEmpty()
                profileExists = s?.documents?.map { it.id }?.toSet().orEmpty()
            }
        }
        val r5 = aDb.collection("users").whereEqualTo("accountType", "patient").limit(1000)
            .addSnapshotListener { s, e -> if (e == null) patientCount = s?.size() ?: 0 }
        val r6 = aDb.collection("renewalRequests").orderBy("createdAt", Query.Direction.DESCENDING).limit(100)
            .addSnapshotListener { s, e ->
                if (e != null) error = "تعذر تحميل طلبات التجديد"
                else renewalRequests = s?.documents?.map { it.data.orEmpty() + ("id" to it.id) }.orEmpty()
            }
        // لا تقرأ لوحة الإدارة سجلات المواعيد أو بياناتها الطبية؛ تبقى بين المريض والطبيب.
        systemStatus = "متصل بقاعدة البيانات"
        onDispose { r1.remove(); r2.remove(); r3.remove(); r4.remove(); r5.remove(); r6.remove() }
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
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("لوحة المطوّر", fontSize = 23.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text("إدارة ومتابعة الطريق إلى صحتي", color = AMuted, fontSize = 12.sp)
                }
                Surface(color = ACard, shape = RoundedCornerShape(20.dp), border = androidx.compose.foundation.BorderStroke(1.dp, ALine)) {
                    Text("ADMIN", Modifier.padding(horizontal = 12.dp, vertical = 7.dp), color = ARed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        if (error.isNotEmpty()) item { Text(error, color = ADanger) }
        if (info.isNotEmpty()) item { Text(info, color = AGreen) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = tab == 0, onClick = { tab = 0 }, label = { Text("نظرة عامة") })
                FilterChip(selected = tab == 1, onClick = { tab = 1 }, label = { Text("الحسابات") })
                FilterChip(selected = tab == 2, onClick = { tab = 2 }, label = { Text("الاشتراكات") })
                FilterChip(selected = tab == 3, onClick = { tab = 3 }, label = { Text("مراقبة") })
            }
            Row {
                FilterChip(
                    selected = tab == 4,
                    onClick = { tab = 4 },
                    label = { Text("طلبات تجديد الاشتراك (${renewalRequests.count { it["status"] == "PENDING" }})") }
                )
            }
        }
        if (tab == 0) {
            item { Text("ملخص المنظومة", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatBox("الأطباء", doctors, Modifier.weight(1f))
                    StatBox("العيادات", clinics, Modifier.weight(1f))
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatBox("المرضى المسجلون*", patientCount, Modifier.weight(1f))
                    StatBox("الحسابات الطبية", doctors + clinics, Modifier.weight(1f))
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
            item { StatBox("عمليات الدفع المسجلة (آخر 100 عملية)", cashN, Modifier.fillMaxWidth()) }
            item { Text("* الأعداد محدودة بأول 1000 سجل محمّل، وليست إحصاءً شاملاً عند تجاوز الحد.", color = AMuted, fontSize = 11.sp) }
            item {
                Row(Modifier.fillMaxWidth().background(ACard, RoundedCornerShape(14.dp))
                    .border(1.dp, ALine, RoundedCornerShape(14.dp)).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(9.dp).background(if (systemStatus.startsWith("متصل")) AGreen else ARed, RoundedCornerShape(50)))
                    Spacer(Modifier.width(9.dp))
                    Column {
                        Text("حالة الاتصال", color = Color.White, fontWeight = FontWeight.Bold)
                        Text(systemStatus, color = AMuted, fontSize = 12.sp)
                    }
                }
            }
        } else if (tab == 3) {
            item { Text("مراقبة النظام", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
            item {
                Column(Modifier.fillMaxWidth().background(ACard, RoundedCornerShape(16.dp))
                    .border(1.dp, ALine, RoundedCornerShape(16.dp)).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("اتصال Firestore", color = Color.White, fontWeight = FontWeight.Bold)
                    Text(systemStatus, color = if (systemStatus.startsWith("متصل")) AGreen else ADanger)
                    Text("اللوحة تعرض مؤشرات تشغيلية فقط، ولا تعرض محتوى الرسائل أو الوصفات الطبية.", color = AMuted, fontSize = 12.sp)
                    Text("لا يوجد حالياً سجل مركزي لأخطاء التطبيق وأحداث الأمان؛ لن نعرض تنبيهات أو أرقاماً غير متوفرة.", color = AMuted, fontSize = 12.sp)
                }
            }
        } else if (tab == 1) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("ابحث بالاسم أو المعرّف (uid)") }
                )
            }
            item { TableHeader() }
            val q = query.trim()
            val shown = if (q.isEmpty()) users else users.filter { u ->
                "${u["name"] ?: ""}".contains(q, ignoreCase = true) ||
                    (u["id"] as String).contains(q, ignoreCase = true)
            }
            if (users.isEmpty()) item { Text("لا توجد حسابات أطباء أو عيادات بعد", color = AMuted) }
            else if (shown.isEmpty()) item { Text("لا توجد نتائج مطابقة للبحث", color = AMuted) }
            items(shown, key = { it["id"] as String }) { u ->
                val id = u["id"] as String
                DoctorRow(
                    u, subs[id], specs[id].orEmpty(), approvals[id] == true,
                    profileExists = id in profileExists,
                    onToggleApproval = {
                        val next = approvals[id] != true
                        aDb.collection("doctors").document(id).update(
                            mapOf(
                                "approved" to next,
                                "approvedBy" to adminUid,
                                "approvedAt" to FieldValue.serverTimestamp()
                            )
                        ).addOnSuccessListener {
                            info = if (next) "تم اعتماد الملف المهني" else "تم إلغاء اعتماد الملف المهني"
                            error = ""
                        }.addOnFailureListener {
                            error = "تعذر تحديث الاعتماد. تحقق من صلاحيات الإدارة والإنترنت"
                        }
                    },
                    onGrant = { grantUser = u },
                    onCancel = { cancelUser = u }
                )
            }
        } else if (tab == 2) {
            if (history.isEmpty()) item { Text("لا توجد عمليات بعد", color = AMuted) }
            items(history, key = { it["id"] as String }) { h -> HistoryCard(h) }
        } else if (tab == 4) {
            item { Text("طلبات الدفع التي أرسلها الأطباء والعيادات", color = Color.White, fontWeight = FontWeight.Bold) }
            if (renewalRequests.isEmpty()) item { Text("لا توجد طلبات تجديد حالياً", color = AMuted) }
            items(renewalRequests, key = { it["id"] as String }) { request ->
                RenewalRequestCard(
                    request = request,
                    busy = busy,
                    onApprove = {
                        val requestId = request["id"]?.toString().orEmpty()
                        val requestRef = aDb.collection("renewalRequests").document(requestId)
                        busy = true
                        aDb.runTransaction { tx ->
                            val freshRequest = tx.get(requestRef)
                            require(freshRequest.exists() && freshRequest.getString("status") == "PENDING") {
                                "تمت مراجعة هذا الطلب مسبقاً"
                            }
                            val uid = freshRequest.getString("uid") ?: throw IllegalStateException("معرّف الحساب مفقود")
                            val months = (freshRequest.getLong("months") ?: 1L).toInt()
                            require(months in listOf(1, 3, 6, 12)) { "مدة الاشتراك غير صحيحة" }
                            val subRef = aDb.collection("subscriptions").document(uid)
                            val oldSub = tx.get(subRef)
                            val now = Date()
                            val oldExpiry = oldSub.getTimestamp("expiresAt")?.toDate()
                            val active = oldSub.exists() && statusOf(oldSub.data) == "ACTIVE"
                            val base = if (active && oldExpiry != null) oldExpiry else now
                            val calendar = Calendar.getInstance().apply { time = base; add(Calendar.MONTH, months) }
                            val newExpiry = calendar.time
                            val started = if (active) oldSub.getTimestamp("startedAt")?.toDate() ?: now else now
                            val historyRef = aDb.collection("subscriptionHistory").document()
                            val doctorName = freshRequest.getString("doctorName").orEmpty()
                            tx.set(subRef, mapOf(
                                "uid" to uid, "status" to "ACTIVE",
                                "startedAt" to Timestamp(started), "expiresAt" to Timestamp(newExpiry),
                                "paymentMethod" to "Sham Cash", "updatedAt" to FieldValue.serverTimestamp()
                            ))
                            tx.update(requestRef, mapOf(
                                "status" to "APPROVED", "reviewedAt" to FieldValue.serverTimestamp(),
                                "reviewedBy" to adminUid
                            ))
                            tx.set(historyRef, mapOf(
                                "uid" to uid, "name" to doctorName,
                                "action" to if (oldSub.exists()) "تجديد" else "تفعيل",
                                "paymentMethod" to "شام كاش",
                                "startAt" to Timestamp(started), "expiresAt" to Timestamp(newExpiry),
                                "months" to months, "adminUid" to adminUid,
                                "createdAt" to FieldValue.serverTimestamp()
                            ))
                            "تم اعتماد طلب الدفع وتفعيل الاشتراك"
                        }.addOnSuccessListener { message ->
                            busy = false; info = message; error = ""
                        }.addOnFailureListener { ex ->
                            busy = false; error = ex.message ?: "تعذر اعتماد طلب الدفع"
                        }
                    },
                    onReject = {
                        val requestId = request["id"]?.toString().orEmpty()
                        busy = true
                        aDb.collection("renewalRequests").document(requestId).update(mapOf(
                            "status" to "REJECTED", "reviewedAt" to FieldValue.serverTimestamp(),
                            "reviewedBy" to adminUid
                        )).addOnSuccessListener {
                            busy = false; info = "تم رفض طلب التجديد"; error = ""
                        }.addOnFailureListener {
                            busy = false; error = "تعذر رفض الطلب"
                        }
                    }
                )
            }
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
                }) { Text("تأكيد الإلغاء", color = ADanger) }
            },
            dismissButton = { TextButton(onClick = { cancelUser = null }) { Text("رجوع") } }
        )
    }
}

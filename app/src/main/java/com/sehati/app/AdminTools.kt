package com.sehati.app

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions

/** إعدادات المطوّر: الاسم وصورة الملف. */
@Composable
fun DevSettingsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val uid = bAuth.currentUser?.uid ?: ""
    var name by remember { mutableStateOf("") }
    var photo by remember { mutableStateOf<String?>(null) }
    var msg by remember { mutableStateOf("") }
    var ok by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(uid) {
        if (uid.isEmpty()) return@LaunchedEffect
        bDb.collection("users").document(uid).get().addOnSuccessListener { name = it.getString("name") ?: "" }
        bDb.collection("config").document("developer").get().addOnSuccessListener { d ->
            photo = d.getString("photo")
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val encoded = compressPhoto(ctx, uri)
        if (encoded == null) {
            ok = false; msg = "الصورة كبيرة أو غير مدعومة، جرّب صورة ثانية"
            return@rememberLauncherForActivityResult
        }
        busy = true
        msg = ""
        bDb.collection("config").document("developer")
            .set(mapOf("photo" to encoded, "updatedAt" to FieldValue.serverTimestamp()), SetOptions.merge())
            .addOnSuccessListener {
                busy = false; ok = true; photo = encoded; msg = "تم حفظ الصورة"
            }
            .addOnFailureListener {
                busy = false; ok = false
                msg = "تعذّر حفظ الصورة، تحقق من الإنترنت وحاول مجدداً"
            }
    }

    Column(
        Modifier.fillMaxSize().background(BBg).padding(20.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TextButton(onClick = onBack) { Text("رجوع", color = BRed) }
        Text("إعدادات المطوّر", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            DoctorAvatar(photo, name.ifBlank { "م" }, 96.dp)
        }
        OutlinedButton(
            onClick = { picker.launch("image/*") },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) { Text(if (busy) "جارٍ الحفظ..." else "تغيير الصورة", color = Color.White) }
        Field(name, { name = it }, "الاسم")
        if (msg.isNotEmpty()) Text(msg, color = if (ok) BGreen else BDanger)
        RedButton("حفظ الاسم", enabled = !busy) {
            val n = name.trim()
            if (n.isBlank()) {
                ok = false; msg = "اكتب اسمك"
                return@RedButton
            }
            busy = true
            msg = ""
            bDb.collection("users").document(uid).set(mapOf("name" to n), SetOptions.merge())
                .addOnSuccessListener {
                    busy = false; ok = true; msg = "تم حفظ الاسم"
                }
                .addOnFailureListener {
                    busy = false; ok = false
                    msg = "تعذر الحفظ، تحقق من الإنترنت وحاول مرة أخرى"
                }
        }
    }
}

/** تجربة التطبيق: اختبار الاتصال الفعلي بقاعدة البيانات من هذا الجهاز. */
@Composable
fun DbTestScreen(onBack: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(BBg).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TextButton(onClick = onBack) { Text("رجوع", color = BRed) }
        Text("تجربة التطبيق", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text("اختبار الاتصال بقاعدة البيانات من هذا الجهاز.", color = BMuted)
        DbStatusCard()
    }
}

/** إشعارات الحساب الحالي. */
@Composable
fun AdminNotifsScreen(onBack: () -> Unit) {
    val uid = bAuth.currentUser?.uid ?: ""
    val ns = rememberNotifs(uid, "")
    Column(
        Modifier.fillMaxSize().background(BBg).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TextButton(onClick = onBack) { Text("رجوع", color = BRed) }
        Text("الإشعارات", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            NotifTab(ns.items, emptySet())
        }
    }
}

@Composable
private fun CountBox(label: String, value: Long?, modifier: Modifier = Modifier, tint: Color = BCard) {
    Column(
        modifier.background(tint, RoundedCornerShape(14.dp))
            .border(1.dp, BLine, RoundedCornerShape(14.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(label, color = BMuted, fontSize = 14.sp)
        Text(value?.toString() ?: "...", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun AdminStatsScreen(onBack: () -> Unit) {
    var patients by remember { mutableStateOf<Long?>(null) }
    var doctors by remember { mutableStateOf<Long?>(null) }
    var clinics by remember { mutableStateOf<Long?>(null) }
    var profiles by remember { mutableStateOf<Long?>(null) }
    var appts by remember { mutableStateOf<Long?>(null) }
    var apptsToday by remember { mutableStateOf<Long?>(null) }
    var error by remember { mutableStateOf("") }
    var tick by remember { mutableIntStateOf(0) }

    LaunchedEffect(tick) {
        error = ""
        fun cnt(q: Query, set: (Long) -> Unit) {
            q.count().get(AggregateSource.SERVER)
                .addOnSuccessListener { set(it.count) }
                .addOnFailureListener { error = "تعذر جلب الإحصائيات. تأكد أنك مسجّل دخول بحساب المطوّر." }
        }
        val users = bDb.collection("users")
        cnt(users.whereEqualTo("accountType", "patient")) { patients = it }
        cnt(users.whereEqualTo("accountType", "doctor")) { doctors = it }
        cnt(users.whereEqualTo("accountType", "clinic")) { clinics = it }
        cnt(bDb.collection("doctors")) { profiles = it }
        cnt(bDb.collection("appointments")) { appts = it }
        cnt(bDb.collection("appointments").whereEqualTo("date", todayKey())) { apptsToday = it }
    }

    val total = if (patients != null && doctors != null && clinics != null)
        (patients ?: 0L) + (doctors ?: 0L) + (clinics ?: 0L) else null

    Column(
        Modifier.fillMaxSize().background(BBg).padding(20.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TextButton(onClick = onBack) { Text("رجوع", color = BRed) }
        Text("إحصائيات المستخدمين", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
        if (error.isNotEmpty()) Text(error, color = BDanger)
        CountBox("إجمالي الحسابات", total, Modifier.fillMaxWidth(), Color(0xFF3A4660))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CountBox("مرضى", patients, Modifier.weight(1f), Color(0xFF0F6B4B))
            CountBox("أطباء", doctors, Modifier.weight(1f), Color(0xFF1F4FB8))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CountBox("عيادات", clinics, Modifier.weight(1f), Color(0xFF5B2FA8))
            CountBox("ملفات مهنية مكتملة", profiles, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CountBox("كل الحجوزات", appts, Modifier.weight(1f))
            CountBox("حجوزات اليوم", apptsToday, Modifier.weight(1f))
        }
        OutlinedButton(
            onClick = {
                patients = null; doctors = null; clinics = null
                profiles = null; appts = null; apptsToday = null
                tick++
            },
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) { Text("تحديث", color = Color.White) }
    }
}

package com.sehati.app

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import java.io.ByteArrayOutputStream

private const val DEV_TAGLINE = "مؤسس ومطوّر الطريق إلى صحتي"
private const val GITHUB_URL = "https://github.com/esraalmasre448-bit/sehati-app"
private const val FIREBASE_URL = "https://console.firebase.google.com/"

fun compressFit(ctx: Context, uri: Uri): String? {
    return try {
        val maxSide = 600
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxSide && bounds.outHeight / (sample * 2) >= maxSide) {
            sample *= 2
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val src = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: return null
        val big = maxOf(src.width, src.height)
        val bmp = if (big > maxSide) {
            val f = maxSide.toFloat() / big
            Bitmap.createScaledBitmap(src, (src.width * f).toInt().coerceAtLeast(1), (src.height * f).toInt().coerceAtLeast(1), true)
        } else src
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 90, out)
        val bytes = out.toByteArray()
        if (bytes.size > 350_000) null else Base64.encodeToString(bytes, Base64.NO_WRAP)
    } catch (e: Exception) {
        null
    }
}

private fun openUrl(ctx: Context, url: String) {
    try {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        Toast.makeText(ctx, "تعذر فتح الرابط", Toast.LENGTH_SHORT).show()
    }
}

/** لوحة المطوّر. تُفتح فقط عبر AdminGate، وكل زر فيها يفتح شاشة حقيقية أو رابطاً فعلياً. */
@Composable
fun AdminHub(onBack: () -> Unit) {
    var page by remember { mutableStateOf("menu") }
    when (page) {
        "subs" -> AdminScreen { page = "menu" }
        "stats" -> AdminStatsScreen { page = "menu" }
        "pay" -> AdminPaymentScreen { page = "menu" }
        "dev" -> DevSettingsScreen { page = "menu" }
        "db" -> DbTestScreen { page = "menu" }
        "notifs" -> AdminNotifsScreen { page = "menu" }
        else -> AdminMenu(onBack = onBack, onPage = { page = it })
    }
}

@Composable
private fun AdminMenu(onBack: () -> Unit, onPage: (String) -> Unit) {
    val ctx = LocalContext.current
    val uid = bAuth.currentUser?.uid ?: ""
    var name by remember { mutableStateOf("") }
    var photo by remember { mutableStateOf<String?>(null) }
    var patients by remember { mutableStateOf<Long?>(null) }
    var doctors by remember { mutableStateOf<Long?>(null) }
    var clinics by remember { mutableStateOf<Long?>(null) }
    var appts by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(uid) {
        if (uid.isEmpty()) return@LaunchedEffect
        bDb.collection("users").document(uid).get().addOnSuccessListener { name = it.getString("name") ?: "" }
        bDb.collection("config").document("developer").get().addOnSuccessListener { d ->
            photo = d.getString("photo")
        }
    }
    LaunchedEffect(Unit) {
        val users = bDb.collection("users")
        users.whereEqualTo("accountType", "patient").count().get(AggregateSource.SERVER)
            .addOnSuccessListener { patients = it.count }
        users.whereEqualTo("accountType", "doctor").count().get(AggregateSource.SERVER)
            .addOnSuccessListener { doctors = it.count }
        users.whereEqualTo("accountType", "clinic").count().get(AggregateSource.SERVER)
            .addOnSuccessListener { clinics = it.count }
        bDb.collection("appointments").count().get(AggregateSource.SERVER)
            .addOnSuccessListener { appts = it.count }
    }
    val total = if (patients != null && doctors != null && clinics != null)
        (patients ?: 0L) + (doctors ?: 0L) + (clinics ?: 0L) else null

    Column(
        Modifier.fillMaxSize().background(BBg).padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TextButton(onClick = onBack) { Text("رجوع", color = BRed) }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "مرحباً بك ${name.ifBlank { "المطوّر" }}",
                    fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White
                )
                Text(DEV_TAGLINE, color = BMuted, fontSize = 14.sp)
                Text("لأن الصحة رسالة.. والتكنولوجيا وسيلتنا", color = BMuted, fontSize = 12.sp)
            }
            Box(Modifier.clickable { onPage("dev") }) {
                DoctorAvatar(photo, name.ifBlank { "م" }, 72.dp)
            }
        }

        OutlinedButton(
            onClick = { onPage("pay") },
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) { Text("إدارة التطبيق والإعدادات", color = Color.White) }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AdminTile(Icons.Filled.Code, "كود التطبيق", "عرض صفحة المستودع", Modifier.weight(1f)) {
                openUrl(ctx, GITHUB_URL)
            }
            AdminTile(Icons.Filled.Storage, "قاعدة البيانات", "إدارة البيانات (Firebase)", Modifier.weight(1f)) {
                openUrl(ctx, FIREBASE_URL)
            }
            AdminTile(Icons.Filled.BarChart, "إحصائيات التطبيق", "المستخدمين - الحجوزات", Modifier.weight(1f)) {
                onPage("stats")
            }
        }

        Text("أدوات المطوّر", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AdminTile(Icons.Filled.PhoneAndroid, "تجربة التطبيق", "اختبار الاتصال", Modifier.weight(1f)) {
                onPage("db")
            }
            AdminTile(Icons.Filled.Shield, "أمان التطبيق", "قواعد Firebase", Modifier.weight(1f)) {
                openUrl(ctx, FIREBASE_URL)
            }
            AdminTile(Icons.Filled.Cloud, "السحابية", "Firebase", Modifier.weight(1f)) {
                openUrl(ctx, FIREBASE_URL)
            }
            AdminTile(Icons.Filled.Group, "المستخدمين", "إدارة الحسابات", Modifier.weight(1f)) {
                onPage("subs")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AdminTile(Icons.Filled.Notifications, "الإشعارات", "داخل التطبيق", Modifier.weight(1f)) {
                onPage("notifs")
            }
            AdminTile(Icons.Filled.Description, "سجلات النظام", "سجل الاشتراكات", Modifier.weight(1f)) {
                onPage("subs")
            }
            AdminTile(Icons.Filled.Settings, "إعدادات عامة", "معلومات التواصل", Modifier.weight(1f)) {
                onPage("pay")
            }
            Spacer(Modifier.weight(1f))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("المستخدمين", total?.toString() ?: "...", Modifier.weight(1f))
            StatTile("الحجوزات", appts?.toString() ?: "...", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("الأطباء", doctors?.toString() ?: "...", Modifier.weight(1f))
            StatTile("المرضى", patients?.toString() ?: "...", Modifier.weight(1f))
        }

        OutlinedButton(
            onClick = { onPage("dev") },
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) { Text("إعدادات المطوّر (الاسم والصورة)", color = Color.White) }
    }
}

@Composable
private fun AdminTile(
    icon: ImageVector, title: String, sub: String, modifier: Modifier, onClick: () -> Unit
) {
    Column(
        modifier.height(110.dp)
            .background(BCard, RoundedCornerShape(14.dp))
            .border(1.dp, BRed, RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(26.dp))
        Spacer(Modifier.height(6.dp))
        Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp, textAlign = TextAlign.Center, maxLines = 2)
        Text(sub, color = BMuted, fontSize = 10.sp, textAlign = TextAlign.Center, maxLines = 2)
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier) {
    Row(
        modifier.background(BCard, RoundedCornerShape(12.dp))
            .border(1.dp, BLine, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column {
            Text(label, color = BMuted, fontSize = 13.sp)
            Text(value, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
    }
}

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

@Composable
fun AdminPaymentScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var sham by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var qr by remember { mutableStateOf("") }
    var p1 by remember { mutableStateOf("") }
    var p3 by remember { mutableStateOf("") }
    var p6 by remember { mutableStateOf("") }
    var p12 by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }
    var ok by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        bDb.collection("config").document("contact").get().addOnSuccessListener { d ->
            if (d.exists()) {
                name = d.getString("name") ?: ""
                phone = d.getString("phone") ?: ""
                sham = d.getString("sham") ?: ""
                notes = d.getString("notes") ?: ""
                qr = d.getString("qr") ?: ""
                p1 = d.getString("price1") ?: ""
                p3 = d.getString("price3") ?: ""
                p6 = d.getString("price6") ?: ""
                p12 = d.getString("price12") ?: ""
            }
            loaded = true
        }.addOnFail

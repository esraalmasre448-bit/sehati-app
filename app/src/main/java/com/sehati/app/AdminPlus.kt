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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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

private val AccBlue = Color(0xFF3B82F6)
private val AccPurple = Color(0xFFA78BFA)
private val AccAmber = Color(0xFFF59E0B)

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

/** لوحة المطوّر بشريط جانبي. تُفتح فقط عبر AdminGate، وكل زر فيها يفتح شاشة حقيقية أو رابطاً فعلياً. */
@Composable
fun AdminHub(onBack: () -> Unit) {
    val uid = bAuth.currentUser?.uid ?: ""
    var page by remember { mutableStateOf("menu") }
    var name by remember { mutableStateOf("") }
    var photo by remember { mutableStateOf<String?>(null) }

    // يُعاد التحميل عند كل تبديل صفحة، فتتحدّث الصورة بعد تغييرها
    LaunchedEffect(uid, page) {
        if (uid.isEmpty()) return@LaunchedEffect
        bDb.collection("users").document(uid).get().addOnSuccessListener { name = it.getString("name") ?: "" }
        bDb.collection("config").document("developer").get().addOnSuccessListener { d ->
            photo = d.getString("photo")
        }
    }

    Row(Modifier.fillMaxSize().background(BBg)) {
        AdminRail(page, name, photo, onSelect = { page = it }, onBack = onBack)
        Box(Modifier.width(1.dp).fillMaxHeight().background(BLine))
        Box(Modifier.weight(1f).fillMaxHeight()) {
            when (page) {
                "subs" -> AdminScreen { page = "menu" }
                "stats" -> AdminStatsScreen { page = "menu" }
                "pay" -> AdminPaymentScreen { page = "menu" }
                "dev" -> DevSettingsScreen { page = "menu" }
                "db" -> DbTestScreen { page = "menu" }
                "notifs" -> AdminNotifsScreen { page = "menu" }
                else -> AdminHome(name, photo, onPage = { page = it })
            }
        }
    }
}

/** الشريط الجانبي: في RTL يظهر على اليمين. */
@Composable
private fun AdminRail(
    page: String, name: String, photo: String?,
    onSelect: (String) -> Unit, onBack: () -> Unit
) {
    Column(
        Modifier
            .width(78.dp)
            .fillMaxHeight()
            .background(BCard)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 6.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        RailItem(Icons.Filled.ArrowForward, "رجوع", false) { onBack() }
        Spacer(Modifier.height(4.dp))
        RailItem(Icons.Filled.Home, "الرئيسية", page == "menu") { onSelect("menu") }
        RailItem(Icons.Filled.Group, "الحسابات", page == "subs") { onSelect("subs") }
        RailItem(Icons.Filled.BarChart, "الإحصائيات", page == "stats") { onSelect("stats") }
        RailItem(Icons.Filled.Settings, "الإدارة", page == "pay") { onSelect("pay") }
        RailItem(Icons.Filled.Notifications, "الإشعارات", page == "notifs") { onSelect("notifs") }
        RailItem(Icons.Filled.PhoneAndroid, "التجربة", page == "db") { onSelect("db") }
        RailItem(Icons.Filled.Person, "المطوّر", page == "dev") { onSelect("dev") }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.clickable { onSelect("dev") }) {
            DoctorAvatar(photo, name.ifBlank { "م" }, 42.dp)
        }
        Text(
            name.ifBlank { "المطوّر" },
            color = BMuted, fontSize = 10.sp, maxLines = 1,
            overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun RailItem(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(if (selected) BRed else Color.Transparent, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Icon(icon, null, tint = if (selected) BOnAccent else BMuted, modifier = Modifier.size(22.dp))
        Text(
            label,
            color = if (selected) BOnAccent else BMuted,
            fontSize = 10.sp, maxLines = 1, textAlign = TextAlign.Center,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun AdminHome(name: String, photo: String?, onPage: (String) -> Unit) {
    val ctx = LocalContext.current
    var patients by remember { mutableStateOf<Long?>(null) }
    var doctors by remember { mutableStateOf<Long?>(null) }
    var clinics by remember { mutableStateOf<Long?>(null) }
    var appts by remember { mutableStateOf<Long?>(null) }

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
        Modifier.fillMaxSize().padding(14.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("الرئيسية", fontSize = 12.sp, color = BRed, fontWeight = FontWeight.Bold)
                Text(
                    "مرحباً بك ${name.ifBlank { "المطوّر" }}",
                    fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White
                )
                Text(DEV_TAGLINE, color = BMuted, fontSize = 13.sp)
                Text("لأن الصحة رسالة.. والتكنولوجيا وسيلتنا", color = BMuted, fontSize = 11.sp)
            }
            Box(Modifier.clickable { onPage("dev") }) {
                DoctorAvatar(photo, name.ifBlank { "م" }, 56.dp)
            }
        }

        // الإحصائيات
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AdminStat("إجمالي المستخدمين", total?.toString() ?: "...", Icons.Filled.Group, AccBlue, Modifier.weight(1f))
            AdminStat("الحجوزات", appts?.toString() ?: "...", Icons.Filled.CalendarMonth, AccAmber, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AdminStat("الأطباء", doctors?.toString() ?: "...", Icons.Filled.MedicalServices, BRed, Modifier.weight(1f))
            AdminStat("المرضى", patients?.toString() ?: "...", Icons.Filled.Person, BGreen, Modifier.weight(1f))
        }

        DonutCard(patients, doctors, clinics)

        Text("أدوات المطوّر", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AdminTile(Icons.Filled.Code, "كود التطبيق", "عرض صفحة المستودع", AccPurple, Modifier.weight(1f)) {
                openUrl(ctx, GITHUB_URL)
            }
            AdminTile(Icons.Filled.Storage, "قاعدة البيانات", "إدارة البيانات (Firebase)", AccAmber, Modifier.weight(1f)) {
                openUrl(ctx, FIREBASE_URL)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AdminTile(Icons.Filled.BarChart, "إحصائيات التطبيق", "المستخدمين - الحجوزات", AccBlue, Modifier.weight(1f)) {
                onPage("stats")
            }
            AdminTile(Icons.Filled.PhoneAndroid, "تجربة التطبيق", "اختبار الاتصال", BGreen, Modifier.weight(1f)) {
                onPage("db")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AdminTile(Icons.Filled.Shield, "أمان التطبيق", "قواعد Firebase", BRed, Modifier.weight(1f)) {
                openUrl(ctx, FIREBASE_URL)
            }
            AdminTile(Icons.Filled.Cloud, "السحابية", "Firebase", AccBlue, Modifier.weight(1f)) {
                openUrl(ctx, FIREBASE_URL)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AdminTile(Icons.Filled.Group, "المستخدمين", "إدارة الحسابات", AccPurple, Modifier.weight(1f)) {
                onPage("subs")
            }
            AdminTile(Icons.Filled.Notifications, "الإشعارات", "داخل التطبيق", AccAmber, Modifier.weight(1f)) {
                onPage("notifs")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AdminTile(Icons.Filled.Description, "سجلات النظام", "سجل الاشتراكات", BMuted, Modifier.weight(1f)) {
                onPage("subs")
            }
            AdminTile(Icons.Filled.Settings, "إعدادات عامة", "معلومات التواصل", BGreen, Modifier.weight(1f)) {
                onPage("pay")
            }
        }

        OutlinedButton(
            onClick = { onPage("pay") },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) { Text("إدارة التطبيق والإعدادات", color = Color.White) }
        OutlinedButton(
            onClick = { onPage("dev") },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) { Text("إعدادات المطوّر (الاسم والصورة)", color = Color.White) }
    }
}

@Composable
private fun AdminStat(label: String, value: String, icon: ImageVector, tint: Color, modifier: Modifier) {
    Column(
        modifier
            .background(BCard, RoundedCornerShape(16.dp))
            .border(1.dp, BLine, RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = BMuted, fontSize = 12.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
        }
        Text(value, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
    }
}

/** توزيع المستخدمين: مرضى وأطباء وعيادات، مرسوم بالكود بدون مكتبات. */
@Composable
private fun DonutCard(patients: Long?, doctors: Long?, clinics: Long?) {
    val p = patients ?: 0L
    val d = doctors ?: 0L
    val c = clinics ?: 0L
    val ready = patients != null && doctors != null && clinics != null
    val sum = (p + d + c).toFloat()
    val values = listOf(p, d, c)
    val colors = listOf(AccBlue, BRed, BMuted)
    val labels = listOf("مرضى", "أطباء", "عيادات")

    Column(
        Modifier
            .fillMaxWidth()
            .background(BCard, RoundedCornerShape(16.dp))
            .border(1.dp, BLine, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("توزيع المستخدمين", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.size(96.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = 14.dp.toPx()
                    val topLeft = Offset(stroke / 2, stroke / 2)
                    val arcSize = Size(size.width - stroke, size.height - stroke)
                    if (!ready || sum <= 0f) {
                        drawArc(
                            color = BLine, startAngle = 0f, sweepAngle = 360f, useCenter = false,
                            topLeft = topLeft, size = arcSize, style = Stroke(stroke)
                        )
                    } else {
                        var start = -90f
                        values.forEachIndexed { i, v ->
                            val sweep = v / sum * 360f
                            if (sweep > 0f) {
                                drawArc(
                                    color = colors[i], startAngle = start, sweepAngle = sweep, useCenter = false,
                                    topLeft = topLeft, size = arcSize, style = Stroke(stroke)
                                )
                            }
                            start += sweep
                        }
                    }
                }
                Text(
                    if (ready) sum.toLong().toString() else "...",
                    color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                labels.forEachIndexed { i, l ->
                    val pct = if (ready && sum > 0f) (values[i] * 100f / sum).toInt() else 0
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.size(10.dp).background(colors[i], CircleShape))
                        Text("$l  ${if (ready) "$pct%" else "..."}", color = BMuted, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminTile(
    icon: ImageVector, title: String, sub: String, tint: Color,
    modifier: Modifier, onClick: () -> Unit
) {
    Row(
        modifier
            .background(BCard, RoundedCornerShape(16.dp))
            .border(1.dp, BLine, RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            Modifier.size(36.dp).background(tint.copy(alpha = 0.16f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, color = BMuted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}/** إعدادات المطوّر: الاسم وصورة الملف. */
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

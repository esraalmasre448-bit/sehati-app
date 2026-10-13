package com.sehati.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import java.io.ByteArrayOutputStream

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

@Composable
fun AdminHub(onBack: () -> Unit) {
    var page by remember { mutableStateOf("menu") }
    when (page) {
        "subs" -> AdminScreen { page = "menu" }
        "stats" -> AdminStatsScreen { page = "menu" }
        "pay" -> AdminPaymentScreen { page = "menu" }
        else -> Column(
            Modifier.fillMaxSize().background(BBg).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TextButton(onClick = onBack) { Text("رجوع", color = BRed) }
            Text("لوحة المطوّر", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text("لسّا ما وصلت… بس رغم كل شي، ما استسلمت.", color = BMuted)
            RedButton("إدارة الاشتراكات") { page = "subs" }
            RedButton("إحصائيات المستخدمين") { page = "stats" }
            RedButton("معلومات التواصل والدفع") { page = "pay" }
        }
    }
}

@Composable
private fun CountBox(label: String, value: Long?, modifier: Modifier = Modifier) {
    Column(
        modifier.background(BCard, RoundedCornerShape(14.dp))
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
        CountBox("إجمالي الحسابات", total, Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CountBox("مرضى", patients, Modifier.weight(1f))
            CountBox("أطباء", doctors, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CountBox("عيادات", clinics, Modifier.weight(1f))
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
    var msg by remember { mutableStateOf("") }
    var ok by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        bDb.collection("config").document("contact").get().addOnSuccessListener { d ->
            if (d.exists()) {
                name = d.getString("name") ?: ""
                phone = d.getString("phone") ?: ""
                sham = d.getString("sham") ?: ""
                notes = d.getString("notes") ?: ""
                qr = d.getString("qr") ?: ""
            }
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            val r = compressFit(ctx, uri)
            if (r == null) { ok = false; msg = "تعذر استخدام هذه الصورة، اختر صورة أصغر" }
            else { qr = r; ok = true; msg = "تم اختيار الصورة. اضغط حفظ" }
        }
    }

    val bmp = remember(qr) {
        if (qr.isBlank()) null
        else try {
            val b = Base64.decode(qr, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(b, 0, b.size)?.asImageBitmap()
        } catch (e: Exception) { null }
    }

    Column(
        Modifier.fillMaxSize().background(BBg).padding(20.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TextButton(onClick = onBack) { Text("رجوع", color = BRed) }
        Text("معلومات التواصل والدفع", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text("هذه المعلومات تظهر للأطباء عند تجديد الاشتراك.", color = BMuted)
        Field(name, { name = it }, "الاسم المعروض")
        Field(phone, { phone = it }, "رقم التواصل مع رمز الدولة (مثل 9639XXXXXXXX)", keyboard = KeyboardType.Phone)
        Field(sham, { sham = it }, "كود شام كاش")
        Field(notes, { notes = it }, "ملاحظة للطبيب (اختياري)")
        if (bmp != null) {
            Image(
                bitmap = bmp, contentDescription = null, contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp)
            )
        }
        OutlinedButton(
            onClick = { picker.launch("image/*") },
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) { Text(if (qr.isBlank()) "إضافة صورة QR" else "تغيير صورة QR", color = Color.White) }
        if (qr.isNotBlank()) {
            OutlinedButton(
                onClick = { qr = ""; ok = true; msg = "تم حذف الصورة. اضغط حفظ" },
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) { Text("حذف صورة QR", color = Color.White) }
        }
        if (msg.isNotEmpty()) Text(msg, color = if (ok) BGreen else BDanger)
        RedButton("حفظ", enabled = !busy) {
            busy = true
            msg = ""
            bDb.collection("config").document("contact").set(
                mapOf(
                    "name" to name.trim(), "phone" to phone.trim(), "sham" to sham.trim(),
                    "notes" to notes.trim(), "qr" to qr,
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            ).addOnSuccessListener {
                busy = false; ok = true; msg = "تم الحفظ"
            }.addOnFailureListener {
                busy = false; ok = false
                msg = "تعذر الحفظ. تأكد أنك المطوّر وأن قواعد Firebase محدّثة."
            }
        }
    }
}

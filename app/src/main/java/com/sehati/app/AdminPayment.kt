package com.sehati.app

import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions

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
        }.addOnFailureListener {
            msg = "تعذر تحميل البيانات الحالية. لن يتم الحفظ حتى لا تُمسح القيم القديمة."
            ok = false
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
        if (!loaded && msg.isEmpty()) Text("جارٍ تحميل البيانات...", color = BMuted)
        Field(name, { name = it }, "الاسم المعروض")
        Field(phone, { phone = it }, "رقم التواصل مع رمز الدولة (مثل 9639XXXXXXXX)", keyboard = KeyboardType.Phone)
        Field(sham, { sham = it }, "كود شام كاش")
        Field(notes, { notes = it }, "ملاحظة للطبيب (اختياري)")
        Text("أسعار الاشتراك (اكتبها كنص مع العملة)", color = BMuted)
        Field(p1, { p1 = it }, "سعر شهر واحد")
        Field(p3, { p3 = it }, "سعر 3 أشهر")
        Field(p6, { p6 = it }, "سعر 6 أشهر")
        Field(p12, { p12 = it }, "سعر 12 شهرًا")
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
        RedButton("حفظ", enabled = !busy && loaded) {
            busy = true
            msg = ""
            bDb.collection("config").document("contact").set(
                mapOf(
                    "name" to name.trim(), "phone" to phone.trim(), "sham" to sham.trim(),
                    "notes" to notes.trim(), "qr" to qr,
                    "price1" to p1.trim(), "price3" to p3.trim(),
                    "price6" to p6.trim(), "price12" to p12.trim(),
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

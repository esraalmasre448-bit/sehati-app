package com.sehati.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

fun waNumber(raw: String): String {
    var d = raw.filter { it.isDigit() }
    if (d.startsWith("00")) d = d.drop(2)
    if (d.startsWith("0")) d = "963" + d.drop(1)
    return d
}

@Composable
fun RenewalScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var sham by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var qr by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        bDb.collection("config").document("contact").get()
            .addOnSuccessListener { d ->
                if (d.exists()) {
                    name = d.getString("name") ?: ""
                    phone = d.getString("phone") ?: ""
                    sham = d.getString("sham") ?: ""
                    notes = d.getString("notes") ?: ""
                    qr = d.getString("qr") ?: ""
                }
                loaded = true
            }
            .addOnFailureListener {
                error = "تعذر تحميل المعلومات، تحقق من الإنترنت"
                loaded = true
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
        Text("تجديد الاشتراك والتواصل", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
        if (error.isNotEmpty()) Text(error, color = BDanger)
        if (loaded && error.isEmpty() && name.isBlank() && phone.isBlank() && sham.isBlank() && bmp == null) {
            Text("لم تضف الإدارة معلومات التواصل بعد.", color = BMuted)
        }
        if (name.isNotBlank()) {
            Text("الإدارة: $name", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        if (notes.isNotBlank()) {
            Text(notes, color = BMuted)
        }
        if (phone.isNotBlank()) {
            Text("رقم التواصل: $phone", color = Color.White, fontSize = 16.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        try {
                            ctx.startActivity(
                                Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        } catch (e: Exception) { msg = "تعذر فتح الاتصال" }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BRed, contentColor = BOnAccent)
                ) { Text("اتصال") }
                OutlinedButton(
                    onClick = {
                        try {
                            ctx.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/" + waNumber(phone)))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        } catch (e: Exception) { msg = "تعذر فتح واتساب" }
                    }
                ) { Text("واتساب", color = Color.White) }
            }
        }
        if (sham.isNotBlank() || bmp != null) {
            Column(
                Modifier.fillMaxWidth().background(BCard, RoundedCornerShape(14.dp))
                    .border(1.dp, BLine, RoundedCornerShape(14.dp)).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("الدفع عبر شام كاش", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                if (sham.isNotBlank()) {
                    Text(sham, color = BRed, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    OutlinedButton(
                        onClick = {
                            try {
                                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("sham", sham))
                                msg = "تم نسخ الكود"
                            } catch (e: Exception) { msg = "تعذر النسخ" }
                        }
                    ) { Text("نسخ الكود", color = Color.White) }
                }
                if (bmp != null) {
                    Image(
                        bitmap = bmp, contentDescription = null, contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp)
                    )
                }
            }
        }
        if (msg.isNotEmpty()) Text(msg, color = BGreen)
        Text(
            "بعد الدفع أرسل صورة الحوالة للإدارة عبر واتساب ليتم تفعيل اشتراكك.",
            color = BMuted, fontSize = 14.sp
        )
    }
}

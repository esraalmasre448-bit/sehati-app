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
    var prices by remember { mutableStateOf(mapOf<Int, String>()) }
    var myName by remember { mutableStateOf("") }
    val myUid = bAuth.currentUser?.uid ?: ""
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
                    prices = mapOf(
                        1 to (d.getString("price1") ?: ""),
                        3 to (d.getString("price3") ?: ""),
                        6 to (d.getString("price6") ?: ""),
                        12 to (d.getString("price12") ?: "")
                    )
                }
                loaded = true
            }
            .addOnFailureListener {
                error = "تعذر تحميل المعلومات، تحقق من الإنترنت"
                loaded = true
            }
    }

    LaunchedEffect(myUid) {
        if (myUid.isNotEmpty()) {
            bDb.collection("users").document(myUid).get()
                .addOnSuccessListener { myName = it.getString("name") ?: "" }
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
        if (phone.isNotBlank()) {
            Text("اطلب التجديد عبر واتساب", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            listOf(1 to "شهر واحد", 3 to "3 أشهر", 6 to "6 أشهر", 12 to "12 شهرًا").forEach { (m, label) ->
                val price = prices[m].orEmpty()
                OutlinedButton(
                    onClick = {
                        val text = "مرحبًا، أنا $myName، أرغب بتجديد اشتراكي في تطبيق الطريق إلى صحتي لمدة $label" +
                            (if (price.isNotBlank()) " (المبلغ: $price)" else "") +
                            ". رقم حسابي: $myUid"
                        try {
                            ctx.startActivity(
                                Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("https://wa.me/" + waNumber(phone) + "?text=" + Uri.encode(text))
                                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        } catch (e: Exception) { msg = "تعذر فتح واتساب" }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "$label" + (if (price.isNotBlank()) " — $price" else ""),
                        color = Color.White
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

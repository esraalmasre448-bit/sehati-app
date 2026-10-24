package com.sehati.app

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
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
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source

private const val DEV_BIO = "المطوّر والمسؤول عن تطوير التطبيق وتشغيله."

/** بطاقة المطوّر: الصورة والاسم والوصف، وتغيير الصورة (للأدمن فقط بسبب القواعد). */
@Composable
fun DevCard() {
    val ctx = LocalContext.current
    val uid = bAuth.currentUser?.uid ?: ""
    var name by remember { mutableStateOf("") }
    var photo by remember { mutableStateOf<String?>(null) }
    var bio by remember { mutableStateOf(DEV_BIO) }
    var msg by remember { mutableStateOf("") }
    var msgOk by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(uid) {
        if (uid.isEmpty()) return@LaunchedEffect
        bDb.collection("users").document(uid).get(Source.SERVER)
            .addOnSuccessListener { name = it.getString("name") ?: "" }
        bDb.collection("config").document("developer").get(Source.SERVER)
            .addOnSuccessListener { d ->
                photo = d.getString("photo")
                bio = d.getString("bio") ?: DEV_BIO
            }
            .addOnFailureListener {
                msg = "تعذّر تحميل بيانات المطوّر، تحقق من الإنترنت"
                msgOk = false
            }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val encoded = compressPhoto(ctx, uri)
        if (encoded == null) {
            msg = "الصورة كبيرة أو غير مدعومة، جرّب صورة ثانية"
            msgOk = false
            return@rememberLauncherForActivityResult
        }
        busy = true
        msg = ""
        bDb.collection("config").document("developer")
            .set(mapOf("photo" to encoded, "updatedAt" to FieldValue.serverTimestamp()), SetOptions.merge())
            .addOnSuccessListener {
                busy = false
                photo = encoded
                msg = "تم حفظ الصورة"
                msgOk = true
            }
            .addOnFailureListener { e ->
                busy = false
                msg = if (e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED)
                    "لا تملك صلاحية تغيير الصورة"
                else "تعذّر حفظ الصورة، تحقق من الإنترنت وحاول مجدداً"
                msgOk = false
            }
    }

    Column(
        Modifier.fillMaxWidth()
            .background(BCard, RoundedCornerShape(16.dp))
            .border(1.dp, BLine, RoundedCornerShape(16.dp))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        DoctorAvatar(photo, name.ifBlank { "م" }, 96.dp)
        Text(name.ifBlank { "المطوّر" }, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(bio, color = BMuted, fontSize = 14.sp)
        OutlinedButton(
            onClick = { picker.launch("image/*") },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().height(46.dp)
        ) { Text(if (busy) "جارٍ الحفظ..." else "تغيير الصورة", color = Color.White) }
        if (msg.isNotEmpty()) Text(msg, color = if (msgOk) BGreen else BDanger, fontSize = 13.sp)
    }
}

/** حالة الاتصال بقاعدة البيانات: تظهر "متصل" فقط بعد قراءة فعلية من السيرفر. */
@Composable
fun DbStatusCard() {
    var state by remember { mutableStateOf("loading") }
    var tick by remember { mutableIntStateOf(0) }

    LaunchedEffect(tick) {
        state = "loading"
        bDb.collection("config").document("contact").get(Source.SERVER)
            .addOnSuccessListener { state = "ok" }
            .addOnFailureListener { e ->
                state = if (e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.UNAVAILABLE) "down" else "error"
            }
    }

    val text = when (state) {
        "ok" -> "متصل بقاعدة البيانات"
        "down" -> "تعذّر الاتصال، تحقق من الإنترنت"
        "error" -> "تعذّر التحقق من الحالة (صلاحيات أو خطأ)"
        else -> "جارٍ التحقق..."
    }
    val color = when (state) {
        "ok" -> BGreen
        "loading" -> BMuted
        else -> BDanger
    }

    Column(
        Modifier.fillMaxWidth()
            .background(BCard, RoundedCornerShape(14.dp))
            .border(1.dp, BLine, RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("حالة قاعدة البيانات", color = BMuted, fontSize = 14.sp)
        Text(text, color = color, fontWeight = FontWeight.Bold)
        OutlinedButton(onClick = { tick++ }, modifier = Modifier.fillMaxWidth().height(44.dp)) {
            Text("إعادة التحقق", color = Color.White)
        }
    }
}

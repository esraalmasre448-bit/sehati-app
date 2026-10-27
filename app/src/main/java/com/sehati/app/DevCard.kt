package com.sehati.app

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(BCard, BBg)), RoundedCornerShape(20.dp))
            .border(1.dp, BLine, RoundedCornerShape(20.dp))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // الصورة مع زر تعديل صغير عليها
        Box(contentAlignment = Alignment.BottomEnd) {
            DoctorAvatar(photo, name.ifBlank { "م" }, 104.dp)
            Box(
                Modifier
                    .size(34.dp)
                    .background(BRed, CircleShape)
                    .border(2.dp, BBg, CircleShape)
                    .clickable(enabled = !busy) { picker.launch("image/*") },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Edit, "تغيير الصورة", tint = BOnAccent, modifier = Modifier.size(16.dp))
            }
        }

        Spacer(Modifier.height(4.dp))
        Text(
            name.ifBlank { "المطوّر" },
            color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Text("مطوّر التطبيق", color = BRed, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Text(bio, color = BMuted, fontSize = 13.sp, textAlign = TextAlign.Center)

        if (msg.isNotEmpty()) {
            Text(msg, color = if (msgOk) BGreen else BDanger, fontSize = 13.sp, textAlign = TextAlign.Center)
        }
        OutlinedButton(
            onClick = { picker.launch("image/*") },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(Icons.Filled.Edit, null, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                if (busy) "جارٍ الحفظ..." else "تغيير الصورة",
                color = Color.White, fontWeight = FontWeight.SemiBold
            )
        }
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

    Row(
        Modifier
            .fillMaxWidth()
            .background(BCard, RoundedCornerShape(16.dp))
            .border(1.dp, BLine, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Circle, null, tint = color, modifier = Modifier.size(10.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("قاعدة البيانات", color = BMuted, fontSize = 12.sp)
            Text(text, color = color, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
        IconButton(onClick = { tick++ }) {
            Icon(Icons.Filled.Refresh, "إعادة التحقق", tint = Color.White)
        }
    }
}

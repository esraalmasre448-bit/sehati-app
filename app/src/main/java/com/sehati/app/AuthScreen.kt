package com.sehati.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.delay

private val Bg = Color(0xFF0B1220)
private val CardC = Color(0xFF131C2E)
private val Line = Color(0xFF24304A)
private val Red = Color(0xFFE5171F)
private val Danger = Color(0xFFFF5A5F)
private val Good = Color(0xFF3DDC84)
private val OnAccent = Color(0xFFFFFFFF)
private val Muted = Color(0xFF9AA7BD)

private val auth get() = FirebaseAuth.getInstance()
private val db get() = FirebaseFirestore.getInstance()

private fun authError(e: Exception): String = when (e) {
    is FirebaseAuthWeakPasswordException -> "كلمة السر ضعيفة، استخدم 6 أحرف على الأقل"
    is FirebaseAuthInvalidUserException -> "لا يوجد حساب بهذا البريد"
    is FirebaseAuthInvalidCredentialsException -> "البريد أو كلمة السر غير صحيحة"
    is FirebaseAuthUserCollisionException -> "هذا البريد مسجّل مسبقًا، سجّل دخولك"
    is FirebaseNetworkException -> "تحقق من اتصال الإنترنت وحاول مرة أخرى"
    else ->
        if (e.message?.contains("API key", true) == true)
            "تعذر الاتصال بالخدمة (رمز: API_KEY). ثبّت آخر نسخة من التطبيق"
        else "تعذر إتمام العملية، حاول مرة أخرى"
}

@Composable
fun Field(
    value: String, onChange: (String) -> Unit, label: String,
    password: Boolean = false, keyboard: KeyboardType = KeyboardType.Text
) {
    var show by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value, onValueChange = onChange,
        label = { Text(label, color = Muted) },
        singleLine = true,
        visualTransformation = if (password && !show) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else keyboard),
        trailingIcon = if (password) {
            {
                IconButton(onClick = { show = !show }) {
                    Icon(
                        if (show) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = null, tint = Muted
                    )
                }
            }
        } else null,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Red, unfocusedBorderColor = Line,
            focusedContainerColor = CardC, unfocusedContainerColor = CardC,
            focusedTextColor = Color.White, unfocusedTextColor = Color.White,
            cursorColor = Red
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
fun RedButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick, enabled = enabled,
        colors = ButtonDefaults.buttonColors(containerColor = Red, contentColor = OnAccent),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().height(52.dp)
    ) { Text(text, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
}

@Composable
fun AuthFlow(onDone: () -> Unit) {
    var signup by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("patient") }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    val errGeneric = stringResource(R.string.error_generic)
    val enterBoth = stringResource(R.string.enter_email_pass)
    val enterName = stringResource(R.string.enter_name)
    var showReset by remember { mutableStateOf(false) }
    var resetEmail by remember { mutableStateOf("") }
    var resetMsg by remember { mutableStateOf("") }
    var resetOk by remember { mutableStateOf(false) }
    var resetBusy by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().background(Bg).padding(24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Image(
            painterResource(R.drawable.ic_launcher), null,
            Modifier.size(150.dp).align(Alignment.CenterHorizontally)
        )
        Text(stringResource(R.string.welcome_back), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text(stringResource(R.string.login_sub), color = Muted)
        if (signup) {
            Field(name, { name = it }, stringResource(R.string.name))
            Field(phone, { phone = it }, stringResource(R.string.phone), keyboard = KeyboardType.Phone)
            Text(stringResource(R.string.account_type), color = Muted)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "patient" to R.string.patient,
                    "doctor" to R.string.doctor,
                    "clinic" to R.string.clinic
                ).forEach { (k, label) ->
                    FilterChip(
                        selected = type == k, onClick = { type = k },
                        label = { Text(stringResource(label)) },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = CardC, labelColor = Color.White,
                            selectedContainerColor = Red, selectedLabelColor = OnAccent
                        )
                    )
                }
            }
        }
        Field(email, { email = it }, stringResource(R.string.email), keyboard = KeyboardType.Email)
        Field(pass, { pass = it }, stringResource(R.string.password), password = true)
        if (!signup) {
            TextButton(onClick = {
                msg = ""
                resetEmail = email.trim()
                resetMsg = ""
                resetOk = false
                showReset = true
            }) { Text(stringResource(R.string.forgot), color = Red) }
        }
        if (msg.isNotEmpty()) Text(msg, color = Danger)
        RedButton(
            text = stringResource(if (signup) R.string.signup_btn else R.string.login),
            enabled = !busy
        ) {
            msg = ""
            val e = email.trim()
            if (e.isBlank() || pass.isBlank()) {
                msg = enterBoth
            } else if (signup && name.isBlank()) {
                msg = enterName
            } else {
                busy = true
                if (signup) {
                    auth.createUserWithEmailAndPassword(e, pass).addOnSuccessListener { r ->
                        val u = r.user!!
                        db.collection("users").document(u.uid).set(
                            mapOf(
                                "name" to name.trim(), "phone" to phone.trim(), "email" to e,
                                "accountType" to type, "createdAt" to FieldValue.serverTimestamp()
                            )
                        ).addOnSuccessListener { busy = false; onDone() }
                            .addOnFailureListener {
                                u.delete().addOnCompleteListener {
                                    auth.signOut()
                                    busy = false
                                    msg = errGeneric
                                }
                            }
                    }.addOnFailureListener { busy = false; msg = authError(it) }
                } else {
                    auth.signInWithEmailAndPassword(e, pass).addOnSuccessListener { r ->
                        val u = r.user!!
                        db.collection("users").document(u.uid).get()
                            .addOnSuccessListener { d ->
                                if (d.exists()) {
                                    busy = false
                                    onDone()
                                } else if (d.metadata.isFromCache) {
                                    auth.signOut()
                                    busy = false
                                    msg = errGeneric
                                } else {
                                    u.delete().addOnCompleteListener {
                                        auth.signOut()
                                        busy = false
                                        msg = "كان الحساب ناقصًا وتم حذفه. أنشئ حسابًا جديدًا بنفس الإيميل"
                                    }
                                }
                            }
                            .addOnFailureListener {
                                auth.signOut()
                                busy = false
                                msg = errGeneric
                            }
                    }.addOnFailureListener { busy = false; msg = authError(it) }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            HorizontalDivider(Modifier.weight(1f), color = Line)
            Text(stringResource(R.string.or), color = Muted, modifier = Modifier.padding(horizontal = 12.dp))
            HorizontalDivider(Modifier.weight(1f), color = Line)
        }
        OutlinedButton(
            onClick = { signup = !signup; msg = "" },
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Line),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Text(
                stringResource(if (signup) R.string.have_account else R.string.create_account),
                color = Color.White, fontSize = 16.sp
            )
        }
    }

    if (showReset) {
        AlertDialog(
            onDismissRequest = { showReset = false },
            containerColor = CardC,
            title = { Text("استعادة كلمة المرور", color = Color.White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("اكتب بريدك الإلكتروني وسنرسل لك رابطًا لتعيين كلمة سر جديدة.", color = Muted)
                    Field(resetEmail, { resetEmail = it; resetMsg = "" }, stringResource(R.string.email), keyboard = KeyboardType.Email)
                    if (resetMsg.isNotEmpty()) Text(resetMsg, color = if (resetOk) Good else Danger)
                    if (resetOk) Text(
                        "افتح الرابط من بريدك، اختر كلمة سر جديدة، ثم ارجع وسجّل دخولك. إن لم تجد الرسالة فابحث في مجلد الرسائل غير المرغوبة (Spam).",
                        color = Muted, fontSize = 13.sp
                    )
                }
            },
            confirmButton = {
                TextButton(enabled = !resetBusy, onClick = {
                    val r = resetEmail.trim()
                    if (r.isBlank()) {
                        resetOk = false
                        resetMsg = "اكتب بريدك الإلكتروني أولاً"
                    } else {
                        resetBusy = true
                        resetMsg = ""
                        auth.sendPasswordResetEmail(r)
                            .addOnSuccessListener { resetBusy = false; resetOk = true; resetMsg = "تم إرسال الرابط إلى $r" }
                            .addOnFailureListener { resetBusy = false; resetOk = false; resetMsg = authError(it) }
                    }
                }) { Text(if (resetOk) "إعادة الإرسال" else "إرسال الرابط", color = Red) }
            },
            dismissButton = { TextButton(onClick = { showReset = false }) { Text("إغلاق", color = Muted) } }
        )
    }
}

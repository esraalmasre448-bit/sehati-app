package com.sehati.app

import android.os.Bundle
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
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
    else -> "تعذر الاتصال بالخدمة الآن. حاولي مرة أخرى بعد قليل، وإذا استمر الأمر حدّثي التطبيق"
}

@Composable
fun Field(
    value: String, onChange: (String) -> Unit, label: String,
    password: Boolean = false, keyboard: KeyboardType = KeyboardType.Text,
    icon: ImageVector? = null
) {
    var show by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value, onValueChange = onChange,
        label = { Text(label, color = Muted) },
        singleLine = true,
        leadingIcon = if (icon != null) {
            { Icon(icon, contentDescription = null, tint = Muted) }
        } else null,
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
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    // ضغط خفيف وسريع بدون ارتداد: يعطي إحساس الاستجابة دون أن يلفت النظر
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.98f else 1f,
        animationSpec = tween(durationMillis = 90, easing = FastOutSlowInEasing),
        label = "pressScale"
    )
    Button(
        onClick = onClick, enabled = enabled,
        interactionSource = source,
        colors = ButtonDefaults.buttonColors(containerColor = Red, contentColor = OnAccent),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .graphicsLayer { scaleX = pressScale; scaleY = pressScale }
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
    var seniorMode by remember { mutableStateOf(false) }
    var awaitingEmailVerification by remember { mutableStateOf(auth.currentUser != null && auth.currentUser?.isEmailVerified == false && auth.currentUser?.phoneNumber.isNullOrBlank()) }

    fun finishVerifiedAccount(user: com.google.firebase.auth.FirebaseUser) {
        busy = true
        user.reload().addOnSuccessListener {
            val current = auth.currentUser
            if (current?.isEmailVerified != true) {
                busy = false
                msg = "لم نتأكد من التحقق بعد. افتح رسالة البريد واضغط رابط التحقق ثم أعد المحاولة."
                return@addOnSuccessListener
            }
            current.getIdToken(true).addOnSuccessListener {
                db.collection("users").document(current.uid).update("emailVerified", true)
                    .addOnSuccessListener {
                        db.collection("users").document(current.uid).get()
                            .addOnSuccessListener { profile ->
                                val accountType = profile.getString("accountType") ?: "patient"
                                if (accountType == "doctor" || accountType == "clinic") {
                                    val ref = db.collection("subscriptions").document(current.uid)
                                    ref.get().addOnSuccessListener { existing ->
                                        if (existing.exists()) {
                                            busy = false
                                            awaitingEmailVerification = false
                                            onDone()
                                        } else {
                                            val end = java.util.Calendar.getInstance()
                                                .apply { add(java.util.Calendar.DAY_OF_YEAR, 30) }.time
                                            ref.set(mapOf(
                                                "uid" to current.uid, "status" to "ACTIVE", "trial" to true,
                                                "paymentMethod" to "Trial",
                                                "startedAt" to FieldValue.serverTimestamp(),
                                                "expiresAt" to com.google.firebase.Timestamp(end)
                                            )).addOnSuccessListener {
                                                busy = false
                                                awaitingEmailVerification = false
                                                onDone()
                                            }.addOnFailureListener {
                                                busy = false
                                                msg = "تم التحقق من البريد، لكن تعذر تجهيز ملف الاشتراك. حاول مجددًا."
                                            }
                                        }
                                    }.addOnFailureListener {
                                        busy = false
                                        msg = errGeneric
                                    }
                                } else {
                                    busy = false
                                    awaitingEmailVerification = false
                                    onDone()
                                }
                            }.addOnFailureListener {
                                busy = false
                                msg = errGeneric
                            }
                    }.addOnFailureListener {
                        busy = false
                        msg = "تم التحقق من البريد، لكن تعذر تحديث ملف الحساب. أعد المحاولة."
                    }
            }.addOnFailureListener {
                busy = false
                msg = "تعذر تحديث حالة التحقق. تحقق من الإنترنت وحاول مجددًا."
            }
        }.addOnFailureListener {
            busy = false
            msg = "تعذر تحديث حالة الحساب. تحقق من الإنترنت وحاول مجددًا."
        }
    }

    fun startEmailVerification(user: com.google.firebase.auth.FirebaseUser, createProfile: Boolean) {
        busy = true
        user.sendEmailVerification().addOnSuccessListener {
            if (!createProfile) {
                busy = false
                awaitingEmailVerification = true
                msg = "أُعيد إرسال رابط التحقق إلى ${user.email ?: email}. افحص البريد الوارد والرسائل غير المرغوبة."
                return@addOnSuccessListener
            }
            val e = user.email ?: email.trim()
            val profile = mapOf(
                "name" to name.trim(),
                "phone" to phone.trim(),
                "email" to e,
                "accountType" to type,
                "createdAt" to FieldValue.serverTimestamp(),
                "emailVerified" to false
            )
            db.collection("users").document(user.uid).set(profile)
                .addOnSuccessListener {
                    busy = false
                    signup = false
                    awaitingEmailVerification = true
                    msg = "أرسلنا رابط التحقق إلى $e. افتح بريدك واضغط الرابط لإكمال إنشاء الحساب."
                }.addOnFailureListener {
                    // لا نحذف حساب Firebase عند فشل حفظ ملف Firestore؛ حذف الحساب هنا
                    // كان يجعل المستخدم يفقد إمكانية تسجيل الدخول نهائياً.
                    busy = false
                    signup = false
                    awaitingEmailVerification = true
                    msg = "تم إنشاء الحساب وإرسال رابط التحقق، لكن تعذر حفظ ملف المستخدم. لا تحذف الحساب؛ تحقق من قواعد Firestore والاتصال ثم أعد المحاولة."
                }
        }.addOnFailureListener { error ->
            // فشل إرسال البريد لا يعني أن الحساب غير صالح. نُبقي الحساب ونسمح بإعادة الإرسال.
            busy = false
            signup = false
            awaitingEmailVerification = true
            msg = authError(error)
        }
    }

    // ظهور سريع وهادئ للشعار والعنوان عند فتح الشاشة فقط
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val enter by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "enter"
    )

    CompositionLocalProvider(
        LocalDensity provides Density(density = LocalDensity.current.density, fontScale = if (seniorMode) 1.22f else 1f)
    ) {
    Column(
        Modifier.fillMaxSize().background(Bg).padding(if (seniorMode) 18.dp else 24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(if (seniorMode) 16.dp else 12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("وضع كبار السن", color = Color.White, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Switch(checked = seniorMode, onCheckedChange = { seniorMode = it })
        }
        Image(
            painterResource(R.drawable.ic_launcher), null,
            Modifier
                .size(150.dp)
                .align(Alignment.CenterHorizontally)
                .graphicsLayer { alpha = enter }
        )
        Text(
            stringResource(R.string.welcome_back),
            fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White,
            modifier = Modifier.graphicsLayer { alpha = enter }
        )
        Text(stringResource(R.string.login_sub), color = Muted)
        if (awaitingEmailVerification) {
            val pendingEmail = auth.currentUser?.email ?: email
            Text("تأكيد البريد الإلكتروني", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("أرسلنا رابط تحقق إلى $pendingEmail. افتح الرسالة واضغط الرابط، ثم ارجع إلى التطبيق.", color = Muted)
            Text("إذا لم تجد الرسالة، افحص مجلد Spam أو الرسائل غير المرغوبة.", color = Muted, fontSize = 13.sp)
            if (msg.isNotEmpty()) Text(msg, color = if (msg.startsWith("أُعيد")) Good else Danger)
            RedButton("تحققت من البريد، متابعة", enabled = !busy) {
                val current = auth.currentUser
                if (current == null) {
                    awaitingEmailVerification = false
                    msg = "سجّل الدخول بالبريد وكلمة المرور للمتابعة."
                } else {
                    finishVerifiedAccount(current)
                }
            }
            OutlinedButton(
                enabled = !busy,
                onClick = { auth.currentUser?.let { startEmailVerification(it, false) } },
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) { Text("إعادة إرسال رابط التحقق", color = Color.White) }
            TextButton(onClick = {
                auth.signOut()
                awaitingEmailVerification = false
                signup = false
                email = ""
                pass = ""
                msg = ""
            }) { Text("استخدام حساب آخر", color = Red) }
        } else {
        if (signup) {
            Field(name, { name = it }, stringResource(R.string.name), icon = Icons.Filled.Person)
            Field(
                phone, { phone = it }, "رقم الهاتف (اختياري للتواصل)",
                keyboard = KeyboardType.Phone, icon = Icons.Filled.Phone
            )
            Text("سيتم تأكيد الحساب عبر رابط يصل إلى بريدك الإلكتروني.", color = Muted, fontSize = 13.sp)
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
        Field(
            email, { email = it }, stringResource(R.string.email),
            keyboard = KeyboardType.Email, icon = Icons.Filled.Email
        )
        Field(
            pass, { pass = it }, stringResource(R.string.password),
            password = true, icon = Icons.Filled.Lock
        )
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
            text = if (!signup) stringResource(R.string.login) else "إنشاء الحساب وإرسال رابط التحقق",
            enabled = !busy
        ) {
            msg = ""
            val e = email.trim()
            if (e.isBlank() || pass.isBlank()) {
                msg = enterBoth
            } else if (signup && name.isBlank()) {
                msg = enterName
            } else if (signup && !android.util.Patterns.EMAIL_ADDRESS.matcher(e).matches()) {
                msg = "اكتب بريدًا إلكترونيًا صحيحًا"
            } else {
                if (signup) {
                    if (phone.isNotBlank() && !phone.trim().matches(Regex("^\\+[1-9][0-9]{7,14}$"))) {
                        msg = "اكتب رقم الهاتف بالصيغة الدولية أو اتركه فارغًا"
                    } else {
                        busy = true
                        auth.createUserWithEmailAndPassword(e, pass).addOnSuccessListener { result ->
                            val user = result.user
                            if (user == null) {
                                busy = false
                                msg = errGeneric
                            } else {
                                startEmailVerification(user, true)
                            }
                        }.addOnFailureListener {
                            busy = false
                            msg = authError(it)
                        }
                    }
                } else {
                    busy = true
                    auth.signInWithEmailAndPassword(e, pass).addOnSuccessListener { result ->
                        val user = result.user
                        if (user == null) {
                            busy = false
                            msg = errGeneric
                        } else if (!user.isEmailVerified && user.phoneNumber.isNullOrBlank()) {
                            startEmailVerification(user, false)
                        } else {
                            db.collection("users").document(user.uid).get()
                                .addOnSuccessListener { profile ->
                                    if (profile.exists()) {
                                        busy = false
                                        onDone()
                                    } else if (profile.metadata.isFromCache) {
                                        auth.signOut()
                                        busy = false
                                        msg = errGeneric
                                    } else {
                                        // لا نحذف حساب Authentication لمجرد غياب ملف Firestore.
                                        auth.signOut()
                                        busy = false
                                        msg = "بيانات الدخول صحيحة، لكن ملف الحساب غير موجود في قاعدة البيانات. لم نحذف حسابك؛ يلزم إصلاح ملف users لهذا الحساب."
                                    }
                                }.addOnFailureListener {
                                    auth.signOut()
                                    busy = false
                                    msg = errGeneric
                                }
                        }
                    }.addOnFailureListener {
                        busy = false
                        msg = authError(it)
                    }
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
                    Field(
                        resetEmail, { resetEmail = it; resetMsg = "" }, stringResource(R.string.email),
                        keyboard = KeyboardType.Email, icon = Icons.Filled.Email
                    )
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

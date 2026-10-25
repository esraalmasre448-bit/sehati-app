package com.sehati.app

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** تخزين رمز القفل على الجوال فقط، مشفّراً (لا يُخزَّن الرمز نفسه). */
object AppLockStore {
    private const val FILE = "applock"
    private const val MAX_FAILS = 5

    private fun p(c: Context) = c.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun enabled(c: Context): Boolean = p(c).getString("hash", null) != null
    fun bioOn(c: Context): Boolean = p(c).getBoolean("bio", false)
    fun setBio(c: Context, v: Boolean) { p(c).edit().putBoolean("bio", v).apply() }
    fun resetFails(c: Context) { p(c).edit().putInt("fails", 0).apply() }
    fun clear(c: Context) { p(c).edit().clear().apply() }
    fun maxFails(): Int = MAX_FAILS

    fun addFail(c: Context): Int {
        val n = p(c).getInt("fails", 0) + 1
        p(c).edit().putInt("fails", n).apply()
        return n
    }

    private fun hash(code: String, salt: ByteArray): String {
        val spec = PBEKeySpec(code.toCharArray(), salt, 20000, 256)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).encoded
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    fun set(c: Context, code: String) {
        val salt = ByteArray(16)
        SecureRandom().nextBytes(salt)
        p(c).edit()
            .putString("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString("hash", hash(code, salt))
            .putInt("fails", 0)
            .apply()
    }

    fun check(c: Context, code: String): Boolean {
        val s = p(c).getString("salt", null) ?: return false
        val h = p(c).getString("hash", null) ?: return false
        return hash(code, Base64.decode(s, Base64.NO_WRAP)) == h
    }

    fun validCode(code: String): Boolean = code.length in 4..32

    /** البصمة تتطلب أندرويد 9 فأعلى وجهازاً فيه مستشعر بصمة. */
    fun bioAvailable(c: Context): Boolean {
        if (Build.VERSION.SDK_INT < 28) return false
        if (!c.packageManager.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT)) return false
        // لا نعرض زر البصمة إلا إذا كانت هناك بصمة مسجّلة فعلاً على الجهاز.
        return try {
            val fm = c.getSystemService(android.hardware.fingerprint.FingerprintManager::class.java)
            fm?.hasEnrolledFingerprints() == true
        } catch (e: Exception) {
            // عند تعذّر التأكد من البصمة نُخفي الخيار، ويبقى رمز القفل متاحاً دائماً.
            false
        }
    }
}

fun askBiometric(c: Context, onOk: () -> Unit, onMsg: (String) -> Unit) {
    if (Build.VERSION.SDK_INT < 28) return
    try {
        val exec = c.mainExecutor
        val prompt = BiometricPrompt.Builder(c)
            .setTitle("فتح التطبيق")
            .setNegativeButton("استخدام الرمز", exec) { _, _ -> }
            .build()
        prompt.authenticate(
            CancellationSignal(), exec,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
                    onOk()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                    if (errorCode != 13 && errorCode != 10 && errorCode != 5) {
                        onMsg("تعذر استخدام البصمة، استخدم الرمز")
                    }
                }
            }
        )
    } catch (e: Exception) {
        onMsg("تعذر استخدام البصمة، استخدم الرمز")
    }
}

@Composable
fun LockScreen(onUnlock: () -> Unit, onForgot: () -> Unit) {
    val ctx = LocalContext.current
    var code by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }
    val bio = AppLockStore.bioOn(ctx) && AppLockStore.bioAvailable(ctx)
    LaunchedEffect(Unit) {
        if (bio) askBiometric(ctx, onUnlock) { msg = it }
    }
    Column(
        Modifier.fillMaxSize().background(BBg).padding(24.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(painterResource(R.drawable.ic_launcher), null, Modifier.size(120.dp))
        Spacer(Modifier.height(16.dp))
        Text("التطبيق مقفل", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text("أدخل رمز القفل للمتابعة", color = BMuted)
        Spacer(Modifier.height(16.dp))
        Field(code, { code = it; msg = "" }, "رمز القفل", password = true)
        if (msg.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(msg, color = BDanger)
        }
        Spacer(Modifier.height(12.dp))
        RedButton("فتح") {
            if (AppLockStore.check(ctx, code)) {
                AppLockStore.resetFails(ctx)
                onUnlock()
            } else {
                val n = AppLockStore.addFail(ctx)
                code = ""
                if (n >= AppLockStore.maxFails()) onForgot()
                else msg = "رمز غير صحيح (المحاولات المتبقية: ${AppLockStore.maxFails() - n})"
            }
        }
        if (bio) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { askBiometric(ctx, onUnlock) { msg = it } },
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Icon(Icons.Filled.Fingerprint, null, tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text("استخدام البصمة", color = Color.White)
            }
        }
        TextButton(onClick = onForgot) { Text("نسيت الرمز؟ سجّل خروج", color = BMuted) }
    }
}

/** قسم إعدادات القفل داخل صفحة الملف الشخصي. */
@Composable
fun AppLockSection() {
    val ctx = LocalContext.current
    var enabled by remember { mutableStateOf(AppLockStore.enabled(ctx)) }
    var bio by remember { mutableStateOf(AppLockStore.bioOn(ctx)) }
    var dialog by remember { mutableStateOf("") }
    var old by remember { mutableStateOf("") }
    var a by remember { mutableStateOf("") }
    var b by remember { mutableStateOf("") }
    var err by remember { mutableStateOf("") }
    val canBio = AppLockStore.bioAvailable(ctx)

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!enabled) {
            OutlinedButton(
                onClick = { old = ""; a = ""; b = ""; err = ""; dialog = "set" },
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Icon(Icons.Filled.Lock, null, tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text("تفعيل قفل التطبيق", color = Color.White)
            }
        } else {
            Text("قفل التطبيق: مفعّل", color = BGreen)
            OutlinedButton(
                onClick = { old = ""; a = ""; b = ""; err = ""; dialog = "set" },
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) { Text("تغيير رمز القفل", color = Color.White) }
            if (canBio) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("فتح بالبصمة", color = Color.White, modifier = Modifier.weight(1f))
                    Switch(
                        checked = bio,
                        onCheckedChange = { bio = it; AppLockStore.setBio(ctx, it) },
                        colors = SwitchDefaults.colors(checkedTrackColor = BRed)
                    )
                }
            }
            OutlinedButton(
                onClick = { old = ""; err = ""; dialog = "off" },
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) { Text("إيقاف القفل", color = BDanger) }
        }
    }

    if (dialog == "set") {
        AlertDialog(
            onDismissRequest = { dialog = "" },
            containerColor = BCard,
            title = { Text(if (enabled) "تغيير رمز القفل" else "تفعيل قفل التطبيق", color = Color.White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (enabled) Field(old, { old = it; err = "" }, "الرمز الحالي", password = true)
                    Field(a, { a = it; err = "" }, "الرمز الجديد", password = true)
                    Field(b, { b = it; err = "" }, "أعد كتابة الرمز", password = true)
                    Text("من 4 إلى 32 خانة، يمكن استخدام أرقام وحروف. احفظه جيداً.", color = BMuted, fontSize = 13.sp)
                    if (err.isNotEmpty()) Text(err, color = BDanger)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (enabled && !AppLockStore.check(ctx, old)) err = "الرمز الحالي غير صحيح"
                    else if (!AppLockStore.validCode(a)) err = "الرمز يجب أن يكون من 4 إلى 32 خانة"
                    else if (a != b) err = "الرمزان غير متطابقين"
                    else {
                        AppLockStore.set(ctx, a)
                        if (!enabled && canBio) { bio = true; AppLockStore.setBio(ctx, true) }
                        enabled = true
                        dialog = ""
                    }
                }) { Text("حفظ", color = BRed) }
            },
            dismissButton = { TextButton(onClick = { dialog = "" }) { Text("إلغاء", color = BMuted) } }
        )
    }

    if (dialog == "off") {
        AlertDialog(
            onDismissRequest = { dialog = "" },
            containerColor = BCard,
            title = { Text("إيقاف القفل", color = Color.White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Field(old, { old = it; err = "" }, "اكتب الرمز الحالي", password = true)
                    if (err.isNotEmpty()) Text(err, color = BDanger)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (AppLockStore.check(ctx, old)) {
                        AppLockStore.clear(ctx)
                        enabled = false
                        bio = false
                        dialog = ""
                    } else err = "الرمز غير صحيح"
                }) { Text("إيقاف", color = BRed) }
            },
            dismissButton = { TextButton(onClick = { dialog = "" }) { Text("إلغاء", color = BMuted) } }
        )
    }
}

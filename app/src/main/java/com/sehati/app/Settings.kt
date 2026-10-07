package com.sehati.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions

@Composable
fun ClinicSettingsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val uid = bAuth.currentUser?.uid ?: ""
    var startH by remember { mutableStateOf("9") }
    var endH by remember { mutableStateOf("17") }
    var step by remember { mutableIntStateOf(30) }
    var bio by remember { mutableStateOf("") }
    var locText by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }
    var ok by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(uid) {
        bDb.collection("doctors").document(uid).get().addOnSuccessListener { d ->
            if (d.exists()) {
                startH = (d.getLong("startHour") ?: 9L).toString()
                endH = (d.getLong("endHour") ?: 17L).toString()
                step = (d.getLong("slotMinutes") ?: 30L).toInt()
                bio = d.getString("bio") ?: ""
                val la = d.getDouble("lat")
                val ln = d.getDouble("lng")
                locText = if (la != null && ln != null) "$la,$ln" else d.getString("mapLink") ?: ""
            }
        }
    }

    fun useGps() {
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val loc = try {
            lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        } catch (e: SecurityException) { null }
        if (loc != null) {
            locText = "${loc.latitude},${loc.longitude}"
            ok = true
            msg = "تم التقاط موقعك. اضغط حفظ الإعدادات"
        } else {
            ok = false
            msg = "تعذر تحديد الموقع. شغّل الـ GPS وافتح خرائط جوجل مرة ثم حاول، أو الصق رابط الموقع"
        }
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) useGps() else { ok = false; msg = "لم يتم منح إذن الموقع" }
    }

    Column(
        Modifier.fillMaxSize().background(BBg).padding(20.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TextButton(onClick = onBack) { Text("رجوع", color = BRed) }
        Text("إعدادات العيادة", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text("ساعات استقبال الحجوزات (بنظام 24 ساعة)", color = BMuted)
        Field(startH, { startH = it }, "بداية الدوام (مثل 9)", keyboard = KeyboardType.Number)
        Field(endH, { endH = it }, "نهاية الدوام (مثل 17)", keyboard = KeyboardType.Number)
        Text("مدة كل حجز بالدقائق", color = BMuted)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(15, 20, 30, 45, 60).forEach { m ->
                FilterChip(selected = step == m, onClick = { step = m }, label = { Text("$m") })
            }
        }
        Field(bio, { bio = it }, "نبذة عنك (تظهر للمريض)")
        Text("موقع العيادة", color = BMuted)
        Field(locText, { locText = it }, "رابط خرائط جوجل أو إحداثيات (33.51,36.29)")
        OutlinedButton(
            onClick = {
                val granted = ContextCompat.checkSelfPermission(
                    ctx, Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
                if (granted) useGps() else launcher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            },
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) { Text("استخدم موقعي الحالي (وأنا بالعيادة)", color = Color.White) }
        if (msg.isNotEmpty()) Text(msg, color = if (ok) BGreen else BRed)
        RedButton("حفظ الإعدادات", enabled = !busy) {
            val sh = startH.trim().toIntOrNull()
            val eh = endH.trim().toIntOrNull()
            if (sh == null || eh == null || sh < 0 || eh > 24 || sh >= eh) {
                ok = false
                msg = "تأكد من ساعات الدوام: البداية أقل من النهاية، من 0 إلى 24"
                return@RedButton
            }
            val data = mutableMapOf<String, Any>(
                "startHour" to sh, "endHour" to eh, "slotMinutes" to step,
                "bio" to bio.trim(),
                "locationUpdatedAt" to FieldValue.serverTimestamp()
            )
            val ll = parseLatLng(locText)
            if (ll != null) {
                data["lat"] = ll.first
                data["lng"] = ll.second
                data["mapLink"] = FieldValue.delete()
            } else if (locText.trim().startsWith("http")) {
                data["mapLink"] = locText.trim()
                data["lat"] = FieldValue.delete()
                data["lng"] = FieldValue.delete()
            } else if (locText.isNotBlank()) {
                ok = false
                msg = "الموقع غير مفهوم. الصق رابط خرائط جوجل أو إحداثيات مثل 33.51,36.29"
                return@RedButton
            }
            busy = true
            msg = ""
            bDb.collection("doctors").document(uid).get().addOnSuccessListener { d ->
                if (!d.exists()) {
                    busy = false
                    ok = false
                    msg = "احفظ ملفك المهني أولًا (الاسم والاختصاص والمدينة)"
                } else {
                    bDb.collection("doctors").document(uid).set(data, SetOptions.merge())
                        .addOnSuccessListener { busy = false; ok = true; msg = "تم حفظ الإعدادات" }
                        .addOnFailureListener {
                            busy = false; ok = false
                            msg = "تعذر الحفظ، تحقق من الإنترنت وحاول مرة أخرى"
                        }
                }
            }.addOnFailureListener {
                busy = false; ok = false
                msg = "تعذر الاتصال، تحقق من الإنترنت"
            }
        }
    }
}

package com.sehati.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration

@Composable
fun PatientHealthProfileScreen(onBack: () -> Unit) {
    val uid = bAuth.currentUser?.uid.orEmpty()
    var bloodType by remember { mutableStateOf("") }
    var allergies by remember { mutableStateOf("") }
    var conditions by remember { mutableStateOf("") }
    var emergencyContact by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var info by remember { mutableStateOf("") }

    DisposableEffect(uid) {
        if (uid.isBlank()) {
            loaded = true
            onDispose { }
        } else {
            val registration: ListenerRegistration = bDb.collection("healthProfiles").document(uid)
                .addSnapshotListener { doc, exception ->
                    if (exception != null) {
                        error = "تعذر تحميل ملفك الصحي. تحقق من اتصالك وصلاحيات الحساب."
                    } else if (doc != null && doc.exists()) {
                        bloodType = doc.getString("bloodType").orEmpty()
                        allergies = doc.getString("allergies").orEmpty()
                        conditions = doc.getString("conditions").orEmpty()
                        emergencyContact = doc.getString("emergencyContact").orEmpty()
                        notes = doc.getString("notes").orEmpty()
                        error = ""
                    }
                    loaded = true
                }
            onDispose { registration.remove() }
        }
    }

    Column(
        Modifier.fillMaxSize().background(Color(0xCC0A0C11)).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "رجوع", tint = Color.White) }
            Icon(Icons.Filled.HealthAndSafety, null, tint = Color(0xFFE5171F))
            Spacer(Modifier.width(8.dp))
            Text("ملفي الصحي", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
        }
        Text(
            "سجل معلوماتك الصحية المهمة لتكون منظمة في مكان واحد. هذا الملف خاص بحسابك ولا يستطيع الطبيب أو أي مستخدم آخر قراءته حالياً.",
            color = Color(0xFFB6C6DA), fontSize = 13.sp
        )
        if (!loaded) {
            LinearProgressIndicator(Modifier.fillMaxWidth(), color = Color(0xFFE5171F))
        } else {
            Column(
                Modifier.fillMaxWidth().background(Color(0xEE14171D), RoundedCornerShape(16.dp))
                    .border(1.dp, Color(0xFF30343D), RoundedCornerShape(16.dp)).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = bloodType, onValueChange = { bloodType = it.take(8); info = "" },
                    modifier = Modifier.fillMaxWidth(), label = { Text("فصيلة الدم (اختياري)") },
                    singleLine = true, enabled = !busy
                )
                OutlinedTextField(
                    value = allergies, onValueChange = { allergies = it.take(2000); info = "" },
                    modifier = Modifier.fillMaxWidth(), label = { Text("الحساسيات") },
                    minLines = 2, enabled = !busy
                )
                OutlinedTextField(
                    value = conditions, onValueChange = { conditions = it.take(2000); info = "" },
                    modifier = Modifier.fillMaxWidth(), label = { Text("الأمراض والحالات المزمنة") },
                    minLines = 2, enabled = !busy
                )
                OutlinedTextField(
                    value = emergencyContact, onValueChange = { emergencyContact = it.take(100); info = "" },
                    modifier = Modifier.fillMaxWidth(), label = { Text("جهة اتصال للطوارئ") },
                    singleLine = true, enabled = !busy
                )
                OutlinedTextField(
                    value = notes, onValueChange = { notes = it.take(3000); info = "" },
                    modifier = Modifier.fillMaxWidth(), label = { Text("ملاحظات صحية مهمة") },
                    minLines = 3, enabled = !busy
                )
                Button(
                    onClick = {
                        busy = true
                        error = ""
                        info = ""
                        bDb.collection("healthProfiles").document(uid).set(
                            mapOf(
                                "uid" to uid,
                                "bloodType" to bloodType.trim(),
                                "allergies" to allergies.trim(),
                                "conditions" to conditions.trim(),
                                "emergencyContact" to emergencyContact.trim(),
                                "notes" to notes.trim(),
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        ).addOnSuccessListener {
                            busy = false
                            info = "تم حفظ ملفك الصحي بشكل خاص."
                        }.addOnFailureListener {
                            busy = false
                            error = "لم يتم حفظ الملف. تحقق من صلاحيات Firestore وحاول مجدداً."
                        }
                    },
                    enabled = !busy && uid.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) {
                    if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Text("حفظ الملف الصحي")
                }
            }
        }
        if (error.isNotBlank()) Text(error, color = Color(0xFFFF6674), fontSize = 13.sp)
        if (info.isNotBlank()) Text(info, color = Color(0xFF39D6A3), fontSize = 13.sp)
        Text(
            "لا تستبدل هذه البيانات مراجعة الطبيب. لا تكتب معلومات طبية لا تعرف صحتها، ويمكنك ترك أي حقل فارغاً.",
            color = Color(0xFFB6C6DA), fontSize = 12.sp
        )
    }
}

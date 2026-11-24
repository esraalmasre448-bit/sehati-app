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

@Composable
fun DoctorSharedHealthProfileScreen(patientUid: String, onBack: () -> Unit) {
    var data by remember(patientUid) { mutableStateOf<Map<String, Any?>?>(null) }
    var loaded by remember(patientUid) { mutableStateOf(false) }
    var error by remember(patientUid) { mutableStateOf("") }

    LaunchedEffect(patientUid) {
        if (patientUid.isBlank()) {
            error = "لم يتم تحديد المريض."
            loaded = true
        } else {
            bDb.collection("healthProfiles").document(patientUid).get()
                .addOnSuccessListener { snapshot ->
                    if (snapshot.exists()) data = snapshot.data.orEmpty()
                    else error = "لم ينشئ المريض ملفاً صحياً بعد."
                    loaded = true
                }
                .addOnFailureListener {
                    error = "لا يمكن عرض الملف. قد لا يكون المريض قد منحك صلاحية الوصول أو انتهت صلاحيتها."
                    loaded = true
                }
        }
    }

    Column(
        Modifier.fillMaxSize().background(BBg).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "رجوع", tint = Color.White) }
            Icon(Icons.Filled.HealthAndSafety, null, tint = BRed)
            Spacer(Modifier.width(8.dp))
            Text("السجل الصحي بموافقة المريض", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        }
        Text("لا يظهر هذا السجل إلا بموافقة المريض السارية، ويمكنه إلغاء الوصول في أي وقت.", color = BMuted, fontSize = 12.sp)
        if (!loaded) CircularProgressIndicator(color = BRed)
        if (error.isNotBlank()) Text(error, color = BDanger)
        data?.let { profile ->
            HealthField("فصيلة الدم", profile["bloodType"])
            HealthField("الحساسيات", profile["allergies"])
            HealthField("الحالات المزمنة", profile["conditions"])
            HealthField("جهة اتصال الطوارئ", profile["emergencyContact"])
            HealthField("ملاحظات صحية", profile["notes"])
        }
    }
}

@Composable
private fun HealthField(label: String, value: Any?) {
    val text = value?.toString().orEmpty().ifBlank { "غير مضاف" }
    Column(
        Modifier.fillMaxWidth().background(BCard, RoundedCornerShape(12.dp))
            .border(1.dp, BLine, RoundedCornerShape(12.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(label, color = BMuted, fontSize = 12.sp)
        Text(text, color = Color.White, fontSize = 15.sp)
    }
}

package com.sehati.app

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PatientVisitSummaryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var symptoms by remember { mutableStateOf("") }
    var duration by remember { mutableStateOf("") }
    var medicines by remember { mutableStateOf("") }
    var allergies by remember { mutableStateOf("") }
    var questions by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    fun summary(): String = buildString {
        appendLine("ملخص المريض قبل الزيارة")
        appendLine("الأعراض: ${symptoms.ifBlank { "لم يُذكر" }}")
        appendLine("منذ متى: ${duration.ifBlank { "لم يُذكر" }}")
        appendLine("الأدوية الحالية: ${medicines.ifBlank { "لم يُذكر" }}")
        appendLine("الحساسيات: ${allergies.ifBlank { "لم تُذكر" }}")
        appendLine("أسئلة للطبيب: ${questions.ifBlank { "لا يوجد" }}")
        appendLine("ملاحظات إضافية: ${note.ifBlank { "لا يوجد" }}")
    }

    Column(
        Modifier.fillMaxSize().background(Color(0xCC0A0C11)).verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "رجوع", tint = Color.White) }
            Text("ملخص قبل زيارة الطبيب", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
        }
        Text("اكتب المعلومات التي تريد تذكّرها أثناء المقابلة. لا يُرسل هذا النموذج تلقائياً إلى أي جهة.", color = Color(0xFFB6C6DA), fontSize = 13.sp)
        VisitSummaryField("ما الأعراض التي تريد مناقشتها؟", symptoms) { symptoms = it }
        VisitSummaryField("منذ متى بدأت؟", duration) { duration = it }
        VisitSummaryField("الأدوية الحالية والجرعات إن عُرفت", medicines) { medicines = it }
        VisitSummaryField("الحساسيات أو التفاعلات السابقة", allergies) { allergies = it }
        VisitSummaryField("أسئلة تريد طرحها على الطبيب", questions) { questions = it }
        VisitSummaryField("ملاحظات إضافية", note) { note = it }
        Button(
            onClick = {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "ملخص قبل زيارة الطبيب")
                    putExtra(Intent.EXTRA_TEXT, summary())
                }
                context.startActivity(Intent.createChooser(intent, "مشاركة الملخص"))
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE5171F))
        ) {
            Icon(Icons.Filled.Share, null)
            Spacer(Modifier.width(8.dp))
            Text("مشاركة الملخص مع الطبيب")
        }
        Text("راجع المعلومات قبل مشاركتها. لا تكتب بيانات لا تريد إرسالها، ولا تستخدم هذا الملخص للطوارئ أو لتأخير طلب الرعاية.", color = Color(0xFFB6C6DA), fontSize = 12.sp)
    }
}

@Composable
private fun VisitSummaryField(label: String, value: String, onValue: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(label, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        OutlinedTextField(
            value = value,
            onValueChange = onValue,
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            maxLines = 5,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Color(0xFFE5171F),
                unfocusedBorderColor = Color(0xFF30343D),
                focusedLabelColor = Color(0xFFE5171F),
                cursorColor = Color(0xFFE5171F)
            )
        )
    }
}

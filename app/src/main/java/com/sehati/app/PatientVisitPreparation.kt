package com.sehati.app

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val VisitRed = Color(0xFFE5171F)
private val VisitCard = Color(0xEE14171D)
private val VisitLine = Color(0xFF30343D)
private val VisitMuted = Color(0xFFB6C6DA)

private data class VisitTask(val id: String, val title: String, val hint: String)

private val visitTasks = listOf(
    VisitTask("old_reports", "جهّز التحاليل والصور القديمة", "أحضر التقارير أو الصور المرتبطة بسبب الزيارة"),
    VisitTask("medicines", "اكتب أسماء أدويتك الحالية", "اذكر الجرعات إن كنت تعرفها، ولا توقف دواء من نفسك"),
    VisitTask("allergies", "تذكّر الحساسية والأدوية التي سببت لك مشكلة", "أخبر الطبيب عن أي حساسية معروفة"),
    VisitTask("questions", "حضّر الأسئلة التي تريد طرحها", "دوّن الأعراض ومتى بدأت وما الذي يزيدها أو يخففها"),
    VisitTask("documents", "تأكد من وجود أوراقك اللازمة", "بطاقة الهوية وأي إحالة أو مستند طلبته العيادة"),
    VisitTask("time", "تأكد من موعدك وطريق الوصول", "راجع حالة الموعد وموقع العيادة قبل الانطلاق")
)

private object VisitPreparationStore {
    private const val PREFS = "patient_visit_preparation_v1"
    fun read(context: Context, id: String) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(id, false)
    fun write(context: Context, id: String, checked: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(id, checked).apply()
    }
    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }
}

@Composable
fun PatientVisitPreparationScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var checked by remember {
        mutableStateOf(visitTasks.associate { it.id to VisitPreparationStore.read(context, it.id) })
    }
    val done = checked.values.count { it }
    Column(
        Modifier.fillMaxSize().background(Color(0xCC0A0C11)).verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "رجوع", tint = Color.White) }
            Spacer(Modifier.width(8.dp))
            Text("الاستعداد للزيارة", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
        }
        Column(
            Modifier.fillMaxWidth().background(VisitCard, RoundedCornerShape(18.dp))
                .border(1.dp, VisitLine, RoundedCornerShape(18.dp)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Checklist, null, tint = VisitRed, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(8.dp))
                Text("قائمة تجهيز بسيطة قبل الموعد", color = Color.White, fontWeight = FontWeight.Bold)
            }
            Text("علّم ما أنجزته. تُحفظ اختياراتك على هذا الهاتف فقط.", color = VisitMuted, fontSize = 13.sp)
            LinearProgressIndicator(
                progress = { done.toFloat() / visitTasks.size },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = VisitRed,
                trackColor = VisitLine
            )
            Text("$done من ${visitTasks.size} خطوات مكتملة", color = Color.White, fontWeight = FontWeight.SemiBold)
        }
        visitTasks.forEach { task ->
            val isDone = checked[task.id] == true
            Row(
                Modifier.fillMaxWidth().background(VisitCard, RoundedCornerShape(14.dp))
                    .border(1.dp, if (isDone) VisitRed.copy(alpha = 0.65f) else VisitLine, RoundedCornerShape(14.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = isDone,
                    onCheckedChange = { value ->
                        VisitPreparationStore.write(context, task.id, value)
                        checked = checked.toMutableMap().apply { put(task.id, value) }
                    },
                    colors = CheckboxDefaults.colors(checkedColor = VisitRed)
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(task.title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text(task.hint, color = VisitMuted, fontSize = 12.sp)
                }
                if (isDone) Icon(Icons.Filled.CheckCircle, null, tint = VisitRed)
            }
        }
        OutlinedButton(
            onClick = {
                VisitPreparationStore.clear(context)
                checked = visitTasks.associate { it.id to false }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Filled.RestartAlt, null)
            Spacer(Modifier.width(8.dp))
            Text("إعادة ضبط القائمة")
        }
        Text(
            "هذه القائمة للتنظيم والتذكير فقط، ولا تُعدّ تشخيصاً أو بديلاً عن تعليمات الطبيب.",
            color = VisitMuted, fontSize = 12.sp
        )
    }
}

package com.sehati.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.firestore.FieldValue

private data class MedicationPlan(
    val id: String,
    val medicine: String,
    val dose: String,
    val time: String,
    val active: Boolean
)

@Composable
fun MedicationPlansScreen(onBack: () -> Unit) {
    val uid = bAuth.currentUser?.uid.orEmpty()
    val ctx = LocalContext.current
    var medicine by remember { mutableStateOf("") }
    var dose by remember { mutableStateOf("") }
    var time by remember { mutableStateOf("") }
    var plans by remember { mutableStateOf<List<MedicationPlan>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var info by remember { mutableStateOf("") }

    DisposableEffect(uid) {
        if (uid.isBlank()) {
            loaded = true
            onDispose { }
        } else {
            val registration = bDb.collection("medicationReminders").document(uid)
                .collection("items").addSnapshotListener { snapshot, exception ->
                    if (exception != null) {
                        error = when ((exception as? com.google.firebase.firestore.FirebaseFirestoreException)?.code) {
                            com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                                "لا توجد صلاحية لقراءة جدول الأدوية."
                            else -> "تعذر تحميل جدول الأدوية. حاول مجدداً."
                        }
                    } else {
                        plans = snapshot?.documents.orEmpty().map { doc ->
                            MedicationPlan(
                                id = doc.id,
                                medicine = doc.getString("medicine").orEmpty(),
                                dose = doc.getString("dose").orEmpty(),
                                time = doc.getString("time").orEmpty(),
                                active = doc.getBoolean("active") == true
                            )
                        }.sortedWith(compareBy<MedicationPlan> { !it.active }.thenBy { it.time })
                        plans.forEach { plan ->
                            if (plan.active) MedicationReminderScheduler.schedule(ctx, plan.id, plan.medicine, plan.dose, plan.time)
                            else MedicationReminderScheduler.cancel(ctx, plan.id)
                        }
                        error = ""
                    }
                    loaded = true
                }
            onDispose { registration.remove() }
        }
    }

    Column(
        Modifier.fillMaxSize().background(Color(0xCC0A0C11)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "رجوع", tint = Color.White) }
            Text("جدول أدويتي", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
        }
        Text(
            "سجّل أدويتك وجرعاتها وحدد وقتاً بصيغة 24 ساعة مثل 08:30 لتصلك تذكرة يومية على هاتفك.",
            color = Color(0xFFB6C6DA), fontSize = 13.sp
        )
        Column(
            Modifier.fillMaxWidth().background(Color(0xEE14171D), RoundedCornerShape(16.dp))
                .border(1.dp, Color(0xFF30343D), RoundedCornerShape(16.dp)).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            OutlinedTextField(
                value = medicine, onValueChange = { medicine = it; error = "" },
                modifier = Modifier.fillMaxWidth(), label = { Text("اسم الدواء") },
                singleLine = true, enabled = !busy
            )
            OutlinedTextField(
                value = dose, onValueChange = { dose = it; error = "" },
                modifier = Modifier.fillMaxWidth(), label = { Text("الجرعة (مثال: حبة واحدة)") },
                singleLine = true, enabled = !busy
            )
            OutlinedTextField(
                value = time, onValueChange = { time = it; error = "" },
                modifier = Modifier.fillMaxWidth(), label = { Text("وقت التذكير (مثال: 08:00)") },
                singleLine = true, enabled = !busy
            )
            Button(
                onClick = {
                    val m = medicine.trim()
                    val d = dose.trim()
                    val t = time.trim()
                    if (m.isBlank() || d.isBlank() || t.isBlank()) {
                        error = "اكتب اسم الدواء والجرعة والوقت."
                    } else if (m.length > 100 || d.length > 80 || !Regex("^([01]\\d|2[0-3]):[0-5]\\d$").matches(t)) {
                        error = "اكتب الوقت بصيغة 24 ساعة مثل 08:00."
                    } else {
                        busy = true
                        error = ""
                        info = ""
                        val ref = bDb.collection("medicationReminders").document(uid)
                            .collection("items").document()
                        ref.set(
                            mapOf(
                                "medicine" to m,
                                "dose" to d,
                                "time" to t,
                                "active" to true,
                                "createdAt" to FieldValue.serverTimestamp(),
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        ).addOnSuccessListener {
                            MedicationReminderScheduler.schedule(ctx, ref.id, m, d, t)
                            medicine = ""; dose = ""; time = ""
                            info = "تمت إضافة الدواء وتفعيل التذكير اليومي على هذا الجهاز."
                            busy = false
                        }.addOnFailureListener {
                            error = if (it is com.google.firebase.firestore.FirebaseFirestoreException &&
                                it.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED
                            ) "رفضت قواعد Firestore حفظ البيانات. لم يُحفظ الدواء."
                            else "تعذر حفظ الدواء. حاول مرة أخرى."
                            busy = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(48.dp), enabled = !busy
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text("إضافة إلى جدول الأدوية")
            }
        }
        if (error.isNotBlank()) Text(error, color = Color(0xFFFF6674), fontSize = 13.sp)
        if (info.isNotBlank()) Text(info, color = Color(0xFF39D6A3), fontSize = 13.sp)
        Text("أدويتك المسجلة", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        when {
            !loaded -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFFE5171F))
            }
            plans.isEmpty() -> Text("لم تضف أدوية بعد.", color = Color(0xFFB6C6DA))
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(plans, key = { it.id }) { plan ->
                    Row(
                        Modifier.fillMaxWidth().background(Color(0xEE14171D), RoundedCornerShape(14.dp))
                            .border(1.dp, Color(0xFF30343D), RoundedCornerShape(14.dp)).padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Medication, null, tint = Color(0xFFE5171F), modifier = Modifier.size(30.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(plan.medicine, color = Color.White, fontWeight = FontWeight.Bold)
                            Text("الجرعة: ${plan.dose}", color = Color(0xFFB6C6DA), fontSize = 12.sp)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Schedule, null, tint = Color(0xFFB6C6DA), modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(plan.time, color = Color(0xFFB6C6DA), fontSize = 12.sp)
                            }
                            Text(if (plan.active) "ضمن الجدول" else "متوقف", color = if (plan.active) Color(0xFF39D6A3) else Color(0xFFB6C6DA), fontSize = 11.sp)
                        }
                        Switch(
                            checked = plan.active,
                            onCheckedChange = { active ->
                                bDb.collection("medicationReminders").document(uid).collection("items")
                                    .document(plan.id)
                                    .update(mapOf("active" to active, "updatedAt" to FieldValue.serverTimestamp()))
                                    .addOnSuccessListener {
                                        if (active) MedicationReminderScheduler.schedule(ctx, plan.id, plan.medicine, plan.dose, plan.time)
                                        else MedicationReminderScheduler.cancel(ctx, plan.id)
                                    }
                                    .addOnFailureListener {
                                        error = "تعذر تحديث حالة الدواء. لم تتغير البيانات."
                                    }
                            }
                        )
                    }
                }
            }
        }
    }
}

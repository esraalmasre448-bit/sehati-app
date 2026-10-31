package com.sehati.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.firestore.FieldValue

@Composable
fun DoctorPrescriptionsScreen(onBack: () -> Unit) {
    val uid = bAuth.currentUser?.uid.orEmpty()
    var doctorName by remember { mutableStateOf("") }
    var appointments by remember { mutableStateOf<List<Map<String, Any?>>>(emptyList()) }
    var selected by remember { mutableStateOf<Map<String, Any?>?>(null) }
    var kind by remember { mutableStateOf("rx") }
    var body by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(uid) {
        if (uid.isNotBlank()) {
            bDb.collection("doctors").document(uid).get()
                .addOnSuccessListener { doctorName = it.getString("name").orEmpty() }
        }
    }
    DisposableEffect(uid) {
        val reg = bDb.collection("appointments").whereEqualTo("doctorId", uid).limit(300)
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    status = "تعذر تحميل المواعيد. تحقق من الاتصال."
                } else {
                    appointments = snap?.documents?.map { it.data.orEmpty() + ("id" to it.id) }
                        ?.filter { it["status"] == "CONFIRMED" || it["status"] == "DONE" }
                        ?.sortedByDescending { it["date"].toString() }.orEmpty()
                }
            }
        onDispose { reg.remove() }
    }

    Column(
        Modifier.fillMaxSize().background(BBg).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, "رجوع", tint = Color.White)
            }
            Text("الوصفات الطبية", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        Text("اكتب وصفة أو طلب تحليل واربطه بموعد المريض.", color = BMuted, fontSize = 13.sp)
        if (appointments.isEmpty()) {
            Text("لا توجد مواعيد مؤكدة أو مكتملة لربط وصفة بها.", color = BMuted)
        } else {
            Text("اختر المريض والموعد", color = Color.White, fontWeight = FontWeight.Bold)
            LazyColumn(
                Modifier.weight(1f, fill = false).heightIn(max = 220.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(appointments, key = { it["id"].toString() }) { appointment ->
                    val active = selected?.get("id") == appointment["id"]
                    Column(
                        Modifier.fillMaxWidth()
                            .background(if (active) BRed.copy(alpha = 0.16f) else BCard, RoundedCornerShape(12.dp))
                            .border(1.dp, if (active) BRed else BLine, RoundedCornerShape(12.dp))
                            .clickable { selected = appointment }
                            .padding(12.dp)
                    ) {
                        Text(appointment["patientName"]?.toString().orEmpty().ifBlank { "مريض" },
                            color = Color.White, fontWeight = FontWeight.Bold)
                        Text("${appointment["date"] ?: ""} • ${appointment["time"] ?: ""}",
                            color = BMuted, fontSize = 12.sp)
                    }
                }
            }
        }
        Text("نوع المستند", color = Color.White, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = kind == "rx", onClick = { kind = "rx" }, label = { Text("وصفة دوائية") })
            FilterChip(selected = kind == "lab", onClick = { kind = "lab" }, label = { Text("طلب تحاليل") })
        }
        OutlinedTextField(
            value = body, onValueChange = { if (it.length <= 2000) body = it },
            modifier = Modifier.fillMaxWidth().heightIn(min = 150.dp),
            label = { Text(if (kind == "rx") "اكتب الأدوية والجرعات والتعليمات" else "اكتب التحاليل المطلوبة") },
            placeholder = { Text("اكتب التفاصيل بوضوح...") },
            minLines = 5, maxLines = 8,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                focusedBorderColor = BRed, unfocusedBorderColor = BLine,
                focusedLabelColor = BRed, unfocusedLabelColor = BMuted,
                cursorColor = BRed
            )
        )
        Text("${body.length}/2000", color = BMuted, fontSize = 11.sp)
        if (status.isNotBlank()) Text(status, color = if (status.startsWith("تم")) BGreen else BDanger)
        Button(
            onClick = {
                val appointment = selected ?: return@Button
                if (body.isBlank()) { status = "اكتب تفاصيل المستند أولاً."; return@Button }
                saving = true
                status = ""
                val patientId = appointment["patientId"]?.toString().orEmpty()
                val data = hashMapOf<String, Any>(
                    "doctorId" to uid,
                    "doctorName" to doctorName,
                    "patientId" to patientId,
                    "patientName" to appointment["patientName"]?.toString().orEmpty(),
                    "appointmentId" to appointment["id"]?.toString().orEmpty(),
                    "kind" to kind,
                    "text" to body.trim(),
                    "createdAt" to FieldValue.serverTimestamp()
                )
                bDb.collection("prescriptions").add(data)
                    .addOnSuccessListener {
                        body = ""
                        status = "تم حفظ المستند وإتاحته للمريض."
                        saving = false
                    }
                    .addOnFailureListener {
                        status = "تعذر الحفظ. تحقق من اشتراك الطبيب وصلاحيات الحساب والاتصال."
                        saving = false
                    }
            },
            enabled = !saving && selected != null && body.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = BRed, contentColor = Color.White)
        ) {
            if (saving) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
            else {
                Icon(Icons.Filled.Save, null)
                Spacer(Modifier.width(8.dp))
                Text("حفظ وإرسال للمريض")
            }
        }
    }
}

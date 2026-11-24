package com.sehati.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.PendingActions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.firestore.ListenerRegistration

@Composable
fun DoctorAnalyticsScreen(onBack: () -> Unit) {
    val uid = bAuth.currentUser?.uid.orEmpty()
    var appointments by remember { mutableStateOf<List<Map<String, Any?>>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }

    DisposableEffect(uid) {
        if (uid.isBlank()) return@DisposableEffect onDispose { }
        val listener: ListenerRegistration = bDb.collection("appointments")
            .whereEqualTo("doctorId", uid).limit(500)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    failed = true
                } else {
                    failed = false
                    appointments = snapshot?.documents?.map { it.data.orEmpty() }.orEmpty()
                    loaded = true
                }
            }
        onDispose { listener.remove() }
    }

    val total = appointments.size
    val pending = appointments.count { it["status"] == "PENDING" }
    val confirmed = appointments.count { it["status"] == "CONFIRMED" }
    val completed = appointments.count { it["status"] == "DONE" }
    val cancelled = appointments.count { it["status"] == "CANCELLED" }
    val patients = appointments.mapNotNull { it["patientId"] as? String }.toSet().size
    val revenueByCurrency = appointments.filter { it["paidAmount"] is Number }
        .groupBy { it["paidCurrency"]?.toString()?.takeIf { value -> value.isNotBlank() } ?: "غير محدد" }
        .mapValues { entry -> entry.value.sumOf { (it["paidAmount"] as Number).toDouble() } }
    val revenueText = if (revenueByCurrency.isEmpty()) "غير مسجلة"
        else revenueByCurrency.entries.joinToString(" • ") {
            it.key + " " + String.format(java.util.Locale.US, "%.2f", it.value)
        }

    LazyColumn(
        Modifier.fillMaxSize().background(BBg).padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "رجوع", tint = Color.White) }
                Icon(Icons.Filled.Analytics, null, tint = BRed)
                Spacer(Modifier.width(8.dp))
                Text("إحصائيات العيادة", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
            }
        }
        item {
            Text("الأرقام محسوبة من سجلات المواعيد الفعلية المرتبطة بحسابك.", color = BMuted, fontSize = 12.sp)
        }
        if (failed) item { Text("تعذر تحميل الإحصائيات. تحقق من الصلاحيات والاتصال.", color = BDanger) }
        if (!loaded && !failed) item { CircularProgressIndicator(color = BRed) }
        item {
            Metric("إجمالي المواعيد", total.toString(), Icons.Filled.CalendarMonth)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) { Metric("مرضى مختلفون", patients.toString(), Icons.Filled.Groups) }
                Box(Modifier.weight(1f)) { Metric("مكتملة", completed.toString(), Icons.Filled.CheckCircle) }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) { Metric("بانتظار الموافقة", pending.toString(), Icons.Filled.PendingActions) }
                Box(Modifier.weight(1f)) { Metric("مؤكدة", confirmed.toString(), Icons.Filled.CheckCircle) }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) { Metric("ملغاة", cancelled.toString(), Icons.Filled.CalendarMonth) }
                Box(Modifier.weight(1f)) {
                    Metric("المبالغ المسجلة", revenueText, Icons.Filled.Analytics)
                }
            }
        }
        item {
            Text(
                "المبالغ يدوّنها الطبيب يدوياً بعد استلامها، وليست تحققاً إلكترونياً من الدفع. تُعرض كل عملة على حدة. البيانات المعروضة حتى آخر 500 موعد.",
                color = BMuted, fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun Metric(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Column(
        Modifier.fillMaxWidth().background(BCard, RoundedCornerShape(16.dp))
            .border(1.dp, BLine, RoundedCornerShape(16.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(icon, null, tint = BRed)
        Text(value, color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
        Text(label, color = BMuted, fontSize = 12.sp)
    }
}

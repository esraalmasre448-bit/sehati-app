package com.sehati.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PatientPrescriptionsScreen(onBack: () -> Unit) {
    val uid = bAuth.currentUser?.uid.orEmpty()
    var documents by remember { mutableStateOf<List<Map<String, Any?>>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf(false) }

    DisposableEffect(uid) {
        if (uid.isBlank()) {
            loading = false
            onDispose { }
        } else {
            val registration = bDb.collection("prescriptions")
                .whereEqualTo("patientId", uid)
                .limit(200)
                .addSnapshotListener { snapshot, exception ->
                    loading = false
                    error = exception != null
                    if (exception == null) {
                        documents = snapshot?.documents
                            ?.map { it.data.orEmpty() + ("id" to it.id) }
                            ?.sortedByDescending {
                                (it["createdAt"] as? Timestamp)?.seconds ?: 0L
                            }.orEmpty()
                    }
                }
            onDispose { registration.remove() }
        }
    }

    Column(
        Modifier.fillMaxSize().background(BBg).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, "رجوع", tint = Color.White)
            }
            Text("وصفاتي الطبية", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        Text("الوصفات وطلبات التحاليل التي أرسلها أطباؤك.", color = BMuted, fontSize = 13.sp)
        when {
            loading -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = BRed)
            }
            error -> Text("تعذر تحميل المستندات. تحقق من الاتصال وحاول مجدداً.", color = BDanger)
            documents.isEmpty() -> Text("لا توجد وصفات أو طلبات تحاليل حتى الآن.", color = BMuted)
            else -> LazyColumn(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(documents, key = { it["id"].toString() }) { item ->
                    val kind = item["kind"]?.toString()
                    Column(
                        Modifier.fillMaxWidth()
                            .background(BCard, RoundedCornerShape(14.dp))
                            .border(1.dp, BLine, RoundedCornerShape(14.dp))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Description, null, tint = BRed)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (kind == "lab") "طلب تحاليل" else "وصفة دوائية",
                                color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp
                            )
                        }
                        Text("الطبيب: ${item["doctorName"]?.toString().orEmpty().ifBlank { "الطبيب" }}", color = BMuted)
                        val timestamp = item["createdAt"] as? Timestamp
                        if (timestamp != null) {
                            Text(
                                SimpleDateFormat("yyyy/MM/dd  HH:mm", Locale.getDefault()).format(Date(timestamp.seconds * 1000)),
                                color = BMuted, fontSize = 11.sp
                            )
                        }
                        Text(item["text"]?.toString().orEmpty(), color = Color.White, fontSize = 15.sp)
                    }
                }
            }
        }
    }
}

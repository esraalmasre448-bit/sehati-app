package com.sehati.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** قائمة مرضى الطبيب بتصميم المرجع: بحث، تبويبات (الكل / جدد / متابَعون)، وبطاقة لكل مريض. البيانات من مواعيد الطبيب فقط. */
@Composable
fun DoctorPatientsScreen(onBack: () -> Unit) {
    val uid = bAuth.currentUser?.uid ?: ""
    var patients by remember { mutableStateOf<List<Map<String, Any?>>?>(null) }
    var failed by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var tab by remember { mutableIntStateOf(0) }

    DisposableEffect(uid) {
        val reg = bDb.collection("appointments").whereEqualTo("doctorId", uid).limit(300)
            .addSnapshotListener { s, e ->
                if (e != null || s == null) {
                    failed = true
                } else {
                    failed = false
                    val rows = s.documents.map { it.data.orEmpty() }
                        .filter { it["status"] != "CANCELLED" && !it["patientId"].toString().isBlank() }
                    patients = rows.groupBy { it["patientId"].toString() }.map { (pid, list) ->
                        val latest = list.maxByOrNull { it["date"].toString() } ?: list.first()
                        mapOf(
                            "id" to pid,
                            "name" to (latest["patientName"]?.toString() ?: ""),
                            "phone" to (latest["patientPhone"]?.toString() ?: ""),
                            "count" to list.size,
                            "last" to (latest["date"]?.toString() ?: "")
                        )
                    }.sortedBy { it["name"].toString() }
                }
            }
        onDispose { reg.remove() }
    }

    Column(Modifier.fillMaxSize().background(BBg).padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("رجوع", color = BRed) }
            Text("قائمة المرضى", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth().height(50.dp)
                .background(BCard, RoundedCornerShape(14.dp))
                .border(1.dp, BLine, RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Filled.Search, null, tint = BMuted)
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) Text("ابحث عن مريض...", color = BMuted)
                BasicTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                    cursorBrush = SolidColor(BRed), modifier = Modifier.fillMaxWidth()
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("الكل", "جدد", "متابَعون").forEachIndexed { i, t ->
                Box(
                    Modifier.weight(1f).height(38.dp)
                        .background(if (tab == i) BRed else BCard, RoundedCornerShape(20.dp))
                        .border(1.dp, if (tab == i) BRed else BLine, RoundedCornerShape(20.dp))
                        .clickable { tab = i },
                    contentAlignment = Alignment.Center
                ) { Text(t, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
            }
        }
        Spacer(Modifier.height(10.dp))

        val list = patients
        val q = query.trim()
        val shown = list.orEmpty().filter { p ->
            val n = p["count"] as? Int ?: 0
            val tOk = tab == 0 || (tab == 1 && n <= 1) || (tab == 2 && n > 1)
            val qOk = q.isEmpty() || p["name"].toString().contains(q, true) || p["phone"].toString().contains(q)
            tOk && qOk
        }
        when {
            failed -> Text("تعذر تحميل المرضى، تحقق من الإنترنت", color = BDanger)
            list == null -> Text("جارٍ التحميل...", color = BMuted)
            list.isEmpty() -> Text("لا يوجد مرضى مرتبطون بحجوزاتك بعد", color = BMuted)
            shown.isEmpty() -> Text("لا توجد نتائج", color = BMuted)
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(shown, key = { it["id"].toString() }) { p ->
                    val pname = p["name"].toString()
                    val n = p["count"] as? Int ?: 0
                    Row(
                        Modifier.fillMaxWidth()
                            .background(BCard, RoundedCornerShape(16.dp))
                            .border(1.dp, BLine, RoundedCornerShape(16.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            Modifier.size(52.dp).background(BRed.copy(alpha = 0.16f), CircleShape)
                                .border(1.dp, BRed, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(pname.trim().take(1).ifBlank { "؟" }, color = BRed, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                pname.ifBlank { "مريض" }, color = Color.White, fontWeight = FontWeight.Bold,
                                fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                            val phone = p["phone"].toString()
                            if (phone.isNotBlank()) Text(phone, color = BMuted, fontSize = 13.sp)
                            Text("الحجوزات: $n  •  آخر موعد: ${p["last"]}", color = BMuted, fontSize = 12.sp)
                        }
                        val pill = if (n > 1) "متابَع" else "جديد"
                        val pc = if (n > 1) BGreen else Color(0xFFF5A524)
                        Box(
                            Modifier.background(pc.copy(alpha = 0.18f), RoundedCornerShape(20.dp))
                                .border(1.dp, pc, RoundedCornerShape(20.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) { Text(pill, color = pc, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
    }
}

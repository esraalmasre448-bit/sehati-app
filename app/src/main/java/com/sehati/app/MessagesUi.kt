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
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Locale

private fun msgListTime(t: Any?): String {
    val d = (t as? Timestamp)?.toDate() ?: return ""
    return SimpleDateFormat("dd/MM HH:mm", Locale.ENGLISH).format(d)
}

/** قائمة المحادثات بتصميم المرجع: بحث وبطاقة لكل محادثة. نفس البيانات والمنطق السابق. */
@Composable
fun MessagesScreen(onBack: () -> Unit, onOpenChat: (String) -> Unit) {
    val uid = bAuth.currentUser?.uid ?: ""
    var asPatient by remember { mutableStateOf<List<Map<String, Any?>>>(emptyList()) }
    var asDoctor by remember { mutableStateOf<List<Map<String, Any?>>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    DisposableEffect(uid) {
        val r1 = bDb.collection("conversations").whereEqualTo("patientId", uid).limit(100)
            .addSnapshotListener { s, e ->
                if (e != null || s == null) failed = true
                else {
                    failed = false
                    asPatient = s.documents.map { it.data.orEmpty() + ("id" to it.id) }
                    loaded = true
                }
            }
        val r2 = bDb.collection("conversations").whereEqualTo("doctorId", uid).limit(100)
            .addSnapshotListener { s, e ->
                if (e != null || s == null) failed = true
                else {
                    failed = false
                    asDoctor = s.documents.map { it.data.orEmpty() + ("id" to it.id) }
                    loaded = true
                }
            }
        onDispose { r1.remove(); r2.remove() }
    }

    val all = (asPatient + asDoctor)
        .distinctBy { it["id"] }
        .sortedByDescending { (it["lastAt"] as? Timestamp)?.toDate()?.time ?: 0L }
    val q = query.trim()
    val shown = all.filter { c ->
        val other = if (c["patientId"] == uid) "${c["doctorName"] ?: ""}" else "${c["patientName"] ?: ""}"
        q.isEmpty() || other.contains(q, true)
    }

    Column(Modifier.fillMaxSize().background(BBg).padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("رجوع", color = BRed) }
            Text("الرسائل", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.weight(1f))
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
                if (query.isEmpty()) Text("ابحث في المحادثات...", color = BMuted)
                BasicTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                    cursorBrush = SolidColor(BRed), modifier = Modifier.fillMaxWidth()
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        when {
            failed -> Text("تعذر تحميل الرسائل، تحقق من الإنترنت", color = BDanger)
            !loaded -> Text("جارٍ التحميل...", color = BMuted)
            all.isEmpty() -> Text("لا توجد محادثات بعد. يمكنك مراسلة طبيبك بعد حجز موعد معه.", color = BMuted)
            shown.isEmpty() -> Text("لا توجد نتائج", color = BMuted)
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(shown, key = { it["id"].toString() }) { c ->
                    val other = if (c["patientId"] == uid) "${c["doctorName"] ?: ""}" else "${c["patientName"] ?: ""}"
                    Row(
                        Modifier.fillMaxWidth()
                            .background(BCard, RoundedCornerShape(16.dp))
                            .border(1.dp, BLine, RoundedCornerShape(16.dp))
                            .clickable { onOpenChat("${c["id"]}") }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            Modifier.size(50.dp).background(BRed.copy(alpha = 0.16f), CircleShape).border(1.dp, BRed, CircleShape),
                            contentAlignment = Alignment.Center
                        ) { Text(other.trim().take(1).ifBlank { "؟" }, color = BRed, fontWeight = FontWeight.Bold, fontSize = 19.sp) }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(other.ifBlank { "محادثة" }, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "${c["lastMessage"] ?: ""}".ifBlank { "لا توجد رسائل بعد" },
                                color = BMuted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(msgListTime(c["lastAt"]), color = BMuted, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

package com.sehati.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import java.text.SimpleDateFormat
import java.util.Locale

private fun fmtTime(t: Any?): String {
    val d = (t as? Timestamp)?.toDate() ?: return ""
    return SimpleDateFormat("dd/MM HH:mm", Locale.ENGLISH).format(d)
}

/** معرّف المحادثة: معرّف الطبيب + "_" + معرّف المريض. */
fun convIdOf(doctorId: String, patientId: String) = "${doctorId}_$patientId"

/**
 * يفتح محادثة المريض مع الطبيب، ويُنشئها إن لم تكن موجودة.
 * المحادثة لا تُنشأ إلا إذا كان للمريض موعد سابق مع الطبيب (تتحقق القواعد من ذلك أيضاً).
 */
fun openConversation(
    doctorId: String, doctorName: String, patientName: String,
    onReady: (String) -> Unit, onError: (String) -> Unit
) {
    val uid = bAuth.currentUser?.uid
    if (uid.isNullOrEmpty()) { onError("سجّلي الدخول أولاً"); return }
    bDb.collection("appointments")
        .whereEqualTo("patientId", uid).whereEqualTo("doctorId", doctorId).limit(1).get()
        .addOnSuccessListener { s ->
            val appt = s.documents.firstOrNull()
            if (appt == null) {
                onError("يمكنك مراسلة الطبيب بعد حجز موعد معه")
                return@addOnSuccessListener
            }
            val id = convIdOf(doctorId, uid)
            val ref = bDb.collection("conversations").document(id)
            ref.get().addOnSuccessListener { c ->
                if (c.exists()) {
                    onReady(id)
                } else {
                    ref.set(mapOf(
                        "doctorId" to doctorId,
                        "patientId" to uid,
                        "doctorName" to doctorName,
                        "patientName" to patientName,
                        "appointmentId" to appt.id,
                        "lastMessage" to "",
                        "lastAt" to FieldValue.serverTimestamp(),
                        "createdAt" to FieldValue.serverTimestamp()
                    ))
                        .addOnSuccessListener { onReady(id) }
                        .addOnFailureListener { onError("تعذر فتح المحادثة، تحقق من الإنترنت") }
                }
            }.addOnFailureListener { onError("تعذر فتح المحادثة، تحقق من الإنترنت") }
        }
        .addOnFailureListener { onError("تعذر فتح المحادثة، تحقق من الإنترنت") }
}

/** قائمة المحادثات للمريض والطبيب معاً. */
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
                if (e != null || s == null) {
                    failed = true
                } else {
                    failed = false
                    asPatient = s.documents.map { it.data.orEmpty() + ("id" to it.id) }
                    loaded = true
                }
            }
        val r2 = bDb.collection("conversations").whereEqualTo("doctorId", uid).limit(100)
            .addSnapshotListener { s, e ->
                if (e != null || s == null) {
                    failed = true
                } else {
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
    val filtered = all.filter { c ->
        val isPatient = c["patientId"] == uid
        val other = if (isPatient) "${c["doctorName"] ?: ""}" else "${c["patientName"] ?: ""}"
        "$other ${c["lastMessage"] ?: ""}".contains(query.trim(), ignoreCase = true)
    }

    Column(Modifier.fillMaxSize().background(BBg).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowForward, null, tint = Color.White) }
            Column(Modifier.weight(1f)) {
                Text("الرسائل", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("${all.size} محادثة", fontSize = 12.sp, color = BMuted)
            }
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("ابحث في المحادثات") },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            shape = RoundedCornerShape(14.dp)
        )
        Spacer(Modifier.height(10.dp))
        when {
            failed -> Text("تعذر تحميل الرسائل، تحقق من الإنترنت", color = BDanger)
            !loaded -> Text("جارٍ التحميل...", color = BMuted)
            all.isEmpty() -> Text("لا توجد محادثات بعد. يمكنك مراسلة طبيبك بعد حجز موعد معه.", color = BMuted)
            filtered.isEmpty() -> Text("لا توجد محادثات مطابقة للبحث", color = BMuted)
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filtered, key = { it["id"].toString() }) { c ->
                    val isPatient = c["patientId"] == uid
                    val other = if (isPatient) "${c["doctorName"] ?: ""}" else "${c["patientName"] ?: ""}"
                    Row(
                        Modifier.fillMaxWidth()
                            .background(BCard, RoundedCornerShape(16.dp))
                            .border(1.dp, BLine, RoundedCornerShape(16.dp))
                            .clickable { onOpenChat("${c["id"]}") }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            Modifier.size(46.dp).background(BRed.copy(alpha = 0.16f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(other.trim().take(1).ifBlank { "؟" }, color = BRed, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(other.ifBlank { "محادثة" }, color = Color.White, fontWeight = FontWeight.Bold)
                            Text(
                                "${c["lastMessage"] ?: ""}".ifBlank { "لا توجد رسائل بعد" },
                                color = BMuted, fontSize = 13.sp, maxLines = 1
                            )
                        }
                        Text(fmtTime(c["lastAt"]), color = BMuted, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

/** شاشة محادثة واحدة. */
@Composable
fun ChatScreen(convId: String, onBack: () -> Unit) {
    val uid = bAuth.currentUser?.uid ?: ""
    var conv by remember { mutableStateOf<Map<String, Any?>?>(null) }
    var msgs by remember { mutableStateOf<List<Map<String, Any?>>?>(null) }
    var failed by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var sendErr by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    DisposableEffect(convId) {
        val r1 = bDb.collection("conversations").document(convId).addSnapshotListener { s, _ ->
            conv = s?.data
        }
        val r2 = bDb.collection("messages").whereEqualTo("convId", convId).limit(300)
            .addSnapshotListener { s, e ->
                if (e != null || s == null) {
                    failed = true
                } else {
                    failed = false
                    msgs = s.documents.map { it.data.orEmpty() + ("id" to it.id) }
                        .sortedBy { (it["createdAt"] as? Timestamp)?.toDate()?.time ?: Long.MAX_VALUE }
                }
            }
        onDispose { r1.remove(); r2.remove() }
    }

    LaunchedEffect(msgs?.size) {
        val n = msgs?.size ?: 0
        if (n > 0) listState.animateScrollToItem(n - 1)
    }

    val c = conv
    val other = when {
        c == null -> ""
        c["patientId"] == uid -> "${c["doctorName"] ?: ""}"
        else -> "${c["patientName"] ?: ""}"
    }

    fun send() {
        val text = input.trim()
        if (text.isEmpty() || sending) return
        if (text.length > 1000) {
            sendErr = "الرسالة طويلة جداً (الحد 1000 حرف)"
            return
        }
        sending = true
        sendErr = ""
        val db = bDb
        val batch = db.batch()
        batch.set(db.collection("messages").document(), mapOf(
            "convId" to convId,
            "senderId" to uid,
            "text" to text,
            "createdAt" to FieldValue.serverTimestamp()
        ))
        batch.update(db.collection("conversations").document(convId), mapOf(
            "lastMessage" to text,
            "lastAt" to FieldValue.serverTimestamp()
        ))
        batch.commit()
            .addOnSuccessListener { input = ""; sending = false }
            .addOnFailureListener { sending = false; sendErr = "تعذر الإرسال، تحقق من الإنترنت" }
    }

    Column(Modifier.fillMaxSize().background(BBg).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowForward, null, tint = Color.White) }
            Text(other.ifBlank { "محادثة" }, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }

        val list = msgs
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                failed -> Text("تعذر تحميل الرسائل", color = BDanger, modifier = Modifier.align(Alignment.Center))
                list == null -> Text("جارٍ التحميل...", color = BMuted, modifier = Modifier.align(Alignment.Center))
                list.isEmpty() -> Text("ابدئي المحادثة بإرسال رسالة", color = BMuted, modifier = Modifier.align(Alignment.Center))
                else -> LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(list, key = { it["id"].toString() }) { m ->
                        val mine = m["senderId"] == uid
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start
                        ) {
                            Column(
                                Modifier.widthIn(max = 280.dp)
                                    .background(if (mine) BRed else BCard, RoundedCornerShape(14.dp))
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text("${m["text"] ?: ""}", color = if (mine) BOnAccent else Color.White)
                                Text(fmtTime(m["createdAt"]), color = if (mine) BOnAccent else BMuted, fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
        }

        if (sendErr.isNotEmpty()) Text(sendErr, color = BDanger, fontSize = 12.sp)
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("اكتبي رسالتك...", color = BMuted) },
                maxLines = 4
            )
            IconButton(onClick = { send() }, enabled = input.isNotBlank() && !sending) {
                Icon(Icons.Filled.Send, null, tint = BRed)
            }
        }
    }
}

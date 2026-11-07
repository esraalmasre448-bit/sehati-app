package com.sehati.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Chat
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
        .whereEqualTo("patientId", uid).whereEqualTo("doctorId", doctorId).limit(20).get()
        .addOnSuccessListener { s ->
            val appt = s.documents.firstOrNull { it.getString("status") in listOf("PENDING", "CONFIRMED", "DONE") }
            if (appt == null) {
                onError("يمكنك مراسلة الطبيب بعد حجز موعد معه")
                return@addOnSuccessListener
            }
            val id = convIdOf(doctorId, uid)
            val ref = bDb.collection("conversations").document(id)
            // نتحقق من وجود المحادثة أولاً؛ القواعد تسمح للمريض بالتحقق من معرّف محادثته غير الموجودة دون كشف أي بيانات.
            ref.get().addOnSuccessListener { existing ->
                if (existing.exists()) {
                    onReady(id)
                } else {
                    ref.set(mapOf(
                        "doctorId" to doctorId,
                        "patientId" to uid,
                        "doctorName" to doctorName,
                        "patientName" to patientName,
                        "appointmentId" to appt.id,
                        "lastMessage" to "",
                        "lastSenderId" to uid,
                        "lastAt" to FieldValue.serverTimestamp(),
                        "createdAt" to FieldValue.serverTimestamp()
                    )).addOnSuccessListener {
                        onReady(id)
                    }.addOnFailureListener {
                        onError("تعذر إنشاء المحادثة. تحقق من اتصالك وصلاحيات الحساب.")
                    }
                }
            }.addOnFailureListener {
                onError("تعذر فتح المحادثة. تحقق من الاتصال وصلاحيات الحساب.")
            }
        }
        .addOnFailureListener { onError("تعذر فتح المحادثة، تحقق من الإنترنت") }
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
        val messageRef = db.collection("messages").document()
        batch.set(messageRef, mapOf(
            "convId" to convId,
            "senderId" to uid,
            "text" to text,
            "createdAt" to FieldValue.serverTimestamp()
        ))
        batch.update(db.collection("conversations").document(convId), mapOf(
            "lastMessage" to text,
            "lastSenderId" to uid,
            "lastAt" to FieldValue.serverTimestamp()
        ))
        batch.commit()
            .addOnSuccessListener {
                input = ""
                sending = false
                PushSender.sendAfterMessage(convId, messageRef.id)
            }
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

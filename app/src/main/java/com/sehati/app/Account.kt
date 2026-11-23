package com.sehati.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue

@Composable
fun DeleteAccountSection(onDeleted: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    var pass by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }

    OutlinedButton(
        onClick = { open = true; msg = ""; pass = "" },
        modifier = Modifier.fillMaxWidth().height(52.dp)
    ) { Text("حذف حسابي", color = BDanger) }

    if (open) {
        AlertDialog(
            onDismissRequest = { if (!busy) open = false },
            title = { Text("حذف الحساب نهائيًا") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("سيُحذف حساب الدخول وملفك الشخصي. ستُخفى بياناتك الشخصية من المواعيد والوصفات مع الاحتفاظ بالسجل الطبي ومحتواه. لا يمكن التراجع عن حذف الحساب.")
                    Field(pass, { pass = it }, "كلمة السر", password = true)
                    if (msg.isNotEmpty()) Text(msg, color = BDanger)
                }
            },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    val user = bAuth.currentUser
                    val email = user?.email
                    if (user == null || email == null) {
                        msg = "تعذر تحديد الحساب"
                    } else if (pass.isBlank()) {
                        msg = "اكتب كلمة السر"
                    } else {
                        busy = true
                        msg = ""

                        fun finishDeletion() {
                            val cleanup = bDb.batch()
                            cleanup.delete(bDb.collection("users").document(user.uid))
                            cleanup.delete(bDb.collection("doctors").document(user.uid))
                            cleanup.delete(bDb.collection("doctorPrivate").document(user.uid))
                            cleanup.delete(bDb.collection("subscriptions").document(user.uid))
                            cleanup.commit()
                                .addOnSuccessListener {
                                    user.delete()
                                        .addOnSuccessListener {
                                            busy = false
                                            open = false
                                            onDeleted()
                                        }
                                        .addOnFailureListener {
                                            busy = false
                                            msg = "حُفظت إزالة بياناتك، لكن تعذر حذف حساب الدخول. سجّل الدخول مجددًا ثم أعد المحاولة."
                                        }
                                }
                                .addOnFailureListener {
                                    busy = false
                                    msg = "تعذر إكمال حذف الحساب. لم يُحذف حساب الدخول؛ حاول مجددًا."
                                }
                        }

                        fun anonymizeInChunks(
                            items: List<Pair<DocumentReference, Map<String, Any>>>,
                            offset: Int = 0,
                            onComplete: () -> Unit
                        ) {
                            if (offset >= items.size) {
                                onComplete()
                                return
                            }
                            val batch = bDb.batch()
                            items.drop(offset).take(400).forEach { (reference, fields) ->
                                batch.update(reference, fields)
                            }
                            batch.commit()
                                .addOnSuccessListener {
                                    anonymizeInChunks(items, offset + 400, onComplete)
                                }
                                .addOnFailureListener {
                                    busy = false
                                    msg = "تعذر إخفاء بعض بيانات السجل. لم يُحذف حساب الدخول؛ يمكنك المحاولة مجددًا."
                                }
                        }

                        fun wipe() {
                            bDb.collection("appointments").whereEqualTo("patientId", user.uid).get()
                                .addOnSuccessListener { appointments ->
                                    bDb.collection("prescriptions").whereEqualTo("patientId", user.uid).get()
                                        .addOnSuccessListener { prescriptions ->
                                            val changes = mutableListOf<Pair<DocumentReference, Map<String, Any>>>()
                                            appointments.documents.forEach { document ->
                                                changes += document.reference to mapOf(
                                                    "patientName" to "حساب محذوف",
                                                    "patientPhone" to "",
                                                    "note" to ""
                                                )
                                            }
                                            prescriptions.documents.forEach { document ->
                                                changes += document.reference to mapOf(
                                                    "patientId" to FieldValue.delete(),
                                                    "patientName" to "حساب محذوف",
                                                    "appointmentId" to FieldValue.delete()
                                                )
                                            }
                                            anonymizeInChunks(changes) { finishDeletion() }
                                        }
                                        .addOnFailureListener {
                                            busy = false
                                            msg = "تعذر الوصول إلى السجل الطبي؛ لم يُحذف الحساب. حاول مجددًا."
                                        }
                                }
                                .addOnFailureListener {
                                    busy = false
                                    msg = "تعذر الوصول إلى المواعيد؛ لم يُحذف الحساب. حاول مجددًا."
                                }
                        }

                        user.reauthenticate(EmailAuthProvider.getCredential(email, pass))
                            .addOnSuccessListener { wipe() }
                            .addOnFailureListener {
                                busy = false
                                msg = "كلمة السر غير صحيحة أو انتهت جلسة الدخول"
                            }
                    }
                }) { Text("حذف نهائي", color = BDanger) }
            },
            dismissButton = {
                TextButton(enabled = !busy, onClick = { open = false }) { Text("إلغاء") }
            }
        )
    }
}

package com.sehati.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.EmailAuthProvider

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
                    Text("سيتم حذف حسابك وملفك الشخصي ولا يمكن التراجع. اكتب كلمة السر للتأكيد.")
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
                        user.reauthenticate(EmailAuthProvider.getCredential(email, pass))
                            .addOnSuccessListener {
                                bDb.collection("config").document("admin").get()
                                    .addOnSuccessListener { a ->
                                        if (a.exists() && a.getString("uid") == user.uid) {
                                            busy = false
                                            msg = "لا يمكن حذف حساب الأدمن"
                                        } else {
                                            val b = bDb.batch()
                                            b.delete(bDb.collection("users").document(user.uid))
                                            b.delete(bDb.collection("doctors").document(user.uid))
                                            b.commit().addOnSuccessListener {
                                                user.delete().addOnSuccessListener {
                                                    busy = false
                                                    open = false
                                                    onDeleted()
                                                }.addOnFailureListener {
                                                    busy = false
                                                    msg = "تعذر حذف الحساب. سجّل خروجًا ثم دخولًا وحاول مرة أخرى"
                                                }
                                            }.addOnFailureListener {
                                                busy = false
                                                msg = "تعذر حذف البيانات، تحقق من الإنترنت"
                                            }
                                        }
                                    }
                                    .addOnFailureListener {
                                        busy = false
                                        msg = "تعذر الاتصال، تحقق من الإنترنت"
                                    }
                            }
                            .addOnFailureListener {
                                busy = false
                                msg = "كلمة السر غير صحيحة"
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

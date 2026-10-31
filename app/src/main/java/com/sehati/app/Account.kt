
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
        onClick = {
            open = true
            msg = ""
            pass = ""
        },
        modifier = Modifier.fillMaxWidth().height(52.dp)
    ) {
        Text("حذف حسابي", color = BDanger)
    }

    if (open) {
        AlertDialog(
            onDismissRequest = { if (!busy) open = false },
            title = { Text("حذف الحساب نهائيًا") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "حذف الحساب غير متاح مؤقتًا حتى نضمن حذف جميع البيانات المرتبطة به بأمان. تواصل مع الدعم لإتمام الطلب."
                    )
                    if (msg.isNotEmpty()) Text(msg, color = BDanger)
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        open = false
                        msg = ""
                    }
                ) {
                    Text("حسنًا")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !busy,
                    onClick = { open = false }
                ) {
                    Text("إلغاء")
                }
            }
        )
    }
}

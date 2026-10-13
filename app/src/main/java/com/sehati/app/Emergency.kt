package com.sehati.app

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun EmergencyScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var items by remember { mutableStateOf(listOf<Map<String, Any?>>()) }
    var loaded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        bDb.collection("doctors").whereEqualTo("emergency", true).limit(50).get()
            .addOnSuccessListener { s ->
                items = s.documents.map { it.data.orEmpty() + ("id" to it.id) }
                loaded = true
            }
            .addOnFailureListener {
                error = "تعذر تحميل أماكن الطوارئ، تحقق من الإنترنت"
                loaded = true
            }
    }

    fun dial(num: String) {
        try {
            ctx.startActivity(
                Intent(Intent.ACTION_DIAL, Uri.parse("tel:$num")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) { msg = "تعذر فتح الاتصال" }
    }

    LazyColumn(
        Modifier.fillMaxSize().background(BBg).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        item { TextButton(onClick = onBack) { Text("رجوع", color = BRed) } }
        item { Text("الطوارئ", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = BDanger) }
        item {
            Text(
                "التطبيق ليس بديلًا عن الإسعاف. في الحالات الخطرة اتصل بالإسعاف أو توجّه لأقرب مستشفى فورًا.",
                color = BDanger,
                modifier = Modifier.fillMaxWidth()
                    .background(Color(0xFF2A1215), RoundedCornerShape(12.dp))
                    .border(1.dp, BDanger, RoundedCornerShape(12.dp)).padding(12.dp)
            )
        }
        item {
            Button(
                onClick = {
                    try {
                        val u = Uri.parse("https://www.google.com/maps/search/" + Uri.encode("مستشفى طوارئ"))
                        ctx.startActivity(Intent(Intent.ACTION_VIEW, u).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (e: Exception) { msg = "تعذر فتح الخرائط على هذا الجهاز" }
                },
                colors = ButtonDefaults.buttonColors(containerColor = BDanger, contentColor = Color.White),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) { Text("أقرب مستشفى (خرائط جوجل)", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
        }
        if (msg.isNotEmpty()) item { Text(msg, color = BDanger) }
        item { Text("أطباء وعيادات متاحون للطوارئ", color = BMuted, fontSize = 16.sp) }
        if (error.isNotEmpty()) item { Text(error, color = BDanger) }
        if (loaded && items.isEmpty() && error.isEmpty()) {
            item { Text("لا يوجد أطباء مسجلون للطوارئ حاليًا. استخدم زر أقرب مستشفى.", color = BMuted) }
        }
        items(items, key = { it["id"] as String }) { d ->
            val emPhone = d["emergencyPhone"]?.toString().orEmpty()
            val phone = if (emPhone.isNotBlank()) emPhone else d["phone"]?.toString().orEmpty()
            Column(
                Modifier.fillMaxWidth().background(BCard, RoundedCornerShape(14.dp))
                    .border(1.dp, BLine, RoundedCornerShape(14.dp)).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    DoctorAvatar(d["photo"]?.toString(), "${d["name"] ?: ""}", 56.dp)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("${d["name"]}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("${d["specialty"] ?: ""}", color = BRed)
                        Text("${d["clinicName"] ?: ""}  ${d["city"] ?: ""}", color = BMuted)
                    }
                }
                val addr = d["address"]?.toString().orEmpty()
                if (addr.isNotBlank()) Text(addr, color = BMuted)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (phone.isNotBlank()) {
                        Button(
                            onClick = { dial(phone) },
                            colors = ButtonDefaults.buttonColors(containerColor = BDanger, contentColor = Color.White)
                        ) { Text("اتصال") }
                    }
                    OutlinedButton(onClick = { msg = openRoute(ctx, d) ?: "" }) {
                        Text("الطريق", color = Color.White)
                    }
                }
            }
        }
    }
}

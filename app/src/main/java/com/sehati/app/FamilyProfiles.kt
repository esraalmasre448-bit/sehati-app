package com.sehati.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.firestore.FieldValue

private data class FamilyMember(val id: String, val name: String, val relation: String)

@Composable
fun FamilyProfilesScreen(onBack: () -> Unit) {
    val uid = bAuth.currentUser?.uid.orEmpty()
    var name by remember { mutableStateOf("") }
    var relation by remember { mutableStateOf("") }
    var members by remember { mutableStateOf<List<FamilyMember>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var info by remember { mutableStateOf("") }

    DisposableEffect(uid) {
        if (uid.isBlank()) {
            loaded = true
            onDispose { }
        } else {
            val registration = bDb.collection("familyProfiles").document(uid).collection("members")
                .addSnapshotListener { snapshot, exception ->
                    if (exception != null) error = "تعذر تحميل أفراد العائلة."
                    else {
                        members = snapshot?.documents.orEmpty().map {
                            FamilyMember(it.id, it.getString("name").orEmpty(), it.getString("relation").orEmpty())
                        }.sortedBy { it.name }
                        error = ""
                    }
                    loaded = true
                }
            onDispose { registration.remove() }
        }
    }

    Column(
        Modifier.fillMaxSize().background(Color(0xCC0A0C11)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "رجوع", tint = Color.White) }
            Icon(Icons.Filled.FamilyRestroom, null, tint = Color(0xFFE5171F))
            Spacer(Modifier.width(8.dp))
            Text("ملفات العائلة", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
        }
        Text(
            "أضف أسماء أفراد عائلتك لتختار الشخص عند طلب موعد. الحجز يبقى تحت حسابك، ويظهر للطبيب اسم الشخص الذي اخترته.",
            color = Color(0xFFB6C6DA), fontSize = 13.sp
        )
        Column(
            Modifier.fillMaxWidth().background(Color(0xEE14171D), RoundedCornerShape(16.dp))
                .border(1.dp, Color(0xFF30343D), RoundedCornerShape(16.dp)).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            OutlinedTextField(
                value = name, onValueChange = { name = it.take(100); error = "" },
                modifier = Modifier.fillMaxWidth(), label = { Text("اسم فرد العائلة") },
                singleLine = true, enabled = !busy
            )
            OutlinedTextField(
                value = relation, onValueChange = { relation = it.take(40); error = "" },
                modifier = Modifier.fillMaxWidth(), label = { Text("صلة القرابة (مثال: الأم)") },
                singleLine = true, enabled = !busy
            )
            Button(
                onClick = {
                    val n = name.trim()
                    val r = relation.trim()
                    if (n.isBlank() || r.isBlank()) error = "اكتب الاسم وصلة القرابة."
                    else {
                        busy = true
                        bDb.collection("familyProfiles").document(uid).collection("members").document()
                            .set(mapOf(
                                "name" to n,
                                "relation" to r,
                                "createdAt" to FieldValue.serverTimestamp()
                            )).addOnSuccessListener {
                                name = ""; relation = ""; busy = false; info = "تمت إضافة فرد العائلة."
                            }.addOnFailureListener {
                                busy = false; error = "لم يتم الحفظ. تحقق من صلاحيات Firestore."
                            }
                    }
                },
                enabled = !busy && uid.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text("إضافة فرد العائلة")
            }
        }
        if (error.isNotBlank()) Text(error, color = Color(0xFFFF6674), fontSize = 13.sp)
        if (info.isNotBlank()) Text(info, color = Color(0xFF39D6A3), fontSize = 13.sp)
        Text("الأفراد المسجلون", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        when {
            !loaded -> CircularProgressIndicator(color = Color(0xFFE5171F))
            members.isEmpty() -> Text("لم تضف أفراداً بعد.", color = Color(0xFFB6C6DA))
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(members, key = { it.id }) { member ->
                    Row(
                        Modifier.fillMaxWidth().background(Color(0xEE14171D), RoundedCornerShape(12.dp))
                            .border(1.dp, Color(0xFF30343D), RoundedCornerShape(12.dp)).padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(member.name, color = Color.White, fontWeight = FontWeight.Bold)
                            Text(member.relation, color = Color(0xFFB6C6DA), fontSize = 12.sp)
                        }
                        TextButton(onClick = {
                            bDb.collection("familyProfiles").document(uid).collection("members").document(member.id)
                                .delete().addOnFailureListener { error = "تعذر حذف الملف." }
                        }) { Text("حذف", color = Color(0xFFFF6674)) }
                    }
                }
            }
        }
    }
}

package com.sehati.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.firestore.FieldValue

private data class ClinicBranch(
    val id: String, val name: String, val city: String, val address: String,
    val mapLink: String, val active: Boolean
)

@Composable
fun ClinicBranchesScreen(onBack: () -> Unit) {
    val uid = bAuth.currentUser?.uid.orEmpty()
    var name by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var mapLink by remember { mutableStateOf("") }
    var branches by remember { mutableStateOf<List<ClinicBranch>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }

    DisposableEffect(uid) {
        if (uid.isBlank()) return@DisposableEffect onDispose { }
        val reg = bDb.collection("doctorBranches").document(uid).collection("items")
            .addSnapshotListener { snapshot, error ->
                if (error != null) message = "تعذر تحميل الفروع. تحقق من صلاحيات Firestore."
                else {
                    branches = snapshot?.documents.orEmpty().map { d ->
                        ClinicBranch(
                            d.id, d.getString("name").orEmpty(), d.getString("city").orEmpty(),
                            d.getString("address").orEmpty(), d.getString("mapLink").orEmpty(),
                            d.getBoolean("active") != false
                        )
                    }.sortedBy { it.name }
                    loaded = true
                }
            }
        onDispose { reg.remove() }
    }

    LazyColumn(
        Modifier.fillMaxSize().background(BBg).padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "رجوع", tint = Color.White) }
                Icon(Icons.Filled.AddLocationAlt, null, tint = BRed)
                Spacer(Modifier.width(8.dp))
                Text("فروع العيادة ومواقعها", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        }
        item {
            Text("أضف كل فرع بمعلوماته وموقعه. هذه البيانات لا تظهر للمرضى إلا إذا كان حساب الطبيب معتمداً والفرع مفعّلاً.", color = BMuted, fontSize = 12.sp)
        }
        item {
            Column(
                Modifier.fillMaxWidth().background(BCard, RoundedCornerShape(16.dp))
                    .border(1.dp, BLine, RoundedCornerShape(16.dp)).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(name, { name = it.take(120) }, modifier = Modifier.fillMaxWidth(), label = { Text("اسم الفرع") }, singleLine = true)
                OutlinedTextField(city, { city = it.take(100) }, modifier = Modifier.fillMaxWidth(), label = { Text("المدينة") }, singleLine = true)
                OutlinedTextField(address, { address = it.take(240) }, modifier = Modifier.fillMaxWidth(), label = { Text("العنوان") }, singleLine = true)
                OutlinedTextField(mapLink, { mapLink = it.take(2048) }, modifier = Modifier.fillMaxWidth(), label = { Text("رابط خرائط Google أو إحداثيات") }, singleLine = true)
                Button(
                    enabled = !busy && name.isNotBlank() && city.isNotBlank() && (mapLink.isBlank() || parseLatLng(mapLink) != null || isMapsLink(mapLink)),
                    onClick = {
                        busy = true
                        val ref = bDb.collection("doctorBranches").document(uid).collection("items").document()
                        val ll = parseLatLng(mapLink)
                        val data = mutableMapOf<String, Any>(
                            "name" to name.trim(), "city" to city.trim(), "address" to address.trim(),
                            "active" to true, "updatedAt" to FieldValue.serverTimestamp()
                        )
                        if (ll != null) { data["lat"] = ll.first; data["lng"] = ll.second }
                        else if (mapLink.isNotBlank()) data["mapLink"] = mapLink.trim()
                        ref.set(data).addOnSuccessListener {
                            busy = false; name = ""; city = ""; address = ""; mapLink = ""
                            message = "تم حفظ الفرع."
                        }.addOnFailureListener {
                            busy = false; message = "تعذر حفظ الفرع. تحقق من اعتماد الحساب واشتراكه."
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (busy) "جارٍ الحفظ..." else "إضافة الفرع") }
            }
        }
        if (message.isNotBlank()) item { Text(message, color = if (message.startsWith("تم")) BGreen else BDanger, fontSize = 13.sp) }
        item { Text("الفروع المسجلة", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp) }
        if (!loaded) item { CircularProgressIndicator(color = BRed) }
        if (loaded && branches.isEmpty()) item { Text("لم تتم إضافة فروع بعد.", color = BMuted) }
        items(branches, key = { it.id }) { branch ->
            Column(
                Modifier.fillMaxWidth().background(BCard, RoundedCornerShape(14.dp))
                    .border(1.dp, BLine, RoundedCornerShape(14.dp)).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(branch.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(listOf(branch.address, branch.city).filter { it.isNotBlank() }.joinToString("، "), color = BMuted)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (branch.active) "مفعّل" else "متوقف", color = if (branch.active) BGreen else BMuted, modifier = Modifier.weight(1f))
                    Switch(checked = branch.active, onCheckedChange = { active ->
                        bDb.collection("doctorBranches").document(uid).collection("items").document(branch.id)
                            .update(mapOf("active" to active, "updatedAt" to FieldValue.serverTimestamp()))
                            .addOnFailureListener { message = "تعذر تحديث حالة الفرع." }
                    })
                    IconButton(onClick = {
                        bDb.collection("doctorBranches").document(uid).collection("items").document(branch.id)
                            .delete().addOnFailureListener { message = "تعذر حذف الفرع." }
                    }) { Icon(Icons.Filled.DeleteOutline, "حذف الفرع", tint = BDanger) }
                }
            }
        }
    }
}

package com.sehati.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

private val DBg = Color(0xFF0A0C11)
private val DCard = Color(0xFF14171D)
private val DLine = Color(0xFF2A2F3A)
private val DRed = Color(0xFFE5171F)
private val DMuted = Color(0xFF9AA0AB)

private val fAuth get() = FirebaseAuth.getInstance()
private val fDb get() = FirebaseFirestore.getInstance()

@Composable
fun DoctorProfileScreen(onBack: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var specialty by remember { mutableStateOf("") }
    var clinicName by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var hours by remember { mutableStateOf("") }
    var fee by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }
    var ok by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val uid = fAuth.currentUser?.uid ?: ""

    LaunchedEffect(uid) {
        if (uid.isNotEmpty()) {
            fDb.collection("doctors").document(uid).get().addOnSuccessListener { d ->
                if (d.exists()) {
                    name = d.getString("name") ?: ""
                    specialty = d.getString("specialty") ?: ""
                    clinicName = d.getString("clinicName") ?: ""
                    city = d.getString("city") ?: ""
                    address = d.getString("address") ?: ""
                    phone = d.getString("phone") ?: ""
                    hours = d.getString("hours") ?: ""
                    fee = d.getString("fee") ?: ""
                } else {
                    fDb.collection("users").document(uid).get().addOnSuccessListener { u ->
                        name = u.getString("name") ?: ""
                        phone = u.getString("phone") ?: ""
                    }
                }
            }
        }
    }

    Column(
        Modifier.fillMaxSize().background(DBg).padding(20.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TextButton(onClick = onBack) { Text("رجوع", color = DRed) }
        Text("ملف الطبيب / العيادة", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Field(name, { name = it }, "الاسم")
        Field(specialty, { specialty = it }, "الاختصاص (مثل: أسنان، أطفال)")
        Field(clinicName, { clinicName = it }, "اسم العيادة")
        Field(city, { city = it }, "المدينة")
        Field(address, { address = it }, "العنوان")
        Field(phone, { phone = it }, "رقم التواصل", keyboard = KeyboardType.Phone)
        Field(hours, { hours = it }, "ساعات الدوام (مثل: 9 صباحًا - 5 مساءً)")
        Field(fee, { fee = it }, "سعر الكشفية", keyboard = KeyboardType.Number)
        if (msg.isNotEmpty()) Text(msg, color = if (ok) Color(0xFF3DDC84) else DRed)
        RedButton("حفظ الملف", enabled = !busy) {
            if (name.isBlank() || specialty.isBlank() || city.isBlank()) {
                ok = false
                msg = "الاسم والاختصاص والمدينة مطلوبة"
                return@RedButton
            }
            busy = true
            msg = ""
            fDb.collection("doctors").document(uid).set(
                mapOf(
                    "uid" to uid, "name" to name.trim(), "specialty" to specialty.trim(),
                    "clinicName" to clinicName.trim(), "city" to city.trim(),
                    "address" to address.trim(), "phone" to phone.trim(),
                    "hours" to hours.trim(), "fee" to fee.trim(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            ).addOnSuccessListener {
                busy = false; ok = true; msg = "تم حفظ الملف"
            }.addOnFailureListener {
                busy = false; ok = false
                msg = "تعذر الحفظ، تحقق من الإنترنت وحاول مرة أخرى"
            }
        }
    }
}

@Composable
fun DoctorSearchScreen(onBack: () -> Unit, onOpen: (Map<String, Any?>) -> Unit) {
    var items by remember { mutableStateOf(listOf<Map<String, Any?>>()) }
    var query by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }

    DisposableEffect(Unit) {
        val reg = fDb.collection("doctors").limit(50).addSnapshotListener { snap, e ->
            if (e != null) error = "تعذر تحميل الأطباء، تحقق من الإنترنت"
            else {
                error = ""
                items = snap?.documents?.map { it.data.orEmpty() + ("id" to it.id) }.orEmpty()
            }
        }
        onDispose { reg.remove() }
    }

    val shown = items.filter {
        query.isBlank() ||
            "${it["name"]} ${it["specialty"]} ${it["clinicName"]} ${it["city"]}".contains(query, true)
    }

    Column(Modifier.fillMaxSize().background(DBg).padding(16.dp)) {
        TextButton(onClick = onBack) { Text("رجوع", color = DRed) }
        Text("ابحث عن طبيب أو عيادة", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(Modifier.height(8.dp))
        Field(query, { query = it }, "اسم، اختصاص، أو مدينة")
        if (error.isNotEmpty()) Text(error, color = DRed)
        if (shown.isEmpty() && error.isEmpty()) {
            Text("لا توجد نتائج", color = DMuted, modifier = Modifier.padding(16.dp))
        }
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            items(shown, key = { it["id"] as String }) { d ->
                Column(
                    Modifier.fillMaxWidth()
                        .background(DCard, RoundedCornerShape(14.dp))
                        .border(1.dp, DLine, RoundedCornerShape(14.dp))
                        .clickable { onOpen(d) }
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("${d["name"]}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text("${d["specialty"]}", color = DRed)
                    val c = "${d["clinicName"] ?: ""}"
                    if (c.isNotBlank()) Text(c, color = Color.White)
                    Text("${d["city"] ?: ""}", color = DMuted)
                }
            }
        }
    }
}

@Composable
fun DoctorDetailScreen(d: Map<String, Any?>, onBack: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(DBg).padding(20.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TextButton(onClick = onBack) { Text("رجوع", color = DRed) }
        Text("${d["name"]}", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text("${d["specialty"]}", fontSize = 16.sp, color = DRed)
        InfoRow("العيادة", d["clinicName"])
        InfoRow("المدينة", d["city"])
        InfoRow("العنوان", d["address"])
        InfoRow("رقم التواصل", d["phone"])
        InfoRow("ساعات الدوام", d["hours"])
        InfoRow("سعر الكشفية", d["fee"])
    }
}

@Composable
private fun InfoRow(label: String, value: Any?) {
    val v = value?.toString().orEmpty()
    if (v.isNotBlank()) {
        Column(
            Modifier.fillMaxWidth()
                .background(DCard, RoundedCornerShape(12.dp))
                .border(1.dp, DLine, RoundedCornerShape(12.dp))
                .padding(12.dp)
        ) {
            Text(label, color = DMuted, fontSize = 13.sp)
            Text(v, color = Color.White, fontSize = 16.sp)
        }
    }
}

package com.sehati.app

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
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
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.delay
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

private val DBg = Color(0xFF081513)
private val DCard = Color(0xFF10231F)
private val DLine = Color(0xFF1F3B35)
private val DRed = Color(0xFF38D0B4)
private val DDanger = Color(0xFFFF5A5F)
private val DOnAccent = Color(0xFF06201B)
private val DMuted = Color(0xFF8FA9A2)

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
    var photo by remember { mutableStateOf("") }
    var emergency by remember { mutableStateOf(false) }
    var emergencyPhone by remember { mutableStateOf("") }
    val ctx = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            val r = compressPhoto(ctx, uri)
            if (r == null) { ok = false; msg = "تعذر استخدام هذه الصورة، اختر صورة أخرى" }
            else { photo = r; ok = true; msg = "تم اختيار الصورة. اضغط حفظ الملف" }
        }
    }
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
                    photo = d.getString("photo") ?: ""
                    emergency = d.getBoolean("emergency") ?: false
                    emergencyPhone = d.getString("emergencyPhone") ?: ""
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
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            DoctorAvatar(photo, name, 110.dp)
        }
        OutlinedButton(
            onClick = { picker.launch("image/*") },
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) { Text(if (photo.isBlank()) "إضافة صورة" else "تغيير الصورة", color = Color.White) }
        Field(name, { name = it }, "الاسم")
        Field(specialty, { specialty = it }, "الاختصاص (مثل: أسنان، أطفال)")
        Field(clinicName, { clinicName = it }, "اسم العيادة")
        Field(city, { city = it }, "المدينة")
        Field(address, { address = it }, "العنوان")
        Field(phone, { phone = it }, "رقم التواصل", keyboard = KeyboardType.Phone)
        Field(hours, { hours = it }, "ساعات الدوام (مثل: 9 صباحًا - 5 مساءً)")
        Field(fee, { fee = it }, "سعر الكشفية", keyboard = KeyboardType.Number)
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("متاح لحالات الطوارئ", color = Color.White, fontSize = 16.sp)
            Switch(checked = emergency, onCheckedChange = { emergency = it })
        }
        if (emergency) {
            Field(emergencyPhone, { emergencyPhone = it }, "رقم الطوارئ (غير رقمك المعتاد)", keyboard = KeyboardType.Phone)
        }
        if (msg.isNotEmpty()) Text(msg, color = if (ok) Color(0xFF3DDC84) else DDanger)
        RedButton("حفظ الملف", enabled = !busy) {
            if (name.isBlank() || specialty.isBlank() || city.isBlank()) {
                ok = false
                msg = "الاسم والاختصاص والمدينة مطلوبة"
                return@RedButton
            }
            if (emergency && emergencyPhone.isBlank()) {
                ok = false
                msg = "أدخل رقم الطوارئ"
                return@RedButton
            }
            busy = true
            msg = ""
            fDb.collection("doctors").document(uid).set(
                mapOf(
                    "uid" to uid, "name" to name.trim(), "specialty" to specialty.trim(),
                    "clinicName" to clinicName.trim(), "city" to city.trim(),
                    "address" to address.trim(), "phone" to phone.trim(),
                    "hours" to hours.trim(), "fee" to fee.trim(), "photo" to photo,
                    "emergency" to emergency, "emergencyPhone" to emergencyPhone.trim(),
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
fun DoctorSearchScreen(onBack: () -> Unit, onOpen: (Map<String, Any?>) -> Unit, initial: String = "") {
    var items by remember { mutableStateOf(listOf<Map<String, Any?>>()) }
    var last by remember { mutableStateOf<DocumentSnapshot?>(null) }
    var more by remember { mutableStateOf(true) }
    var loading by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf(initial) }
    var error by remember { mutableStateOf("") }
    var found by remember { mutableStateOf<Map<String, Map<String, Any?>>>(emptyMap()) }
    var searching by remember { mutableStateOf(false) }

    fun loadPage() {
        if (loading || !more) return
        loading = true
        var q: Query = fDb.collection("doctors").orderBy("name").limit(40)
        val l = last
        if (l != null) q = q.startAfter(l)
        q.get().addOnSuccessListener { s ->
            val docs = s.documents
            items = items + docs.map { it.data.orEmpty() + ("id" to it.id) }
            if (docs.isNotEmpty()) last = docs.last()
            if (docs.size < 40) more = false
            error = ""
            loading = false
        }.addOnFailureListener {
            loading = false
            error = "تعذر تحميل الأطباء، تحقق من الإنترنت"
        }
    }

    LaunchedEffect(Unit) { loadPage() }

    LaunchedEffect(query) {
        val q = query.trim()
        if (q.isEmpty()) {
            found = emptyMap()
            searching = false
            return@LaunchedEffect
        }
        delay(450)
        searching = true
        found = emptyMap()
        var pending = 3
        fun finish() { pending--; if (pending <= 0) searching = false }
        fun add(s: QuerySnapshot) {
            if (q != query.trim()) return
            found = found + s.documents.associate { it.id to (it.data.orEmpty() + ("id" to it.id)) }
        }
        val col = fDb.collection("doctors")
        col.orderBy("name").startAt(q).endAt(q + "\uf8ff").limit(30).get()
            .addOnSuccessListener { add(it); finish() }.addOnFailureListener { finish() }
        col.whereEqualTo("city", q).limit(30).get()
            .addOnSuccessListener { add(it); finish() }.addOnFailureListener { finish() }
        col.whereEqualTo("specialty", q).limit(30).get()
            .addOnSuccessListener { add(it); finish() }.addOnFailureListener { finish() }
    }

    val qNow = query.trim()
    val shown = if (qNow.isEmpty()) items else {
        val local = items.filter {
            "${it["name"]} ${it["specialty"]} ${it["clinicName"]} ${it["city"]}".contains(qNow, true)
        }
        (found.values.toList() + local).distinctBy { it["id"] as String }
    }

    Column(Modifier.fillMaxSize().background(DBg).padding(16.dp)) {
        TextButton(onClick = onBack) { Text("رجوع", color = DRed) }
        Text("ابحث عن طبيب أو عيادة", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(Modifier.height(8.dp))
        Field(query, { query = it }, "اسم، اختصاص، أو مدينة")
        if (error.isNotEmpty()) Text(error, color = DDanger)
        if (searching) Text("جاري البحث...", color = DMuted, modifier = Modifier.padding(8.dp))
        if (shown.isEmpty() && error.isEmpty() && !searching && !loading) {
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
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        DoctorAvatar(d["photo"]?.toString(), "${d["name"] ?: ""}", 56.dp)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("${d["name"]}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Text("${d["specialty"]}", color = DRed)
                            val c = "${d["clinicName"] ?: ""}"
                            if (c.isNotBlank()) Text(c, color = Color.White)
                            Text("${d["city"] ?: ""}", color = DMuted)
                        }
                    }
                }
            }
            if (qNow.isEmpty() && more) {
                item {
                    OutlinedButton(
                        onClick = { loadPage() },
                        enabled = !loading,
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) { Text(if (loading) "جاري التحميل..." else "عرض المزيد", color = Color.White) }
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

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

private val DBg = Color(0xFF0B1220)
private val DCard = Color(0xFF131C2E)
private val DLine = Color(0xFF24304A)
private val DRed = Color(0xFFE5171F)
private val DDanger = Color(0xFFFF5A5F)
private val DOnAccent = Color(0xFFFFFFFF)
private val DMuted = Color(0xFF9AA7BD)

private val fAuth get() = FirebaseAuth.getInstance()
private val fDb get() = FirebaseFirestore.getInstance()

@Composable
private fun SectionTitle(text: String) {
    Text(text, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = DRed, modifier = Modifier.padding(top = 8.dp))
}

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
    var hospitalName by remember { mutableStateOf("") }
    var hospitalHours by remember { mutableStateOf("") }
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
                    val rawSp = d.getString("specialty") ?: ""
                    specialty = matchSpecialty(rawSp)?.name ?: rawSp
                    clinicName = d.getString("clinicName") ?: ""
                    city = d.getString("city") ?: ""
                    address = d.getString("address") ?: ""
                    phone = d.getString("phone") ?: ""
                    hours = d.getString("hours") ?: ""
                    fee = d.getString("fee") ?: ""
                    photo = d.getString("photo") ?: ""
                    emergency = d.getBoolean("emergency") ?: false
                    emergencyPhone = d.getString("emergencyPhone") ?: ""
                    hospitalName = d.getString("hospitalName") ?: ""
                    hospitalHours = d.getString("hospitalHours") ?: ""
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
        SectionTitle("معلوماتي")
        Field(name, { name = it }, "الاسم")
        SpecialtyPicker(specialty) { specialty = it }
        SectionTitle("عملي بالعيادة")
        Field(clinicName, { clinicName = it }, "اسم العيادة")
        Field(city, { city = it }, "المدينة")
        Field(address, { address = it }, "العنوان")
        Field(phone, { phone = it }, "رقم التواصل", keyboard = KeyboardType.Phone)
        Field(hours, { hours = it }, "أيام وساعات الدوام بالعيادة (مثل: السبت والأحد 5 - 9 مساءً)")
        Field(fee, { fee = it }, "سعر الكشفية", keyboard = KeyboardType.Number)
        SectionTitle("المشفى (اختياري)")
        Text("إذا كنت تداوم أيضاً بمشفى، اكتب اسمه ومواعيد دوامك فيه ليراها المرضى.", color = DMuted, fontSize = 13.sp)
        Field(hospitalName, { hospitalName = it }, "اسم المشفى")
        Field(hospitalHours, { hospitalHours = it }, "أيام وساعات الدوام بالمشفى (مثل: الإثنين والثلاثاء 8 - 2)")
        SectionTitle("الطوارئ")
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
            if (SPECIALTIES.none { it.name == specialty }) {
                ok = false
                msg = "اختر التخصص من القائمة"
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
                    "hospitalName" to hospitalName.trim(), "hospitalHours" to hospitalHours.trim(),
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
    var tab by remember { mutableIntStateOf(if (initial.startsWith("@clinics")) 2 else if (initial.startsWith("@doctors")) 1 else 0) }
    var query by remember { mutableStateOf(if (initial.startsWith("@")) "" else initial) }
    var error by remember { mutableStateOf("") }
    var found by remember { mutableStateOf<Map<String, Map<String, Any?>>>(emptyMap()) }
    var searching by remember { mutableStateOf(false) }
    var showFilter by remember { mutableStateOf(false) }
    var fCity by remember { mutableStateOf("") }
    var fSpec by remember { mutableStateOf("") }
    var fMax by remember { mutableStateOf("") }

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

    LaunchedEffect(query, fSpec, fCity) {
        val q = query.trim()
        val fs = fSpec
        val fc = fCity.trim()
        if (q.isEmpty() && fs.isEmpty() && fc.isEmpty()) {
            found = emptyMap()
            searching = false
            return@LaunchedEffect
        }
        delay(450)
        searching = true
        found = emptyMap()
        val jobs = mutableListOf<(() -> Unit) -> Unit>()
        val col = fDb.collection("doctors")
        fun cur() = q == query.trim() && fs == fSpec && fc == fCity.trim()
        fun add(s: QuerySnapshot) {
            if (!cur()) return
            found = found + s.documents.associate { it.id to (it.data.orEmpty() + ("id" to it.id)) }
        }
        if (q.isNotEmpty()) {
            jobs.add { done -> col.orderBy("name").startAt(q).endAt(q + "").limit(30).get()
                .addOnSuccessListener { add(it); done() }.addOnFailureListener { done() } }
            jobs.add { done -> col.whereEqualTo("city", q).limit(30).get()
                .addOnSuccessListener { add(it); done() }.addOnFailureListener { done() } }
            val sp = matchSpecialty(q)
            if (sp != null) {
                jobs.add { done -> col.whereIn("specialty", specVariants(sp)).limit(50).get()
                    .addOnSuccessListener { add(it); done() }.addOnFailureListener { done() } }
            } else {
                jobs.add { done -> col.whereEqualTo("specialty", q).limit(30).get()
                    .addOnSuccessListener { add(it); done() }.addOnFailureListener { done() } }
            }
        }
        if (fs.isNotEmpty()) {
            val sp = matchSpecialty(fs)
            if (sp != null) jobs.add { done -> col.whereIn("specialty", specVariants(sp)).limit(50).get()
                .addOnSuccessListener { add(it); done() }.addOnFailureListener { done() } }
        }
        if (fc.isNotEmpty()) {
            jobs.add { done -> col.whereEqualTo("city", fc).limit(50).get()
                .addOnSuccessListener { add(it); done() }.addOnFailureListener { done() } }
        }
        if (jobs.isEmpty()) { searching = false; return@LaunchedEffect }
        var pending = jobs.size
        val fin = { pending--; if (pending <= 0) searching = false }
        jobs.forEach { it(fin) }
    }

    val qNow = query.trim()
    val filtersOn = qNow.isNotEmpty() || fSpec.isNotEmpty() || fCity.isNotBlank()
    val qSpec = matchSpecialty(qNow)
    fun ok(d: Map<String, Any?>): Boolean {
        val text = "${d["name"]} ${d["specialty"]} ${d["clinicName"]} ${d["city"]}"
        val dSpec = matchSpecialty("${d["specialty"] ?: ""}")?.name
        val qOk = qNow.isEmpty() || text.contains(qNow, true) || (qSpec != null && dSpec == qSpec.name)
        val sOk = fSpec.isEmpty() || dSpec == fSpec
        val cOk = fCity.isBlank() || "${d["city"] ?: ""}".contains(fCity.trim(), true)
        val maxV = fMax.trim().toLongOrNull()
        val feeV = "${d["fee"] ?: ""}".trim().toLongOrNull()
        val fOk = maxV == null || (feeV != null && feeV <= maxV)
        val hasClinic = "${d["clinicName"] ?: ""}".isNotBlank()
        val tOk = tab == 0 || (tab == 1 && !hasClinic) || (tab == 2 && hasClinic)
        return qOk && sOk && cOk && fOk && tOk
    }
    val pool = if (filtersOn) (found.values.toList() + items).distinctBy { it["id"] as String } else items
    val shown = pool.filter { ok(it) }

    Column(Modifier.fillMaxSize().background(DBg).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = onBack) { Text("رجوع", color = DRed) }
            Text("ابحث عن طبيب أو عيادة", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
            TextButton(onClick = { showFilter = !showFilter }) { Text(if (showFilter) "إخفاء الفلتر" else "فلتر", color = DRed) }
        }
        Field(query, { query = it }, "اسم، اختصاص، أو مدينة")
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("الكل", "الأطباء", "العيادات").forEachIndexed { i, t ->
                Box(
                    Modifier.weight(1f).height(40.dp)
                        .background(if (tab == i) DRed else DCard, RoundedCornerShape(20.dp))
                        .border(1.dp, if (tab == i) DRed else DLine, RoundedCornerShape(20.dp))
                        .clickable { tab = i },
                    contentAlignment = Alignment.Center
                ) { Text(t, color = Color.White, fontWeight = FontWeight.Bold) }
            }
        }
        if (showFilter) {
            Column(
                Modifier.fillMaxWidth().padding(top = 8.dp)
                    .background(DCard, RoundedCornerShape(14.dp))
                    .border(1.dp, DLine, RoundedCornerShape(14.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Field(fCity, { fCity = it }, "المدينة")
                SpecialtyPicker(fSpec) { fSpec = it }
                if (fSpec.isNotEmpty()) TextButton(onClick = { fSpec = "" }) { Text("إلغاء فلتر التخصص", color = DRed) }
                Field(fMax, { fMax = it.filter { c -> c.isDigit() } }, "أقصى سعر للكشفية", keyboard = KeyboardType.Number)
                RedButton("تطبيق الفلتر") { showFilter = false }
                TextButton(onClick = { fCity = ""; fSpec = ""; fMax = "" }) { Text("مسح كل الفلاتر", color = DMuted) }
            }
        }
        if (error.isNotEmpty()) Text(error, color = DDanger)
        if (searching) Text("جاري البحث...", color = DMuted, modifier = Modifier.padding(8.dp))
        if (shown.isEmpty() && error.isEmpty() && !searching && !loading) {
            val spName = if (fSpec.isNotEmpty()) fSpec else qSpec?.name
            Text(
                if (spName != null) "لا يوجد حالياً أطباء مسجلون بتخصص $spName" else "لا توجد نتائج",
                color = DMuted, modifier = Modifier.padding(16.dp)
            )
        }
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            items(shown, key = { it["id"] as String }) { d ->
                Row(
                    Modifier.fillMaxWidth()
                        .background(DCard, RoundedCornerShape(14.dp))
                        .border(1.dp, DLine, RoundedCornerShape(14.dp))
                        .clickable { onOpen(d) }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    DoctorAvatar(d["photo"]?.toString(), "${d["name"] ?: ""}", 56.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("${d["name"]}", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("${d["specialty"]}", color = DRed)
                        val c = "${d["clinicName"] ?: ""}"
                        if (c.isNotBlank()) Text(c, color = Color.White)
                        Text("${d["city"] ?: ""}", color = DMuted)
                    }
                    val fee = "${d["fee"] ?: ""}"
                    if (fee.isNotBlank()) Text("كشفية: $fee", color = DMuted, fontSize = 12.sp)
                }
            }
            if (!filtersOn && more) {
                item {
                    RedButton(if (loading) "جاري التحميل..." else "عرض المزيد", enabled = !loading) { loadPage() }
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

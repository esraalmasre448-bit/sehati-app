package com.sehati.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import kotlinx.coroutines.delay
import java.util.Calendar

private fun searchError(e: Exception): String =
    when ((e as? FirebaseFirestoreException)?.code) {
        FirebaseFirestoreException.Code.PERMISSION_DENIED -> "لا توجد صلاحية لقراءة قائمة الأطباء. راجع قواعد Firestore."
        FirebaseFirestoreException.Code.FAILED_PRECONDITION -> "Firestore رفض الاستعلام بسبب فهرس مطلوب. راجع فهارس قاعدة البيانات."
        FirebaseFirestoreException.Code.UNAVAILABLE -> "خدمة Firestore غير متاحة حالياً. أعد المحاولة بعد قليل."
        FirebaseFirestoreException.Code.UNAUTHENTICATED -> "انتهت جلسة الدخول. سجّل الدخول من جديد."
        else -> "فشل تحميل الأطباء: ${e.localizedMessage ?: "خطأ غير معروف"}"
    }

/** شاشة البحث عن الأطباء بتصميم المرجع. المنطق نفسه: approved فقط، والفلترة محلياً بدون فهارس مركبة. */
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
    val currentUid = bAuth.currentUser?.uid.orEmpty()
    var favoriteIds by remember(currentUid) { mutableStateOf<Set<String>>(emptySet()) }

    DisposableEffect(currentUid) {
        if (currentUid.isBlank()) return@DisposableEffect onDispose { }
        val registration = bDb.collection("favorites").document(currentUid)
            .collection("doctors").addSnapshotListener { snapshot, error ->
                if (error == null) favoriteIds = snapshot?.documents?.map { it.id }?.toSet().orEmpty()
            }
        onDispose { registration.remove() }
    }

    fun loadPage() {
        if (loading || !more) return
        loading = true
        var q: Query = bDb.collection("doctors").whereEqualTo("approved", true).limit(40)
        val l = last
        if (l != null) q = q.startAfter(l)
        q.get().addOnSuccessListener { s ->
            val docs = s.documents
            items = (items + docs.map { it.data.orEmpty() + ("id" to it.id) })
                .distinctBy { it["id"] }
                .sortedBy { "${it["name"] ?: ""}".lowercase() }
            if (docs.isNotEmpty()) last = docs.last()
            if (docs.size < 40) more = false
            error = ""
            loading = false
        }.addOnFailureListener { e ->
            loading = false
            error = searchError(e)
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
        delay(350)
        searching = true
        error = ""
        bDb.collection("doctors").whereEqualTo("approved", true).limit(500).get()
            .addOnSuccessListener { snapshot ->
                if (q == query.trim() && fs == fSpec && fc == fCity.trim()) {
                    found = snapshot.documents.associate { doc ->
                        doc.id to (doc.data.orEmpty() + ("id" to doc.id))
                    }
                    searching = false
                }
            }
            .addOnFailureListener { e ->
                if (q == query.trim() && fs == fSpec && fc == fCity.trim()) {
                    searching = false
                    error = searchError(e)
                }
            }
    }

    val qNow = query.trim()
    val filtersOn = qNow.isNotEmpty() || fSpec.isNotEmpty() || fCity.isNotBlank()
    val qSpec = matchSpecialty(qNow)
    fun passes(d: Map<String, Any?>): Boolean {
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
        return d["approved"] == true && qOk && sOk && cOk && fOk && tOk
    }
    val pool = if (filtersOn) (found.values.toList() + items).distinctBy { it["id"] as String } else items
    val shown = pool.filter { passes(it) }

    Column(Modifier.fillMaxSize().background(BBg).padding(16.dp)) {
        // الشريط العلوي: في RTL زر الرجوع يظهر يمين العنوان
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("رجوع", color = BRed) }
            Text("البحث عن الأطباء", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth().height(52.dp)
                .background(BCard, RoundedCornerShape(14.dp))
                .border(1.dp, BLine, RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Filled.Search, null, tint = BMuted)
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) Text("ابحث عن طبيب أو تخصص...", color = BMuted)
                androidx.compose.foundation.text.BasicTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 16.sp),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(BRed),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        Spacer(Modifier.height(10.dp))

        // رقائق الفلاتر الثلاث كما في المرجع: المدينة / التخصص / السعر
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChipBox("المدينة", fCity.ifBlank { "الكل" }, fCity.isNotBlank(), Modifier.weight(1f)) { showFilter = !showFilter }
            FilterChipBox("التخصص", fSpec.ifBlank { "الكل" }, fSpec.isNotBlank(), Modifier.weight(1f)) { showFilter = !showFilter }
            FilterChipBox("السعر", if (fMax.isBlank()) "الكل" else "≤ $fMax", fMax.isNotBlank(), Modifier.weight(1f)) { showFilter = !showFilter }
        }
        if (showFilter) {
            Column(
                Modifier.fillMaxWidth().padding(top = 8.dp)
                    .background(BCard, RoundedCornerShape(14.dp))
                    .border(1.dp, BLine, RoundedCornerShape(14.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Field(fCity, { fCity = it }, "المدينة")
                SpecialtyPicker(fSpec) { fSpec = it }
                if (fSpec.isNotEmpty()) TextButton(onClick = { fSpec = "" }) { Text("إلغاء فلتر التخصص", color = BRed) }
                Field(fMax, { fMax = it.filter { c -> c.isDigit() } }, "أقصى سعر للكشفية", keyboard = KeyboardType.Number)
                RedButton("تطبيق الفلتر") { showFilter = false }
                TextButton(onClick = { fCity = ""; fSpec = ""; fMax = "" }) { Text("مسح كل الفلاتر", color = BMuted) }
            }
        }
        Spacer(Modifier.height(10.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("الكل", "الأطباء", "العيادات").forEachIndexed { i, t ->
                Box(
                    Modifier.weight(1f).height(38.dp)
                        .background(if (tab == i) BRed else BCard, RoundedCornerShape(20.dp))
                        .border(1.dp, if (tab == i) BRed else BLine, RoundedCornerShape(20.dp))
                        .clickable { tab = i },
                    contentAlignment = Alignment.Center
                ) { Text(t, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
            }
        }

        if (error.isNotEmpty()) Text(error, color = BDanger, modifier = Modifier.padding(top = 8.dp))
        if (searching) Text("جاري البحث...", color = BMuted, modifier = Modifier.padding(8.dp))
        if (shown.isEmpty() && error.isEmpty() && !searching && !loading) {
            val spName = if (fSpec.isNotEmpty()) fSpec else qSpec?.name
            Text(
                if (spName != null) "لا يوجد حالياً أطباء مسجلون بتخصص $spName" else "لا توجد نتائج",
                color = BMuted, modifier = Modifier.padding(16.dp)
            )
        }
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            items(shown, key = { it["id"] as String }) { d ->
                val doctorId = d["id"]?.toString().orEmpty()
                SearchDoctorCard(
                    d = d,
                    isFavorite = doctorId in favoriteIds,
                    onToggleFavorite = { saved ->
                        favoriteIds = if (saved) favoriteIds + doctorId else favoriteIds - doctorId
                    },
                    onClick = { onOpen(d) }
                )
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
private fun FilterChipBox(label: String, value: String, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.height(54.dp)
            .background(BCard, RoundedCornerShape(12.dp))
            .border(1.dp, if (active) BRed else BLine, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text(value, color = if (active) BRed else BMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}


private fun nextWorkShiftLabel(d: Map<String, Any?>): String? {
    val places = d["workLocations"] as? Map<*, *> ?: return null
    val candidates = listOf("clinic" to "العيادة", "hospital" to "المشفى").mapNotNull { (key, label) ->
        val p = places[key] as? Map<*, *> ?: return@mapNotNull null
        val name = p["name"]?.toString().orEmpty().ifBlank { label }
        val start = (p["startHour"] as? Number)?.toInt() ?: return@mapNotNull null
        val end = (p["endHour"] as? Number)?.toInt() ?: return@mapNotNull null
        val days = (p["workDays"] as? List<*>)?.mapNotNull { (it as? Number)?.toInt() }?.toSet().orEmpty()
        if (days.isEmpty()) return@mapNotNull null
        Triple(name, start * 60, Pair(end * 60, days))
    }
    if (candidates.isEmpty()) return null
    val now = Calendar.getInstance()
    val nowMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
    for (offset in 0..7) {
        val day = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, offset) }
        val dayOfWeek = day.get(Calendar.DAY_OF_WEEK)
        val possible = candidates.filter { dayOfWeek in it.third.second && (offset > 0 || it.second > nowMinutes) }
            .minByOrNull { it.second } ?: continue
        val dayName = WEEK_DAYS.firstOrNull { it.first == dayOfWeek }?.second ?: "القادم"
        val hour = possible.second / 60
        return "الدوام القادم: ${possible.first}، $dayName ${"%02d:00".format(hour)}"
    }
    return null
}

/** بطاقة نتيجة: الصورة (يمين في RTL)، الاسم والتخصص والمكان، وحالة التوفر إن كانت مسجلة. بدون تقييمات أو مسافات وهمية. */
@Composable
private fun SearchDoctorCard(
    d: Map<String, Any?>,
    isFavorite: Boolean,
    onToggleFavorite: (Boolean) -> Unit,
    onClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth()
            .background(BCard, RoundedCornerShape(16.dp))
            .border(1.dp, BLine, RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        DoctorAvatar(d["photo"]?.toString(), "${d["name"] ?: ""}", 64.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${d["name"] ?: ""}", modifier = Modifier.weight(1f),
                    fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                FavoriteDoctorButton(
                    doctorId = d["id"]?.toString().orEmpty(),
                    isFavorite = isFavorite,
                    onChanged = onToggleFavorite
                )
            }
            Text("${d["specialty"] ?: ""}", color = BRed, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val clinic = "${d["clinicName"] ?: ""}"
            val city = "${d["city"] ?: ""}"
            val place = listOf(clinic, city).filter { it.isNotBlank() }.joinToString(" - ")
            if (place.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Filled.LocationOn, null, tint = BMuted, modifier = Modifier.size(14.dp))
                    Text(place, color = BMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            val currentPlace = if (d["locationStatus"] == "AT_WORK") {
                d["currentWorkPlaceName"]?.toString().orEmpty()
                    .ifBlank { if (d["currentWorkLocationId"] == "hospital") "المشفى" else "العيادة" }
            } else ""
            if (currentPlace.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Filled.MyLocation, null, tint = BGreen, modifier = Modifier.size(14.dp))
                    Text("متواجد حالياً في $currentPlace", color = BGreen, fontSize = 12.sp)
                }
            }
            val fee = "${d["fee"] ?: ""}"
            if (fee.isNotBlank()) Text("كشفية: $fee", color = BMuted, fontSize = 12.sp)
        }
        val currentPlaceName = d["currentWorkPlaceName"]?.toString().orEmpty()
            .ifBlank { if (d["currentWorkLocationId"] == "hospital") "المشفى" else "العيادة" }
        when {
            d["locationStatus"] == "AT_WORK" -> StatusPill("في $currentPlaceName", BGreen)
            d["locationStatus"] == "OFFSITE" -> StatusPill(
                nextWorkShiftLabel(d) ?: "خارج موقعي العمل",
                BMuted
            )
            d["available"] == true -> StatusPill("متاح", BGreen)
            d["available"] == false -> StatusPill(
                nextWorkShiftLabel(d) ?: "غير متاح",
                BMuted
            )
        }
    }
}

@Composable
private fun StatusPill(text: String, color: Color) {
    Box(
        Modifier.background(color.copy(alpha = 0.18f), RoundedCornerShape(20.dp))
            .border(1.dp, color, RoundedCornerShape(20.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) { Text(text, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
}

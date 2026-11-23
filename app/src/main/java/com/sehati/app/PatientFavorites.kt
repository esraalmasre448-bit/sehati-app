package com.sehati.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.firestore.FieldValue
import android.widget.Toast

@Composable
fun FavoriteDoctorButton(
    doctorId: String,
    isFavorite: Boolean,
    onChanged: (Boolean) -> Unit
) {
    val context = LocalContext.current
    var busy by remember(doctorId) { mutableStateOf(false) }
    IconButton(
        enabled = !busy && doctorId.isNotBlank(),
        onClick = {
            val uid = bAuth.currentUser?.uid.orEmpty()
            if (uid.isBlank()) {
                Toast.makeText(context, "سجّل الدخول أولاً", Toast.LENGTH_SHORT).show()
                return@IconButton
            }
            busy = true
            val ref = bDb.collection("favorites").document(uid)
                .collection("doctors").document(doctorId)
            val task = if (isFavorite) ref.delete() else ref.set(
                mapOf("doctorId" to doctorId, "savedAt" to FieldValue.serverTimestamp())
            )
            task.addOnSuccessListener {
                busy = false
                onChanged(!isFavorite)
            }.addOnFailureListener {
                busy = false
                Toast.makeText(
                    context,
                    if (isFavorite) "تعذر إزالة الطبيب من المفضلة" else "تعذر حفظ الطبيب. تحقق من صلاحيات الحساب.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    ) {
        Icon(
            imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            contentDescription = if (isFavorite) "إزالة من المفضلة" else "إضافة إلى المفضلة",
            tint = if (isFavorite) Color(0xFFE5171F) else Color(0xFFB6C6DA)
        )
    }
}

@Composable
fun PatientFavoritesScreen(
    onBack: () -> Unit,
    onOpen: (Map<String, Any?>) -> Unit
) {
    val uid = bAuth.currentUser?.uid.orEmpty()
    var doctors by remember(uid) { mutableStateOf<List<Map<String, Any?>>>(emptyList()) }
    var loading by remember(uid) { mutableStateOf(true) }
    var failed by remember(uid) { mutableStateOf(false) }

    DisposableEffect(uid) {
        if (uid.isBlank()) {
            loading = false
            onDispose { }
        } else {
            var generation = 0
            val registration = bDb.collection("favorites").document(uid)
                .collection("doctors").addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null) {
                        failed = true
                        loading = false
                    } else {
                        failed = false
                        val request = ++generation
                        val ids = snapshot.documents.map { it.id }
                        doctors = emptyList()
                        if (ids.isEmpty()) {
                            loading = false
                        } else {
                            loading = true
                            var remaining = ids.size
                            ids.forEach { doctorId ->
                                bDb.collection("doctors").document(doctorId).get()
                                    .addOnSuccessListener { doctor ->
                                        if (request == generation) {
                                            if (doctor.exists() && doctor.getBoolean("approved") == true) {
                                                doctors = (doctors.filterNot { it["id"] == doctorId } +
                                                    (doctor.data.orEmpty() + ("id" to doctor.id)))
                                                    .sortedBy { it["name"]?.toString().orEmpty() }
                                            }
                                            remaining -= 1
                                            if (remaining == 0) loading = false
                                        }
                                    }
                                    .addOnFailureListener {
                                        if (request == generation) {
                                            remaining -= 1
                                            if (remaining == 0) loading = false
                                            failed = doctors.isEmpty()
                                        }
                                    }
                            }
                        }
                    }
                }
            onDispose {
                generation += 1
                registration.remove()
            }
        }
    }

    Column(
        Modifier.fillMaxSize().background(BBg).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, "رجوع", tint = Color.White)
            }
            Text("أطبائي المفضلون", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
        }
        Text("احفظ الأطباء الذين تود الرجوع إليهم بسرعة.", color = BMuted, fontSize = 13.sp)
        when {
            loading -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = BRed)
            }
            failed && doctors.isEmpty() -> Text("تعذر تحميل المفضلة. تحقق من الاتصال والصلاحيات ثم أعد المحاولة.", color = BDanger)
            doctors.isEmpty() -> Text("لم تضف أي طبيب إلى المفضلة بعد. يمكنك الضغط على رمز القلب في نتائج البحث.", color = BMuted)
            else -> LazyColumn(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(doctors, key = { it["id"].toString() }) { doctor ->
                    Row(
                        Modifier.fillMaxWidth()
                            .background(BCard, RoundedCornerShape(15.dp))
                            .border(1.dp, BLine, RoundedCornerShape(15.dp))
                            .clickable { onOpen(doctor) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        DoctorAvatar(doctor["photo"]?.toString(), doctor["name"]?.toString().orEmpty(), 58.dp)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                doctor["name"]?.toString().orEmpty(),
                                color = Color.White, fontWeight = FontWeight.Bold,
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                            Text(doctor["specialty"]?.toString().orEmpty(), color = BRed, fontSize = 12.sp)
                            val place = listOf(
                                doctor["clinicName"]?.toString().orEmpty(),
                                doctor["city"]?.toString().orEmpty()
                            ).filter { it.isNotBlank() }.joinToString(" - ")
                            if (place.isNotBlank()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.LocationOn, null, tint = BMuted, modifier = Modifier.size(13.dp))
                                    Text(place, color = BMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                        FavoriteDoctorButton(
                            doctorId = doctor["id"]?.toString().orEmpty(),
                            isFavorite = true,
                            onChanged = { }
                        )
                    }
                }
            }
        }
    }
}

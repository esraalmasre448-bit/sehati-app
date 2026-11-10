package com.sehati.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import java.util.Date

private val DTeal = Color(0xFF29BDF4)

/** الصفحة الرئيسية للطبيب. الصفحات الفرعية تُفتح داخلها بحالة محلية. */
@Composable
fun DoctorHome(name: String, unread: Int, onGo: (String) -> Unit, onTab: (Int) -> Unit) {
    val context = LocalContext.current
    val doctorUid = bAuth.currentUser?.uid.orEmpty()
    LaunchedEffect(doctorUid) {
        if (doctorUid.isNotBlank()) DoctorWorkplaceGeofencing.sync(context, doctorUid)
    }
    var page by remember { mutableStateOf("home") }
    BackHandler(enabled = page != "home") { page = "home" }
    when (page) {
        "patients" -> DoctorPatientsScreen(onBack = { page = "home" })
        else -> DoctorHomeContent(name, unread, onGo, onTab, onPatients = { page = "patients" })
    }
}

@Composable
private fun DoctorHomeContent(
    name: String, unread: Int,
    onGo: (String) -> Unit, onTab: (Int) -> Unit, onPatients: () -> Unit
) {
    val uid = bAuth.currentUser?.uid ?: ""
    val today = todayKey()
    var docName by remember { mutableStateOf("") }
    var specialty by remember { mutableStateOf("") }
    var clinic by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }
    var photo by remember { mutableStateOf<String?>(null) }
    var todayN by remember { mutableStateOf<Int?>(null) }
    var newN by remember { mutableStateOf<Int?>(null) }
    var waiting by remember { mutableStateOf<Long?>(null) }
    var failed by remember { mutableStateOf(false) }
    var available by remember { mutableStateOf<Boolean?>(null) }
    var toggleMsg by remember { mutableStateOf("") }
    var hasProfile by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(uid) {
        if (uid.isEmpty()) return@LaunchedEffect
        bDb.collection("doctors").document(uid).get().addOnSuccessListener { d ->
            docName = d.getString("name") ?: ""
            specialty = d.getString("specialty") ?: ""
            clinic = d.getString("clinicName") ?: ""
            city = d.getString("city") ?: ""
            photo = d.getString("photo")
            available = d.getBoolean("available")
            // الملف مكتمل فقط إذا كانت الحقول التي يطلبها نموذج الحفظ موجودة وصالحة
            hasProfile = d.exists() &&
                !(d.getString("name") ?: "").isBlank() &&
                !(d.getString("city") ?: "").isBlank() &&
                SPECIALTIES.any { it.name == (d.getString("specialty") ?: "") }
        }
    }

    DisposableEffect(uid, today) {
        val r1 = bDb.collection("appointments").whereEqualTo("doctorId", uid).limit(300)
            .addSnapshotListener { s, e ->
                if (e != null || s == null) {
                    failed = true
                } else {
                    failed = false
                    val dayAgo = Date(System.currentTimeMillis() - 24L * 3600 * 1000)
                    val list = s.documents.map { it.data.orEmpty() }
                    todayN = list.count { it["date"] == today && it["status"] != "CANCELLED" }
                    newN = list.count {
                        it["status"] == "PENDING" && (it["createdAt"] as? Timestamp)?.toDate()?.after(dayAgo) == true
                    }
                }
            }
        // الرقم الحالي = آخر رقم نُودي، آخر رقم صدر = آخر رقم أُعطي. المنتظرون = الفرق بينهما.
        val r2 = bDb.collection("queues").document("${uid}_$today").addSnapshotListener { s, _ ->
            val cur = s?.getLong("current") ?: 0L
            val lst = s?.getLong("last") ?: 0L
            waiting = (lst - cur).coerceAtLeast(0L)
        }
        onDispose { r1.remove(); r2.remove() }
    }

    val shownName = docName.ifBlank { name }
    val countText: (Long?) -> String = { n -> if (failed) "—" else n?.toString() ?: "..." }
    val isOn = available == true
    val statusText = when (available) {
        true -> "متاح لاستقبال المرضى"
        false -> "غير متاح حالياً"
        null -> "لم تحددي حالتك بعد"
    }

    // الخلفية الثابتة موضوعة على مستوى التطبيق؛ نضيف تعتيماً خفيفاً للطبيب فقط.
    Box(Modifier.fillMaxSize().background(Color(0x55040B17))) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
        // الرأس
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.ic_launcher), null, Modifier.size(40.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("الطريق إلى صحتي", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("مريضك أخوك، فارحم وجعه", fontSize = 12.sp, color = BMuted)
            }
            Box {
                IconButton(onClick = { onTab(4) }) {
                    Icon(Icons.Filled.Notifications, "الإشعارات", tint = Color.White)
                }
                if (unread > 0) Badge(Modifier.align(Alignment.TopEnd)) { Text("$unread") }
            }
        }

        SehatiHero(
            eyebrow = "مساحة عملك الطبية",
            title = if (shownName.isBlank()) "مرحباً بك" else "د. $shownName",
            subtitle = "تابع الحجوزات والدور وحالة استقبال المرضى من مكان واحد.",
            icon = Icons.Filled.MedicalServices
        )

        // بطاقة الهوية والتوافر
        Column(
            Modifier
                .fillMaxWidth()
                .background(BCard, RoundedCornerShape(22.dp))
                .border(1.dp, BLine, RoundedCornerShape(22.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                DoctorAvatar(photo, shownName.ifBlank { "د" }, 68.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        shownName.ifBlank { "أهلاً بك" },
                        fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Color.White,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    if (specialty.isNotBlank()) {
                        Text(specialty, color = DTeal, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                    val place = listOf(clinic, city).filter { it.isNotBlank() }.joinToString(" - ")
                    if (place.isNotBlank()) {
                        Text(place, color = BMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }

            // صف التوافر
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(BBg, RoundedCornerShape(16.dp))
                    .border(1.dp, BLine, RoundedCornerShape(16.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Circle, null, tint = if (isOn) BGreen else BMuted, modifier = Modifier.size(10.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        statusText,
                        color = if (isOn) BGreen else Color.White,
                        fontWeight = FontWeight.Bold, fontSize = 14.sp
                    )
                    Text("تظهر حالتك للمرضى في قائمة الأطباء", color = BMuted, fontSize = 11.sp)
                }
                Switch(
                    checked = isOn,
                    enabled = hasProfile == true,
                    onCheckedChange = { v ->
                        toggleMsg = ""
                        val ref = bDb.collection("doctors").document(uid)
                        ref.get().addOnSuccessListener { cur ->
                            val oldPhone = cur.getString("phone").orEmpty()
                            val oldEmg = cur.getString("emergencyPhone").orEmpty()
                            val upd = hashMapOf<String, Any>(
                                "available" to v,
                                "availableUpdatedAt" to FieldValue.serverTimestamp()
                            )
                            // الأرقام القديمة داخل مستند الطبيب ممنوعة بالقواعد: تُنقل إلى doctorPrivate ثم تُحذف.
                            if (cur.contains("phone")) upd["phone"] = FieldValue.delete()
                            if (cur.contains("emergencyPhone")) upd["emergencyPhone"] = FieldValue.delete()
                            val batch = bDb.batch()
                            batch.update(ref, upd)
                            if (oldPhone.isNotBlank() || oldEmg.isNotBlank()) {
                                val priv = hashMapOf<String, Any>("updatedAt" to FieldValue.serverTimestamp())
                                if (oldPhone.isNotBlank()) priv["phone"] = oldPhone
                                if (oldEmg.isNotBlank()) priv["emergencyPhone"] = oldEmg
                                batch.set(
                                    bDb.collection("doctorPrivate").document(uid), priv,
                                    com.google.firebase.firestore.SetOptions.merge()
                                )
                            }
                            batch.commit()
                                .addOnSuccessListener { available = v }
                                .addOnFailureListener { ex ->
                                    val code = (ex as? com.google.firebase.firestore.FirebaseFirestoreException)?.code
                                    toggleMsg = "تعذر تغيير الحالة: ${code ?: "خطأ"}"
                                }
                        }.addOnFailureListener { toggleMsg = "تعذر قراءة الملف، تحقق من الإنترنت" }
                    }
                )
            }
            if (hasProfile == false) {
                Text("أكملي ملفك المهني أولاً لتتمكني من تغيير حالة التوافر", color = BMuted, fontSize = 12.sp)
            }
            if (toggleMsg.isNotEmpty()) Text(toggleMsg, color = BDanger, fontSize = 12.sp)
        }

        // لوحة مختصرة بأربع بطاقات كما في التصميم المرجعي
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            DoctorSectionTitle("لوحة العمل")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DoctorMetricCard(Icons.Filled.CalendarMonth, "مواعيدي", countText(todayN?.toLong()), Modifier.weight(1f)) { onGo("dash") }
                DoctorMetricCard(Icons.Filled.Schedule, "بانتظار الدور", countText(waiting), Modifier.weight(1f)) { onGo("dash") }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DoctorMetricCard(Icons.Filled.Inbox, "طلبات جديدة", newN?.toString() ?: "...", Modifier.weight(1f)) { onGo("dash") }
                DoctorMetricCard(Icons.Filled.Group, "المرضى", "عرض القائمة", Modifier.weight(1f)) { onPatients() }
            }
        }

        // اختصارات العمل على شكل شبكة بطاقات مثل التصميم المرجعي.
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            DoctorSectionTitle("الخدمات")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DoctorServiceCard(Icons.Filled.Badge, "ملفي المهني", Modifier.weight(1f)) { onGo("profile") }
                DoctorServiceCard(Icons.Filled.Settings, "إعدادات العيادة", Modifier.weight(1f)) { onGo("settings") }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DoctorServiceCard(Icons.Filled.ManageAccounts, "إعدادات الحساب", Modifier.weight(1f)) { onTab(3) }
                DoctorServiceCard(Icons.Filled.Autorenew, "تجديد الاشتراك", Modifier.weight(1f)) { onGo("renew") }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DoctorServiceCard(Icons.Filled.Chat, "الرسائل", Modifier.weight(1f)) { onGo("messages") }
                DoctorServiceCard(Icons.Filled.Group, "قائمة المرضى", Modifier.weight(1f)) { onPatients() }
            }
            DoctorServiceCard(Icons.Filled.Description, "الوصفات الطبية", Modifier.fillMaxWidth()) { onGo("prescriptions") }
        }
        }
    }
}

@Composable
private fun DoctorMetricCard(icon: ImageVector, title: String, value: String, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .height(116.dp)
            .background(BCard, RoundedCornerShape(18.dp))
            .border(1.dp, BLine, RoundedCornerShape(18.dp))
            .clickable { onClick() }
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = DTeal, modifier = Modifier.size(22.dp))
            Spacer(Modifier.weight(1f))
            Icon(Icons.Filled.ChevronLeft, null, tint = BMuted, modifier = Modifier.size(18.dp))
        }
        Text(value, color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(title, color = BMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun DoctorSectionTitle(text: String) {
    Text(text, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
}

/** بطاقة كبيرة بتدرج لوني: أيقونة، عنوان، نص صغير، ورقم أو سهم. */
@Composable
private fun DoctorBigCard(
    icon: ImageVector, title: String, sub: String, value: String?,
    brush: Brush, onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(96.dp)
            .background(brush, RoundedCornerShape(22.dp))
            .clickable { onClick() }
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            Modifier.size(46.dp).background(Color.White.copy(alpha = 0.22f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(24.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, color = Color.White.copy(alpha = 0.88f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (value != null) {
            Text(value, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        } else {
            Icon(Icons.Filled.ChevronLeft, null, tint = Color.White)
        }
    }
}

@Composable
private fun DoctorServiceCard(icon: ImageVector, title: String, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.height(94.dp)
            .background(BCard, RoundedCornerShape(16.dp))
            .border(1.dp, BLine, RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Box(
            Modifier.size(34.dp).background(DTeal.copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = DTeal, modifier = Modifier.size(19.dp))
        }
        Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

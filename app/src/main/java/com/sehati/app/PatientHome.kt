package com.sehati.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ألوان هذه الشاشة: الأساس من ألوان التطبيق، والتمييز بألوان خاصة بها
private val PTeal = Color(0xFF2DD4BF)
private val PCoral = Color(0xFFFF6B57)
private val PAmber = Color(0xFFFBBF24)
private val PViolet = Color(0xFFA78BFA)
private val PRose = Color(0xFFFB7185)
private val PSky = Color(0xFF38BDF8)

private val SPEC_COLORS = listOf(PTeal, PViolet, PAmber, PRose, PSky)

/** تخصصات الرئيسية للمريض (9 + أخرى). */
private val PATIENT_SPECS: List<Spec> = listOf(
    "أعصاب", "طب الأسنان", "طب العيون", "عظمية ومفاصل", "قلبية",
    "طب الأطفال", "نسائية وتوليد", "باطنية", "جلدية"
).mapNotNull { n -> SPECIALTIES.firstOrNull { it.name == n } }

@Composable
fun PatientHome(
    name: String, unread: Int,
    onGo: (String) -> Unit, onOpen: (Map<String, Any?>) -> Unit, onTab: (Int) -> Unit
) {
    // ظهور خفيف للشاشة مرة واحدة
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val enter by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "homeEnter"
    )

    Column(
        Modifier
            .fillMaxSize()
            .background(BBg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .graphicsLayer {
                alpha = enter
                translationY = (1f - enter) * 8.dp.toPx()
            },
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // الترحيب
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(48.dp)
                    .background(PTeal.copy(alpha = 0.18f), CircleShape)
                    .border(1.dp, PTeal.copy(alpha = 0.5f), CircleShape)
                    .clickable { onTab(3) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    name.trim().take(1).uppercase().ifBlank { "؟" },
                    color = PTeal, fontWeight = FontWeight.Bold, fontSize = 19.sp
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (name.isNotBlank()) "مرحباً، ${name.trim()}" else "مرحباً بك",
                    fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Color.White,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text("اهتم بصحتك دائماً", fontSize = 12.sp, color = BMuted)
            }
            Box {
                IconButton(onClick = { onTab(4) }) {
                    Icon(Icons.Filled.Notifications, "الإشعارات", tint = Color.White)
                }
                if (unread > 0) Badge(Modifier.align(Alignment.TopEnd)) { Text("$unread") }
            }
        }

        // البحث
        Row(
            Modifier
                .fillMaxWidth()
                .height(54.dp)
                .background(BCard, RoundedCornerShape(27.dp))
                .border(1.dp, BLine, RoundedCornerShape(27.dp))
                .clickable { onGo("search") }
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Filled.Search, null, tint = BMuted)
            Text("ابحث عن طبيب أو عيادة...", color = BMuted, fontSize = 14.sp)
        }

        // زرّان كبيران: الطوارئ والحجز
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PHBigAction(
                Icons.Filled.Emergency, "طوارئ", "خدمات عاجلة",
                Brush.linearGradient(listOf(Color(0xFFFF6B57), Color(0xFFE5484D))),
                Modifier.weight(1f)
            ) { onGo("emergency") }
            PHBigAction(
                Icons.Filled.CalendarMonth, "حجز موعد", "احجز الآن",
                Brush.linearGradient(listOf(Color(0xFF14B8A6), Color(0xFF0F766E))),
                Modifier.weight(1f)
            ) { onGo("search:@doctors") }
        }

        // العيادات والأطباء (بقيت كما كانت)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PHChip(Icons.Filled.LocalHospital, "العيادات", Modifier.weight(1f)) { onGo("search:@clinics") }
            PHChip(Icons.Filled.MedicalServices, "الأطباء", Modifier.weight(1f)) { onGo("search:@doctors") }
        }

        // التخصصات
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                PHSectionTitle("التخصصات")
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { onGo("specs") }) {
                    Text("عرض الكل", color = PTeal, fontWeight = FontWeight.SemiBold)
                }
            }
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                PATIENT_SPECS.forEachIndexed { i, sp ->
                    PHSpec(sp.icon, sp.name, SPEC_COLORS[i % SPEC_COLORS.size], Modifier.width(76.dp)) {
                        onGo("search:" + sp.name)
                    }
                }
                PHSpec(Icons.Filled.MoreHoriz, "أخرى", BMuted, Modifier.width(76.dp)) { onGo("specs") }
            }
        }

        // الأطباء والعيادات
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                PHSectionTitle("أطباء وعيادات")
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { onGo("search:@doctors") }) {
                    Text("عرض الكل", color = PTeal, fontWeight = FontWeight.SemiBold)
                }
            }
            PatientDoctorList(onOpen)
        }

        // الاختصارات
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PHSectionTitle("اختصارات")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PHShortcut(Icons.Filled.Schedule, "دوري", Modifier.weight(1f)) { onGo("queue") }
                PHShortcut(Icons.Filled.Map, "طريق العيادة", Modifier.weight(1f)) { onGo("route") }
            }
            PHShortcut(Icons.Filled.Chat, "الرسائل", Modifier.fillMaxWidth()) { onGo("messages") }
        }
    }
}

@Composable
private fun PHSectionTitle(text: String) {
    Text(text, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
}

/** زر كبير بتدرج لوني: أيقونة وعنوان ونص صغير. */
@Composable
private fun PHBigAction(
    icon: ImageVector, title: String, sub: String, brush: Brush,
    modifier: Modifier, onClick: () -> Unit
) {
    Column(
        modifier
            .height(120.dp)
            .background(brush, RoundedCornerShape(24.dp))
            .clickable { onClick() }
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Box(
            Modifier.size(40.dp).background(Color.White.copy(alpha = 0.22f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(22.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text(sub, color = Color.White.copy(alpha = 0.88f), fontSize = 12.sp)
        }
    }
}

@Composable
private fun PHChip(icon: ImageVector, text: String, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier
            .height(48.dp)
            .background(BCard, RoundedCornerShape(24.dp))
            .border(1.dp, BLine, RoundedCornerShape(24.dp))
            .clickable { onClick() }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(icon, null, tint = PTeal, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}

/** دائرة تخصص: خلفية شفافة بلون التمييز وأيقونة بنفس اللون. */
@Composable
private fun PHSpec(icon: ImageVector, label: String, accent: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.clickable { onClick() }.padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(60.dp)
                .background(accent.copy(alpha = 0.16f), CircleShape)
                .border(1.dp, accent.copy(alpha = 0.35f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = accent, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(
            label, color = Color.White, fontSize = 12.sp,
            textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun PHShortcut(icon: ImageVector, title: String, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier
            .background(BCard, RoundedCornerShape(20.dp))
            .border(1.dp, BLine, RoundedCornerShape(20.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            Modifier.size(38.dp).background(PTeal.copy(alpha = 0.16f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = PTeal, modifier = Modifier.size(20.dp))
        }
        Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Icon(Icons.Filled.ChevronLeft, null, tint = BMuted)
    }
}

/** قائمة الأطباء والعيادات الحقيقية. عند منح إذن الموقع تُرتّب بالأقرب، وإلا تُعرض بالترتيب الأبجدي. */
@Composable
private fun PatientDoctorList(onOpen: (Map<String, Any?>) -> Unit) {
    val ctx = LocalContext.current
    var docs by remember { mutableStateOf<List<Map<String, Any?>>?>(null) }
    var failed by remember { mutableStateOf(false) }
    var hasPerm by remember { mutableStateOf(ctx.hasLocationPermission()) }
    var here by remember { mutableStateOf<Location?>(null) }
    var locMsg by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        bDb.collection("doctors").orderBy("name").limit(50).get()
            .addOnSuccessListener { s ->
                failed = false
                docs = s.documents.map { it.data.orEmpty() + ("id" to it.id) }
            }
            .addOnFailureListener { failed = true }
    }

    fun locate() {
        val loc = lastKnownLocation(ctx)
        here = loc
        locMsg = if (loc == null) "تعذر تحديد موقعك. شغّل الـ GPS وحاول مرة أخرى" else ""
    }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPerm = granted
        if (granted) locate() else locMsg = "يمكنك البحث حسب المدينة والتخصص بدون تحديد الموقع"
    }

    LaunchedEffect(hasPerm) { if (hasPerm && here == null) locate() }

    if (!hasPerm) {
        OutlinedButton(
            onClick = { permLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(25.dp)
        ) {
            Icon(Icons.Filled.LocationOn, null, tint = PTeal, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("فعّل الموقع لعرض الأقرب إليك", color = Color.White, fontWeight = FontWeight.SemiBold)
        }
    }
    if (locMsg.isNotEmpty()) Text(locMsg, color = BMuted, fontSize = 12.sp)

    val list = docs
    val from = here
    when {
        failed -> Text("تعذر تحميل الأطباء، تحقق من الإنترنت", color = BDanger)
        list == null -> Text("جارٍ تحميل الأطباء...", color = BMuted)
        list.isEmpty() -> Text("لا يوجد أطباء أو عيادات مسجلة بعد", color = BMuted)
        else -> {
            val shown = if (from == null) list else list.sortedBy { distanceKm(it, from) ?: Double.MAX_VALUE }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                shown.take(20).forEach { d ->
                    val km = from?.let { distanceKm(d, it) }
                    DoctorListCard(d, km, onOpen)
                }
            }
        }
    }
}

@Composable
private fun DoctorListCard(d: Map<String, Any?>, km: Double?, onOpen: (Map<String, Any?>) -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.985f else 1f,
        animationSpec = tween(durationMillis = 90, easing = FastOutSlowInEasing),
        label = "cardPress"
    )

    Row(
        Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = pressScale; scaleY = pressScale }
            .background(BCard, RoundedCornerShape(22.dp))
            .border(1.dp, BLine, RoundedCornerShape(22.dp))
            .clickable(interactionSource = source, indication = LocalIndication.current) { onOpen(d) }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        DoctorAvatar(d["photo"]?.toString(), "${d["name"] ?: ""}", 60.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                "${d["name"] ?: ""}",
                fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text("${d["specialty"] ?: ""}", color = PTeal, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            val c = "${d["clinicName"] ?: ""}"
            if (c.isNotBlank()) Text(c, color = Color.White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${d["city"] ?: ""}", color = BMuted, fontSize = 12.sp)
                when (d["available"]) {
                    true -> Text("• متاح الآن", color = BGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    false -> Text("• غير متاح حالياً", color = BMuted, fontSize = 12.sp)
                    else -> {}
                }
            }
        }
        if (km != null) {
            Box(
                Modifier
                    .background(BBg, RoundedCornerShape(12.dp))
                    .border(1.dp, BLine, RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text("%.1f كم".format(km), color = BMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

private fun Context.hasLocationPermission(): Boolean =
    checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

private fun lastKnownLocation(ctx: Context): Location? {
    val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    return try {
        lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
    } catch (e: SecurityException) {
        null
    }
}

/** المسافة بالكيلومتر، أو null إذا لم تكن للطبيب إحداثيات مسجلة. */
private fun distanceKm(d: Map<String, Any?>, from: Location): Double? {
    val la = (d["lat"] as? Number)?.toDouble() ?: return null
    val ln = (d["lng"] as? Number)?.toDouble() ?: return null
    val out = FloatArray(1)
    Location.distanceBetween(from.latitude, from.longitude, la, ln, out)
    return out[0] / 1000.0
}

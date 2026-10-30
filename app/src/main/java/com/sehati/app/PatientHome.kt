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
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val PH_COLORS = listOf(
    Color(0xFFE5171F), Color(0xFF6EA8FE), Color(0xFFFF6B8A),
    Color(0xFF8E9DB8), Color(0xFFE6A15C), Color(0xFF7C9DCE)
)

@Composable
fun PatientHome(
    name: String, unread: Int,
    onGo: (String) -> Unit, onOpen: (Map<String, Any?>) -> Unit, onTab: (Int) -> Unit
) {
    // ظهور خفيف للشاشة مرة واحدة: شفافية فقط مع انزياح بسيط جداً
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
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
            .graphicsLayer {
                alpha = enter
                translationY = (1f - enter) * 8.dp.toPx()
            },
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // الرأس: في RTL العنصر الأول يظهر على اليمين
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier.size(40.dp).background(BCard, CircleShape).border(1.dp, BLine, CircleShape).clickable { onTab(3) },
                contentAlignment = Alignment.Center
            ) {
                Text(name.trim().take(1).uppercase().ifBlank { "؟" }, color = Color.White, fontWeight = FontWeight.Bold)
            }
            Box {
                IconButton(onClick = { onTab(4) }) { Icon(Icons.Filled.Notifications, null, tint = Color.White) }
                if (unread > 0) Badge(Modifier.align(Alignment.TopEnd)) { Text("$unread") }
            }
            Spacer(Modifier.weight(1f))
            Column(Modifier.weight(2f)) {
                Text("الطريق إلى صحتي", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("لأن صحتك أولويتنا، نحن معك.", fontSize = 12.sp, color = BMuted)
            }
            Image(painterResource(R.drawable.ic_launcher), null, Modifier.size(48.dp))
        }

        Row(
            Modifier.fillMaxWidth().height(52.dp)
                .background(BCard, RoundedCornerShape(14.dp))
                .border(1.dp, BLine, RoundedCornerShape(14.dp))
                .clickable { onGo("search") }
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Filled.Search, null, tint = BMuted)
            Text("ابحث عن طبيب أو عيادة...", color = BMuted)
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickBtn(Icons.Filled.Warning, "الطوارئ", BDanger, Modifier.weight(1f)) { onGo("emergency") }
            QuickBtn(Icons.Filled.LocalHospital, "العيادات", BRed, Modifier.weight(1f)) { onGo("search:@clinics") }
            QuickBtn(Icons.Filled.MedicalServices, "الأطباء", BRed, Modifier.weight(1f)) { onGo("search:@doctors") }
        }

        Text("التخصصات الأكثر طلباً", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        val cells: List<Spec?> = COMMON_SPECIALTIES + listOf<Spec?>(null)
        cells.chunked(4).forEachIndexed { r, row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEachIndexed { c, sp ->
                    val color = PH_COLORS[(r * 4 + c) % PH_COLORS.size]
                    if (sp != null) PHSpec(sp, color, Modifier.weight(1f)) { onGo("search:" + sp.name) }
                    else PHAllSpec(Modifier.weight(1f)) { onGo("specs") }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { onGo("search:@doctors") }) { Text("عرض الكل", color = BRed) }
            Spacer(Modifier.weight(1f))
            Text("أطباء وعيادات", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        PatientDoctorList(onOpen)

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Tile(Icons.Filled.Person, stringResource(R.string.my_turn), false, Modifier.weight(1f)) { onGo("queue") }
            Tile(Icons.Filled.LocationOn, stringResource(R.string.way_to_clinic), false, Modifier.weight(1f)) { onGo("route") }
        }
        Tile(Icons.Filled.Description, "وصفاتي الطبية", false, Modifier.fillMaxWidth()) { onGo("patient_prescriptions") }
        Tile(Icons.Filled.Email, "الرسائل", false, Modifier.fillMaxWidth()) { onGo("messages") }
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
                    .filter { it["approved"] == true }
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
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) { Text("فعّل الموقع لعرض الأقرب إليك", color = Color.White) }
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
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
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
    // ضغط بسيط وسريع على البطاقة، بدون ارتداد
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.985f else 1f,
        animationSpec = tween(durationMillis = 90, easing = FastOutSlowInEasing),
        label = "cardPress"
    )

    Column(
        Modifier.width(154.dp)
            .graphicsLayer { scaleX = pressScale; scaleY = pressScale }
            .background(BCard, RoundedCornerShape(16.dp))
            .border(1.dp, BLine, RoundedCornerShape(16.dp))
            .clickable(interactionSource = source, indication = LocalIndication.current) { onOpen(d) }
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box {
            DoctorAvatar(d["photo"]?.toString(), "${d["name"] ?: ""}", 76.dp)
            if (d["available"] == true) {
                Text("متاح", Modifier.align(Alignment.TopEnd)
                    .background(BGreen, RoundedCornerShape(10.dp)).padding(horizontal = 7.dp, vertical = 2.dp),
                    color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
        Text("${d["name"] ?: "مقدم خدمة"}", fontSize = 13.sp, fontWeight = FontWeight.Bold,
            color = Color.White, maxLines = 1)
        Text("${d["specialty"] ?: d["clinicName"] ?: ""}", color = BMuted, fontSize = 11.sp, maxLines = 1)
        Text("${d["city"] ?: ""}", color = BMuted, fontSize = 10.sp, maxLines = 1)
        if (km != null) Text("%.1f كم".format(km), color = BMuted, fontSize = 10.sp)
    }
}

@Composable
private fun PHSpec(sp: Spec, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.clickable { onClick() }.padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(76.dp).background(color.copy(alpha = 0.14f), CircleShape).border(1.5.dp, color.copy(alpha = 0.72f), CircleShape),
            contentAlignment = Alignment.Center
        ) { SpecImage(sp, 50.dp) }
        Spacer(Modifier.height(6.dp))
        Text(sp.name, color = Color.White, fontSize = 11.sp, textAlign = TextAlign.Center, maxLines = 2)
    }
}

@Composable
private fun PHAllSpec(modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.clickable { onClick() }.padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(64.dp).background(BRed.copy(alpha = 0.16f), CircleShape).border(1.dp, BRed, CircleShape),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Filled.GridView, null, tint = Color.White, modifier = Modifier.size(30.dp)) }
        Spacer(Modifier.height(6.dp))
        Text("كل التخصصات", color = Color.White, fontSize = 11.sp, textAlign = TextAlign.Center, maxLines = 2)
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

package com.sehati.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** تخصصات الرئيسية للمريض (9 + أخرى)، بحسب التصميم المرجعي. */
private val PATIENT_SPECS: List<Spec> = listOf(
    "أعصاب", "طب الأسنان", "طب العيون", "عظمية ومفاصل", "قلبية",
    "طب الأطفال", "نسائية وتوليد", "باطنية", "جلدية"
).mapNotNull { n -> SPECIALTIES.firstOrNull { it.name == n } }

@Composable
fun PatientHome(
    name: String, unread: Int,
    onGo: (String) -> Unit, onOpen: (Map<String, Any?>) -> Unit, onTab: (Int) -> Unit
) {
    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
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
                IconButton(onClick = { onTab(2) }) { Icon(Icons.Filled.Notifications, null, tint = Color.White) }
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

        Text("التخصصات", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        val specCells: List<Spec?> = PATIENT_SPECS + listOf<Spec?>(null)
        specCells.chunked(5).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { sp ->
                    if (sp != null) SpecCircle(sp, Modifier.weight(1f)) { onGo("search:" + sp.name) }
                    else OtherSpecCircle(Modifier.weight(1f)) { onGo("specs") }
                }
                repeat(5 - row.size) { Spacer(Modifier.weight(1f)) }
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
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
    Row(
        Modifier.fillMaxWidth()
            .background(BCard, RoundedCornerShape(14.dp))
            .border(1.dp, BLine, RoundedCornerShape(14.dp))
            .clickable { onOpen(d) }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        DoctorAvatar(d["photo"]?.toString(), "${d["name"] ?: ""}", 56.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("${d["name"]}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text("${d["specialty"] ?: ""}", color = BRed, fontSize = 13.sp)
            val c = "${d["clinicName"] ?: ""}"
            if (c.isNotBlank()) Text(c, color = Color.White, fontSize = 12.sp)
            Text("${d["city"] ?: ""}", color = BMuted, fontSize = 12.sp)
            when (d["available"]) {
                true -> Text("متاح الآن", color = BGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                false -> Text("غير متاح حالياً", color = BMuted, fontSize = 12.sp)
                else -> {}
            }
        }
        if (km != null) Text("%.1f كم".format(km), color = BMuted, fontSize = 12.sp)
    }
}

@Composable
private fun OtherSpecCircle(modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.clickable { onClick() }.padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(56.dp).background(BCard, CircleShape).border(1.dp, BRed, CircleShape),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Filled.MoreHoriz, null, tint = Color.White, modifier = Modifier.size(26.dp)) }
        Spacer(Modifier.height(6.dp))
        Text("أخرى", color = Color.White, fontSize = 12.sp, textAlign = TextAlign.Center)
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

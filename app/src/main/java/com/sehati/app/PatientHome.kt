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
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.firestore.FirebaseFirestoreException

/** التخصصات الثمانية الظاهرة في المرجع، مع زر كل التخصصات كالعنصر التاسع. */
private val PATIENT_SPECS: List<Spec> = listOf(
    "طب العيون", "عظمية ومفاصل", "قلبية",
    "طب الأسنان", "أعصاب", "طب الأطفال",
    "نسائية وتوليد", "جلدية"
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
                IconButton(onClick = { onTab(4) }) { Icon(Icons.Filled.Notifications, null, tint = Color.White) }
                if (unread > 0) Badge(Modifier.align(Alignment.TopEnd)) { Text("$unread") }
            }
            Spacer(Modifier.weight(1f))
            Column(Modifier.weight(2f)) {
                Text("الطريق الصحي", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("لأن صحتك أولويتنا، نحن معك.", fontSize = 12.sp, color = BMuted)
            }
            Image(painterResource(R.drawable.ic_launcher), null, Modifier.size(48.dp))
        }

        SehatiHero(
            eyebrow = "رعايتك أقرب مما تتخيل",
            title = if (name.isBlank()) "أهلاً بك في صحتي" else "أهلاً، $name",
            subtitle = "ابحث عن الطبيب المناسب، وتابع مواعيدك واهتم بصحتك بسهولة.",
            icon = Icons.Filled.Favorite
        )

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

        // الطوارئ: زر أحمر ممتلئ كما في التصميم، والعيادات والأطباء بطاقات داكنة
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            EmergencyQuickBtn(Modifier.weight(1.2f)) { onGo("emergency") }
            QuickBtn(Icons.Filled.CalendarMonth, "المواعيد", BRed, Modifier.weight(1f)) { onGo("myappts") }
            QuickBtn(Icons.Filled.MedicalServices, "الأطباء", BRed, Modifier.weight(1f)) { onGo("search:@doctors") }
        }

        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("التخصصات الطبية", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("اختر التخصص الذي تبحث عنه", color = Color(0xFF45CFFF), fontSize = 12.sp)
        }
        val specCells: List<Spec?> = PATIENT_SPECS + listOf<Spec?>(null)
        specCells.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { sp ->
                    if (sp != null) NeonSpecialtyCard(sp, Modifier.weight(1f)) { onGo("search:" + sp.name) }
                    else NeonAllSpecialtiesCard(Modifier.weight(1f)) { onGo("specs") }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { onGo("search:@doctors") }) { Text("عرض الكل", color = BRed) }
            Spacer(Modifier.weight(1f))
            Text("أطباء وعيادات", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        // الوصول السريع إلى خدمات المريض قبل قائمة الأطباء.
        Row(
            Modifier.fillMaxWidth()
                .background(Brush.horizontalGradient(listOf(Color(0xFF351316), Color(0xFF171B25))), RoundedCornerShape(18.dp))
                .border(1.dp, BRed.copy(alpha = 0.75f), RoundedCornerShape(18.dp))
                .clickable { onGo("services") }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(Modifier.size(48.dp).background(BRed, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Apps, contentDescription = null, tint = Color.White, modifier = Modifier.size(27.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("مركز الخدمات الصحية", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("كل أدواتك الطبية في مكان واحد", color = BMuted, fontSize = 12.sp)
            }
            Icon(Icons.Filled.ArrowBackIosNew, contentDescription = null, tint = Color.White)
        }

        PatientDoctorList(onOpen)

        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("خدماتي الصحية", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("متابعة مواعيدك وملفك الصحي من مكان واحد", color = BMuted, fontSize = 12.sp)
        }

        Tile(Icons.Filled.AssignmentTurnedIn, "الاستعداد للزيارة", false, Modifier.fillMaxWidth()) { onGo("visit_preparation") }
        Tile(Icons.Filled.Assignment, "ملخص قبل زيارة الطبيب", false, Modifier.fillMaxWidth()) { onGo("visit_summary") }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Tile(Icons.Filled.Person, stringResource(R.string.my_turn), false, Modifier.weight(1f)) { onGo("queue") }
            Tile(Icons.Filled.LocationOn, stringResource(R.string.way_to_clinic), false, Modifier.weight(1f)) { onGo("route") }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Tile(Icons.Filled.Description, "وصفاتي الطبية", false, Modifier.weight(1f)) { onGo("patient_prescriptions") }
            Tile(Icons.Filled.Email, "الرسائل", false, Modifier.weight(1f)) { onGo("messages") }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Tile(Icons.Filled.Favorite, "أطبائي المفضلون", false, Modifier.weight(1f)) { onGo("favorites") }
            Tile(Icons.Filled.Medication, "جدول أدويتي", false, Modifier.weight(1f)) { onGo("medication_plans") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Tile(Icons.Filled.HealthAndSafety, "ملفي الصحي", false, Modifier.weight(1f)) { onGo("health_profile") }
            Tile(Icons.Filled.FamilyRestroom, "ملفات العائلة", false, Modifier.weight(1f)) { onGo("family_profiles") }
        }
    }
}

/**
 * قائمة الأطباء المعتمدين. تُرتّب بالأقرب عند منح إذن الموقع، وإلا أبجدياً.
 * الاستعلام يعتمد على approved فقط (بدون orderBy) حتى لا يحتاج فهرساً مركباً،
 * والترتيب يتم داخل التطبيق. رسالة الخطأ تبيّن السبب الحقيقي بدل رسالة الإنترنت العامة.
 */
@Composable
private fun PatientDoctorList(onOpen: (Map<String, Any?>) -> Unit) {
    val ctx = LocalContext.current
    var docs by remember { mutableStateOf<List<Map<String, Any?>>?>(null) }
    var failed by remember { mutableStateOf("") }
    var hasPerm by remember { mutableStateOf(ctx.hasLocationPermission()) }
    var here by remember { mutableStateOf<Location?>(null) }
    var locMsg by remember { mutableStateOf("") }

    fun load() {
        failed = ""
        docs = null
        bDb.collection("doctors").whereEqualTo("approved", true).limit(50).get()
            .addOnSuccessListener { s ->
                failed = ""
                docs = s.documents
                    .map { it.data.orEmpty() + ("id" to it.id) }
                    .sortedBy { "${it["name"] ?: ""}" }
            }
            .addOnFailureListener { e ->
                failed = when ((e as? FirebaseFirestoreException)?.code) {
                    FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                        "لا تملك صلاحية قراءة الأطباء (قواعد Firestore). أبلغ المطوّر."
                    FirebaseFirestoreException.Code.UNAVAILABLE ->
                        "تعذر الاتصال بالخادم. تحقق من الإنترنت أو أوقف الـVPN وحاول مجدداً."
                    FirebaseFirestoreException.Code.FAILED_PRECONDITION ->
                        "الاستعلام يحتاج فهرساً في Firestore. أبلغ المطوّر."
                    else -> "تعذر تحميل الأطباء، حاول مرة أخرى."
                }
            }
    }

    LaunchedEffect(Unit) { load() }

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
    val hScroll = rememberScrollState()
    when {
        failed.isNotEmpty() -> {
            Text(failed, color = BDanger)
            OutlinedButton(
                onClick = { load() },
                modifier = Modifier.fillMaxWidth().height(44.dp)
            ) { Text("إعادة المحاولة", color = Color.White) }
        }
        list == null -> Text("جارٍ تحميل الأطباء...", color = BMuted)
        list.isEmpty() -> Text("لا يوجد أطباء معتمدون بعد", color = BMuted)
        else -> {
            val shown = if (from == null) list else list.sortedBy { distanceKm(it, from) ?: Double.MAX_VALUE }
            Row(
                Modifier.fillMaxWidth().horizontalScroll(hScroll),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                shown.take(20).forEach { d ->
                    val km = from?.let { distanceKm(d, it) }
                    DoctorHCard(d, km, onOpen)
                }
            }
        }
    }
}

/** بطاقة أفقية للطبيب: الصورة والاسم والتخصص والمدينة والمسافة (إن توفرت). بدون تقييمات وهمية. */
@Composable
private fun DoctorHCard(d: Map<String, Any?>, km: Double?, onOpen: (Map<String, Any?>) -> Unit) {
    Column(
        Modifier.width(150.dp)
            .background(BCard, RoundedCornerShape(14.dp))
            .border(1.dp, BLine, RoundedCornerShape(14.dp))
            .clickable { onOpen(d) }
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        DoctorAvatar(d["photo"]?.toString(), "${d["name"] ?: ""}", 72.dp)
        Text(
            "${d["name"] ?: ""}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White,
            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center
        )
        Text(
            "${d["specialty"] ?: ""}", color = BRed, fontSize = 12.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center
        )
        val place = "${d["clinicName"] ?: ""}".ifBlank { "${d["city"] ?: ""}" }
        if (place.isNotBlank()) {
            Text(
                place, color = BMuted, fontSize = 11.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center
            )
        }
        if (km != null) Text("%.1f كم".format(km), color = BMuted, fontSize = 11.sp)
    }
}

/** زر الطوارئ الأحمر الممتلئ في الصف العلوي. */
@Composable
private fun EmergencyQuickBtn(modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.height(96.dp)
            .background(BRed, RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Filled.Warning, null, tint = Color.White, modifier = Modifier.size(32.dp))
        Spacer(Modifier.height(6.dp))
        Text("الطوارئ", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

@Composable
private fun featuredSpecialtyImage(sp: Spec): Int? = when (sp.img) {
    "heart" -> R.drawable.spec_heart
    "knee" -> R.drawable.spec_bones
    "eye" -> R.drawable.spec_eye
    "tooth" -> R.drawable.spec_tooth
    "brain" -> R.drawable.spec_brain
    "child" -> R.drawable.spec_child
    "pregnant" -> R.drawable.spec_women
    "skin" -> R.drawable.spec_skin
    else -> null
}

@Composable
private fun NeonSpecialtyCard(sp: Spec, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.clickable { onClick() }.padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            Modifier.size(108.dp)
                .background(
                    Brush.radialGradient(listOf(Color(0xFF164C91), Color(0xFF071A3D))),
                    CircleShape
                )
                .border(
                    2.dp,
                    Brush.sweepGradient(listOf(Color(0xFF7CEBFF), Color(0xFF168BFF), Color(0xFF7CEBFF))),
                    CircleShape
                )
                .padding(4.dp),
            contentAlignment = Alignment.Center
        ) {
            val image = featuredSpecialtyImage(sp)
            if (image != null) {
                Image(
                    painter = painterResource(image),
                    contentDescription = sp.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(CircleShape)
                )
            } else {
                SpecImage(sp, 72.dp)
            }
        }
        Box(
            Modifier.widthIn(min = 104.dp, max = 132.dp)
                .background(
                    Brush.horizontalGradient(listOf(Color(0xFF071A3D), Color(0xFF123B78), Color(0xFF071A3D))),
                    RoundedCornerShape(50)
                )
                .border(1.dp, Color(0xFF168BFF), RoundedCornerShape(50))
                .padding(horizontal = 8.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                sp.name,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2
            )
        }
    }
}

@Composable
private fun NeonAllSpecialtiesCard(modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.clickable { onClick() }.padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            Modifier.size(108.dp)
                .background(
                    Brush.radialGradient(listOf(Color(0xFF176DDB), Color(0xFF061B43))),
                    CircleShape
                )
                .border(
                    2.dp,
                    Brush.sweepGradient(listOf(Color(0xFF7CEBFF), Color(0xFF168BFF), Color(0xFF7CEBFF))),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.GridView, null, tint = Color.White, modifier = Modifier.size(42.dp))
        }
        Box(
            Modifier.widthIn(min = 104.dp, max = 132.dp)
                .background(
                    Brush.horizontalGradient(listOf(Color(0xFF071A3D), Color(0xFF123B78), Color(0xFF071A3D))),
                    RoundedCornerShape(50)
                )
                .border(1.dp, Color(0xFF168BFF), RoundedCornerShape(50))
                .padding(horizontal = 8.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("كل التخصصات", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun OtherSpecCircle(modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.clickable { onClick() }.padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(72.dp)
                .background(Color(0xFF071A3D), CircleShape)
                .border(1.5.dp, Color(0xFF168BFF), CircleShape),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Filled.GridView, null, tint = Color.White, modifier = Modifier.size(32.dp)) }
        Spacer(Modifier.height(6.dp))
        Text("كل التخصصات", color = Color.White, fontSize = 11.sp, textAlign = TextAlign.Center, maxLines = 1)
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

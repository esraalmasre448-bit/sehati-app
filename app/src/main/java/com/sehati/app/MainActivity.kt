package com.sehati.app

import android.os.Bundle
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.scale
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.delay

private val Bg = Color(0xC90A1424)
private val CardC = Color(0xEE101D32)
private val Line = Color(0xFF294766)
private val Red = Color(0xFF27B8F2)
private val Danger = Color(0xFFFF5968)
private val Good = Color(0xFF39D6A3)
private val OnAccent = Color(0xFF061522)
private val Muted = Color(0xFFB6C6DA)

private val auth get() = FirebaseAuth.getInstance()
private val db get() = FirebaseFirestore.getInstance()

class MainActivity : ComponentActivity() {
    var pendingConversationId by mutableStateOf<String?>(null)

    override fun onStart() {
        super.onStart()
        AppVisibility.foreground = true
    }

    override fun onStop() {
        AppVisibility.foreground = false
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingConversationId = intent.getStringExtra("open_conv_id") ?: intent.getStringExtra("convId")
        setContent {
            val scheme = darkColorScheme(
                primary = Red, onPrimary = OnAccent, secondary = Red,
                secondaryContainer = Color(0xFF12334A), onSecondaryContainer = Color(0xFFEAF7FF),
                background = Bg, surface = CardC,
                onSurface = Color(0xFFF4F8FF), onBackground = Color(0xFFF4F8FF)
            )
            MaterialTheme(colorScheme = scheme) {
                Box(Modifier.fillMaxSize()) {
                    // الخلفية الطبية ثابتة خلف جميع أقسام التطبيق.
                    Image(
                        painter = painterResource(R.drawable.medical_fixed_background),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Surface(Modifier.fillMaxSize(), color = Color.Transparent) { App() }
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingConversationId = intent.getStringExtra("open_conv_id") ?: intent.getStringExtra("convId")
    }
}

@Composable
private fun NotificationWatcher(uid: String): NotifState {
    var type by remember(uid) { mutableStateOf("") }
    LaunchedEffect(uid) {
        type = ""
        if (uid.isNotBlank()) {
            db.collection("users").document(uid).get()
                .addOnSuccessListener { type = it.getString("accountType").orEmpty() }
        }
    }
    return rememberNotifs(uid, type)
}

@Composable
fun App() {
    var splash by remember { mutableStateOf(true) }
    var loggedIn by remember { mutableStateOf(auth.currentUser != null) }
    var screen by remember { mutableStateOf("main") }
    var selected by remember { mutableStateOf<Map<String, Any?>?>(null) }
    var initialQuery by remember { mutableStateOf("") }
    var chatId by remember { mutableStateOf("") }
    var myName by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        auth.currentUser?.uid?.let { id ->
            db.collection("users").document(id).get().addOnSuccessListener { myName = it.getString("name") ?: "" }
        }
    }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(splash) {
        if (!splash) (ctx as? MainActivity)?.let { NotificationPermission.prepareAndRequest(it) }
    }
    val notificationState = NotificationWatcher(if (loggedIn) auth.currentUser?.uid.orEmpty() else "")
    LaunchedEffect(loggedIn) {
        if (loggedIn) PushTokenManager.sync(ctx)
    }
    val pendingConversation = (ctx as? MainActivity)?.pendingConversationId
    LaunchedEffect(pendingConversation) {
        if (!pendingConversation.isNullOrBlank()) {
            chatId = pendingConversation
            screen = "chat"
            (ctx as? MainActivity)?.pendingConversationId = null
        }
    }
    var locked by remember { mutableStateOf(AppLockStore.enabled(ctx) && auth.currentUser != null) }
    val owner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(owner) {
        var away = 0L
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_STOP) away = System.currentTimeMillis()
            if (e == Lifecycle.Event.ON_START && away != 0L) {
                if (AppLockStore.enabled(ctx) && auth.currentUser != null &&
                    System.currentTimeMillis() - away > 30_000L
                ) locked = true
            }
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    LaunchedEffect(Unit) { delay(6200); splash = false }
    BackHandler(enabled = screen != "main") {
        screen = when (screen) {
            "detail" -> "search"
            "book" -> "detail"
            "chat" -> "messages"
            else -> "main"
        }
    }
    val sel = selected
    androidx.compose.animation.Crossfade(
        targetState = splash,
        animationSpec = tween(650, easing = FastOutSlowInEasing),
        label = "splash-to-app"
    ) { showSplash ->
    when {
        showSplash -> Splash()
        loggedIn && locked -> LockScreen(
            onUnlock = { locked = false },
            onForgot = {
                PushTokenManager.unregister(ctx) {
                    AppLockStore.clear(ctx)
                    auth.signOut()
                    loggedIn = false
                    locked = false
                    screen = "main"
                }
            }
        )
        !loggedIn -> AuthFlow { loggedIn = true }
        screen == "search" -> DoctorSearchScreen(
            onBack = { initialQuery = ""; screen = "main" },
            onOpen = { selected = it; screen = "detail" },
            initial = initialQuery
        )
        screen == "detail" && sel != null -> DoctorDetailWithChat(
            sel, patientName = myName,
            onBack = { screen = "search" }, onBook = { screen = "book" },
            onOpenChat = { chatId = it; screen = "chat" }
        )
        screen == "book" && sel != null -> QueueBookingScreen(
            sel, onBack = { screen = "detail" }, onDone = { screen = "myappts" }
        )
        screen == "specs" -> AllSpecialtiesScreen(
            onBack = { screen = "main" },
            onPick = { initialQuery = it; screen = "search" }
        )
        screen == "myappts" -> MyAppointmentsContent(
            onFind = { screen = "search" }, onBack = { screen = "main" }
        )
        screen == "queue" -> QueueScreen(onBack = { screen = "main" }, onFind = { screen = "search" })
        screen == "route" -> RouteScreen(onBack = { screen = "main" }, onFind = { screen = "search" })
        screen == "profile" -> DoctorProfileScreen { screen = "main" }
        screen == "settings" -> ClinicSettingsScreen { screen = "main" }
        screen == "dash" -> DoctorDashboardContent(onBack = { screen = "main" })
        screen == "admin" -> AdminGate(onBack = { screen = "main" })
        screen == "renew" -> RenewalScreen(onBack = { screen = "main" })
        screen == "prescriptions" -> DoctorPrescriptionsScreen(onBack = { screen = "main" })
        screen == "patient_prescriptions" -> PatientPrescriptionsScreen(onBack = { screen = "main" })
        screen == "emergency" -> EmergencyScreen(onBack = { screen = "main" })
        screen == "privacy" -> PrivacyScreen(onBack = { screen = "main" })
        screen == "messages" -> MessagesScreen(
            onBack = { screen = "main" },
            onOpenChat = { chatId = it; screen = "chat" }
        )
        screen == "chat" && chatId.isNotEmpty() -> ChatScreen(
            convId = chatId, onBack = { screen = "messages" }
        )
        else -> MainShell(
            notificationState = notificationState,
            onOpen = { selected = it; screen = "detail" },
            onGo = {
                if (it.startsWith("search:")) { initialQuery = it.removePrefix("search:"); screen = "search" }
                else { if (it == "search") initialQuery = ""; screen = it }
            },
            onLogout = {
                PushTokenManager.unregister(ctx) {
                    AppLockStore.clear(ctx)
                    auth.signOut()
                    loggedIn = false
                    locked = false
                    screen = "main"
                }
            }
        )
    }
    }
}

/** صفحة تفاصيل الطبيب مع زر «راسل الطبيب» ثابت أسفل الشاشة. */
@Composable
fun DoctorDetailWithChat(
    d: Map<String, Any?>, patientName: String,
    onBack: () -> Unit, onBook: () -> Unit, onOpenChat: (String) -> Unit
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    Column(Modifier.fillMaxSize().background(Bg)) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            DoctorDetailFull(d, onBack = onBack, onBook = onBook)
        }
        OutlinedButton(
            onClick = {
                openConversation(
                    doctorId = "${d["id"]}",
                    doctorName = "${d["name"] ?: ""}",
                    patientName = patientName,
                    onReady = onOpenChat,
                    onError = { android.widget.Toast.makeText(ctx, it, android.widget.Toast.LENGTH_LONG).show() }
                )
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp).height(52.dp)
        ) { Text("راسل الطبيب", color = Color.White) }
    }
}

/** بوابة لوحة المطوّر: تفتحها فقط إذا كان الحساب الحالي هو الأدمن المسجّل في config/admin. */
@Composable
fun AdminGate(onBack: () -> Unit) {
    var ok by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(Unit) {
        db.collection("config").document("admin").get()
            .addOnSuccessListener { d -> ok = d.getString("uid") == auth.currentUser?.uid }
            .addOnFailureListener { ok = false }
    }
    when (ok) {
        null -> Box(Modifier.fillMaxSize().background(Bg)) {
            CircularProgressIndicator(Modifier.align(Alignment.Center), color = Red)
        }
        true -> AdminHub(onBack = onBack)
        else -> Column(Modifier.fillMaxSize().background(Bg).padding(24.dp)) {
            TextButton(onClick = onBack) { Text("رجوع", color = Red) }
            Text("لا يمكن فتح هذه الصفحة", color = Color.White)
        }
    }
}

private fun startSplashHeartbeat(): () -> Unit {
    // A 1.9-second double-beat loop matches the ECG animation cycle below.
    val sampleRate = 22050
    val totalSamples = (sampleRate * 1.9).toInt()
    val pcm = ShortArray(totalSamples)
    val pulses = listOf(0.82, 0.99)
    pulses.forEachIndexed { index, start ->
        val duration = if (index == 0) 0.13 else 0.11
        val startSample = (start * sampleRate).toInt()
        val count = (duration * sampleRate).toInt()
        for (i in 0 until count) {
            val t = i.toDouble() / sampleRate
            val envelope = exp(-19.0 * t) * (1.0 - exp(-90.0 * t))
            val frequency = if (index == 0) 68.0 else 88.0
            val wave = sin(2.0 * PI * frequency * t) * 0.72 +
                sin(2.0 * PI * frequency * 1.72 * t) * 0.28
            val at = startSample + i
            if (at in pcm.indices) {
                val mixed = pcm[at].toDouble() + wave * envelope * 22000.0
                pcm[at] = mixed.coerceIn(-32768.0, 32767.0).toInt().toShort()
            }
        }
    }

    val released = AtomicBoolean(false)
    var track: AudioTrack? = null
    fun release() {
        if (released.compareAndSet(false, true)) {
            try {
                track?.let {
                    if (it.playState == AudioTrack.PLAYSTATE_PLAYING) it.pause()
                    it.flush()
                    it.release()
                }
            } catch (_: Exception) { }
            track = null
        }
    }

    return try {
        val audio = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(pcm.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        track = audio
        audio.setVolume(0.38f)
        val written = audio.write(pcm, 0, pcm.size)
        if (written == pcm.size && audio.setLoopPoints(0, pcm.size, -1) == AudioTrack.SUCCESS) {
            audio.play()
        } else {
            release()
        }
        ::release
    } catch (_: Exception) {
        release()
        val noOp: () -> Unit = {}
        noOp
    }
}

@Composable
fun Splash() {
    DisposableEffect(Unit) {
        val stopHeartbeat = startSplashHeartbeat()
        onDispose { stopHeartbeat() }
    }
    val motion = rememberInfiniteTransition(label = "internal-logo-ecg")
    // الحركة الوحيدة في الشعار: نقطة ضوئية تتبع خط تخطيط القلب.
    val ecgProgress by motion.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1900, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ecg-pulse"
    )
    Box(
        Modifier.fillMaxSize().background(
            androidx.compose.ui.graphics.Brush.verticalGradient(
                listOf(Color(0xFF101A2A), Color(0xFF182B43), Color(0xFF101A2A))
            )
        ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 34.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(Modifier.weight(0.65f))
            androidx.compose.foundation.Canvas(Modifier.size(286.dp)) {
                val unit = size.width / 300f
                val c = Offset(size.width / 2f, size.height / 2f)
                // الحلقات ثابتة تماماً؛ لا دوران ولا تكبير ولا تغيير إضاءة.
                listOf(116f, 101f, 86f).forEachIndexed { index, r ->
                    drawCircle(
                        color = when (index) {
                            0 -> Color(0x884B8DFF)
                            1 -> Color(0x997B35C9)
                            else -> Color(0x99FF426B)
                        },
                        radius = r * unit,
                        center = c,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = (1.8f - index * 0.25f) * unit)
                    )
                }
                // قلب ثابت بإطار نيون دافئ.
                val heart = androidx.compose.ui.graphics.Path().apply {
                    moveTo(c.x, c.y + 66f * unit)
                    cubicTo(c.x - 20f * unit, c.y + 48f * unit, c.x - 72f * unit, c.y + 13f * unit, c.x - 72f * unit, c.y - 28f * unit)
                    cubicTo(c.x - 72f * unit, c.y - 75f * unit, c.x - 16f * unit, c.y - 83f * unit, c.x, c.y - 39f * unit)
                    cubicTo(c.x + 19f * unit, c.y - 83f * unit, c.x + 72f * unit, c.y - 75f * unit, c.x + 72f * unit, c.y - 28f * unit)
                    cubicTo(c.x + 72f * unit, c.y + 13f * unit, c.x + 20f * unit, c.y + 48f * unit, c.x, c.y + 66f * unit)
                }
                drawPath(
                    heart,
                    color = Color(0xFFFFB15E),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.2f * unit, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                )
                // مسار ECG ثابت؛ لا يتحرك سوى الوميض الذي يمر عليه.
                val ecg = listOf(
                    0.08f to 0.50f, 0.25f to 0.50f, 0.34f to 0.50f,
                    0.40f to 0.36f, 0.44f to 0.70f, 0.49f to 0.22f,
                    0.54f to 0.58f, 0.61f to 0.50f, 0.92f to 0.50f
                )
                val trace = androidx.compose.ui.graphics.Path().apply {
                    moveTo(size.width * ecg.first().first, size.height * ecg.first().second)
                    ecg.drop(1).forEach { (x, y) -> lineTo(size.width * x, size.height * y) }
                }
                drawPath(trace, Color(0xAAFF5A72), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.2f * unit, cap = androidx.compose.ui.graphics.StrokeCap.Round))
                val x = 0.08f + 0.84f * ecgProgress
                val segment = ecg.zipWithNext().firstOrNull { (a, b) -> x in a.first..b.first }
                val pulseY = if (segment == null) 0.50f else {
                    val (a, b) = segment
                    val t = ((x - a.first) / (b.first - a.first)).coerceIn(0f, 1f)
                    a.second + (b.second - a.second) * t
                }
                val pulseCenter = Offset(size.width * x, size.height * pulseY)
                drawCircle(
                    brush = androidx.compose.ui.graphics.Brush.radialGradient(
                        colors = listOf(Color(0xFFFFF1D6), Color(0x99FF4D70), Color.Transparent),
                        center = pulseCenter,
                        radius = 19f * unit
                    ),
                    radius = 19f * unit,
                    center = pulseCenter
                )
                drawCircle(Color.White, radius = 3.2f * unit, center = pulseCenter)
            }
            Spacer(Modifier.height(28.dp))
            Text(
                "الطريق الصحي",
                color = Color.White,
                fontSize = 29.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "معك... دائماً نحو الأفضل",
                color = Color(0xFFB9D5E8),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(26.dp))
            Text(
                "جارٍ تجهيز تجربتك الصحية",
                color = Color(0xFFB5C9D9),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(14.dp))
            LinearProgressIndicator(
                modifier = Modifier.width(116.dp).height(2.dp),
                color = Color(0xFF56C9F5),
                trackColor = Color(0x3356C9F5)
            )
            Spacer(Modifier.weight(0.65f))
        }
        Column(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("DEVELOPED BY", color = Color(0xFF8FA9BC), fontSize = 8.sp, fontWeight = FontWeight.Medium, letterSpacing = 2.sp)
            Spacer(Modifier.height(3.dp))
            Text("SUHAEB ALTURKMANE", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
            Spacer(Modifier.height(2.dp))
            Text("تم التطوير والتنفيذ بواسطة", color = Color(0xFF8FA9BC), fontSize = 9.sp, fontWeight = FontWeight.Medium)
        }
    }
}
@Composable
fun MainShell(notificationState: NotifState, onGo: (String) -> Unit, onOpen: (Map<String, Any?>) -> Unit, onLogout: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("") }
    var isAdmin by remember { mutableStateOf(false) }
    var showFirstGuide by remember { mutableStateOf(false) }
    var subscriptionExpired by remember { mutableStateOf(false) }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val uid = auth.currentUser?.uid ?: ""
    val email = auth.currentUser?.email ?: ""
    LaunchedEffect(Unit) {
        auth.currentUser?.uid?.let { id ->
            db.collection("users").document(id).get().addOnSuccessListener { d ->
                name = d.getString("name") ?: ""
                type = d.getString("accountType") ?: ""
                val prefs = ctx.getSharedPreferences("sehati_first_run", android.content.Context.MODE_PRIVATE)
                if (!prefs.getBoolean("guide_seen_$id", false)) showFirstGuide = true
                if (type == "doctor" || type == "clinic") {
                    db.collection("doctors").document(id).get().addOnSuccessListener { doctor ->
                        val complete = doctor.exists() &&
                            !doctor.getString("name").isNullOrBlank() &&
                            !doctor.getString("specialty").isNullOrBlank() &&
                            !doctor.getString("city").isNullOrBlank()
                        if (!complete) onGo("profile")
                    }
                }
            }
            db.collection("config").document("admin").get().addOnSuccessListener { d ->
                isAdmin = d.getString("uid") == id
            }
        }
    }
    DisposableEffect(uid, type) {
        if (uid.isBlank() || (type != "doctor" && type != "clinic")) {
            subscriptionExpired = false
            onDispose { }
        } else {
            val registration = db.collection("subscriptions").document(uid)
                .addSnapshotListener { snapshot, error ->
                    if (error == null) {
                        val current = snapshot
                    val expiry = current?.getTimestamp("expiresAt")?.toDate()
                    subscriptionExpired = current == null || !current.exists() ||
                        current.getString("status") != "ACTIVE" ||
                        expiry == null || !expiry.after(java.util.Date())
                    }
                }
            onDispose { registration.remove() }
        }
    }
    val ns = notificationState
    var seen by remember(uid) { mutableStateOf(loadSeen(ctx, uid)) }
    var fresh by remember { mutableStateOf(setOf<String>()) }
    LaunchedEffect(ns.loaded, ns.items.size) {
        if (ns.loaded && seen == null) {
            val all = ns.items.map { it.key }.toSet()
            seen = all
            saveSeen(ctx, uid, all)
        }
    }
    val seenNow = seen
    val unread = if (seenNow == null) 0 else ns.items.count { it.key !in seenNow }
    LaunchedEffect(tab, ns.items.size) {
        val sn = seen
        if (tab == 4 && sn != null) {
            fresh = ns.items.map { it.key }.filter { it !in sn }.toSet() + fresh
            val all = sn + ns.items.map { it.key }
            if (all != sn) { seen = all; saveSeen(ctx, uid, all) }
        }
    }
    val typeLabel = when (type) {
        "doctor" -> stringResource(R.string.doctor)
        "clinic" -> stringResource(R.string.clinic)
        "patient" -> stringResource(R.string.patient)
        else -> ""
    }
    val itemColors = NavigationBarItemDefaults.colors(
        selectedIconColor = Red, selectedTextColor = Red, indicatorColor = Color.Transparent,
        unselectedIconColor = Muted, unselectedTextColor = Muted
    )
    Scaffold(
        containerColor = Bg,
        bottomBar = {
            NavigationBar(containerColor = CardC) {
                NavigationBarItem(tab == 0, { tab = 0 }, { Icon(Icons.Filled.Home, null) },
                    label = { Text(stringResource(R.string.nav_home)) }, colors = itemColors)
                NavigationBarItem(tab == 1, { tab = 1 }, { Icon(Icons.Filled.CalendarMonth, null) },
                    label = { Text(stringResource(R.string.nav_appts)) }, colors = itemColors)
                NavigationBarItem(false, { onGo("messages") }, { Icon(Icons.Filled.Email, null) },
                    label = { Text("الرسائل") }, colors = itemColors)
                NavigationBarItem(tab == 3, { tab = 3 }, { Icon(Icons.Filled.Person, null) },
                    label = { Text(stringResource(R.string.nav_profile)) }, colors = itemColors)
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                0 -> HomeTab(name, type, unread, onGo, onOpen, onTab = { tab = it })
                1 -> if (type == "doctor" || type == "clinic") DoctorDashboardContent()
                else MyAppointmentsContent(onFind = { onGo("search") })
                4 -> NotifTab(ns.items, fresh)
                else -> ProfileTab(name, email, typeLabel, onLogout, onPrivacy = { onGo("privacy") }, isAdmin = isAdmin, onAdmin = { onGo("admin") })
            }
            if (showFirstGuide && type.isNotBlank()) {
                FirstRunGuide(type) {
                    ctx.getSharedPreferences("sehati_first_run", android.content.Context.MODE_PRIVATE)
                        .edit().putBoolean("guide_seen_$uid", true).apply()
                    showFirstGuide = false
                }
            }
            if (subscriptionExpired && !showFirstGuide && (type == "doctor" || type == "clinic")) {
                AlertDialog(
                    onDismissRequest = { },
                    icon = { Icon(Icons.Filled.EventBusy, contentDescription = null, tint = Red) },
                    title = { Text("انتهى اشتراكك") },
                    text = { Text("انتهت مدة اشتراكك. يمكنك تجديده الآن، وستبقى بياناتك ومواعيدك محفوظة.") },
                    confirmButton = {
                        TextButton(onClick = { onGo("renew") }) { Text("تجديد الاشتراك") }
                    }
                )
            }
        }
    }
}

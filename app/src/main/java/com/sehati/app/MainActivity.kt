package com.sehati.app

import android.os.Bundle
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
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
        NotificationPermission.prepareAndRequest(this)
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
    LaunchedEffect(Unit) { delay(3100); splash = false }
    BackHandler(enabled = screen != "main") {
        screen = when (screen) {
            "detail" -> "search"
            "book" -> "detail"
            "chat" -> "messages"
            else -> "main"
        }
    }
    val sel = selected
    when {
        splash -> Splash()
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

@Composable
private fun startSplashHeartbeat(context: Context): () -> Unit {
    val sampleRate = 22050
    val totalSamples = (sampleRate * 2.9).toInt()
    val pcm = ShortArray(totalSamples)
    val pulses = listOf(0.10, 0.27, 0.92, 1.09, 1.74, 1.91, 2.56, 2.73)
    pulses.forEachIndexed { index, start ->
        val duration = if (index % 2 == 0) 0.13 else 0.11
        val startSample = (start * sampleRate).toInt()
        val count = (duration * sampleRate).toInt()
        for (i in 0 until count) {
            val t = i.toDouble() / sampleRate
            val envelope = exp(-19.0 * t) * (1.0 - exp(-90.0 * t))
            val frequency = if (index % 2 == 0) 58.0 else 72.0
            val wave = sin(2.0 * PI * frequency * t) * 0.72 +
                sin(2.0 * PI * frequency * 1.72 * t) * 0.28
            val at = startSample + i
            if (at in pcm.indices) {
                val mixed = pcm[at].toDouble() + wave * envelope * 10500.0
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
                    if (it.playState == AudioTrack.PLAYSTATE_PLAYING) it.stop()
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
        audio.setVolume(0.48f)
        val written = audio.write(pcm, 0, pcm.size)
        if (written > 0) audio.play() else release()
        Handler(Looper.getMainLooper()).postDelayed({ release() }, 3300L)
        ::release
    } catch (_: Exception) {
        release()
        {}
    }
}

@Composable
fun Splash() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var soundEnabled by remember { mutableStateOf(true) }
    val pulse = rememberInfiniteTransition(label = "heart-pulse")
    val heartScale by pulse.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(430, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "heart-scale"
    )
    val ringAlpha by pulse.animateFloat(
        initialValue = 0.12f,
        targetValue = 0.42f,
        animationSpec = infiniteRepeatable(
            animation = tween(650, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "heart-ring"
    )
    DisposableEffect(soundEnabled) {
        val stopSound = if (soundEnabled) startSplashHeartbeat(context) else null
        onDispose { stopSound?.invoke() }
    }
    Column(
        Modifier.fillMaxSize().background(Bg).padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier.size(250.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier.size(222.dp)
                    .border(2.dp, Red.copy(alpha = ringAlpha), CircleShape)
            )
            Box(
                Modifier.size(194.dp)
                    .border(1.dp, Red.copy(alpha = ringAlpha * 0.7f), CircleShape)
            )
            Image(
                painterResource(R.drawable.ic_launcher),
                contentDescription = "شعار الطريق إلى صحتي",
                modifier = Modifier.size(166.dp).scale(heartScale)
            )
        }
        Spacer(Modifier.height(18.dp))
        Text(
            "الطريق إلى صحتي",
            color = Color.White,
            fontSize = 25.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.tagline),
            color = Muted,
            fontSize = 16.sp,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(25.dp))
        androidx.compose.foundation.Canvas(
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            val mid = size.height / 2f
            val points = listOf(
                0.00f to 0.50f, 0.12f to 0.50f, 0.19f to 0.50f,
                0.24f to 0.18f, 0.29f to 0.88f, 0.35f to 0.08f,
                0.41f to 0.50f, 0.53f to 0.50f, 0.60f to 0.50f,
                0.65f to 0.18f, 0.70f to 0.88f, 0.76f to 0.08f,
                0.82f to 0.50f, 1.00f to 0.50f
            )
            val path = androidx.compose.ui.graphics.Path()
            points.forEachIndexed { index, point ->
                val x = size.width * point.first
                val y = mid + (point.second - 0.5f) * size.height * 0.9f
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(
                path = path,
                color = Red.copy(alpha = 0.9f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.5f)
            )
        }
        Spacer(Modifier.height(20.dp))
        LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth(0.62f),
            color = Red,
            trackColor = Line
        )
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.loading), color = Muted, fontSize = 13.sp)
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = { soundEnabled = !soundEnabled }) {
            Text(
                if (soundEnabled) "كتم صوت النبض" else "تشغيل صوت النبض",
                color = Muted,
                fontSize = 13.sp
            )
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

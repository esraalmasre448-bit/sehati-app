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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
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
import androidx.compose.ui.graphics.CompositingStrategy
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
            val frequency = if (index % 2 == 0) 68.0 else 88.0
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
        audio.setVolume(0.62f)
        val written = audio.write(pcm, 0, pcm.size)
        if (written > 0) audio.play() else release()
        Handler(Looper.getMainLooper()).postDelayed({ release() }, 3300L)
        ::release
    } catch (_: Exception) {
        release()
        val noOp: () -> Unit = {}
        noOp
    }
}

@Composable
fun Splash() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val motion = rememberInfiniteTransition(label = "cinematic-splash")
    var soundEnabled by remember { mutableStateOf(true) }
    val logoScale by motion.animateFloat(
        initialValue = 0.90f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "logo-scale"
    )
    val logoTilt by motion.animateFloat(
        initialValue = -2.2f, targetValue = 2.2f,
        animationSpec = infiniteRepeatable(tween(3600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "logo-tilt"
    )
    val halo by motion.animateFloat(
        initialValue = 0.18f, targetValue = 0.42f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "halo"
    )
    val sweep by motion.animateFloat(
        initialValue = -0.2f, targetValue = 1.2f,
        animationSpec = infiniteRepeatable(tween(2800, easing = androidx.compose.animation.core.LinearEasing)),
        label = "light-sweep"
    )
    val pulse by motion.animateFloat(
        initialValue = 0.28f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(620, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "heartbeat"
    )
    val titleReveal by androidx.compose.animation.core.animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(1250, delayMillis = 650, easing = FastOutSlowInEasing),
        label = "title-reveal"
    )
    DisposableEffect(soundEnabled) {
        val stopSound = if (soundEnabled) startSplashHeartbeat(context) else null
        onDispose { stopSound?.invoke() }
    }
    Box(
        Modifier.fillMaxSize().background(
            androidx.compose.ui.graphics.Brush.verticalGradient(
                listOf(Color(0xFF07101D), Color(0xFF102A42), Color(0xFF050A12))
            )
        )
    ) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height * 0.39f)
            val radius = size.width * 0.62f
            drawCircle(
                brush = androidx.compose.ui.graphics.Brush.radialGradient(
                    listOf(Color((0x1838BDF8 + (halo * 0x1838).toInt()).toLong()), Color.Transparent),
                    center, radius
                ),
                radius = radius, center = center
            )
            val beamY = size.height * sweep.coerceIn(0f, 1f)
            drawRect(
                brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                    listOf(Color.Transparent, Color(0x2238BDF8), Color.Transparent),
                    beamY - 70f, beamY + 70f
                ),
                topLeft = Offset(0f, beamY - 70f), size = Size(size.width, 140f)
            )
            for (i in 0 until 26) {
                val phase = i * 0.71f + sweep * 2.4f
                val x = size.width * (0.07f + ((i * 31) % 86) / 100f)
                val y = size.height * (0.13f + ((i * 23) % 73) / 100f)
                val drift = kotlin.math.sin(phase) * 10f
                drawCircle(
                    color = if (i % 5 == 0) Color(0x99FF5268) else Color(0x775BC8F2),
                    radius = if (i % 5 == 0) 2.4f else 1.5f,
                    center = Offset(x + drift, y)
                )
            }
        }
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 26.dp, vertical = 34.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(Modifier.weight(0.8f))
            Box(Modifier.size(286.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(270.dp).background(
                        androidx.compose.ui.graphics.Brush.radialGradient(
                            listOf(Color(0x4438BDF8), Color(0x1838BDF8), Color.Transparent)
                        ), CircleShape
                    )
                )
                Box(
                    Modifier.size(238.dp).graphicsLayer {
                        shadowElevation = 34.dp.toPx()
                        shape = CircleShape
                        clip = false
                    }.background(Color(0xFF071321), CircleShape)
                    .border(1.dp, Color(0x6649BFF2), CircleShape)
                )
                Image(
                    painter = painterResource(R.drawable.ic_launcher),
                    contentDescription = "شعار الطريق إلى صحتي",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(224.dp).graphicsLayer {
                        scaleX = logoScale
                        scaleY = logoScale
                        rotationY = logoTilt
                        rotationX = -logoTilt * 0.45f
                        cameraDistance = 22f * density
                        shadowElevation = 30.dp.toPx()
                        shape = CircleShape
                    }
                )
                // Moving specular highlight adds depth without changing the approved logo artwork.
                Box(
                    Modifier.size(224.dp).graphicsLayer {
                        alpha = 0.10f + halo * 0.12f
                        rotationZ = -18f
                        translationX = (sweep - 0.5f) * 46.dp.toPx()
                    }.background(
                        androidx.compose.ui.graphics.Brush.horizontalGradient(
                            listOf(Color.Transparent, Color(0x88FFFFFF), Color.Transparent)
                        ),
                        RoundedCornerShape(50)
                    )
                )
            }
            Spacer(Modifier.height(18.dp))
            Text(
                "الطريق إلى صحتي",
                modifier = Modifier.graphicsLayer {
                    alpha = titleReveal
                    translationY = (1f - titleReveal) * 28.dp.toPx()
                    shadowElevation = 14.dp.toPx()
                },
                color = Color.White,
                fontSize = 29.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.2.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "صحتك.. أولويتي",
                modifier = Modifier.graphicsLayer {
                    alpha = titleReveal * 0.88f
                    translationY = (1f - titleReveal) * 18.dp.toPx()
                },
                color = Color(0xFFB9D5E8),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(26.dp))
            androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(38.dp)) {
                val mid = size.height / 2f
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(0f, mid)
                    lineTo(size.width * 0.25f, mid)
                    lineTo(size.width * 0.31f, mid)
                    lineTo(size.width * 0.37f, size.height * 0.12f)
                    lineTo(size.width * 0.43f, size.height * 0.90f)
                    lineTo(size.width * 0.49f, size.height * 0.22f)
                    lineTo(size.width * 0.53f, mid)
                    lineTo(size.width * 0.57f, mid)
                    lineTo(size.width * 0.63f, size.height * 0.30f)
                    lineTo(size.width * 0.69f, size.height * 0.84f)
                    lineTo(size.width * 0.75f, mid)
                    lineTo(size.width, mid)
                }
                drawPath(path, Color(0x55FF5268), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 11f))
                drawPath(path, Color(0xFFFF5268).copy(alpha = pulse), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.2f))
            }
            Spacer(Modifier.height(24.dp))
            Text(
                "جارٍ تجهيز تجربتك الصحية",
                color = Color(0xFFB5C9D9),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.3.sp
            )
            Spacer(Modifier.height(14.dp))
            LinearProgressIndicator(
                modifier = Modifier.width(116.dp).height(2.dp),
                color = Color(0xFF56C9F5),
                trackColor = Color(0x3356C9F5)
            )
            Spacer(Modifier.weight(0.8f))
        }
        Text(
            "CARE  •  CONNECT  •  LIVE HEALTHIER",
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 22.dp),
            color = Color(0x557FA6C0),
            fontSize = 8.sp,
            letterSpacing = 2.sp
        )
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

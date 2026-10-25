package com.sehati.app

import android.os.Bundle
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

private val Bg = Color(0xFF0B1220)
private val CardC = Color(0xFF131C2E)
private val Line = Color(0xFF24304A)
private val Red = Color(0xFFE5171F)
private val Danger = Color(0xFFFF5A5F)
private val Good = Color(0xFF3DDC84)
private val OnAccent = Color(0xFFFFFFFF)
private val Muted = Color(0xFF9AA7BD)

private val auth get() = FirebaseAuth.getInstance()
private val db get() = FirebaseFirestore.getInstance()

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val scheme = darkColorScheme(
                primary = Red, onPrimary = OnAccent, secondary = Red,
                secondaryContainer = Color(0xFF3A1519), onSecondaryContainer = Color.White,
                background = Bg, surface = CardC,
                onSurface = Color.White, onBackground = Color.White
            )
            MaterialTheme(colorScheme = scheme) {
                Surface(Modifier.fillMaxSize(), color = Bg) { App() }
            }
        }
    }
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
    LaunchedEffect(Unit) { delay(1500); splash = false }
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
                AppLockStore.clear(ctx)
                auth.signOut()
                loggedIn = false
                locked = false
                screen = "main"
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
            onOpen = { selected = it; screen = "detail" },
            onGo = {
                if (it.startsWith("search:")) { initialQuery = it.removePrefix("search:"); screen = "search" }
                else { if (it == "search") initialQuery = ""; screen = it }
            },
            onLogout = {
                AppLockStore.clear(ctx)
                auth.signOut()
                loggedIn = false
                locked = false
                screen = "main"
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
fun Splash() {
    Column(
        Modifier.fillMaxSize().background(Bg).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(painterResource(R.drawable.ic_launcher), null, Modifier.size(230.dp))
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.tagline), color = Muted, fontSize = 16.sp)
        Spacer(Modifier.height(32.dp))
        LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth(0.6f), color = Red, trackColor = Line
        )
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.loading), color = Muted, fontSize = 13.sp)
    }
}

@Composable
fun MainShell(onGo: (String) -> Unit, onOpen: (Map<String, Any?>) -> Unit, onLogout: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("") }
    var isAdmin by remember { mutableStateOf(false) }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val uid = auth.currentUser?.uid ?: ""
    val email = auth.currentUser?.email ?: ""
    LaunchedEffect(Unit) {
        auth.currentUser?.uid?.let { id ->
            db.collection("users").document(id).get().addOnSuccessListener { d ->
                name = d.getString("name") ?: ""
                type = d.getString("accountType") ?: ""
            }
            db.collection("config").document("admin").get().addOnSuccessListener { d ->
                isAdmin = d.getString("uid") == id
            }
        }
    }
    val ns = rememberNotifs(uid, type)
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
        if (tab == 2 && sn != null) {
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
                NavigationBarItem(tab == 2, { tab = 2 },
                    {
                        BadgedBox(badge = { if (unread > 0 && tab != 2) Badge { Text("$unread") } }) {
                            Icon(Icons.Filled.Notifications, null)
                        }
                    },
                    label = { Text("الإشعارات") }, colors = itemColors)
                NavigationBarItem(tab == 3, { tab = 3 }, { Icon(Icons.Filled.Person, null) },
                    label = { Text(stringResource(R.string.nav_profile)) }, colors = itemColors)
                if (isAdmin) {
                    NavigationBarItem(
                        selected = false,
                        onClick = { onGo("admin") },
                        icon = { Icon(Icons.Filled.Code, null) },
                        label = { Text("المطوّر") },
                        colors = itemColors
                    )
                }
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                0 -> HomeTab(name, type, unread, onGo, onOpen, onTab = { tab = it })
                1 -> if (type == "doctor" || type == "clinic") DoctorDashboardContent()
                else MyAppointmentsContent(onFind = { onGo("search") })
                2 -> NotifTab(ns.items, fresh)
                else -> ProfileTab(name, email, typeLabel, onLogout, onPrivacy = { onGo("privacy") })
            }
        }
    }
}

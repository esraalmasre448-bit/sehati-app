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
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.delay

private val Bg = Color(0xFF0A0C11)
private val CardC = Color(0xFF14171D)
private val Line = Color(0xFF2A2F3A)
private val Red = Color(0xFFE5171F)
private val Muted = Color(0xFF9AA0AB)

private val auth get() = FirebaseAuth.getInstance()
private val db get() = FirebaseFirestore.getInstance()

private fun authError(e: Exception): String = when (e) {
    is FirebaseAuthWeakPasswordException ->
        "كلمة السر ضعيفة، استخدم 6 أحرف على الأقل"

    is FirebaseAuthInvalidUserException ->
        "لا يوجد حساب بهذا البريد"

    is FirebaseAuthInvalidCredentialsException ->
        "البريد أو كلمة السر غير صحيحة"

    is FirebaseAuthUserCollisionException ->
        "هذا البريد مسجّل مسبقًا، سجّل دخولك"

    is FirebaseNetworkException ->
        "تحقق من اتصال الإنترنت وحاول مرة أخرى"

    else ->
        if (e.message?.contains("API key", true) == true)
            "تعذر الاتصال بالخدمة (رمز: API_KEY). ثبّت آخر نسخة من التطبيق"
        else
            "تعذر إتمام العملية، حاول مرة أخرى"
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val scheme = darkColorScheme(
                primary = Red,
                background = Bg,
                surface = CardC,
                onSurface = Color.White,
                onBackground = Color.White
            )

            MaterialTheme(colorScheme = scheme) {
                Surface(
                    Modifier.fillMaxSize(),
                    color = Bg
                ) {
                    App()
                }
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

    LaunchedEffect(Unit) {
        delay(1500)
        splash = false
    }

    BackHandler(enabled = screen != "main") {
        screen = when (screen) {
            "detail" -> "search"
            "book" -> "detail"
            else -> "main"
        }
    }

    val sel = selected

    when {
        splash -> Splash()

        !loggedIn -> AuthFlow {
            loggedIn = true
        }

        screen == "search" -> DoctorSearchScreen(
            onBack = { screen = "main" },
            onOpen = {
                selected = it
                screen = "detail"
            }
        )

        screen == "detail" && sel != null -> DoctorDetailFull(
            sel,
            onBack = { screen = "search" },
            onBook = { screen = "book" }
        )

        screen == "book" && sel != null -> QueueBookingScreen(
            sel,
            onBack = { screen = "detail" },
            onDone = { screen = "myappts" }
        )

        screen == "myappts" -> MyAppointmentsContent(
            onFind = { screen = "search" },
            onBack = { screen = "main" }
        )

        screen == "queue" -> QueueScreen(
            onBack = { screen = "main" },
            onFind = { screen = "search" }
        )

        screen == "route" -> RouteScreen(
            onBack = { screen = "main" },
            onFind = { screen = "search" }
        )

        screen == "profile" -> DoctorProfileScreen {
            screen = "main"
        }

        screen == "settings" -> ClinicSettingsScreen {
            screen = "main"
        }

        screen == "dash" -> DoctorDashboardContent(
            onBack = { screen = "main" }
        )

        screen == "admin" -> AdminScreen {
            screen = "main"
        }

        else -> MainShell(
            onGo = { screen = it },
            onLogout = {
                auth.signOut()
                loggedIn = false
                screen = "main"
            }
        )
    }
}

@Composable
fun Splash() {
    Column(
        Modifier
            .fillMaxSize()
            .background(Bg)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painterResource(R.drawable.ic_launcher),
            null,
            Modifier.size(230.dp)
        )

        Spacer(Modifier.height(16.dp))

        Text(
            stringResource(R.string.tagline),
            color = Muted,
            fontSize = 16.sp
        )

        Spacer(Modifier.height(32.dp))

        LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth(0.6f),
            color = Red,
            trackColor = Line
        )

        Spacer(Modifier.height(8.dp))

        Text(
            stringResource(R.string.loading),
            color = Muted,
            fontSize = 13.sp
        )
    }
}

@Composable
fun Field(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    password: Boolean = false,
    keyboard: KeyboardType = KeyboardType.Text
) {
    var show by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = {
            Text(label, color = Muted)
        },
        singleLine = true,
        visualTransformation =
            if (password && !show)
                PasswordVisualTransformation()
            else
                VisualTransformation.None,

        keyboardOptions = KeyboardOptions(
            keyboardType =
                if (password)
                    KeyboardType.Password
                else
                    keyboard
        ),

        trailingIcon =
            if (password) {
                {
                    IconButton(
                        onClick = { show = !show }
                    ) {
                        Icon(
                            if (show)
                                Icons.Filled.VisibilityOff
                            else
                                Icons.Filled.Visibility,
                            contentDescription = null,
                            tint = Muted
                        )
                    }
                }
            } else {
                null
            },

        shape = RoundedCornerShape(12.dp),

        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Red,
            unfocusedBorderColor = Line,
            focusedContainerColor = CardC,
            unfocusedContainerColor = CardC,
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            cursorColor = Red
        ),

        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
fun RedButton(
    text: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = Red,
            contentColor = Color.White
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
    ) {
        Text(
            text,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun AuthFlow(onDone: () -> Unit) {
    var signup by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("patient") }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }

    val errGeneric = stringResource(R.string.error_generic)
    val enterBoth = stringResource(R.string.enter_email_pass)
    val enterName = stringResource(R.string.enter_name)
    val resetSent = stringResource(R.string.reset_sent)

    Column(
        Modifier
            .fillMaxSize()
            .background(Bg)
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Image(
            painterResource(R.drawable.ic_launcher),
            null,
            Modifier
                .size(150.dp)
                .align(Alignment.CenterHorizontally)
        )

        Text(
            stringResource(R.string.welcome_back),
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        Text(
            stringResource(R.string.login_sub),
            color = Muted
        )

        if (signup) {
            Field(
                name,
                { name = it },
                stringResource(R.string.name)
            )

            Field(
                phone,
                { phone = it },
                stringResource(R.string.phone),
                keyboard = KeyboardType.Phone
            )

            Text(
                stringResource(R.string.account_type),
                color = Muted
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "patient" to R.string.patient,
                    "doctor" to R.string.doctor,
                    "clinic" to R.string.clinic
                ).forEach { (k, label) ->

                    FilterChip(
                        selected = type == k,
                        onClick = { type = k },
                        label = {
                            Text(stringResource(label))
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = CardC,
                            labelColor = Color.White,
                            selectedContainerColor = Red,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }
        }

        Field(
            email,
            { email = it },
            stringResource(R.string.email),
            keyboard = KeyboardType.Email
        )

        Field(
            pass,
            { pass = it },
            stringResource(R.string.password),
            password = true
        )

        if (!signup) {
            TextButton(
                onClick = {
                    if (email.isBlank()) {
                        msg = enterBoth
                    } else {
                        auth.sendPasswordResetEmail(email.trim())
                            .addOnSuccessListener {
                                msg = resetSent
                            }
                            .addOnFailureListener {
                                msg = authError(it)
                            }
                    }
                }
            ) {
                Text(
                    stringResource(R.string.forgot),
                    color = Red
                )
            }
        }

        if (msg.isNotEmpty()) {
            Text(
                msg,
                color = Red
            )
        }

        RedButton(
            text = stringResource(
                if (signup)
                    R.string.signup_btn
                else
                    R.string.login
            ),
            enabled = !busy
        ) {
            msg = ""

            val e = email.trim()

            if (e.isBlank() || pass.isBlank()) {
                msg = enterBoth
            } else if (signup && name.isBlank()) {
                msg = enterName
            } else {
                busy = true

                if (signup) {
                    auth.createUserWithEmailAndPassword(
                        e,
                        pass
                    ).addOnSuccessListener { r ->

                        db.collection("users")
                            .document(r.user!!.uid)
                            .set(
                                mapOf(
                                    "name" to name.trim(),
                                    "phone" to phone.trim(),
                                    "email" to e,
                                    "accountType" to type,
                                    "createdAt" to FieldValue.serverTimestamp()
                                )
                            )
                            .addOnSuccessListener {
                                busy = false
                                onDone()
                            }
                            .addOnFailureListener {
                                busy = false
                                msg = errGeneric
                            }

                    }.addOnFailureListener {
                        busy = false
                        msg = authError(it)
                    }

                } else {
                    auth.signInWithEmailAndPassword(
                        e,
                        pass
                    )
                        .addOnSuccessListener {
                            busy = false
                            onDone()
                        }
                        .addOnFailureListener {
                            busy = false
                            msg = authError(it)
                        }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HorizontalDivider(
                Modifier.weight(1f),
                color = Line
            )

            Text(
                stringResource(R.string.or),
                color = Muted,
                modifier = Modifier.padding(horizontal = 12.dp)
            )

            HorizontalDivider(
                Modifier.weight(1f),
                color = Line
            )
        }

        OutlinedButton(
            onClick = {
                signup = !signup
                msg = ""
            },
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                Line
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Text(
                stringResource(
                    if (signup)
                        R.string.have_account
                    else
                        R.string.create_account
                ),
                color = Color.White,
                fontSize = 16.sp
            )
        }
    }
}

@Composable
fun MainShell(
    onGo: (String) -> Unit,
    onLogout: () -> Unit
) {
    var tab by remember { mutableIntStateOf(0) }
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("") }

    val email = auth.currentUser?.email ?: ""

    LaunchedEffect(Unit) {
        auth.currentUser?.uid?.let { uid ->
            db.collection("users")
                .document(uid)
                .get()
                .addOnSuccessListener { d ->
                    name = d.getString("name") ?: ""
                    type = d.getString("accountType") ?: ""
                }
        }
    }

    val typeLabel = when (type) {
        "doctor" -> stringResource(R.string.doctor)
        "clinic" -> stringResource(R.string.clinic)
        "patient" -> stringResource(R.string.patient)
        else -> ""
    }

    val itemColors = NavigationBarItemDefaults.colors(
        selectedIconColor = Red,
        selectedTextColor = Red,
        indicatorColor = Color.Transparent,
        unselectedIconColor = Muted,
        unselectedTextColor = Muted
    )

    Scaffold(
        containerColor = Bg,

        bottomBar = {
            NavigationBar(
                containerColor = CardC
            ) {
                NavigationBarItem(
                    tab == 0,
                    { tab = 0 },
                    {
                        Icon(
                            Icons.Filled.Home,
                            null
                        )
                    },
                    label = {
                        Text(stringResource(R.string.nav_home))
                    },
                    colors = itemColors
                )

                NavigationBarItem(
                    tab == 1,
                    { tab = 1 },
                    {
                        Icon(
                            Icons.Filled.CalendarMonth,
                            null
                        )
                    },
                    label = {
                        Text(stringResource(R.string.nav_appts))
                    },
                    colors = itemColors
                )

                NavigationBarItem(
                    tab == 2,
                    { tab = 2 },
                    {
                        Icon(
                            Icons.Filled.Person,
                            null
                        )
                    },
                    label = {
                        Text(stringResource(R.string.nav_profile))
                    },
                    colors = itemColors
                )
            }
        }
    ) { pad ->

        Box(
            Modifier
                .padding(pad)
                .fillMaxSize()
        ) {
            when (tab) {
                0 -> HomeTab(
                    name,
                    type,
                    onGo
                )

                1 ->
                    if (type == "doctor" || type == "clinic") {
                        DoctorDashboardContent()
                    } else {
                        MyAppointmentsContent(
                            onFind = {
                                onGo("search")
                            }
                        )
                    }

                else -> Profile

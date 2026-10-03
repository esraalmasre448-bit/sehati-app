package com.sehati.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val Bg = Color(0xFF0A0C11)
private val CardC = Color(0xFF14171D)
private val Line = Color(0xFF2A2F3A)
private val Red = Color(0xFFE5171F)
private val Muted = Color(0xFF9AA0AB)

private val auth get() = FirebaseAuth.getInstance()
private val db get() = FirebaseFirestore.getInstance()

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val scheme = darkColorScheme(
                primary = Red, background = Bg, surface = CardC,
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
    LaunchedEffect(Unit) { delay(1500); splash = false }
    when {
        splash -> Splash()
        !loggedIn -> AuthFlow { loggedIn = true }
        else -> MainShell { auth.signOut(); loggedIn = false }
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
fun Field(
    value: String, onChange: (String) -> Unit, label: String,
    password: Boolean = false, keyboard: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value = value, onValueChange = onChange,
        label = { Text(label, color = Muted) },
        singleLine = true,
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else keyboard),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Red, unfocusedBorderColor = Line,
            focusedContainerColor = CardC, unfocusedContainerColor = CardC,
            focusedTextColor = Color.White, unfocusedTextColor = Color.White,
            cursorColor = Red
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
fun RedButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick, enabled = enabled,
        colors = ButtonDefaults.buttonColors(containerColor = Red, contentColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().height(52.dp)
    ) { Text(text, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
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
        Modifier.fillMaxSize().background(Bg).padding(24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Image(
            painterResource(R.drawable.ic_launcher), null,
            Modifier.size(150.dp).align(Alignment.CenterHorizontally)
        )
        Text(stringResource(R.string.welcome_back), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text(stringResource(R.string.login_sub), color = Muted)
        if (signup) {
            Field(name, { name = it }, stringResource(R.string.name))
            Field(phone, { phone = it }, stringResource(R.string.phone), keyboard = KeyboardType.Phone)
            Text(stringResource(R.string.account_type), color = Muted)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "patient" to R.string.patient,
                    "doctor" to R.string.doctor,
                    "clinic" to R.string.clinic
                ).forEach { (k, label) ->
                    FilterChip(
                        selected = type == k, onClick = { type = k },
                        label = { Text(stringResource(label)) },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = CardC, labelColor = Color.White,
                            selectedContainerColor = Red, selectedLabelColor = Color.White
                        )
                    )
                }
            }
        }
        Field(email, { email = it }, stringResource(R.string.email), keyboard = KeyboardType.Email)
        Field(pass, { pass = it }, stringResource(R.string.password), password = true)
        if (!signup) {
            TextButton(onClick = {
                if (email.isBlank()) msg = enterBoth
                else auth.sendPasswordResetEmail(email.trim())
                    .addOnSuccessListener { msg = resetSent }
                    .addOnFailureListener { msg = errGeneric }
            }) { Text(stringResource(R.string.forgot), color = Red) }
        }
        if (msg.isNotEmpty()) Text(msg, color = Red)
        RedButton(
            text = stringResource(if (signup) R.string.signup_btn else R.string.login),
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
                    auth.createUserWithEmailAndPassword(e, pass).addOnSuccessListener { r ->
                        db.collection("users").document(r.user!!.uid).set(
                            mapOf(
                                "name" to name.trim(), "phone" to phone.trim(), "email" to e,
                                "accountType" to type, "createdAt" to FieldValue.serverTimestamp()
                            )
                        ).addOnSuccessListener { busy = false; onDone() }
                            .addOnFailureListener { busy = false; msg = errGeneric }
                    }.addOnFailureListener { busy = false; msg = it.localizedMessage ?: errGeneric }
                } else {
                    auth.signInWithEmailAndPassword(e, pass)
                        .addOnSuccessListener { busy = false; onDone() }
                        .addOnFailureListener { busy = false; msg = it.localizedMessage ?: errGeneric }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            HorizontalDivider(Modifier.weight(1f), color = Line)
            Text(stringResource(R.string.or), color = Muted, modifier = Modifier.padding(horizontal = 12.dp))
            HorizontalDivider(Modifier.weight(1f), color = Line)
        }
        OutlinedButton(
            onClick = { signup = !signup; msg = "" },
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Line),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Text(
                stringResource(if (signup) R.string.have_account else R.string.create_account),
                color = Color.White, fontSize = 16.sp
            )
        }
    }
}

@Composable
fun MainShell(onLogout: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("") }
    val email = auth.currentUser?.email ?: ""
    val host = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val soon = stringResource(R.string.soon)
    LaunchedEffect(Unit) {
        auth.currentUser?.uid?.let { uid ->
            db.collection("users").document(uid).get().addOnSuccessListener { d ->
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
        selectedIconColor = Red, selectedTextColor = Red, indicatorColor = Color.Transparent,
        unselectedIconColor = Muted, unselectedTextColor = Muted
    )
    Scaffold(
        containerColor = Bg,
        snackbarHost = { SnackbarHost(host) },
        bottomBar = {
            NavigationBar(containerColor = CardC) {
                NavigationBarItem(tab == 0, { tab = 0 }, { Icon(Icons.Filled.Home, null) },
                    label = { Text(stringResource(R.string.nav_home)) }, colors = itemColors)
                NavigationBarItem(tab == 1, { tab = 1 }, { Icon(Icons.Filled.CalendarMonth, null) },
                    label = { Text(stringResource(R.string.nav_appts)) }, colors = itemColors)
                NavigationBarItem(tab == 2, { tab = 2 }, { Icon(Icons.Filled.Person, null) },
                    label = { Text(stringResource(R.string.nav_profile)) }, colors = itemColors)
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                0 -> HomeTab(name) { scope.launch { host.showSnackbar(soon) } }
                1 -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(soon, color = Muted, textAlign = TextAlign.Center, modifier = Modifier.padding(32.dp))
                }
                else -> ProfileTab(name, email, typeLabel, onLogout)
            }
        }
    }
}

@Composable
fun HomeTab(name: String, onSoon: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Image(painterResource(R.drawable.ic_launcher), null, Modifier.size(90.dp).align(Alignment.CenterHorizontally))
        Text("${stringResource(R.string.hello)} $name", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text(stringResource(R.string.home_sub), color = Muted)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Tile(Icons.Filled.Search, stringResource(R.string.find_doctor), true, Modifier.weight(1f), onSoon)
            Tile(Icons.Filled.CalendarMonth, stringResource(R.string.book_appt), false, Modifier.weight(1f), onSoon)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Tile(Icons.Filled.Person, stringResource(R.string.my_turn), false, Modifier.weight(1f), onSoon)
            Tile(Icons.Filled.LocationOn, stringResource(R.string.way_to_clinic), false, Modifier.weight(1f), onSoon)
        }
    }
}

@Composable
fun Tile(icon: ImageVector, text: String, red: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .height(130.dp)
            .background(if (red) Red else CardC, RoundedCornerShape(16.dp))
            .border(1.dp, if (red) Red else Line, RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(36.dp))
        Spacer(Modifier.height(8.dp))
        Text(text, color = Color.White, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    }
}

@Composable
fun ProfileTab(name: String, email: String, typeLabel: String, onLogout: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Spacer(Modifier.height(16.dp))
        Box(
            Modifier.size(84.dp).background(CardC, CircleShape).border(1.dp, Line, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(name.take(1).uppercase(), fontSize = 34.sp, color = Color.White, fontWeight = FontWeight.Bold)
        }
        Text(name, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text(email, color = Muted)
        Text(typeLabel, color = Red)
        Spacer(Modifier.height(24.dp))
        RedButton(stringResource(R.string.logout), onClick = onLogout)
    }
}

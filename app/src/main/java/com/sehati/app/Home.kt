package com.sehati.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun HomeTab(name: String, type: String, onGo: (String) -> Unit) {
    var taps by remember { mutableIntStateOf(0) }
    var last by remember { mutableLongStateOf(0L) }
    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Image(
            painterResource(R.drawable.ic_launcher), null,
            Modifier.size(90.dp).align(Alignment.CenterHorizontally).clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                val now = System.currentTimeMillis()
                taps = if (now - last < 1500) taps + 1 else 1
                last = now
                if (taps >= 4) { taps = 0; onGo("admin") }
            }
        )
        Text("مرحباً بك $name", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text(
            when (type) {
                "doctor", "clinic" -> "مريضك أخوك، فارحم وجعه وقدّر ظروفه."
                "patient" -> "لأن صحتك أولويتنا، نحن معك."
                else -> stringResource(R.string.home_sub)
            },
            color = BMuted
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
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickBtn(Icons.Filled.Warning, "الطوارئ", BDanger, Modifier.weight(1f)) { onGo("emergency") }
            QuickBtn(Icons.Filled.LocalHospital, "العيادات", BRed, Modifier.weight(1f)) { onGo("search:@clinics") }
            QuickBtn(Icons.Filled.MedicalServices, "الأطباء", BRed, Modifier.weight(1f)) { onGo("search:@doctors") }
        }
        Text("تخصصات شائعة", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        COMMON_SPECIALTIES.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { sp -> SpecCircle(sp, Modifier.weight(1f)) { onGo("search:" + sp.name) } }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        OutlinedButton(
            onClick = { onGo("specs") },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, BRed)
        ) { Text("عرض كل التخصصات", color = Color.White) }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Tile(Icons.Filled.Search, stringResource(R.string.find_doctor), true, Modifier.weight(1f)) { onGo("search") }
            Tile(Icons.Filled.CalendarMonth, stringResource(R.string.book_appt), false, Modifier.weight(1f)) { onGo("search") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Tile(Icons.Filled.Person, stringResource(R.string.my_turn), false, Modifier.weight(1f)) { onGo("queue") }
            Tile(Icons.Filled.LocationOn, stringResource(R.string.way_to_clinic), false, Modifier.weight(1f)) { onGo("route") }
        }
        if (type == "doctor" || type == "clinic") {
            Tile(Icons.Filled.Person, "ملفي المهني", true, Modifier.fillMaxWidth()) { onGo("profile") }
            Tile(Icons.Filled.LocationOn, "إعدادات العيادة (الدوام والموقع)", false, Modifier.fillMaxWidth()) { onGo("settings") }
            Tile(Icons.Filled.CalendarMonth, "لوحتي — الحجوزات والدور", false, Modifier.fillMaxWidth()) { onGo("dash") }
            Tile(Icons.Filled.Info, "تجديد الاشتراك والتواصل مع الإدارة", false, Modifier.fillMaxWidth()) { onGo("renew") }
        }
    }
}

@Composable
fun QuickBtn(icon: ImageVector, text: String, tint: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .height(92.dp)
            .background(BCard, RoundedCornerShape(16.dp))
            .border(1.dp, BLine, RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(30.dp))
        Spacer(Modifier.height(6.dp))
        Text(text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, textAlign = TextAlign.Center)
    }
}

@Composable
fun SpecCircle(sp: Spec, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.clickable { onClick() }.padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(56.dp).background(BCard, CircleShape).border(1.dp, BRed, CircleShape),
            contentAlignment = Alignment.Center
        ) { Icon(sp.icon, null, tint = Color.White, modifier = Modifier.size(26.dp)) }
        Spacer(Modifier.height(6.dp))
        Text(sp.name, color = Color.White, fontSize = 12.sp, textAlign = TextAlign.Center, maxLines = 2)
    }
}

@Composable
fun Tile(icon: ImageVector, text: String, red: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .height(130.dp)
            .background(if (red) BRed else BCard, RoundedCornerShape(16.dp))
            .border(1.dp, if (red) BRed else BLine, RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, null, tint = if (red) BOnAccent else Color.White, modifier = Modifier.size(36.dp))
        Spacer(Modifier.height(8.dp))
        Text(text, color = if (red) BOnAccent else Color.White, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    }
}

@Composable
fun ProfileTab(name: String, email: String, typeLabel: String, onLogout: () -> Unit, onPrivacy: () -> Unit = {}) {
    Column(
        Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Spacer(Modifier.height(16.dp))
        Box(
            Modifier.size(84.dp).background(BCard, CircleShape).border(1.dp, BLine, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(name.take(1).uppercase(), fontSize = 34.sp, color = Color.White, fontWeight = FontWeight.Bold)
        }
        Text(name, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text(email, color = BMuted)
        Text(typeLabel, color = BRed)
        Spacer(Modifier.height(24.dp))
        OutlinedButton(onClick = onPrivacy, modifier = Modifier.fillMaxWidth().height(48.dp)) {
            Text("سياسة الخصوصية", color = Color.White)
        }
        RedButton(stringResource(R.string.logout), onClick = onLogout)
        Spacer(Modifier.height(8.dp))
        DeleteAccountSection(onDeleted = onLogout)
    }
}

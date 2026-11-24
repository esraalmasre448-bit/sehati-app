package com.sehati.app

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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class PatientServiceItem(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val destination: String,
    val accent: Color
)

/** مركز موحد يربط المريض بالخدمات الموجودة فعلياً داخل التطبيق. */
@Composable
fun PatientServicesHub(onBack: () -> Unit, onNavigate: (String) -> Unit) {
    val groups = listOf(
        "الحجز والرعاية" to listOf(
            PatientServiceItem("ابحث عن طبيب", "حسب التخصص والمدينة", Icons.Filled.Search, "search", Color(0xFFE5171F)),
            PatientServiceItem("مواعيدي", "الحجوزات وحالات الموافقة", Icons.Filled.CalendarMonth, "myappts", Color(0xFF4DA3FF)),
            PatientServiceItem("الدور والانتظار", "تابع رقمك والدور الحالي", Icons.Filled.Groups, "queue", Color(0xFF39D6A3)),
            PatientServiceItem("اتجاهات العيادة", "الوصول إلى موقع الطبيب", Icons.Filled.Directions, "route", Color(0xFFB38BFF))
        ),
        "ملفي الصحي" to listOf(
            PatientServiceItem("ملفي الصحي", "معلوماتك الصحية الأساسية", Icons.Filled.HealthAndSafety, "health_profile", Color(0xFF39D6A3)),
            PatientServiceItem("وصفاتي الطبية", "الوصفات المسجلة لك", Icons.Filled.Description, "patient_prescriptions", Color(0xFF4DA3FF)),
            PatientServiceItem("جدول أدويتي", "خطط وتذكيرات الأدوية", Icons.Filled.Medication, "medication_plans", Color(0xFFFFB74D)),
            PatientServiceItem("ملفات العائلة", "إدارة ملفات أفراد العائلة", Icons.Filled.FamilyRestroom, "family_profiles", Color(0xFFE58ACB))
        ),
        "التواصل والخدمات" to listOf(
            PatientServiceItem("أطبائي المفضلون", "الوصول السريع لأطبائك", Icons.Filled.Favorite, "favorites", Color(0xFFFF6B81)),
            PatientServiceItem("الرسائل", "محادثاتك مع الأطباء", Icons.Filled.Chat, "messages", Color(0xFF4DA3FF)),
            PatientServiceItem("الطوارئ", "معلومات وجهات الطوارئ", Icons.Filled.Emergency, "emergency", Color(0xFFE5171F))
        )
    )

    Column(
        Modifier.fillMaxSize().background(Color(0xCC0A0C11))
            .verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowForward, contentDescription = "رجوع", tint = Color.White)
            }
            Column(Modifier.weight(1f)) {
                Text("مركز الخدمات الصحية", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                Text("كل خدماتك الطبية مرتبة في مكان واحد", color = Color(0xFFB6C6DA), fontSize = 12.sp)
            }
            Icon(Icons.Filled.HealthAndSafety, contentDescription = null, tint = Color(0xFFE5171F), modifier = Modifier.size(30.dp))
        }

        groups.forEach { (heading, items) ->
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(heading, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                items.chunked(2).forEach { rowItems ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        rowItems.forEach { item ->
                            PatientServiceCard(item, Modifier.weight(1f)) { onNavigate(item.destination) }
                        }
                        if (rowItems.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun PatientServiceCard(item: PatientServiceItem, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.heightIn(min = 126.dp).background(Color(0xEE14171D), RoundedCornerShape(17.dp))
            .border(1.dp, Color(0xFF30343D), RoundedCornerShape(17.dp))
            .clickable(onClick = onClick).padding(13.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Box(Modifier.size(42.dp).background(item.accent.copy(alpha = 0.16f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(item.icon, contentDescription = item.title, tint = item.accent, modifier = Modifier.size(24.dp))
        }
        Text(item.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Text(item.subtitle, color = Color(0xFFB6C6DA), fontSize = 11.sp, lineHeight = 15.sp)
    }
}

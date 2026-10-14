package com.sehati.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** تخصص معتمد: name هو القيمة الوحيدة التي تُحفظ عند الطبيب وتُبحث عند المريض. */
class Spec(val name: String, val icon: ImageVector, val aliases: List<String> = emptyList())

val SPECIALTIES: List<Spec> = listOf(
    Spec("طب العيون", Icons.Filled.Visibility, listOf("عيون", "عيون وجراحة العين", "طبيب عيون", "طب عيون", "العيون", "عين")),
    Spec("طب الأسنان", Icons.Filled.Mood, listOf("أسنان", "اسنان", "طب اسنان", "الأسنان", "طبيب أسنان", "اسنان ولثة")),
    Spec("طب الأطفال", Icons.Filled.ChildCare, listOf("أطفال", "اطفال", "طب اطفال", "الأطفال", "طبيب أطفال")),
    Spec("نسائية وتوليد", Icons.Filled.PregnantWoman, listOf("نسائية", "نسائيه", "نسائية وتوليد", "نسائية و توليد", "طب النساء", "النسائية", "توليد", "أمراض نسائية")),
    Spec("قلبية", Icons.Filled.Favorite, listOf("قلب", "القلب", "طب القلب", "أمراض القلب", "قلبية وأوعية", "قلبية وشرايين")),
    Spec("عظمية ومفاصل", Icons.Filled.Accessibility, listOf("عظام", "عظمية", "العظام", "طب العظام", "جراحة عظمية", "عظام ومفاصل")),
    Spec("طب عام", Icons.Filled.MedicalServices, listOf("عام", "طبيب عام", "ممارس عام", "الطب العام")),
    Spec("طب الأسرة", Icons.Filled.Home, listOf("أسرة", "طب اسرة", "الأسرة")),
    Spec("باطنية", Icons.Filled.MonitorHeart, listOf("الباطنية", "باطنة", "داخلية", "أمراض باطنية", "طب باطني")),
    Spec("جلدية", Icons.Filled.Face, listOf("الجلدية", "جلدية وتناسلية", "أمراض جلدية", "جلد")),
    Spec("أنف وأذن وحنجرة", Icons.Filled.Hearing, listOf("أذن وأنف وحنجرة", "انف واذن وحنجرة", "أذنية", "اذن انف حنجرة", "أنف أذن حنجرة", "ENT")),
    Spec("أعصاب", Icons.Filled.Psychology, listOf("عصبية", "الأعصاب", "أمراض عصبية", "دماغ وأعصاب")),
    Spec("جراحة عامة", Icons.Filled.Healing, listOf("جراحة", "الجراحة العامة", "جراح عام")),
    Spec("جراحة أعصاب", Icons.Filled.Biotech, listOf("جراحة الأعصاب", "جراحة دماغ وأعصاب")),
    Spec("جراحة تجميل", Icons.Filled.Spa, listOf("تجميل", "جراحة التجميل", "تجميلية")),
    Spec("مسالك بولية", Icons.Filled.WaterDrop, listOf("بولية", "المسالك البولية", "جراحة بولية")),
    Spec("كلية", Icons.Filled.Science, listOf("الكلية", "أمراض الكلى", "كلى", "الكلى")),
    Spec("جهاز هضمي", Icons.Filled.Restaurant, listOf("هضمية", "الجهاز الهضمي", "هضمي", "أمراض هضمية")),
    Spec("غدد صماء وسكري", Icons.Filled.Bloodtype, listOf("غدد", "سكري", "الغدد الصماء", "الغدد", "غدد وسكري")),
    Spec("صدرية", Icons.Filled.Air, listOf("الصدرية", "صدر", "رئة", "أمراض صدرية", "صدرية وتنفسية")),
    Spec("أمراض الدم", Icons.Filled.Bloodtype, listOf("دم", "الدم", "أمراض دم")),
    Spec("أورام", Icons.Filled.Biotech, listOf("الأورام", "أمراض الأورام", "أورام وسرطان")),
    Spec("طب نفسي", Icons.Filled.Psychology, listOf("نفسية", "نفسي", "الطب النفسي", "أمراض نفسية", "صحة نفسية")),
    Spec("روماتيزم", Icons.Filled.Accessibility, listOf("الروماتيزم", "رثية", "أمراض رثوية")),
    Spec("أوعية دموية", Icons.Filled.MonitorHeart, listOf("الأوعية الدموية", "جراحة الأوعية", "وعائية")),
    Spec("تخدير", Icons.Filled.Vaccines, listOf("التخدير", "إنعاش", "تخدير وإنعاش")),
    Spec("أشعة تشخيصية", Icons.Filled.Science, listOf("أشعة", "الأشعة", "اشعة", "تصوير شعاعي")),
    Spec("علاج فيزيائي", Icons.Filled.FitnessCenter, listOf("فيزيائي", "فيزيو", "العلاج الفيزيائي", "تأهيل", "علاج طبيعي")),
    Spec("تغذية", Icons.Filled.Restaurant, listOf("التغذية", "تغذية علاجية", "حمية"))
)

/** الأكثر شيوعاً — تظهر في الصفحة الرئيسية. */
val COMMON_SPECIALTIES: List<Spec> = listOf(
    "طب العيون", "طب الأسنان", "طب الأطفال", "نسائية وتوليد", "قلبية", "عظمية ومفاصل", "باطنية", "جلدية"
).mapNotNull { n -> SPECIALTIES.firstOrNull { it.name == n } }

private fun norm(s: String): String {
    var t = s.trim().lowercase()
    t = t.replace("أ", "ا").replace("إ", "ا").replace("آ", "ا")
        .replace("ة", "ه").replace("ى", "ي").replace(" ", "")
    return t
}

/** يطابق نصاً (قديم/حر/معتمد) مع تخصص معتمد، أو null. */
fun matchSpecialty(text: String): Spec? {
    val n = norm(text)
    if (n.length < 2) return null
    SPECIALTIES.firstOrNull { norm(it.name) == n || it.aliases.any { a -> norm(a) == n } }?.let { return it }
    return null
}

/** كل القيم النصية التي قد تكون محفوظة عند أطباء لهذا التخصص (للبحث في قاعدة البيانات، حد أقصى 30). */
fun specVariants(s: Spec): List<String> =
    (listOf(s.name) + s.aliases).distinct().take(30)

@Composable
fun AllSpecialtiesScreen(onBack: () -> Unit, onPick: (String) -> Unit) {
    Column(Modifier.fillMaxSize().background(BBg).padding(16.dp)) {
        TextButton(onClick = onBack) { Text("رجوع", color = BRed) }
        Text("كل التخصصات", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(SPECIALTIES.chunked(3)) { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { s ->
                        SpecTile(s, Modifier.weight(1f)) { onPick(s.name) }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
fun SpecTile(s: Spec, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .height(104.dp)
            .background(BCard, RoundedCornerShape(14.dp))
            .border(1.dp, BLine, RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier.size(40.dp).background(BBg, CircleShape).border(1.dp, BRed, CircleShape),
            contentAlignment = Alignment.Center
        ) { Icon(s.icon, null, tint = BRed, modifier = Modifier.size(22.dp)) }
        Spacer(Modifier.height(6.dp))
        Text(s.name, color = Color.White, fontSize = 12.sp, textAlign = TextAlign.Center, maxLines = 2)
    }
}

/** حقل اختيار تخصص من القائمة المعتمدة (نافذة اختيار). */
@Composable
fun SpecialtyPicker(value: String, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth()
            .background(BCard, RoundedCornerShape(12.dp))
            .border(1.dp, BLine, RoundedCornerShape(12.dp))
            .clickable { open = true }
            .padding(14.dp)
    ) {
        Text("التخصص (اختر من القائمة)", color = BMuted, fontSize = 12.sp)
        Text(if (value.isBlank()) "اضغط للاختيار" else value, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            confirmButton = { TextButton(onClick = { open = false }) { Text("إغلاق", color = BRed) } },
            title = { Text("اختر تخصصك") },
            text = {
                LazyColumn {
                    items(SPECIALTIES) { s ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onPick(s.name); open = false }.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(s.icon, null, tint = BRed, modifier = Modifier.size(20.dp))
                            Text(s.name, fontWeight = if (s.name == value) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
            }
        )
    }
}

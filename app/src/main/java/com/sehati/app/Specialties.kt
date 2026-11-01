package com.sehati.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** تخصص معتمد: name هو القيمة الوحيدة التي تُحفظ عند الطبيب وتُبحث عند المريض. img اسم الصورة (sp_<img>.png). */
class Spec(val name: String, val img: String, val group: String, val aliases: List<String> = emptyList())

val SPEC_GROUPS: List<String> = listOf("الأسنان", "الأطفال والنساء", "طب عام وباطني", "القلب والأعصاب والنفس", "العظام والتأهيل", "العيون والأذن والجلد", "الجراحات", "أورام وفحوصات وطوارئ")

val SPECIALTIES: List<Spec> = listOf(
    Spec("طب الأسنان", "tooth", "الأسنان", listOf("أسنان", "اسنان", "طب اسنان", "الأسنان", "طبيب أسنان", "اسنان ولثة")),
    Spec("تقويم أسنان", "braces", "الأسنان", listOf("تقويم", "تقويم الأسنان", "تقويم اسنان")),
    Spec("زراعة أسنان", "implant", "الأسنان", listOf("زراعة الأسنان", "زراعة اسنان", "زرع الأسنان")),
    Spec("أسنان أطفال", "tooth_kid", "الأسنان", listOf("طب أسنان الأطفال", "اسنان اطفال", "أسنان الأطفال")),
    Spec("لثة وأنسجة داعمة", "gums", "الأسنان", listOf("أمراض اللثة", "لثة", "معالجة لثة")),
    Spec("معالجة لبية", "rootcanal", "الأسنان", listOf("لبية", "حشوات وعصب", "علاج العصب", "ترميمية أسنان")),
    Spec("جراحة فم وفكين", "jaw", "الأسنان", listOf("جراحة الفم والفكين", "جراحة فكين", "جراحة وجه وفكين", "جراحة وجه وتجميل أسنان")),
    Spec("طب الأطفال", "child", "الأطفال والنساء", listOf("أطفال", "اطفال", "طب اطفال", "الأطفال", "طبيب أطفال")),
    Spec("حديثي الولادة", "newborn", "الأطفال والنساء", listOf("خدج", "حاضنات", "طب الوليد", "ولدان")),
    Spec("نسائية وتوليد", "pregnant", "الأطفال والنساء", listOf("نسائية", "نسائيه", "نسائية وتوليد", "نسائية و توليد", "طب النساء", "النسائية", "توليد", "أمراض نسائية")),
    Spec("عقم وإخصاب", "fertility", "الأطفال والنساء", listOf("عقم", "إخصاب", "أطفال أنابيب", "العقم")),
    Spec("جراحة أطفال", "child_surgery", "الأطفال والنساء", listOf("جراحة الأطفال", "جراحة اطفال", "جراح أطفال")),
    Spec("جراحة نسائية", "uterus", "الأطفال والنساء", listOf("الجراحة النسائية", "جراحة نسائية وتوليد")),
    Spec("طب عام", "steth", "طب عام وباطني", listOf("عام", "طبيب عام", "ممارس عام", "الطب العام", "طب العمل", "صحة عامة", "طب سفر", "طب بديل", "طب شرعي", "تخدير")),
    Spec("طب الأسرة", "family", "طب عام وباطني", listOf("أسرة", "طب اسرة", "الأسرة", "طب الأسرة والمجتمع")),
    Spec("باطنية", "organs", "طب عام وباطني", listOf("الباطنية", "باطنة", "داخلية", "أمراض باطنية", "طب باطني", "طب الشيخوخة")),
    Spec("جهاز هضمي", "intestines", "طب عام وباطني", listOf("هضمية", "الجهاز الهضمي", "هضمي", "أمراض هضمية", "هضمية أطفال")),
    Spec("أمراض الكبد", "liver", "طب عام وباطني", listOf("كبد", "الكبد", "أمراض الكبد والجهاز الهضمي")),
    Spec("صدرية", "lungs", "طب عام وباطني", listOf("الصدرية", "صدر", "رئة", "أمراض صدرية", "صدرية وتنفسية")),
    Spec("كلية", "kidney", "طب عام وباطني", listOf("الكلية", "أمراض الكلى", "كلى", "الكلى", "كلية أطفال", "جراحة كلى وزرع")),
    Spec("مسالك بولية", "bladder", "طب عام وباطني", listOf("بولية", "المسالك البولية", "جراحة بولية", "أمراض تناسلية")),
    Spec("غدد صماء وسكري", "thyroid", "طب عام وباطني", listOf("غدد", "سكري", "الغدد الصماء", "الغدد", "غدد وسكري", "سكري وغدة درقية")),
    Spec("أمراض الدم", "blood", "طب عام وباطني", listOf("دم", "الدم", "أمراض دم", "دم أطفال")),
    Spec("أمراض معدية", "virus", "طب عام وباطني", listOf("الأمراض المعدية", "امراض معدية", "أمراض إنتانية")),
    Spec("حساسية ومناعة", "allergy", "طب عام وباطني", listOf("الحساسية", "المناعة", "حساسية", "أمراض المناعة")),
    Spec("تغذية", "nutrition", "طب عام وباطني", listOf("التغذية", "تغذية علاجية", "حمية")),
    Spec("قلبية", "heart", "القلب والأعصاب والنفس", listOf("قلب", "القلب", "طب القلب", "أمراض القلب", "قلبية وأوعية", "قلبية وشرايين", "قلبية أطفال", "ضغط وشرايين")),
    Spec("أوعية دموية", "vessels", "القلب والأعصاب والنفس", listOf("الأوعية الدموية", "جراحة الأوعية", "وعائية", "جراحة وعائية")),
    Spec("أعصاب", "brain", "القلب والأعصاب والنفس", listOf("عصبية", "الأعصاب", "أمراض عصبية", "دماغ وأعصاب", "أعصاب أطفال", "طب النوم", "علاج الألم")),
    Spec("طب نفسي", "psych", "القلب والأعصاب والنفس", listOf("نفسية", "نفسي", "الطب النفسي", "أمراض نفسية", "صحة نفسية", "طب نفسي أطفال", "إدمان وعلاج نفسي")),
    Spec("عظمية ومفاصل", "knee", "العظام والتأهيل", listOf("عظام", "عظمية", "العظام", "طب العظام", "جراحة عظمية", "عظام ومفاصل")),
    Spec("روماتيزم", "rheum", "العظام والتأهيل", listOf("الروماتيزم", "رثية", "أمراض رثوية")),
    Spec("علاج فيزيائي", "physio", "العظام والتأهيل", listOf("فيزيائي", "فيزيو", "العلاج الفيزيائي", "تأهيل", "علاج طبيعي", "أطراف صناعية وتأهيل", "علاج وظيفي")),
    Spec("طب رياضي", "sport", "العظام والتأهيل", listOf("الطب الرياضي", "إصابات رياضية")),
    Spec("طب العيون", "eye", "العيون والأذن والجلد", listOf("عيون", "عيون وجراحة العين", "طبيب عيون", "طب عيون", "العيون", "عين", "طب العيون العصبي", "عيون أطفال")),
    Spec("أنف وأذن وحنجرة", "ent", "العيون والأذن والجلد", listOf("أذن وأنف وحنجرة", "انف واذن وحنجرة", "أذنية", "اذن انف حنجرة", "أنف أذن حنجرة", "ENT")),
    Spec("سمعيات ونطق", "hearing", "العيون والأذن والجلد", listOf("سمعيات", "نطق", "معالجة نطق", "تأهيل نطق")),
    Spec("جلدية", "skin", "العيون والأذن والجلد", listOf("الجلدية", "جلدية وتناسلية", "أمراض جلدية", "جلد", "جلدية وتجميل")),
    Spec("شعر وزراعة", "hair", "العيون والأذن والجلد", listOf("زراعة الشعر", "أمراض الشعر", "تساقط الشعر")),
    Spec("جراحة عامة", "scalpel", "الجراحات", listOf("جراحة", "الجراحة العامة", "جراح عام")),
    Spec("جراحة أعصاب", "brain_surgery", "الجراحات", listOf("جراحة الأعصاب", "جراحة دماغ وأعصاب")),
    Spec("جراحة قلبية", "heart_surgery", "الجراحات", listOf("جراحة القلب", "جراحة قلب وصدر", "جراحة القلب والصدر")),
    Spec("جراحة عظمية", "bone_surgery", "الجراحات", listOf("جراحة العظام", "جراحة عظام", "جراحة العظام والمفاصل", "جراحة قدم")),
    Spec("جراحة عمود فقري", "spine", "الجراحات", listOf("جراحة العمود الفقري", "جراحة الظهر", "جراحة فقرية")),
    Spec("جراحة تجميل", "plastic", "الجراحات", listOf("تجميل", "جراحة التجميل", "تجميلية", "طب الحروق")),
    Spec("جراحة سمنة", "bariatric", "الجراحات", listOf("جراحة السمنة", "عمليات التنحيف", "تكميم المعدة")),
    Spec("جراحة عيون", "eye_surgery", "الجراحات", listOf("جراحة العيون", "جراحة العين", "ليزك")),
    Spec("جراحة أذن وأنف وحنجرة", "larynx", "الجراحات", listOf("جراحة الأنف والأذن والحنجرة", "جراحة أنف", "جراحة أذن")),
    Spec("جراحة غدد", "thyroid_surgery", "الجراحات", listOf("جراحة الغدد", "جراحة الغدة الدرقية", "جراحة الثدي")),
    Spec("جراحة هضمية", "stomach_surgery", "الجراحات", listOf("جراحة الجهاز الهضمي", "جراحة المعدة", "جراحة الكبد")),
    Spec("جراحة صدرية", "lung_surgery", "الجراحات", listOf("جراحة الصدر", "جراحة صدر", "جراحة الرئة")),
    Spec("جراحة أورام", "tumor_surgery", "الجراحات", listOf("جراحة الأورام", "جراحة سرطانية")),
    Spec("جراحة يد وترميم", "hand_surgery", "الجراحات", listOf("جراحة اليد", "جراحة الحروق", "جراحة ترميمية")),
    Spec("أورام", "tumor", "أورام وفحوصات وطوارئ", listOf("الأورام", "أمراض الأورام", "أورام وسرطان", "علاج إشعاعي")),
    Spec("أشعة تشخيصية", "xray", "أورام وفحوصات وطوارئ", listOf("أشعة", "الأشعة", "اشعة", "تصوير شعاعي", "طب نووي")),
    Spec("مخبر وتحاليل", "lab", "أورام وفحوصات وطوارئ", listOf("مخبر", "تحاليل", "مختبر", "تحاليل طبية")),
    Spec("طب الطوارئ", "ambulance", "أورام وفحوصات وطوارئ", listOf("طوارئ", "الطوارئ", "إسعاف"))
)

/** الأكثر طلباً — تظهر في الصفحة الرئيسية. */
val COMMON_SPECIALTIES: List<Spec> = listOf(
    "طب العيون", "طب الأسنان", "طب الأطفال", "نسائية وتوليد", "قلبية", "عظمية ومفاصل", "باطنية", "جلدية", "أنف وأذن وحنجرة", "أعصاب", "مسالك بولية", "جهاز هضمي", "طب عام", "طب نفسي", "جراحة عامة"
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
    var q by remember { mutableStateOf("") }
    val nq = norm(q)
    val shown = if (nq.isEmpty()) SPECIALTIES
        else SPECIALTIES.filter { norm(it.name).contains(nq) || it.aliases.any { a -> norm(a).contains(nq) } }

    Column(Modifier.fillMaxSize().background(BBg).padding(16.dp)) {
        TextButton(onClick = onBack) { Text("رجوع", color = BRed) }
        Text("التخصصات الطبية", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text("${SPECIALTIES.size} تخصصاً — اختر تخصصك أو ابحث عنه", color = Color(0xFF45CFFF), fontSize = 13.sp)
        Spacer(Modifier.height(8.dp))
        Field(q, { q = it }, "ابحث عن تخصص... (مثل: عيون، قلب، أسنان)")
        Spacer(Modifier.height(8.dp))
        if (shown.isEmpty()) {
            Text("ما لقينا تخصص بهذا الاسم. جرّب كلمة ثانية", color = BMuted)
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val groups = if (nq.isEmpty()) SPEC_GROUPS else listOf("")
            groups.forEach { g ->
                val list = if (nq.isEmpty()) shown.filter { it.group == g } else shown
                if (list.isNotEmpty()) {
                    if (g.isNotEmpty()) item {
                        Text(g, color = BRed, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                            modifier = Modifier.padding(top = 8.dp))
                    }
                    items(list.chunked(3)) { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { s -> SpecTile(s, Modifier.weight(1f)) { onPick(s.name) } }
                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SpecTile(s: Spec, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier.clickable { onClick() }.padding(horizontal = 2.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Box(
            Modifier.size(88.dp)
                .background(
                    Brush.radialGradient(listOf(Color(0xFF164C91), Color(0xFF071A3D))),
                    CircleShape
                )
                .border(
                    1.8.dp,
                    Brush.sweepGradient(listOf(Color(0xFF7CEBFF), Color(0xFF168BFF), Color(0xFF7CEBFF))),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            SpecImage(s, 66.dp)
        }
        Box(
            Modifier.fillMaxWidth()
                .background(
                    Brush.horizontalGradient(listOf(Color(0xFF071A3D), Color(0xFF123B78), Color(0xFF071A3D))),
                    RoundedCornerShape(50)
                )
                .border(1.dp, Color(0xFF168BFF), RoundedCornerShape(50))
                .padding(horizontal = 5.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                s.name, color = Color.White, fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 2
            )
        }
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
                            Modifier.fillMaxWidth().clickable { onPick(s.name); open = false }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            SpecImage(s, 34.dp)
                            Text(s.name, fontWeight = if (s.name == value) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
            }
        )
    }
}

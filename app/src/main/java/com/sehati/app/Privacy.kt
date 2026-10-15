package com.sehati.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
private fun PSection(title: String, body: String) {
    Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = BRed)
    Text(body, color = Color.White, fontSize = 15.sp)
}

@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(BBg).padding(20.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        TextButton(onClick = onBack) { Text("رجوع", color = BRed) }
        Text("سياسة الخصوصية", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
        PSection(
            "البيانات التي نحفظها",
            "الاسم، رقم الهاتف، البريد الإلكتروني، ونوع الحساب (مريض أو طبيب أو عيادة). " +
                "وبيانات الحجوزات والمواعيد. وللأطباء والعيادات: بيانات الملف المهني " +
                "(الاختصاص، اسم العيادة، العنوان، ساعات الدوام، الموقع، الصورة، رقم الطوارئ)."
        )
        PSection(
            "الملاحظات الطبية",
            "إذا كتب لك الطبيب راشيتة أو طلب تحاليل داخل التطبيق، يُحفظ النص مرتبطاً بموعدك، " +
                "ولا يراه إلا أنت والطبيب الذي كتبه. هي ملاحظة عبر التطبيق وليست وصفة رسمية."
        )
        PSection(
            "كيف نستخدمها",
            "لتشغيل التطبيق فقط: تسجيل الدخول، حجز المواعيد، تنظيم الدور، " +
                "الإشعارات داخل التطبيق، والتواصل بين المريض والطبيب."
        )
        PSection(
            "من يرى بياناتك",
            "اسم المريض ورقمه وملاحظة الحجز يراها الطبيب أو العيادة التي حجزت عندها فقط. " +
                "بيانات الملف المهني للأطباء تظهر لمستخدمي التطبيق المسجّلين. " +
                "لا نبيع بياناتك ولا نشاركها لأغراض إعلانية."
        )
        PSection(
            "أين تُحفظ",
            "تُحفظ البيانات في خدمات Google Firebase (تسجيل الدخول وقاعدة البيانات) " +
                "مع قواعد أمان تمنع أي مستخدم من الوصول إلى بيانات غيره."
        )
        PSection(
            "حذف الحساب",
            "يمكنك حذف حسابك في أي وقت من صفحة الحساب. عند الحذف يُحذف حسابك، " +
                "ويُخفى اسمك ورقمك وملاحظاتك من الحجوزات السابقة، وتُحذف الراشيتات وطلبات التحاليل الخاصة بك."
        )
        PSection(
            "تنبيه طبي",
            "التطبيق لتنظيم المواعيد والوصول إلى الأطباء، وليس بديلًا عن الاستشارة الطبية " +
                "أو الإسعاف. في الحالات الخطرة توجّه لأقرب مستشفى فورًا."
        )
        PSection(
            "الأطفال",
            "التطبيق غير موجّه للأطفال دون 13 سنة، وعلى الأهل إنشاء الحساب للطفل."
        )
        Spacer(Modifier.height(24.dp))
    }
}

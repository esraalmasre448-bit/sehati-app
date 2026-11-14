package com.sehati.app

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.text.font.FontWeight

@Composable
fun FirstRunGuide(accountType: String, onDismiss: () -> Unit) {
    val steps = when (accountType) {
        "doctor", "clinic" -> listOf(
            "أكمل ملفك المهني وصورة الحساب والاختصاص والمدينة.",
            "حدّد عنوان العيادة وأيام وساعات الاستقبال وأجرة الكشفية.",
            "تابع طلبات الحجز ووافق على الموعد قبل تأكيده للمريض.",
            "أدر قائمة الانتظار، وأرسل الوصفات وطلبات التحاليل من ملف المريض.",
            "عند التجديد اختر المدة، حوّل المبلغ عبر شام كاش باستخدام الكود أو QR، ثم أرسل إثبات الحوالة للإدارة عبر واتساب وانتظر تأكيد التفعيل."
        )
        else -> listOf(
            "ابحث عن الطبيب حسب الاختصاص والمدينة.",
            "افتح ملف الطبيب وراجع العنوان وأوقات الاستقبال.",
            "اختر الموعد المناسب وأرسل طلب الحجز.",
            "انتظر موافقة الطبيب، ثم تابع رقم دورك وحالة الموعد.",
            "تابع رسائل الطبيب والوصفات وطلبات التحاليل من داخل التطبيق."
        )
    }
    var index by remember { mutableIntStateOf(0) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Explore, contentDescription = null) },
        title = { Text(if (index == 0) "أهلاً بك في الطريق الصحي" else "دليل الاستخدام") },
        text = {
            androidx.compose.foundation.layout.Column {
                Text("الخطوة ${index + 1} من ${steps.size}", fontWeight = FontWeight.Bold)
                Text(steps[index])
            }
        },
        confirmButton = {
            TextButton(onClick = { if (index == steps.lastIndex) onDismiss() else index++ }) {
                Text(if (index == steps.lastIndex) "إنهاء" else "التالي")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("تخطي") }
        }
    )
}

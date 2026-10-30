package com.sehati.app

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp

/**
 * رسومات التخصصات الشائعة. تُعرض كرموز طبية ملونة واضحة عند عدم وجود ملفات الصور
 * المخصصة في res/drawable، بدلاً من إظهار أيقونة طبية عامة لكل التخصصات.
 */
@Composable
fun SpecImage(s: Spec, size: Dp) {
    val symbol = when (s.img) {
        "eye", "eye_surgery" -> "👁️"
        "tooth", "braces", "implant", "tooth_kid", "gums", "rootcanal", "jaw" -> "🦷"
        "child", "newborn", "child_surgery" -> "👶"
        "pregnant", "fertility", "uterus" -> "🤰"
        "heart", "heart_surgery", "vessels" -> "🫀"
        "knee", "bone_surgery", "spine", "rheum" -> "🦴"
        "organs", "stomach_surgery", "liver" -> "🫁"
        "skin", "hair", "plastic" -> "🧑🏻"
        "ent", "hearing", "larynx" -> "👂"
        "brain", "psych", "brain_surgery" -> "🧠"
        "bladder", "kidney" -> "🫘"
        "intestines" -> "🩻"
        "steth", "family" -> "🩺"
        "scalpel", "hand_surgery", "thyroid_surgery" -> "🩹"
        "lungs", "lung_surgery" -> "🫁"
        "thyroid", "blood", "virus", "allergy" -> "🧬"
        "physio", "sport" -> "🏃"
        "ambulance" -> "🚑"
        "xray" -> "🩻"
        "lab" -> "🧪"
        "tumor", "tumor_surgery" -> "🔬"
        "psych" -> "🧠"
        else -> "⚕️"
    }
    Text(
        text = symbol,
        modifier = Modifier,
        fontSize = (size.value * 0.88f).sp,
        textAlign = TextAlign.Center,
        maxLines = 1
    )
}

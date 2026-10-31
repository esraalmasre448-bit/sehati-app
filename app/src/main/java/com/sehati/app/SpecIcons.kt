package com.sehati.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp

/**
 * يعرض الصورة المخصصة للتخصص إن كانت موجودة، وإلا يستخدم رمزاً طبياً واضحاً
 * خاصاً بالتخصص بدلاً من تكرار أيقونة طبية عامة.
 */
@Composable
fun SpecImage(s: Spec, size: Dp) {
    val ctx = LocalContext.current
    val res = remember(s.img) {
        ctx.resources.getIdentifier("sp_" + s.img, "drawable", ctx.packageName)
    }
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
        "nutrition" -> "🍎"
        "physio", "sport" -> "🏃"
        "ambulance" -> "🚑"
        "xray" -> "🩻"
        "lab" -> "🧪"
        "tumor", "tumor_surgery" -> "🔬"
        else -> "⚕️"
    }
    if (res != 0) {
        Image(painterResource(res), null, Modifier.size(size))
    } else {
        Text(
            text = symbol,
            fontSize = (size.value * 0.88f).sp,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

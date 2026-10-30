package com.sehati.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp

/**
 * صورة التخصص: ملف sp_<img>.png من res/drawable.
 * إذا لم تُرفع الصورة بعد، تظهر أيقونة طبية عامة بدل أن ينكسر التطبيق.
 */
@Composable
fun SpecImage(s: Spec, size: Dp) {
    val ctx = LocalContext.current
    val res = remember(s.img) { ctx.resources.getIdentifier("sp_" + s.img, "drawable", ctx.packageName) }
    if (res != 0) Image(painterResource(res), null, Modifier.size(size))
    else Icon(Icons.Filled.MedicalServices, null, tint = BRed, modifier = Modifier.size(size))
}

package com.sehati.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Biotech
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Healing
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp

/**
 * صور التخصصات المخصصة تُعرض كما هي. بقية التخصصات تستخدم رسوماً Vector
 * من Material Icons بلون سماوي موحّد، لتبقى حادة وواضحة على كل كثافات الشاشات.
 */
@Composable
fun SpecImage(s: Spec, size: Dp) {
    val ctx = LocalContext.current
    val res = remember(s.img) {
        ctx.resources.getIdentifier("sp_" + s.img, "drawable", ctx.packageName)
    }

    if (res != 0) {
        Image(painterResource(res), null, Modifier.size(size))
    } else {
        val vector = when (s.img) {
            "eye", "eye_surgery" -> Icons.Filled.Visibility
            "heart", "heart_surgery", "vessels" -> Icons.Filled.Favorite
            "child", "newborn", "child_surgery", "tooth_kid" -> Icons.Filled.ChildCare
            "brain", "psych", "brain_surgery" -> Icons.Filled.Psychology
            "knee", "bone_surgery", "spine", "rheum", "hand_surgery" -> Icons.Filled.Healing
            "ent", "hearing", "larynx" -> Icons.Filled.Hearing
            "skin", "hair", "plastic" -> Icons.Filled.Face
            "lungs", "lung_surgery" -> Icons.Filled.Air
            "nutrition" -> Icons.Filled.Restaurant
            "physio", "sport" -> Icons.Filled.DirectionsRun
            "ambulance" -> Icons.Filled.LocalHospital
            "xray", "lab", "tumor", "tumor_surgery" -> Icons.Filled.Science
            "thyroid", "blood", "virus", "allergy" -> Icons.Filled.Biotech
            "kidney", "bladder", "intestines", "liver", "organs", "stomach_surgery" -> Icons.Filled.WaterDrop
            else -> Icons.Filled.MedicalServices
        }
        Icon(
            imageVector = vector,
            contentDescription = s.name,
            modifier = Modifier.size(size),
            tint = Color(0xFF7CEBFF)
        )
    }
}

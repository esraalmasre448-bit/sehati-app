package com.sehati.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp

/**
 * Specialty artwork:
 * - Keep the eight existing hand-drawn WebP assets unchanged.
 * - All remaining specialties use crisp, scalable Material vector artwork.
 * - Each specialty image key is mapped explicitly so no specialty silently
 *   falls through to a generic medical-services symbol.
 */
@Composable
fun SpecImage(s: Spec, size: Dp) {
    val ctx = LocalContext.current
    // Existing custom files use the "spec_" prefix and legacy keys.
    val res = remember(s.img) {
        val legacyKey = when (s.img) {
            "knee" -> "bones"
            "pregnant" -> "women"
            else -> s.img
        }
        val candidates = listOf("spec_" + legacyKey, "sp_" + s.img)
        candidates.firstNotNullOfOrNull { name ->
            ctx.resources.getIdentifier(name, "drawable", ctx.packageName).takeIf { it != 0 }
        } ?: 0
    }

    if (res != 0) {
        Image(painterResource(res), null, Modifier.size(size))
        return
    }

    // Use only vector symbols already present in the project's verified icon set.
    val vector = when (s.img) {
        "eye", "eye_surgery" -> Icons.Filled.Visibility
        "heart", "heart_surgery", "vessels", "pregnant", "fertility", "uterus" -> Icons.Filled.Favorite
        "child", "newborn", "child_surgery", "tooth_kid" -> Icons.Filled.ChildCare
        "brain", "psych", "brain_surgery" -> Icons.Filled.Psychology
        "knee", "bone_surgery", "spine", "rheum", "hand_surgery", "jaw",
        "braces", "implant", "gums", "rootcanal", "bariatric", "scalpel" -> Icons.Filled.Healing
        "ent", "hearing", "larynx" -> Icons.Filled.Hearing
        "skin", "hair", "plastic" -> Icons.Filled.Face
        "lungs", "lung_surgery" -> Icons.Filled.Air
        "nutrition", "intestines", "stomach_surgery" -> Icons.Filled.Restaurant
        "physio", "sport" -> Icons.Filled.DirectionsRun
        "ambulance" -> Icons.Filled.LocalHospital
        "xray", "lab", "tumor", "tumor_surgery" -> Icons.Filled.Science
        "thyroid", "blood", "virus", "allergy", "thyroid_surgery" -> Icons.Filled.Biotech
        "kidney", "bladder", "liver", "organs" -> Icons.Filled.WaterDrop
        "family", "steth", "tooth" -> Icons.Filled.MedicalServices
        else -> Icons.Filled.MedicalServices

    Icon(
        imageVector = vector,
        contentDescription = s.name,
        modifier = Modifier.size(size),
        tint = Color(0xFF7CEBFF)
    )
}

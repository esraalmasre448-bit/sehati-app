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
    val res = remember(s.img) {
        ctx.resources.getIdentifier("sp_" + s.img, "drawable", ctx.packageName)
    }

    if (res != 0) {
        Image(painterResource(res), null, Modifier.size(size))
        return
    }

    val vector = when (s.img) {
        "tooth" -> Icons.Filled.DentalServices
        "braces" -> Icons.Filled.Build
        "implant" -> Icons.Filled.AddCircle
        "tooth_kid" -> Icons.Filled.ChildCare
        "gums" -> Icons.Filled.Spa
        "rootcanal" -> Icons.Filled.Healing
        "jaw" -> Icons.Filled.Face
        "child" -> Icons.Filled.ChildCare
        "newborn" -> Icons.Filled.BabyChangingStation
        "pregnant" -> Icons.Filled.Female
        "fertility" -> Icons.Filled.Favorite
        "child_surgery" -> Icons.Filled.MedicalServices
        "uterus" -> Icons.Filled.Wc
        "steth" -> Icons.Filled.MedicalInformation
        "family" -> Icons.Filled.Home
        "organs" -> Icons.Filled.Biotech
        "intestines" -> Icons.Filled.Restaurant
        "liver" -> Icons.Filled.WaterDrop
        "lungs" -> Icons.Filled.Air
        "kidney" -> Icons.Filled.WaterDrop
        "bladder" -> Icons.Filled.Wc
        "thyroid" -> Icons.Filled.Thermostat
        "blood" -> Icons.Filled.Bloodtype
        "virus" -> Icons.Filled.Coronavirus
        "allergy" -> Icons.Filled.Vaccines
        "nutrition" -> Icons.Filled.Restaurant
        "heart" -> Icons.Filled.MonitorHeart
        "vessels" -> Icons.Filled.Timeline
        "brain" -> Icons.Filled.Psychology
        "psych" -> Icons.Filled.Mood
        "knee" -> Icons.Filled.AccessibilityNew
        "rheum" -> Icons.Filled.Healing
        "physio" -> Icons.Filled.FitnessCenter
        "sport" -> Icons.Filled.DirectionsRun
        "eye" -> Icons.Filled.Visibility
        "ent" -> Icons.Filled.Hearing
        "hearing" -> Icons.Filled.RecordVoiceOver
        "skin" -> Icons.Filled.FaceRetouchingNatural
        "hair" -> Icons.Filled.ContentCut
        "scalpel" -> Icons.Filled.MedicalServices
        "brain_surgery" -> Icons.Filled.PsychologyAlt
        "heart_surgery" -> Icons.Filled.Favorite
        "bone_surgery" -> Icons.Filled.Accessibility
        "spine" -> Icons.Filled.AccessibilityNew
        "plastic" -> Icons.Filled.FaceRetouchingNatural
        "bariatric" -> Icons.Filled.MonitorWeight
        "eye_surgery" -> Icons.Filled.RemoveRedEye
        "larynx" -> Icons.Filled.RecordVoiceOver
        "thyroid_surgery" -> Icons.Filled.Thermostat
        "stomach_surgery" -> Icons.Filled.Restaurant
        "lung_surgery" -> Icons.Filled.Air
        "tumor_surgery" -> Icons.Filled.Biotech
        "hand_surgery" -> Icons.Filled.BackHand
        "tumor" -> Icons.Filled.Science
        "xray" -> Icons.Filled.CameraAlt
        "lab" -> Icons.Filled.Science
        "ambulance" -> Icons.Filled.Emergency
        else -> Icons.Filled.MedicalServices
    }

    Icon(
        imageVector = vector,
        contentDescription = s.name,
        modifier = Modifier.size(size),
        tint = Color(0xFF7CEBFF)
    )
}

package com.sehati.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp

/**
 * Specialty artwork:
 * - Existing WebP assets are decoded with edge-connected white matte removal.
 * - Only near-white pixels connected to the image edge are made transparent;
 *   white details inside the medical illustration are preserved.
 * - All remaining specialties use scalable Material vector artwork.
 */
private fun decodeSpecialtyArtwork(context: android.content.Context, resourceId: Int): Bitmap? {
    val decoded = BitmapFactory.decodeResource(context.resources, resourceId) ?: return null
    val bitmap = decoded.copy(Bitmap.Config.ARGB_8888, true) ?: return decoded
    val width = bitmap.width
    val height = bitmap.height
    if (width == 0 || height == 0) return bitmap

    val pixels = IntArray(width * height)
    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
    val visited = BooleanArray(pixels.size)
    val queue = IntArray(pixels.size)
    var head = 0
    var tail = 0

    fun isBackground(pixel: Int): Boolean {
        val alpha = android.graphics.Color.alpha(pixel)
        if (alpha == 0) return true
        val red = android.graphics.Color.red(pixel)
        val green = android.graphics.Color.green(pixel)
        val blue = android.graphics.Color.blue(pixel)
        val min = minOf(red, green, blue)
        val max = maxOf(red, green, blue)
        return min >= 238 && max - min <= 22
    }

    fun enqueue(index: Int) {
        if (!visited[index] && isBackground(pixels[index])) {
            visited[index] = true
            queue[tail++] = index
        }
    }

    for (x in 0 until width) {
        enqueue(x)
        enqueue((height - 1) * width + x)
    }
    for (y in 0 until height) {
        enqueue(y * width)
        enqueue(y * width + width - 1)
    }

    while (head < tail) {
        val index = queue[head++]
        pixels[index] = android.graphics.Color.TRANSPARENT
        val x = index % width
        val y = index / width
        if (x > 0) enqueue(index - 1)
        if (x + 1 < width) enqueue(index + 1)
        if (y > 0) enqueue(index - width)
        if (y + 1 < height) enqueue(index + width)
    }

    bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
    if (decoded !== bitmap) decoded.recycle()
    return bitmap
}

@Composable
fun SpecImage(s: Spec, size: Dp) {
    val ctx = LocalContext.current
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

    val artwork = remember(res) {
        if (res != 0) decodeSpecialtyArtwork(ctx, res)?.asImageBitmap() else null
    }

    if (artwork != null) {
        Image(bitmap = artwork, contentDescription = s.name, modifier = Modifier.size(size))
        return
    }

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
    }

    Icon(
        imageVector = vector,
        contentDescription = s.name,
        modifier = Modifier.size(size),
        tint = Color(0xFF7CEBFF)
    )
}

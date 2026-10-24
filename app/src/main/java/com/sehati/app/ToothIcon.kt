package com.sehati.app

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** أيقونة سن مرسومة، لطب الأسنان. */
val ToothIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Tooth",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(fill = SolidColor(Color.White)) {
        moveTo(12f, 2f)
        curveTo(9.5f, 2f, 8f, 3f, 7f, 3f)
        curveTo(5f, 3f, 4f, 4.5f, 4f, 7f)
        curveTo(4f, 9.5f, 5f, 11f, 5.5f, 13f)
        curveTo(6f, 15f, 6.5f, 22f, 8.5f, 22f)
        curveTo(10f, 22f, 10.5f, 17f, 12f, 17f)
        curveTo(13.5f, 17f, 14f, 22f, 15.5f, 22f)
        curveTo(17.5f, 22f, 18f, 15f, 18.5f, 13f)
        curveTo(19f, 11f, 20f, 9.5f, 20f, 7f)
        curveTo(20f, 4.5f, 19f, 3f, 17f, 3f)
        curveTo(16f, 3f, 14.5f, 2f, 12f, 2f)
        close()
    }.build()
}

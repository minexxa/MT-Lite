package com.ma.manager.ui.theme

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Colors / metrics measured from the official MA Manager file browser
 * (screenshot, density 3.21875 px/dp).
 */
object MAStyle {
    val ToolbarBg = Color(0xFF303030)
    val ToolbarHeight: Dp = 56.dp
    val RowHeight: Dp = 40.dp
    val TileSize: Dp = 28.dp
    val TileRadius: Dp = 4.5.dp
    val ShadowSize: Dp = 5.6.dp

    @Composable
    fun isDark(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f

    @Composable fun listBg() = if (isDark()) Color(0xFF121212) else Color(0xFFFAFAFA)
    @Composable fun barBg() = if (isDark()) Color(0xFF1E1E1E) else Color(0xFFFAFAFA)
    @Composable fun tile() = if (isDark()) Color(0xFF3A3A3A) else Color(0xFF2B2B2B)
    @Composable fun nameColor() = if (isDark()) Color(0xFFEEEEEE) else Color(0xFF121212)
    @Composable fun dateColor() = if (isDark()) Color(0xFF9E9E9E) else Color(0xFF646464)
    @Composable fun barIcon() = if (isDark()) Color(0xFFBDBDBD) else Color(0xFF555555)
    @Composable fun barIconLight() = if (isDark()) Color(0xFF616161) else Color(0xFFB4B4B4)

    /** Shadow falloff measured from the screenshot (alpha of black, 0 = at the edge). */
    val shadowStops: Array<Pair<Float, Color>> = arrayOf(
        0.00f to Color.Black.copy(alpha = 0.416f),
        0.11f to Color.Black.copy(alpha = 0.31f),
        0.22f to Color.Black.copy(alpha = 0.216f),
        0.33f to Color.Black.copy(alpha = 0.16f),
        0.44f to Color.Black.copy(alpha = 0.13f),
        0.55f to Color.Black.copy(alpha = 0.10f),
        0.67f to Color.Black.copy(alpha = 0.064f),
        0.78f to Color.Black.copy(alpha = 0.032f),
        0.89f to Color.Black.copy(alpha = 0.008f),
        1.00f to Color.Transparent
    )
}

/** Vertical shadow cast downward from the toolbar. */
@Composable
fun ShadowBelow(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(MAStyle.ShadowSize)
            .background(Brush.verticalGradient(colorStops = MAStyle.shadowStops))
    )
}

/** Horizontal shadow fading out to the left; place at the right edge of the left pane. */
@Composable
fun ShadowToLeft(modifier: Modifier = Modifier) {
    Box(
        modifier
            .width(MAStyle.ShadowSize)
            .fillMaxHeight()
            .background(
                Brush.horizontalGradient(
                    colorStops = MAStyle.shadowStops
                        .map { (1f - it.first) to it.second }
                        .reversed()
                        .toTypedArray()
                )
            )
    )
}

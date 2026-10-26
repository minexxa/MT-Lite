package com.ma.manager.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.ma.manager.ui.theme.MAStyle

/**
 * Official MA Manager bottom bar: 5 equal cells, icon only, order
 * 后退 / 前进 / 新建 / 同步 / 上级. Content height 45dp + navigation inset.
 */
@Composable
fun BottomActionBar(
    canGoBack: Boolean,
    canGoForward: Boolean,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onNew: () -> Unit,
    onSync: () -> Unit,
    onUp: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dark = MAStyle.barIcon()
    val light = MAStyle.barIconLight()
    Column(
        modifier
            .fillMaxWidth()
            .background(MAStyle.barBg())
            .navigationBarsPadding()
    ) {
        Row(Modifier.fillMaxWidth().height(45.dp)) {
            BarCell(onBack, canGoBack) { icon(this, Kind.BACK, if (canGoBack) dark else light, light) }
            BarCell(onForward, canGoForward) { icon(this, Kind.FORWARD, if (canGoForward) dark else light, light) }
            BarCell(onNew, true) { icon(this, Kind.PLUS, dark, light) }
            BarCell(onSync, true) { icon(this, Kind.SWAP, dark, light) }
            BarCell(onUp, true) { icon(this, Kind.UP, dark, light) }
        }
    }
}

private enum class Kind { BACK, FORWARD, PLUS, SWAP, UP }

@Composable
private fun RowScope.BarCell(
    onClick: () -> Unit,
    enabled: Boolean,
    draw: DrawScope.() -> Unit
) {
    Box(
        Modifier
            .weight(1f)
            .fillMaxHeight()
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(24.dp)) { draw() }
    }
}

/** Draws the glyphs (24dp canvas, measured from the official app). */
private fun icon(s: DrawScope, kind: Kind, c: Color, light: Color) = with(s) {
    val d = 1.dp.toPx()
    val cx = size.width / 2
    val cy = size.height / 2
    fun p(x: Float, y: Float) = Offset(cx + x * d, cy + y * d)
    val sw2 = 2f * d
    val sw19 = 1.9f * d
    when (kind) {
        Kind.BACK -> {
            val path = Path().apply {
                moveTo(p(2.9f, -5.2f).x, p(2.9f, -5.2f).y)
                lineTo(p(-2.3f, 0f).x, p(-2.3f, 0f).y)
                lineTo(p(2.9f, 5.2f).x, p(2.9f, 5.2f).y)
            }
            drawPath(path, c, style = Stroke(sw2, cap = StrokeCap.Butt, join = StrokeJoin.Miter))
        }
        Kind.FORWARD -> {
            val path = Path().apply {
                moveTo(p(-2.9f, -5.2f).x, p(-2.9f, -5.2f).y)
                lineTo(p(2.3f, 0f).x, p(2.3f, 0f).y)
                lineTo(p(-2.9f, 5.2f).x, p(-2.9f, 5.2f).y)
            }
            drawPath(path, c, style = Stroke(sw2, cap = StrokeCap.Butt, join = StrokeJoin.Miter))
        }
        Kind.PLUS -> {
            drawLine(c, p(-7f, 0f), p(7f, 0f), sw19)
            drawLine(c, p(0f, -7f), p(0f, 7f), sw19)
        }
        Kind.UP -> {
            val top = -7.9f
            drawLine(c, p(0f, top + 1.4f), p(0f, top + 15.8f), sw19)
            val path = Path().apply {
                moveTo(p(-7f, top + 8.4f).x, p(-7f, top + 8.4f).y)
                lineTo(p(0f, top + 1.4f).x, p(0f, top + 1.4f).y)
                lineTo(p(7f, top + 8.4f).x, p(7f, top + 8.4f).y)
            }
            drawPath(path, c, style = Stroke(sw2, cap = StrokeCap.Butt, join = StrokeJoin.Miter))
        }
        Kind.SWAP -> {
            // icon box 19 x 14.3dp, centred
            val ox = -9.5f
            val oy = -7.15f
            fun q(x: Float, y: Float) = Offset(cx + (ox + x) * d, cy + (oy + y) * d)
            // top arrow, pointing right (light)
            drawLine(light, q(7.8f, 3.6f), q(15.2f, 3.6f), sw19)
            val head1 = Path().apply {
                moveTo(q(15.2f, -0.3f).x, q(15.2f, -0.3f).y)
                lineTo(q(19f, 3.6f).x, q(19f, 3.6f).y)
                lineTo(q(15.2f, 7.5f).x, q(15.2f, 7.5f).y)
                close()
            }
            drawPath(head1, light, style = Fill)
            // bottom arrow, pointing left (dark)
            drawLine(c, q(3.8f, 10.7f), q(11.8f, 10.7f), sw19)
            val head2 = Path().apply {
                moveTo(q(4.1f, 6.5f).x, q(4.1f, 6.5f).y)
                lineTo(q(0f, 10.7f).x, q(0f, 10.7f).y)
                lineTo(q(4.1f, 14.9f).x, q(4.1f, 14.9f).y)
                close()
            }
            drawPath(head2, c, style = Fill)
        }
    }
}

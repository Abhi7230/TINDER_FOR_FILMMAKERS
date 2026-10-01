package com.tff.app.ui.screens.onboarding

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tff.app.ui.theme.Ct

/**
 * The Calltime mark: a clapperboard slate drawn in Tungsten. [openFraction] 1 = clapper open (−18°),
 * 0 = closed, so Splash can clap it shut once.
 */
@Composable
fun SlateMark(modifier: Modifier = Modifier, size: Dp = 64.dp, openFraction: Float = 1f, color: Color = Ct.colors.accent) {
    val bg = Ct.colors.bg
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = w * 0.055f
        // board
        val boardTop = h * 0.38f
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.08f, boardTop),
            size = Size(w * 0.84f, h * 0.54f),
            cornerRadius = CornerRadius(w * 0.06f),
            style = Stroke(width = stroke),
        )
        // two "slate lines" on the board
        drawLine(color.copy(alpha = 0.7f), Offset(w * 0.2f, h * 0.58f), Offset(w * 0.62f, h * 0.58f), strokeWidth = stroke * 0.8f)
        drawLine(color.copy(alpha = 0.7f), Offset(w * 0.2f, h * 0.74f), Offset(w * 0.46f, h * 0.74f), strokeWidth = stroke * 0.8f)
        // clapper bar, hinged at the left
        val barH = h * 0.15f
        val y0 = boardTop - barH - h * 0.02f
        rotate(degrees = -18f * openFraction.coerceIn(0f, 1f), pivot = Offset(w * 0.08f, y0 + barH)) {
            drawRoundRect(color = color, topLeft = Offset(w * 0.08f, y0), size = Size(w * 0.84f, barH), cornerRadius = CornerRadius(w * 0.03f))
            // diagonal stripes cut in the background colour
            for (i in 0 until 3) {
                val x = w * (0.22f + i * 0.25f)
                val p = Path().apply {
                    moveTo(x, y0)
                    lineTo(x + w * 0.09f, y0)
                    lineTo(x + w * 0.03f, y0 + barH)
                    lineTo(x - w * 0.06f, y0 + barH)
                    close()
                }
                drawPath(p, bg)
            }
        }
    }
}

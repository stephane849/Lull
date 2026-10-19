package app.lull.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class Glyph { BACK, CHEVRON, SEARCH, LIBRARY, NOTE, PLAY, PAUSE, NEXT, PREVIOUS, DOWNLOAD, DOWNLOADED, PENDING, CLOSE, HEART, HEART_FILLED, SHUFFLE, MOON, FAST }

/**
 * Simple stroke icons drawn on a 24-unit grid. Drawn locally so the app needs no icon
 * library and every icon is a crisp single-colour line, which suits E Ink.
 */
@Composable
fun LineIcon(glyph: Glyph, modifier: Modifier = Modifier, size: Dp = 24.dp, color: Color = Color.Black) {
    Canvas(modifier.size(size)) {
        val u = this.size.minDimension / 24f
        val stroke = Stroke(width = 2.5f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun p(x: Float, y: Float) = Offset(x * u, y * u)
        fun line(vararg pts: Pair<Float, Float>) {
            for (i in 0 until pts.size - 1) {
                drawLine(color, p(pts[i].first, pts[i].second), p(pts[i + 1].first, pts[i + 1].second), stroke.width, StrokeCap.Round)
            }
        }
        fun poly(fill: Boolean, vararg pts: Pair<Float, Float>) {
            val path = Path().apply {
                moveTo(pts[0].first * u, pts[0].second * u)
                pts.drop(1).forEach { lineTo(it.first * u, it.second * u) }
                close()
            }
            drawPath(path, color, style = if (fill) Fill else stroke)
        }
        fun circle(cx: Float, cy: Float, r: Float) = drawCircle(color, r * u, p(cx, cy), style = stroke)

        when (glyph) {
            Glyph.BACK -> line(15f to 5f, 8f to 12f, 15f to 19f)
            Glyph.CHEVRON -> line(9f to 5f, 16f to 12f, 9f to 19f)
            Glyph.SEARCH -> { circle(11f, 11f, 7f); line(20f to 20f, 16f to 16f) }
            Glyph.LIBRARY -> { line(4f to 5f, 4f to 19f); line(9f to 5f, 9f to 19f); line(14f to 5f, 19f to 19f) }
            Glyph.NOTE -> { line(9f to 18f, 9f to 6f, 19f to 4f, 19f to 16f); circle(6.5f, 18f, 2.5f); circle(16.5f, 16f, 2.5f) }
            Glyph.PLAY -> poly(true, 8f to 5f, 19f to 12f, 8f to 19f)
            Glyph.PAUSE -> { line(9f to 6f, 9f to 18f); line(15f to 6f, 15f to 18f) }
            Glyph.NEXT -> { poly(false, 6f to 6f, 15f to 12f, 6f to 18f); line(18f to 6f, 18f to 18f) }
            Glyph.PREVIOUS -> { poly(false, 18f to 6f, 9f to 12f, 18f to 18f); line(6f to 6f, 6f to 18f) }
            Glyph.DOWNLOAD -> { line(12f to 4f, 12f to 15f); line(7f to 10f, 12f to 15f, 17f to 10f); line(5f to 20f, 19f to 20f) }
            Glyph.DOWNLOADED -> { circle(12f, 12f, 9f); line(8f to 12f, 11f to 15f, 16f to 9f) }
            Glyph.PENDING -> { circle(12f, 12f, 9f); line(12f to 7f, 12f to 12f, 15f to 14f) }
            Glyph.CLOSE -> { line(6f to 6f, 18f to 18f); line(18f to 6f, 6f to 18f) }
            Glyph.HEART, Glyph.HEART_FILLED -> drawHeart(u, color, if (glyph == Glyph.HEART_FILLED) Fill else stroke)
            Glyph.SHUFFLE -> { line(4f to 7f, 7f to 7f, 17f to 17f, 20f to 17f); line(4f to 17f, 7f to 17f, 10f to 14f); line(14f to 10f, 17f to 7f, 20f to 7f); line(18f to 5f, 20f to 7f, 18f to 9f); line(18f to 15f, 20f to 17f, 18f to 19f) }
            Glyph.FAST -> { line(5f to 6f, 11f to 12f, 5f to 18f); line(12f to 6f, 18f to 12f, 12f to 18f) }
            Glyph.MOON -> { circle(12f, 12f, 8f); poly(true, 12f to 4f, 16f to 5f, 18f to 8f, 18f to 12f, 15f to 10f, 12f to 9f) }
        }
    }
}

private fun DrawScope.drawHeart(u: Float, color: Color, style: androidx.compose.ui.graphics.drawscope.DrawStyle) {
    val path = Path().apply {
        moveTo(12f * u, 20f * u)
        cubicTo(2f * u, 13f * u, 4f * u, 3.5f * u, 12f * u, 8.5f * u)
        cubicTo(20f * u, 3.5f * u, 22f * u, 13f * u, 12f * u, 20f * u)
        close()
    }
    drawPath(path, color, style = style)
}

package com.sheen.puzzler.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Gem and color bomb geometry shared by the board and the launch intro. The launcher icon and splash drawables use
 * the same proportions (docs/SPEC.md §5).
 */
internal object ColorBomb {
    /** Gem centers sit on a ring of this radius, as a fraction of the orb radius. */
    const val RING = 0.6f

    /** Gem size as a fraction of the orb radius. */
    const val GEM = 0.19f

    /** Sparkle outer radius as a fraction of the orb radius. */
    const val SPARKLE = 0.23f

    /** Angle of gem [index] on the ring: the first at the top, then clockwise. */
    fun angle(index: Int): Double = -PI / 2 + 2 * PI * index / PuzzlerColors.pieces.size

    /** Unit direction from the orb center toward gem [index]. */
    fun direction(index: Int) = Offset(cos(angle(index)).toFloat(), sin(angle(index)).toFloat())
}

/** Draws the color bomb: flat orb, the six mini gems on their ring, and the gold sparkle core. */
internal fun DrawScope.drawColorBomb(center: Offset, radius: Float, alpha: Float = 1f) {
    drawCircle(PuzzlerColors.bombOrb.copy(alpha = alpha), radius, center)
    PuzzlerColors.pieces.forEachIndexed { i, color ->
        drawPath(shapePath(i, center + ColorBomb.direction(i) * (radius * ColorBomb.RING), radius * ColorBomb.GEM), color.copy(alpha = alpha))
    }
    drawPath(sparklePath(center, radius * ColorBomb.SPARKLE), PuzzlerColors.gold.copy(alpha = alpha))
}

/** A distinct outline per color index: circle, rounded square, diamond, triangle, hexagon, star. */
internal fun shapePath(colorIndex: Int, c: Offset, r: Float): Path = Path().apply {
    when (colorIndex % 6) {
        0 -> addOval(Rect(c, r * 0.95f))
        1 -> {
            val h = r * 0.85f
            addRoundRect(RoundRect(c.x - h, c.y - h, c.x + h, c.y + h, r * 0.3f, r * 0.3f))
        }
        2 -> polygon(c, r * 1.05f, 4, -PI / 2)
        3 -> polygon(c, r * 1.08f, 3, -PI / 2, yShift = r * 0.15f)
        4 -> polygon(c, r, 6, 0.0)
        else -> {
            for (i in 0 until 10) {
                val rr = if (i % 2 == 0) r * 1.05f else r * 0.5f
                val a = -PI / 2 + i * PI / 5
                val p = Offset(c.x + (cos(a) * rr).toFloat(), c.y + (sin(a) * rr).toFloat())
                if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
            }
            close()
        }
    }
}

private fun Path.polygon(c: Offset, r: Float, sides: Int, start: Double, yShift: Float = 0f) {
    for (i in 0 until sides) {
        val a = start + i * 2 * PI / sides
        val x = c.x + (cos(a) * r).toFloat()
        val y = c.y + yShift + (sin(a) * r).toFloat()
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

/** Four-point sparkle: the color bomb's core. */
internal fun sparklePath(c: Offset, r: Float): Path = Path().apply {
    for (i in 0 until 8) {
        val rr = if (i % 2 == 0) r else r * 0.3f
        val a = -PI / 2 + i * PI / 4
        val p = Offset(c.x + (cos(a) * rr).toFloat(), c.y + (sin(a) * rr).toFloat())
        if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
    }
    close()
}

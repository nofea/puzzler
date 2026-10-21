package com.sheen.puzzler.ui.tutorial

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * One loop of the swipe hint, [t] in 0..1: a fingertip presses on [from], slides to [to] leaving a trail, holds, and
 * fades. A faint arrow shows the direction throughout. [cell] is the board cell size in px.
 */
internal fun DrawScope.drawSwipeHint(from: Offset, to: Offset, t: Float, cell: Float) {
    val slide = FastOutSlowInEasing.transform(((t - 0.15f) / 0.45f).coerceIn(0f, 1f))
    val alpha = when {
        t < 0.15f -> t / 0.15f
        t > 0.8f -> ((1f - t) / 0.2f).coerceAtLeast(0f)
        else -> 1f
    }
    val tip = lerp(from, to, slide)

    // Direction arrow.
    val arrow = Color.White.copy(alpha = 0.4f * alpha)
    drawLine(arrow, from, to, cell * 0.06f, StrokeCap.Round)
    val angle = atan2(to.y - from.y, to.x - from.x)
    for (side in listOf(-1, 1)) {
        val a = angle + Math.PI.toFloat() * (1f - 0.18f * side)
        drawLine(arrow, to, to + Offset(cos(a), sin(a)) * (cell * 0.22f), cell * 0.06f, StrokeCap.Round)
    }

    // Trail behind the fingertip.
    drawLine(Color.White.copy(alpha = 0.35f * alpha), from, tip, cell * 0.3f, StrokeCap.Round)

    // Press ripple at the start.
    if (t < 0.3f) {
        val r = (t / 0.3f)
        drawCircle(Color.White.copy(alpha = 0.6f * (1f - r)), cell * (0.2f + 0.25f * r), from, style = Stroke(cell * 0.04f))
    }

    // Fingertip.
    drawCircle(Color.White.copy(alpha = 0.92f * alpha), cell * 0.19f, tip)
    drawCircle(Color.Black.copy(alpha = 0.3f * alpha), cell * 0.19f, tip, style = Stroke(cell * 0.03f))
}

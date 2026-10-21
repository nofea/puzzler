package com.sheen.puzzler.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sheen.puzzler.fx.Sfx
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/** Finale timeline in milliseconds (docs/SPEC.md §5). */
private object Finale {
    const val ITEM_STAGGER = 60f
    const val ITEM_FLIGHT = 500f
    const val DETONATE = 800f
    const val FLASH = 600f
    const val TITLE = 1100f
    const val TITLE_IN = 400f
    const val COUNT_FROM = 1500f
    const val COUNT_TO = 2600f
    const val CONTINUE = 4000f
    const val CONTINUE_IN = 400f

    /** When each firework bursts. */
    val fireworks = listOf(1000f, 1350f, 1700f, 2150f, 2600f, 3100f, 3600f)
    const val FIREWORK = 900f
    const val SPARKS = 28

    const val CONFETTI = 36
    const val CONFETTI_FALL = 3200f

    fun itemStart(i: Int) = i * ITEM_STAGGER

    fun itemLand(i: Int) = itemStart(i) + ITEM_FLIGHT
}

/**
 * All levels complete: the color bomb assembles and detonates, fireworks and item confetti fill the screen, and the
 * total stars count up under the title. Waits for a tap after about 4 s (a tap before then jumps there). With the
 * system's animations off it's a still card with a Continue button instead; the sounds still play.
 */
@Composable
fun FinaleOverlay(
    totalStars: Int,
    maxStars: Int,
    fx: Fx,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val animated = remember { animationsEnabled(context) }
    if (!animated) {
        LaunchedEffect(Unit) {
            fx.sound.play(Sfx.FANFARE)
            fx.haptics.celebrate()
        }
        FinaleCard(totalStars, maxStars, onDone)
        return
    }

    var time by remember { mutableFloatStateOf(0f) }
    var offset by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameMillis { it }
        var landed = 0
        var detonated = false
        var bursts = 0
        while (true) {
            withFrameMillis { now -> time = now - start + offset }
            val t = time
            while (landed < PuzzlerColors.pieces.size && t >= Finale.itemLand(landed) && t < Finale.DETONATE) {
                landed++
                fx.sound.play(Sfx.LOCK)
                fx.haptics.tick()
            }
            if (!detonated && t >= Finale.DETONATE) {
                detonated = true
                landed = PuzzlerColors.pieces.size
                fx.sound.play(Sfx.DETONATE)
                fx.haptics.heavy()
            }
            while (bursts < Finale.fireworks.size && t >= Finale.fireworks[bursts]) {
                if (t < Finale.fireworks[bursts] + (Finale.FIREWORK / 3)) {
                    fx.sound.play(Sfx.SPARKLE)
                    if (bursts == 0) fx.haptics.celebrate()
                }
                bursts++
            }
        }
    }
    // The fanfare rises into the detonation.
    LaunchedEffect(Unit) { fx.sound.play(Sfx.FANFARE) }

    val images = rememberItemImages()
    val measurer = rememberTextMeasurer()
    val titleStyle = TextStyle(fontFamily = PuzzlerFonts.display, fontSize = 30.sp, color = PuzzlerColors.gold)
    val starsStyle = TextStyle(fontFamily = PuzzlerFonts.display, fontSize = 26.sp, color = Color.White)
    val hintStyle = TextStyle(fontSize = 16.sp, color = Color.White.copy(alpha = 0.8f))
    val confetti = remember { List(Finale.CONFETTI) { Confetto(Random((it * 97) + 13)) } }
    val sparks = remember { Finale.fireworks.indices.map { Firework(Random((it * 31) + 7)) } }

    Canvas(
        Modifier
            .fillMaxSize()
            .testTag("finale")
            .pointerInput(Unit) {
                detectTapGestures {
                    if (time >= Finale.CONTINUE) onDone() else offset += Finale.CONTINUE - time
                }
            },
    ) {
        val t = time
        val center = Offset(size.width / 2, size.height * 0.38f)
        val orb = 56.dp.toPx()
        val far = hypot(size.width, size.height)
        drawRect(PuzzlerColors.background)

        // Assemble, then the orb vanishes in the detonation.
        if (t < Finale.DETONATE) {
            drawCircle(PuzzlerColors.bombOrb, orb, center)
            drawPath(sparklePath(center, orb * ColorBomb.SPARKLE), PuzzlerColors.gold)
            images.indices.forEach { i ->
                if (t < Finale.itemStart(i)) return@forEach
                val f = FastOutSlowInEasing.transform(((t - Finale.itemStart(i)) / Finale.ITEM_FLIGHT).coerceIn(0f, 1f))
                val dist = far + (((orb * ColorBomb.RING) - far) * f)
                drawItem(images, i, center + (ColorBomb.direction(i) * dist), orb * ColorBomb.ITEM, shadow = false)
            }
        }

        // Fireworks: sparks in the piece colors fly out and fade.
        Finale.fireworks.forEachIndexed { i, at ->
            val f = (t - at) / Finale.FIREWORK
            if (f in 0f..1f) sparks[i].draw(this, f, size.width, size.height)
        }

        // Confetti made of the six items keeps raining down once the bomb has gone off.
        if (t >= Finale.DETONATE) {
            confetti.forEachIndexed { i, c ->
                val local = (t - Finale.DETONATE - c.delay) / Finale.CONFETTI_FALL
                if (local < 0f) return@forEachIndexed
                val fall = local % 1f
                val pos =
                    Offset(
                        (c.x * size.width) + (sin(local * 2 * PI.toFloat() * c.sway) * 24.dp.toPx()),
                        -40.dp.toPx() + (fall * (size.height + 80.dp.toPx())),
                    )
                rotate(local * 360f * c.spin, pos) { drawItem(images, i % images.size, pos, 30.dp.toPx(), shadow = false) }
            }
        }

        // The detonation's gold flash fills the screen and fades.
        val flash = (t - Finale.DETONATE) / Finale.FLASH
        if (flash in 0f..1f) drawRect(PuzzlerColors.gold.copy(alpha = 0.85f * (1f - flash)))

        // Title, then the stars counting up, then the tap hint.
        val titleIn = ((t - Finale.TITLE) / Finale.TITLE_IN).coerceIn(0f, 1f)
        if (titleIn > 0f) {
            val title = measurer.measure("ALL LEVELS COMPLETE!", titleStyle)
            val s = 0.6f + (0.4f * FastOutSlowInEasing.transform(titleIn))
            val top = size.height * 0.55f
            withTransform({ scale(s, s, Offset(center.x, top + (title.size.height / 2f))) }) {
                drawText(title, topLeft = Offset(center.x - (title.size.width / 2f), top), alpha = titleIn)
            }
            val count = (totalStars * ((t - Finale.COUNT_FROM) / (Finale.COUNT_TO - Finale.COUNT_FROM)).coerceIn(0f, 1f)).toInt()
            val stars = measurer.measure("★ $count / $maxStars", starsStyle)
            drawText(stars, topLeft = Offset(center.x - (stars.size.width / 2f), top + title.size.height + 12.dp.toPx()), alpha = titleIn)
            val hintIn = ((t - Finale.CONTINUE) / Finale.CONTINUE_IN).coerceIn(0f, 1f)
            if (hintIn > 0f) {
                val hint = measurer.measure("Tap to continue", hintStyle)
                drawText(hint, topLeft = Offset(center.x - (hint.size.width / 2f), size.height * 0.85f), alpha = hintIn)
            }
        }
    }
}

/** One piece of item confetti: where it falls, when it starts, how it sways and spins. */
private class Confetto(
    rng: Random,
) {
    val x = rng.nextFloat()
    val delay = rng.nextFloat() * Finale.CONFETTI_FALL
    val sway = 0.5f + rng.nextFloat()
    val spin = (rng.nextFloat() - 0.5f) * 2f
}

/** One firework burst at a random spot in the upper part of the screen, in one of the piece colors. */
private class Firework(
    rng: Random,
) {
    private val x = 0.15f + (0.7f * rng.nextFloat())
    private val y = 0.1f + (0.4f * rng.nextFloat())
    private val color = PuzzlerColors.pieces[rng.nextInt(PuzzlerColors.pieces.size)]
    private val reach = 0.18f + (0.1f * rng.nextFloat())

    fun draw(
        scope: DrawScope,
        f: Float,
        width: Float,
        height: Float,
    ) = with(scope) {
        val center = Offset(x * width, y * height)
        val fade = 1f - f
        // A bright bloom where it bursts, then streaking sparks that droop as they fade.
        if (f < 0.25f) drawCircle(Color.White.copy(alpha = 1f - (f * 4f)), radius = 18.dp.toPx() * (1f + (f * 4f)), center = center)
        val r = FastOutSlowInEasing.transform(f) * reach * width
        val trail = (14.dp.toPx() * fade) + 2.dp.toPx()
        for (k in 0 until Finale.SPARKS) {
            val a = 2 * PI * k / Finale.SPARKS
            val dir = Offset(cos(a).toFloat(), sin(a).toFloat())
            val droop = Offset(0f, f * f * 40.dp.toPx())
            val tip = center + (dir * r) + droop
            drawLine(color.copy(alpha = fade), tip - (dir * trail), tip, strokeWidth = (3.dp.toPx() * fade) + 1f, cap = StrokeCap.Round)
            drawCircle(Color.White.copy(alpha = fade), radius = (2.5.dp.toPx() * fade) + 1f, center = tip)
        }
    }
}

/** The finale as a still card, for when the system's animations are off. */
@Composable
private fun FinaleCard(
    totalStars: Int,
    maxStars: Int,
    onDone: () -> Unit,
) {
    Box(Modifier.fillMaxSize().background(PuzzlerColors.background).testTag("finale"), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("ALL LEVELS COMPLETE!", fontFamily = PuzzlerFonts.display, fontSize = 30.sp, color = PuzzlerColors.gold)
            Text("★ $totalStars / $maxStars", fontSize = 24.sp, modifier = Modifier.padding(top = 12.dp))
            Button(onClick = onDone, modifier = Modifier.padding(top = 24.dp).testTag("finale_continue")) { Text("Continue") }
        }
    }
}

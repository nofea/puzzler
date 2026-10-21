package com.sheen.puzzler.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sheen.puzzler.data.Progress
import com.sheen.puzzler.data.ProgressRepository
import com.sheen.puzzler.fx.Haptics
import com.sheen.puzzler.fx.Sfx
import com.sheen.puzzler.fx.SoundPlayer
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.sin

private val RevealEasing = CubicBezierEasing(0.4f, 0f, 0.6f, 1f)

/** Intro timeline in milliseconds (docs/SPEC.md §4). */
private object Intro {
    const val ITEM_STAGGER = 60f
    const val ITEM_FLIGHT = 500f
    const val FLASH = 800f
    const val FLASH_LENGTH = 300f
    const val DETONATE = 1300f
    const val END = 2000f

    fun itemStart(i: Int) = i * ITEM_STAGGER

    fun itemLand(i: Int) = itemStart(i) + ITEM_FLIGHT
}

/**
 * Launch intro drawn over the level map: the items fly in and lock around the splash orb, the title appears, then the
 * bomb detonates and a growing circle reveals the map underneath. Tapping skips ahead to the detonation.
 * [anchor] arrives when the system splash hands over; until then only the splash background is drawn.
 */
@Composable
fun IntroOverlay(
    anchor: OrbAnchor?,
    progressRepo: ProgressRepository,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val progress by progressRepo.progress.collectAsState(initial = Progress())
    val sound = remember { SoundPlayer() }
    val haptics = remember { Haptics(context) }
    sound.muted = progress.muted
    haptics.enabled = progress.haptics
    DisposableEffect(Unit) { onDispose { sound.release() } }

    val time = remember { Animatable(0f) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    var fallback by remember { mutableStateOf(false) }
    var started by remember { mutableStateOf(false) }
    var skipped by remember { mutableStateOf(false) }
    val running = anchor != null || fallback

    // If the splash never hands over (no exit callback), start from the screen center anyway.
    LaunchedEffect(Unit) {
        delay(1500)
        fallback = true
    }
    LaunchedEffect(running, skipped) {
        if (!running) return@LaunchedEffect
        started = true
        if (skipped && time.value < Intro.DETONATE) time.snapTo(Intro.DETONATE)
        time.animateTo(Intro.END, tween((Intro.END - time.value).toInt().coerceAtLeast(0), easing = LinearEasing))
        onDone()
    }
    // Sounds and haptics as the timeline crosses each beat. Items skipped past by a tap land silently.
    LaunchedEffect(Unit) {
        val landed = BooleanArray(PuzzlerColors.pieces.size)
        var detonated = false
        snapshotFlow { time.value }.collect { t ->
            for (i in landed.indices) {
                if (!landed[i] && t >= Intro.itemLand(i)) {
                    landed[i] = true
                    if (t < Intro.DETONATE) {
                        sound.play(Sfx.LOCK)
                        haptics.tick()
                    }
                }
            }
            if (!detonated && t >= Intro.DETONATE) {
                detonated = true
                sound.play(Sfx.DETONATE)
                haptics.heavy()
            }
        }
    }

    val images = rememberItemImages()
    val textMeasurer = rememberTextMeasurer()
    val titleStyle = TextStyle(fontFamily = PuzzlerFonts.display, fontSize = 48.sp, color = PuzzlerColors.gold, letterSpacing = 4.sp)
    val taglineStyle = TextStyle(fontFamily = PuzzlerFonts.display, fontSize = 16.sp, color = Color.White.copy(alpha = 0.7f))

    Canvas(
        Modifier
            .fillMaxSize()
            .testTag("intro")
            .onGloballyPositioned { origin = it.positionInWindow() }
            .pointerInput(Unit) {
                detectTapGestures { if (started && time.value < Intro.DETONATE) skipped = true }
            },
    ) {
        val t = time.value
        val center = anchor?.let { it.center - origin } ?: Offset(size.width / 2, size.height / 2)
        val orb = anchor?.radius ?: 56.dp.toPx()
        val far = hypot(size.width, size.height)
        val boom = ((t - Intro.DETONATE) / (Intro.END - Intro.DETONATE)).coerceIn(0f, 1f)

        // Everything but the revealed circle is still splash background. It grows just far enough to clear the
        // farthest corner, evenly enough that the wipe is visible.
        val corner =
            maxOf(
                hypot(center.x, center.y),
                hypot(size.width - center.x, center.y),
                hypot(center.x, size.height - center.y),
                hypot(size.width - center.x, size.height - center.y),
            )
        val hole = Path().apply { addOval(Rect(center, corner * RevealEasing.transform(boom))) }
        clipPath(hole, ClipOp.Difference) {
            drawRect(PuzzlerColors.background)
            if (boom < 1f) {
                drawCircle(PuzzlerColors.bombOrb.copy(alpha = 1f - boom), orb * (1f + 0.3f * boom), center)
                val flash = ((t - Intro.FLASH) / Intro.FLASH_LENGTH).coerceIn(0f, 1f)
                val sparkleScale = 1f + 0.6f * sin(PI * flash).toFloat()
                drawPath(sparklePath(center, orb * ColorBomb.SPARKLE * sparkleScale), PuzzlerColors.gold.copy(alpha = 1f - boom))
            }
        }

        // Items fly in to the ring, then (after detonating) out past the screen edge, over the revealed map.
        if (started) {
            images.indices.forEach { i ->
                if (t < Intro.itemStart(i)) return@forEach
                val dir = ColorBomb.direction(i)
                val ring = orb * ColorBomb.RING
                val dist =
                    if (t < Intro.DETONATE) {
                        val f = FastOutSlowInEasing.transform(((t - Intro.itemStart(i)) / Intro.ITEM_FLIGHT).coerceIn(0f, 1f))
                        far + (ring - far) * f
                    } else {
                        ring + far * FastOutLinearInEasing.transform(boom)
                    }
                drawItem(images, i, center + dir * dist, orb * ColorBomb.ITEM, shadow = false)
            }
        }

        // Title and tagline fade and rise in after the sparkle flash, and fade out quickly on detonation.
        val fadeOut = (1f - boom * 3f).coerceIn(0f, 1f)
        val titleIn = ((t - Intro.FLASH) / Intro.FLASH_LENGTH).coerceIn(0f, 1f)
        val taglineIn = ((t - Intro.FLASH - 100f) / Intro.FLASH_LENGTH).coerceIn(0f, 1f)
        val rise = 12.dp.toPx()
        if (titleIn > 0f) {
            val title = textMeasurer.measure("PUZZLER", titleStyle)
            val top = center.y + orb + 28.dp.toPx() + rise * (1f - titleIn)
            drawText(title, topLeft = Offset(center.x - title.size.width / 2f, top), alpha = titleIn * fadeOut)
            if (taglineIn > 0f) {
                val tagline = textMeasurer.measure("Match. Cascade. Detonate.", taglineStyle)
                val taglineTop = center.y + orb + 28.dp.toPx() + title.size.height + 4.dp.toPx() + rise * (1f - taglineIn)
                drawText(tagline, topLeft = Offset(center.x - tagline.size.width / 2f, taglineTop), alpha = taglineIn * fadeOut)
            }
        }
    }
}

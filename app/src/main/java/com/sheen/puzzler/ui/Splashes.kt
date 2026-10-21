package com.sheen.puzzler.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sheen.puzzler.fx.Sfx
import kotlin.math.PI
import kotlin.math.sin

/** Something a splash does once its clock passes [at] milliseconds: a sound, a vibration. */
class Cue(
    val at: Float,
    val action: () -> Unit,
)

/**
 * A splash's clock: runs from 0 to [length] ms, firing each cue as it passes, then calls [onDone]. Tapping ([skip])
 * jumps to the end. With the system's animations off it jumps to the end straight away; the cues still fire, so the
 * sounds and vibration still play (docs/SPEC.md §5).
 */
class SplashClock(
    private val time: Animatable<Float, *>,
    val skip: () -> Unit,
) {
    val millis get() = time.value
}

@Composable
fun rememberSplashClock(
    length: Float,
    cues: List<Cue>,
    onDone: () -> Unit,
): SplashClock {
    val context = LocalContext.current
    val time = remember { Animatable(0f) }
    var skipped by remember { mutableStateOf(false) }
    val fired = remember { BooleanArray(cues.size) }
    val done by rememberUpdatedState(onDone)

    fun fireDue(t: Float) {
        cues.forEachIndexed { i, cue ->
            if (!fired[i] && t >= cue.at) {
                fired[i] = true
                cue.action()
            }
        }
    }
    LaunchedEffect(skipped) {
        if (skipped || !animationsEnabled(context)) {
            time.snapTo(length)
        } else {
            time.animateTo(length, tween((length - time.value).toInt().coerceAtLeast(0), easing = LinearEasing)) { fireDue(value) }
        }
        fireDue(length)
        done()
    }
    return remember { SplashClock(time) { skipped = true } }
}

/** Level cleared splash timeline (docs/SPEC.md §5), in milliseconds. */
object ClearedSplash {
    const val LENGTH = 1000f
    const val POP = 300f
    const val HOP = 500f
    const val STAR_GAP = 233f
    const val STAMP = 150f

    fun starAt(i: Int) = POP + (i * STAR_GAP)

    fun cues(
        stars: Int,
        fx: Fx,
    ) = (0 until stars).map { i ->
        Cue(starAt(i)) {
            fx.sound.star(i + 1)
            fx.haptics.tick()
        }
    }
}

/** Game Over splash timeline (docs/SPEC.md §5), in milliseconds. */
object GameOverSplash {
    const val LENGTH = 1500f
    const val SLAM = 600f
    const val SLAM_IN = 150f
    const val SHAKE = 300f
    const val SUBTITLE = 700f
    const val SUBTITLE_IN = 300f

    fun cues(fx: Fx) =
        listOf(
            Cue(0f) { fx.sound.play(Sfx.GAME_OVER) },
            Cue(SLAM) {
                fx.sound.play(Sfx.THUD)
                fx.haptics.slam()
            },
        )

    /** How far the items have fallen off the board (0..1). */
    fun collapse(t: Float) = (t / SLAM).coerceIn(0f, 1f)

    /** Horizontal screen shake right after the slam, in dp. */
    fun shake(t: Float): Float {
        val s = (t - SLAM) / SHAKE
        if (s !in 0f..1f) return 0f
        return sin(s * 6 * PI).toFloat() * 12f * (1f - s)
    }
}

private fun ease(f: Float) = FastOutSlowInEasing.transform(f.coerceIn(0f, 1f))

/** A pop that overshoots a little before settling at 1. */
private fun pop(f: Float): Float {
    val x = f.coerceIn(0f, 1f)
    return 1f + (0.25f * sin(x * PI).toFloat()) - ((1f - ease(x)) * 0.7f)
}

/**
 * "Level cleared!" with a small gold ring burst, then the earned stars stamping in one by one. No dimming and no shake:
 * it's meant to be smaller than the Game Over splash and the finale. Tapping skips.
 */
@Composable
fun LevelClearedSplash(
    clock: SplashClock,
    stars: Int,
) {
    val t = clock.millis
    Box(
        Modifier
            .fillMaxSize()
            .testTag("cleared_splash")
            .pointerInput(Unit) { detectTapGestures { clock.skip() } },
        contentAlignment = Alignment.Center,
    ) {
        val popIn = t / ClearedSplash.POP
        // The ring bursts out from behind the title and fades as it grows.
        Canvas(Modifier.size(260.dp)) {
            val f = (t / ClearedSplash.HOP).coerceIn(0f, 1f)
            if (f in 0.001f..0.999f) {
                drawCircle(
                    PuzzlerColors.gold.copy(alpha = 0.8f * (1f - f)),
                    radius = size.minDimension / 2 * (0.3f + (0.7f * ease(f))),
                    style = Stroke((10.dp.toPx() * (1f - f)) + 1f),
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "Level cleared!",
                style = TextStyle(fontFamily = PuzzlerFonts.display, fontSize = 40.sp, color = PuzzlerColors.gold, shadow = textShadow),
                modifier =
                    Modifier.graphicsLayer {
                        val s = pop(popIn)
                        scaleX = s
                        scaleY = s
                        alpha = popIn.coerceIn(0f, 1f)
                    },
            )
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (i in 0 until 3) {
                    val f = (t - ClearedSplash.starAt(i)) / ClearedSplash.STAMP
                    val earned = i < stars && f >= 0f
                    Text(
                        if (earned) "★" else "☆",
                        fontSize = 44.sp,
                        color = if (earned) PuzzlerColors.gold else Color.White.copy(alpha = 0.35f),
                        style = TextStyle(shadow = textShadow),
                        modifier =
                            Modifier.graphicsLayer {
                                // An earned star stamps down from oversized.
                                val s = if (earned) 1f + (1.2f * (1f - ease(f))) else 1f
                                scaleX = s
                                scaleY = s
                            },
                    )
                }
            }
        }
    }
}

/**
 * The Game Over dim and title; the screen collapses the board ([GameOverSplash.collapse]) and shakes
 * ([GameOverSplash.shake]) on the same clock. Tapping skips.
 */
@Composable
fun GameOverSplashOverlay(
    clock: SplashClock,
    subtitle: String,
) {
    val t = clock.millis
    Box(
        Modifier
            .fillMaxSize()
            .testTag("game_over_splash")
            .background(Color(0xFF3A0610).copy(alpha = 0.78f * GameOverSplash.collapse(t)))
            .pointerInput(Unit) { detectTapGestures { clock.skip() } },
        contentAlignment = Alignment.Center,
    ) {
        if (t < GameOverSplash.SLAM) return@Box
        val slam = ease((t - GameOverSplash.SLAM) / GameOverSplash.SLAM_IN)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "GAME OVER",
                style =
                    TextStyle(
                        fontFamily = PuzzlerFonts.display,
                        fontSize = 52.sp,
                        color = Color(0xFFFF4D5E),
                        shadow = Shadow(Color.Black, blurRadius = 2f),
                        letterSpacing = 2.sp,
                    ),
                modifier =
                    Modifier.graphicsLayer {
                        // Slams in from oversized.
                        val s = 1f + (1.6f * (1f - slam))
                        scaleX = s
                        scaleY = s
                        alpha = slam
                    },
            )
            Text(
                subtitle,
                fontSize = 20.sp,
                color = Color.White,
                modifier =
                    Modifier
                        .padding(top = 8.dp)
                        .graphicsLayer {
                            alpha = ((t - GameOverSplash.SUBTITLE) / GameOverSplash.SUBTITLE_IN).coerceIn(0f, 1f)
                        },
            )
        }
    }
}

private val textShadow = Shadow(Color.Black.copy(alpha = 0.6f), blurRadius = 8f)

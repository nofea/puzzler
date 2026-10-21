package com.sheen.puzzler.ui

import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.sheen.puzzler.data.Progress
import com.sheen.puzzler.data.ProgressRepository
import com.sheen.puzzler.engine.Pattern
import com.sheen.puzzler.engine.Phase
import com.sheen.puzzler.engine.PhaseKind
import com.sheen.puzzler.fx.Haptics
import com.sheen.puzzler.fx.Sfx
import com.sheen.puzzler.fx.SoundPlayer

/** Sound and haptic cues a game screen plays as a move animates. */
sealed interface GameEvent {
    object Swap : GameEvent

    object Invalid : GameEvent

    data class Match(
        val cascade: Int,
    ) : GameEvent

    object Special : GameEvent

    object Shuffle : GameEvent

    data class StageUp(
        val stage: Int,
    ) : GameEvent
}

/** How long each phase animates, in milliseconds. */
fun durationOf(kind: PhaseKind) =
    when (kind) {
        PhaseKind.SWAP, PhaseKind.SWAP_BACK -> 140
        PhaseKind.CLEAR -> 220
        PhaseKind.FALL -> 260
        PhaseKind.OBSTACLE -> 300
        PhaseKind.SHUFFLE -> 450
    }

/** The banner a phase shows; null keeps the current one (swaps and falls). */
fun bannerFor(phase: Phase): String? =
    when {
        phase.kind == PhaseKind.SHUFFLE -> "No moves — shuffling…"
        phase.kind != PhaseKind.CLEAR -> null
        phase.cascade >= 2 -> "Cascade x${phase.cascade}!  +${phase.scoreDelta}"
        Pattern.LINE5 in phase.patterns -> "Line of 5!  +${phase.scoreDelta}"
        Pattern.LT in phase.patterns -> "L / T shape!  +${phase.scoreDelta}"
        Pattern.LINE4 in phase.patterns -> "Line of 4!  +${phase.scoreDelta}"
        else -> "+${phase.scoreDelta}"
    }

/** Sound and haptic events for a phase. */
fun effectsOf(phase: Phase): List<GameEvent> =
    when (phase.kind) {
        PhaseKind.CLEAR -> {
            listOfNotNull(
                GameEvent.Match(phase.cascade),
                GameEvent.Special.takeIf { phase.specialsFired > 0 || phase.specialsCreated.isNotEmpty() },
            )
        }

        PhaseKind.SHUFFLE -> {
            listOf(GameEvent.Shuffle)
        }

        else -> {
            emptyList()
        }
    }

/** A screen's sound player and vibrator, following the Sound and Vibration settings. */
class Fx(
    val sound: SoundPlayer,
    val haptics: Haptics,
) {
    fun play(event: GameEvent) {
        when (event) {
            GameEvent.Swap -> {
                sound.play(Sfx.SWAP)
                haptics.tick()
            }

            GameEvent.Invalid -> {
                sound.play(Sfx.INVALID)
                haptics.tick()
            }

            is GameEvent.Match -> {
                sound.match(event.cascade)
                haptics.match()
            }

            GameEvent.Special -> {
                sound.play(Sfx.SPECIAL)
                haptics.heavy()
            }

            GameEvent.Shuffle -> {
                sound.play(Sfx.SHUFFLE)
            }

            is GameEvent.StageUp -> {
                sound.play(Sfx.STAGE_UP)
                haptics.tick()
            }
        }
    }
}

@Composable
fun rememberFx(progressRepo: ProgressRepository): Fx {
    val context = LocalContext.current
    val progress by progressRepo.progress.collectAsState(initial = Progress())
    val fx = remember { Fx(SoundPlayer(), Haptics(context)) }
    fx.sound.muted = progress.muted
    fx.haptics.enabled = progress.haptics
    DisposableEffect(Unit) { onDispose { fx.sound.release() } }
    return fx
}

/** False when the system's animations are turned off (accessibility, and how the UI tests run). */
fun animationsEnabled(context: Context) =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f

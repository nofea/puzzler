package com.sheen.puzzler.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.sheen.puzzler.data.Progress
import com.sheen.puzzler.data.ProgressRepository
import com.sheen.puzzler.fx.Haptics
import com.sheen.puzzler.fx.Sfx
import com.sheen.puzzler.fx.SoundPlayer
import com.sheen.puzzler.ui.tutorial.BOARD_TARGET
import com.sheen.puzzler.ui.tutorial.HudTarget
import com.sheen.puzzler.ui.tutorial.TutorialOverlay
import com.sheen.puzzler.ui.tutorial.key
import com.sheen.puzzler.ui.tutorial.tutorialTarget

@Composable
fun GameScreen(
    vm: GameViewModel,
    progressRepo: ProgressRepository,
    hasNext: Boolean,
    onExit: () -> Unit,
    onNext: () -> Unit,
    onRules: () -> Unit,
) {
    val state by vm.state.collectAsState()
    val progress by progressRepo.progress.collectAsState(initial = Progress())
    val context = LocalContext.current
    val sound = remember { SoundPlayer() }
    val haptics = remember { Haptics(context) }
    sound.muted = progress.muted
    haptics.enabled = progress.haptics

    DisposableEffect(Unit) { onDispose { sound.release() } }

    // Pause the timer while the app is in the background.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_PAUSE -> vm.setPaused(true)
                    Lifecycle.Event.ON_RESUME -> vm.setPaused(false)
                    else -> Unit
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(vm) {
        vm.events.collect { event ->
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

                GameEvent.Win -> {
                    sound.play(Sfx.WIN)
                    haptics.win()
                }

                GameEvent.Fail -> {
                    sound.play(Sfx.FAIL)
                    haptics.heavy()
                }
            }
        }
    }

    BackHandler(onBack = onExit)

    // Bounds of the HUD stats, Finish button and board, for the tutorial spotlight.
    val targets = remember { mutableStateMapOf<String, Rect>() }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Hud(state, targets, onRules)
            Spacer(Modifier.height(8.dp))
            Box(Modifier.height(32.dp), contentAlignment = Alignment.Center) {
                state.banner?.let {
                    Text(
                        it,
                        color = PuzzlerColors.gold,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.testTag("banner"),
                    )
                }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                    .padding(6.dp)
                    .testTag("board_container")
                    .semantics { stateDescription = if (state.busy) "busy" else "ready" },
            ) {
                BoardCanvas(
                    previous = state.previous,
                    current = state.snapshot,
                    phaseKind = state.phaseKind,
                    phaseId = state.phaseId,
                    phaseMillis = state.phaseMillis,
                    selected = state.selected,
                    onTap = vm::onTap,
                    onSwipe = vm::onSwipe,
                    modifier = Modifier.fillMaxWidth().tutorialTarget(BOARD_TARGET, targets),
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                OutlinedButton(onClick = onExit) { Text("Quit") }
                Button(
                    onClick = vm::finish,
                    enabled = state.passed && !state.busy && state.status == GameStatus.PLAYING,
                    modifier = Modifier.testTag("finish_button").tutorialTarget(HudTarget.FINISH.key(), targets),
                ) { Text("Finish ★${state.stars}") }
            }
        }

        state.tutorial?.takeIf { it.visible && state.status == GameStatus.PLAYING }?.let { tutorial ->
            TutorialOverlay(
                tutorial = tutorial,
                targets = targets,
                cols = state.snapshot.cols,
                onNext = vm::nextTutorialStep,
                onSkip = vm::skipTutorial,
                onSwipe = vm::onSwipe,
                onFinish = vm::finish,
            )
        }
    }

    if (state.status != GameStatus.PLAYING) {
        ResultDialog(
            state = state,
            best = maxOf(progress.of(state.level.id).bestScore, if (state.status == GameStatus.WON) state.score else 0),
            hasNext = hasNext,
            onRetry = vm::restart,
            onNext = onNext,
            onExit = onExit,
        )
    }
}

@Composable
private fun Hud(
    state: GameUiState,
    targets: MutableMap<String, Rect>,
    onRules: () -> Unit,
) {
    val level = state.level
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Level ${level.id}", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(8.dp))
        // How to play; leaving the screen pauses the timer like any other pause.
        Box(
            Modifier
                .size(30.dp)
                .border(1.5.dp, Color.White.copy(alpha = 0.6f), CircleShape)
                .clip(CircleShape)
                .clickable(onClick = onRules)
                .testTag("rules_button")
                .semantics { contentDescription = "How to play" },
            contentAlignment = Alignment.Center,
        ) { Text("?", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.weight(1f))
        Stat("Moves", "${state.movesLeft}", "moves", state.movesLeft <= 5, Modifier.tutorialTarget(HudTarget.MOVES.key(), targets))
        state.timeLeft?.let { left ->
            Spacer(Modifier.padding(horizontal = 8.dp))
            Stat("Time", "%d:%02d".format(left / 60, left % 60), "time", left <= 15, Modifier.tutorialTarget(HudTarget.TIME.key(), targets))
        }
    }
    Spacer(Modifier.height(8.dp))
    Column(Modifier.fillMaxWidth().tutorialTarget(HudTarget.SCORE.key(), targets)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Score ", fontSize = 16.sp)
            Text("${state.score}", fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("score"))
            Text(" / ${level.target}", fontSize = 16.sp, color = Color.White.copy(alpha = 0.7f))
            Spacer(Modifier.weight(1f))
            Text("★".repeat(state.stars) + "☆".repeat(3 - state.stars), color = PuzzlerColors.gold, fontSize = 22.sp)
        }
        // Bar runs to the 3-star score; the pass mark is shown in the label.
        LinearProgressIndicator(
            progress = { (state.score.toFloat() / level.star3).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(10.dp).padding(top = 4.dp),
            color = if (state.passed) PuzzlerColors.gold else MaterialTheme.colorScheme.primary,
            trackColor = Color.White.copy(alpha = 0.15f),
        )
        Text(
            "Pass ${level.target}  ·  ★★ ${level.star2}  ·  ★★★ ${level.star3}",
            fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.6f),
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        )
    }
}

@Composable
private fun Stat(
    label: String,
    value: String,
    tag: String,
    warn: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
        Text(
            value,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = if (warn) Color(0xFFFF6B6B) else Color.White,
            modifier = Modifier.testTag(tag),
        )
    }
}

@Composable
private fun ResultDialog(
    state: GameUiState,
    best: Int,
    hasNext: Boolean,
    onRetry: () -> Unit,
    onNext: () -> Unit,
    onExit: () -> Unit,
) {
    val won = state.status == GameStatus.WON
    val title =
        when {
            won -> "Level ${state.level.id} cleared!"
            state.endReason == EndReason.OUT_OF_TIME -> "Out of time"
            else -> "Out of moves"
        }
    AlertDialog(
        onDismissRequest = {},
        modifier = Modifier.testTag("result_dialog"),
        title = { Text(title) },
        text = {
            Column {
                if (won) Text("★".repeat(state.stars) + "☆".repeat(3 - state.stars), color = PuzzlerColors.gold, fontSize = 32.sp)
                Text("Score: ${state.score}")
                Text("Best: $best")
                if (!won) Text("You need ${state.level.target} points to pass.")
            }
        },
        confirmButton = {
            if (won && hasNext) {
                TextButton(onClick = onNext, modifier = Modifier.testTag("next_button")) { Text("Next level") }
            } else {
                TextButton(onClick = onRetry, modifier = Modifier.testTag("retry_button")) { Text(if (won) "Play again" else "Retry") }
            }
        },
        dismissButton = {
            Row {
                if (won && hasNext) TextButton(onClick = onRetry) { Text("Replay") }
                TextButton(onClick = onExit) { Text("Map") }
            }
        },
    )
}

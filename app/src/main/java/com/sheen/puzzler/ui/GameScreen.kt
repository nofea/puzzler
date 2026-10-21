package com.sheen.puzzler.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
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
import com.sheen.puzzler.engine.Goals
import com.sheen.puzzler.ui.tutorial.BOARD_TARGET
import com.sheen.puzzler.ui.tutorial.HudTarget
import com.sheen.puzzler.ui.tutorial.TutorialOverlay
import com.sheen.puzzler.ui.tutorial.key
import com.sheen.puzzler.ui.tutorial.tutorialTarget

/**
 * A campaign level (docs/SPEC.md §5). [onFinale] is called instead of the dialog's buttons when this win should play
 * the All levels complete finale.
 */
@Composable
fun GameScreen(
    vm: GameViewModel,
    progressRepo: ProgressRepository,
    hasNext: Boolean,
    isLastLevel: Boolean,
    onExit: () -> Unit,
    onNext: () -> Unit,
    onRules: () -> Unit,
    onFinale: () -> Unit,
) {
    val state by vm.state.collectAsState()
    val progress by progressRepo.progress.collectAsState(initial = Progress())
    val fx = rememberFx(progressRepo)

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

    LaunchedEffect(vm) { vm.events.collect(fx::play) }

    BackHandler(onBack = onExit)

    // Bounds of the HUD stats, Finish button and board, for the tutorial spotlight.
    val targets = remember { mutableStateMapOf<String, Rect>() }

    // The win or loss splash; keyed on the attempt, so a retry gets a fresh clock.
    val won = state.status == GameStatus.WON
    val clock =
        if (state.splash) {
            key(state.status, state.level.id) {
                rememberSplashClock(
                    length = if (won) ClearedSplash.LENGTH else GameOverSplash.LENGTH,
                    cues = if (won) ClearedSplash.cues(state.stars, fx) else GameOverSplash.cues(fx),
                    onDone = vm::splashDone,
                )
            }
        } else {
            null
        }
    val t = clock?.millis
    val hop = if (won && t != null) (t / ClearedSplash.HOP).coerceIn(0f, 1f) else 0f
    val collapse =
        when {
            state.status != GameStatus.FAILED -> 0f
            t != null -> GameOverSplash.collapse(t)
            else -> 1f
        }
    val shake = if (!won && t != null) GameOverSplash.shake(t) else 0f

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .graphicsLayer { translationX = shake.dp.toPx() }
                .padding(horizontal = 12.dp, vertical = 16.dp),
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
                    hop = hop,
                    collapse = collapse,
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

        if (clock != null) {
            if (won) {
                LevelClearedSplash(clock, state.stars)
            } else {
                GameOverSplashOverlay(clock, if (state.endReason == EndReason.OUT_OF_TIME) "Out of time" else "Out of moves")
            }
        }
    }

    if (state.status != GameStatus.PLAYING && !state.splash) {
        // The first time the last level is won, leaving the dialog plays the finale.
        val finale = won && isLastLevel && !progress.finaleSeen
        val record = progress.of(state.level.id)
        ResultDialog(
            state = state,
            bestScore = maxOf(record.bestScore, if (won) state.score else 0),
            fewestMoves = if (won) record.fewestMoves.takeIf { it > 0 }?.let { minOf(it, state.movesMade) } ?: state.movesMade else 0,
            hasNext = hasNext,
            onRetry = if (finale) onFinale else vm::restart,
            onNext = onNext,
            onExit = if (finale) onFinale else onExit,
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
        RulesButton(onRules)
        Spacer(Modifier.weight(1f))
        // Moves count up on levels without a limit, and down on levels with one.
        if (level.limited) {
            Stat("Moves", "${state.movesLeft}", "moves", state.movesLeft <= 5, Modifier.tutorialTarget(HudTarget.MOVES.key(), targets))
        } else {
            Stat("Moves", "${state.movesMade}", "moves", false, Modifier.tutorialTarget(HudTarget.MOVES.key(), targets))
        }
        state.timeLeft?.let { left ->
            Spacer(Modifier.padding(horizontal = 8.dp))
            Stat("Time", "%d:%02d".format(left / 60, left % 60), "time", left <= 15, Modifier.tutorialTarget(HudTarget.TIME.key(), targets))
        }
    }
    if (state.startGoals.total > 0) {
        GoalCounters(state.startGoals, state.goals, Modifier.padding(top = 6.dp).tutorialTarget(HudTarget.GOALS.key(), targets))
    }
    Spacer(Modifier.height(8.dp))
    Column(Modifier.fillMaxWidth().tutorialTarget(HudTarget.SCORE.key(), targets)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Score ", fontSize = 16.sp)
            Text("${state.score}", fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("score"))
            Text(" / ${level.target}", fontSize = 16.sp, color = Color.White.copy(alpha = 0.7f))
            Spacer(Modifier.weight(1f))
            if (!level.limited) {
                nextPar(state)?.let {
                    Text(it, fontSize = 13.sp, color = Color.White.copy(alpha = 0.7f), modifier = Modifier.padding(end = 8.dp))
                }
            }
            Text("★".repeat(state.stars) + "☆".repeat(3 - state.stars), color = PuzzlerColors.gold, fontSize = 22.sp)
        }
        LinearProgressIndicator(
            progress = { barProgress(state) },
            modifier = Modifier.fillMaxWidth().height(10.dp).padding(top = 4.dp),
            color = if (state.passed) PuzzlerColors.gold else MaterialTheme.colorScheme.primary,
            trackColor = Color.White.copy(alpha = 0.15f),
        )
        val pass = if (state.startGoals.total > 0) "Pass ${level.target} + goals" else "Pass ${level.target}"
        val thresholds =
            if (level.limited) "★★ ${level.star2}  ·  ★★★ ${level.star3}" else "★★ ≤ ${level.par2} moves  ·  ★★★ ≤ ${level.par3} moves"
        Text(
            "$pass  ·  $thresholds",
            fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.6f),
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        )
    }
}

/** On a level without a limit: the move par for the next star the player would lose, e.g. "★★★ ≤ 15". */
private fun nextPar(state: GameUiState): String? {
    val level = state.level
    return when {
        state.movesMade <= level.par3 -> "★★★ ≤ ${level.par3}"
        state.movesMade <= level.par2 -> "★★ ≤ ${level.par2}"
        else -> null
    }
}

/** Without a limit the bar shows progress towards passing (goals and 100 points); with one, it runs to the 3★ score. */
private fun barProgress(state: GameUiState): Float {
    val level = state.level
    val points = (state.score.toFloat() / level.target).coerceIn(0f, 1f)
    val total = state.startGoals.total
    return when {
        level.limited -> (state.score.toFloat() / level.star3).coerceIn(0f, 1f)
        total == 0 -> points
        else -> (points + (1f - (state.goals.total.toFloat() / total)).coerceIn(0f, 1f)) / 2f
    }
}

@Composable
fun RulesButton(onRules: () -> Unit) {
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
}

@Composable
fun Stat(
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

private enum class GoalKind(
    val tag: String,
    val label: String,
) {
    JELLY("goal_jelly", "Jelly"),
    LOCK("goal_lock", "Locked items"),
    STONE("goal_stone", "Stones"),
}

/** One counter per obstacle type the level has: its icon and how many are left, or a check mark once none are. */
@Composable
private fun GoalCounters(
    start: Goals,
    now: Goals,
    modifier: Modifier = Modifier,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Goals", fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
        listOf(
            Triple(GoalKind.JELLY, start.jelly, now.jelly),
            Triple(GoalKind.LOCK, start.locked, now.locked),
            Triple(GoalKind.STONE, start.stones, now.stones),
        ).filter { it.second > 0 }.forEach { (kind, _, left) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.semantics { contentDescription = "${kind.label}: $left left" },
            ) {
                GoalIcon(kind)
                Text(
                    if (left == 0) "✓" else "$left",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (left == 0) PuzzlerColors.gold else Color.White,
                    modifier = Modifier.padding(start = 4.dp).testTag(kind.tag),
                )
            }
        }
    }
}

/** The obstacle as it looks on the board, small. */
@Composable
private fun GoalIcon(kind: GoalKind) {
    Canvas(Modifier.size(20.dp)) {
        val r = CornerRadius(size.width * 0.22f)
        when (kind) {
            GoalKind.JELLY -> {
                drawRoundRect(PuzzlerColors.jelly.copy(alpha = 1f), cornerRadius = r)
            }

            GoalKind.STONE -> {
                drawRoundRect(PuzzlerColors.stone, cornerRadius = r)
            }

            GoalKind.LOCK -> {
                val w = size.width * 0.1f
                drawRoundRect(PuzzlerColors.lock, cornerRadius = r, style = Stroke(w))
                drawLine(PuzzlerColors.lock, Offset(w, w), Offset(size.width - w, size.height - w), w, StrokeCap.Round)
                drawLine(PuzzlerColors.lock, Offset(size.width - w, w), Offset(w, size.height - w), w, StrokeCap.Round)
            }
        }
        if (kind == GoalKind.STONE) {
            drawRoundRect(
                Color.White.copy(alpha = 0.2f),
                topLeft = Offset(size.width * 0.25f, size.height * 0.25f),
                size = Size(size.width * 0.5f, size.height * 0.5f),
                cornerRadius = r,
            )
        }
    }
}

/** What's still missing after a loss, e.g. "2 jelly, 1 lock left". */
private fun missingGoals(goals: Goals): String? {
    val parts =
        listOfNotNull(
            goals.jelly.takeIf { it > 0 }?.let { "$it jelly" },
            goals.locked.takeIf { it > 0 }?.let { "$it ${if (it == 1) "lock" else "locks"}" },
            goals.stones.takeIf { it > 0 }?.let { "$it ${if (it == 1) "stone" else "stones"}" },
        )
    return if (parts.isEmpty()) null else parts.joinToString(", ") + " left"
}

@Composable
private fun ResultDialog(
    state: GameUiState,
    bestScore: Int,
    fewestMoves: Int,
    hasNext: Boolean,
    onRetry: () -> Unit,
    onNext: () -> Unit,
    onExit: () -> Unit,
) {
    val won = state.status == GameStatus.WON
    val level = state.level
    val title =
        when {
            won -> "Level ${level.id} cleared!"
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
                if (won && !level.limited) {
                    Text("Moves: ${state.movesMade}")
                    Text("Fewest: $fewestMoves")
                } else {
                    Text("Score: ${state.score}")
                    if (won) Text("Best: $bestScore")
                }
                if (!won) {
                    missingGoals(state.goals)?.let { Text(it, modifier = Modifier.testTag("goals_left")) }
                    if (state.score < level.target) Text("You need ${level.target} points to pass.")
                }
            }
        },
        confirmButton = {
            if (won && hasNext) {
                NextButton(pulsing = state.autoFinished, onClick = onNext)
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

/** Next level; after a 3★ auto-finish it's a big button that pulses gently, to push on (docs/SPEC.md §5). */
@Composable
private fun NextButton(
    pulsing: Boolean,
    onClick: () -> Unit,
) {
    if (!pulsing) {
        TextButton(onClick = onClick, modifier = Modifier.testTag("next_button")) { Text("Next level") }
        return
    }
    val pulse by rememberInfiniteTransition(label = "next").animateFloat(
        1f,
        1.08f,
        infiniteRepeatable(tween(600), RepeatMode.Reverse),
        label = "pulse",
    )
    Button(
        onClick = onClick,
        modifier =
            Modifier
                .testTag("next_button")
                .graphicsLayer {
                    scaleX = pulse
                    scaleY = pulse
                },
    ) { Text("Next level ›", fontSize = 18.sp, fontWeight = FontWeight.Bold) }
}

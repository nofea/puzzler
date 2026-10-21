package com.sheen.puzzler.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sheen.puzzler.data.ProgressRepository

/** Endless mode (docs/SPEC.md §4). */
@Composable
fun EndlessScreen(
    vm: EndlessViewModel,
    progressRepo: ProgressRepository,
    onExit: () -> Unit,
    onRules: () -> Unit,
) {
    val state by vm.state.collectAsState()
    val fx = rememberFx(progressRepo)
    LaunchedEffect(vm) { vm.events.collect(fx::play) }
    BackHandler(onBack = onExit)
    var confirmNewRun by remember { mutableStateOf(false) }

    val clock =
        if (state.splash) {
            key(state.summary) { rememberSplashClock(GameOverSplash.LENGTH, GameOverSplash.cues(fx), vm::splashDone) }
        } else {
            null
        }
    val t = clock?.millis
    val collapse =
        when {
            !state.jammed -> 0f
            t != null -> GameOverSplash.collapse(t)
            else -> 1f
        }
    val shake = t?.let(GameOverSplash::shake) ?: 0f

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .graphicsLayer { translationX = shake.dp.toPx() }
                .padding(horizontal = 12.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            EndlessHud(state, onRules)
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
                    .semantics { stateDescription = if (state.busy || state.loading) "busy" else "ready" },
            ) {
                val snapshot = state.snapshot
                val previous = state.previous
                if (snapshot != null && previous != null) {
                    BoardCanvas(
                        previous = previous,
                        current = snapshot,
                        phaseKind = state.phaseKind,
                        phaseId = state.phaseId,
                        phaseMillis = state.phaseMillis,
                        selected = state.selected,
                        onTap = vm::onTap,
                        onSwipe = vm::onSwipe,
                        modifier = Modifier.fillMaxWidth(),
                        collapse = collapse,
                    )
                }
                StageToast(state.stageToast, state.stageToastId, Modifier.align(Alignment.TopCenter))
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                OutlinedButton(onClick = onExit) { Text("Quit") }
                Button(
                    onClick = { confirmNewRun = true },
                    enabled = !state.loading && !state.busy && !state.jammed,
                    modifier = Modifier.testTag("new_run_button"),
                ) { Text("New run") }
            }
        }
        if (clock != null) GameOverSplashOverlay(clock, "Board jammed")
    }

    if (confirmNewRun) {
        AlertDialog(
            onDismissRequest = { confirmNewRun = false },
            title = { Text("Start a new run?") },
            text = { Text("This run ends here and goes into your recent runs.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmNewRun = false
                    vm.newRun()
                }, modifier = Modifier.testTag("confirm_new_run")) { Text("New run") }
            },
            dismissButton = { TextButton(onClick = { confirmNewRun = false }) { Text("Keep playing") } },
        )
    }

    val summary = state.summary
    if (summary != null && !state.splash) RunSummaryDialog(summary, onNewRun = vm::newRun, onHome = onExit)
}

@Composable
private fun EndlessHud(
    state: EndlessUiState,
    onRules: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Endless", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(8.dp))
        RulesButton(onRules)
        Spacer(Modifier.width(8.dp))
        StageBadge(state.stage)
        Spacer(Modifier.weight(1f))
        Stat("Moves", "${state.movesMade}", "moves", false)
        Spacer(Modifier.width(16.dp))
        Stat("Pts/move", state.pointsPerMove?.let { "%.1f".format(it) } ?: "–", "points_per_move", false)
    }
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Score ", fontSize = 16.sp)
        Text("${state.score}", fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("score"))
    }
}

/** The stage badge; it pulses when the stage goes up. */
@Composable
private fun StageBadge(stage: Int) {
    val pulse = remember { Animatable(1f) }
    LaunchedEffect(stage) {
        if (stage > 1) {
            pulse.snapTo(1.35f)
            pulse.animateTo(1f, tween(500))
        }
    }
    Text(
        "Stage $stage",
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = PuzzlerColors.background,
        modifier =
            Modifier
                .graphicsLayer {
                    scaleX = pulse.value
                    scaleY = pulse.value
                }.background(PuzzlerColors.gold, RoundedCornerShape(10.dp))
                .padding(horizontal = 8.dp, vertical = 2.dp)
                .testTag("stage"),
    )
}

/** "Stage N!" slides in over the top of the board for about a second. It never takes touches. */
@Composable
private fun StageToast(
    stage: Int?,
    id: Int,
    modifier: Modifier,
) {
    if (stage == null) return
    val anim = remember { Animatable(0f) }
    LaunchedEffect(id) {
        anim.snapTo(0f)
        anim.animateTo(1f, tween(STAGE_TOAST_MS))
    }
    val f = anim.value
    if (f >= 1f) return
    // Slides down in the first fifth, holds, then fades in the last fifth.
    val slide = (f / 0.2f).coerceIn(0f, 1f)
    val fade = ((1f - f) / 0.2f).coerceIn(0f, 1f)
    Text(
        "Stage $stage!",
        fontFamily = PuzzlerFonts.display,
        fontSize = 26.sp,
        color = PuzzlerColors.gold,
        modifier =
            modifier
                .padding(top = 8.dp)
                .graphicsLayer {
                    translationY = (slide - 1f) * 40.dp.toPx()
                    alpha = fade * slide
                }.background(PuzzlerColors.surface.copy(alpha = 0.9f), RoundedCornerShape(12.dp))
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .testTag("stage_toast"),
    )
}

private const val STAGE_TOAST_MS = 1000

@Composable
private fun RunSummaryDialog(
    summary: RunSummary,
    onNewRun: () -> Unit,
    onHome: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = {},
        modifier = Modifier.testTag("run_summary"),
        title = { Text("Run over") },
        text = {
            Column {
                SummaryLine("Score", "${summary.score}", summary.newBestScore)
                SummaryLine("Moves", "${summary.moves}", false)
                SummaryLine("Stage reached", "${summary.stage}", false)
                SummaryLine("Points per move", summary.pointsPerMove?.let { "%.1f".format(it) } ?: "–", summary.newBestPointsPerMove)
            }
        },
        confirmButton = { TextButton(onClick = onNewRun, modifier = Modifier.testTag("summary_new_run")) { Text("New run") } },
        dismissButton = { TextButton(onClick = onHome) { Text("Home") } },
    )
}

@Composable
private fun SummaryLine(
    label: String,
    value: String,
    newBest: Boolean,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
        Text("$label: $value")
        if (newBest) {
            Text(
                "New best!",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = PuzzlerColors.background,
                modifier =
                    Modifier
                        .padding(start = 8.dp)
                        .background(PuzzlerColors.gold, RoundedCornerShape(8.dp))
                        .padding(horizontal = 6.dp, vertical = 1.dp),
            )
        }
    }
}

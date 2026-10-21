package com.sheen.puzzler.ui.rules

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sheen.puzzler.engine.BoardSnapshot
import com.sheen.puzzler.engine.PhaseKind
import com.sheen.puzzler.engine.Pos
import com.sheen.puzzler.ui.BoardCanvas
import com.sheen.puzzler.ui.GameViewModel
import com.sheen.puzzler.ui.PuzzlerColors
import com.sheen.puzzler.ui.tutorial.drawSwipeHint
import kotlinx.coroutines.delay

/**
 * Plays [demos] in a loop: shows the swipe hint for each scripted move, then plays the engine's phases through
 * [BoardCanvas] with the same timings as a real level, with a small moves and score readout above the board.
 */
@Composable
fun DemoBoard(demos: List<RuleDemo>, modifier: Modifier = Modifier) {
    val first = remember(demos) { demos.first().newEngine().snapshot() }
    var caption by remember(demos) { mutableStateOf(demos.first().caption) }
    var previous by remember(demos) { mutableStateOf<BoardSnapshot>(first) }
    var current by remember(demos) { mutableStateOf<BoardSnapshot>(first) }
    var phaseKind by remember(demos) { mutableStateOf<PhaseKind?>(null) }
    var phaseId by remember(demos) { mutableIntStateOf(0) }
    var phaseMillis by remember(demos) { mutableIntStateOf(0) }
    var moves by remember(demos) { mutableIntStateOf(RuleDemo.DEMO_MOVES) }
    var score by remember(demos) { mutableIntStateOf(0) }
    var hint by remember(demos) { mutableStateOf<Pair<Pos, Pos>?>(null) }
    val hintTime = remember(demos) { Animatable(0f) }

    LaunchedEffect(demos) {
        while (true) {
            for (demo in demos) {
                val engine = demo.newEngine()
                caption = demo.caption
                previous = engine.snapshot()
                current = previous
                phaseKind = null
                moves = engine.movesLeft
                score = 0
                delay(700)
                for (move in demo.moves) {
                    hint = move.a to move.b
                    hintTime.snapTo(0f)
                    hintTime.animateTo(1f, tween(1400, easing = LinearEasing))
                    hint = null
                    val result = engine.swap(move.a, move.b)
                    for (phase in result.phases) {
                        previous = current
                        current = phase.snapshot
                        phaseKind = phase.kind
                        phaseMillis = GameViewModel.durationOf(phase.kind)
                        phaseId++
                        score += phase.scoreDelta
                        moves = engine.movesLeft
                        delay(phaseMillis.toLong() + 60)
                    }
                    delay(500)
                }
                delay(1300)
            }
        }
    }

    Column(modifier.testTag("rules_demo"), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(caption, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = PuzzlerColors.gold)
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Text("Moves $moves", fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
            Spacer(Modifier.weight(1f))
            Text("Score $score", fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
        }
        Box(
            Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
                .padding(4.dp),
        ) {
            BoardCanvas(
                previous = previous,
                current = current,
                phaseKind = phaseKind,
                phaseId = phaseId,
                phaseMillis = phaseMillis,
                selected = null,
                onTap = {},
                onSwipe = { _, _ -> },
                modifier = Modifier.fillMaxWidth(),
            )
            Canvas(Modifier.matchParentSize()) {
                val (a, b) = hint ?: return@Canvas
                val cell = size.width / current.cols
                fun center(p: Pos) = Offset((p.col + 0.5f) * cell, (p.row + 0.5f) * cell)
                drawSwipeHint(center(a), center(b), hintTime.value, cell)
            }
        }
    }
}

package com.sheen.puzzler.ui.tutorial

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sheen.puzzler.engine.Pos
import com.sheen.puzzler.ui.PuzzlerColors
import com.sheen.puzzler.ui.PuzzlerFonts
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

private fun union(
    a: Rect,
    b: Rect,
) = Rect(minOf(a.left, b.left), minOf(a.top, b.top), maxOf(a.right, b.right), maxOf(a.bottom, b.bottom))

/** Key under which the game board's bounds are registered with [tutorialTarget]. */
const val BOARD_TARGET = "board"

fun HudTarget.key() = name.lowercase()

/** Records this element's bounds (root coordinates) under [key], so the tutorial overlay can spotlight it. */
fun Modifier.tutorialTarget(
    key: String,
    targets: MutableMap<String, Rect>,
) = onGloballyPositioned { coords: LayoutCoordinates -> targets[key] = coords.boundsInRoot() }

/**
 * Dims the game screen except the current step's target, shows the step's callout, and for a guided swap animates a
 * swipe hint between the two cells. The overlay takes all touches: a drag that starts on a highlighted cell is turned
 * into [onSwipe], and a tap on a highlighted Finish button into [onFinish]; everything else is ignored.
 */
@Composable
fun TutorialOverlay(
    tutorial: TutorialState,
    targets: Map<String, Rect>,
    cols: Int,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    onSwipe: (Pos, Pos) -> Unit,
    onFinish: () -> Unit,
) {
    val step = tutorial.step ?: return
    val density = LocalDensity.current
    var origin by remember { mutableStateOf(Offset.Zero) }
    var overlayHeight by remember { mutableIntStateOf(0) }
    var cardHeight by remember { mutableIntStateOf(0) }

    val transition = rememberInfiniteTransition(label = "tutorial")
    val pulse by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "pulse")
    val hint by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = LinearEasing)), label = "hint")

    // Geometry in overlay coordinates.
    val board = targets[BOARD_TARGET]?.translate(-origin)
    val cell = board?.let { it.width / cols } ?: 0f

    fun cellRect(p: Pos) =
        Rect(
            board!!.left + (p.col * cell),
            board.top + (p.row * cell),
            board.left + ((p.col + 1) * cell),
            board.top + ((p.row + 1) * cell),
        )
    val pad = with(density) { 6.dp.toPx() }
    val holes: List<Rect> =
        when (val target = step.target) {
            is Target.Hud -> {
                listOfNotNull(targets[target.key.key()]?.translate(-origin)?.inflate(pad))
            }

            is Target.Cells -> {
                val cells = if (board == null) emptyList() else target.cells.map { cellRect(it).inflate(-cell * 0.03f) }
                cells + listOfNotNull(target.hud?.let { targets[it.key()]?.translate(-origin)?.inflate(pad) })
            }

            Target.None -> {
                emptyList()
            }
        }
    val finishHole = (step.target as? Target.Hud)?.takeIf { it.key == HudTarget.FINISH }?.let { holes.firstOrNull() }
    val guided = step as? TutorialStep.GuidedSwap

    Box(
        Modifier
            .fillMaxSize()
            .testTag("tutorial")
            .onGloballyPositioned {
                origin = it.positionInRoot()
                overlayHeight = it.size.height
            },
    ) {
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(step, board, finishHole) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        val start =
                            board
                                ?.takeIf { guided != null && it.contains(down.position) }
                                ?.let {
                                    Pos(((down.position.y - it.top) / cell).toInt(), ((down.position.x - it.left) / cell).toInt())
                                }?.takeIf { it == guided!!.a || it == guided.b }
                        var swiped = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            change.consume()
                            if (!change.pressed) {
                                if (!swiped && finishHole?.contains(change.position) == true) onFinish()
                                break
                            }
                            val d = change.position - down.position
                            val dragged = abs(d.x) > cell * 0.35f || abs(d.y) > cell * 0.35f
                            if (start != null && !swiped && dragged) {
                                val target =
                                    if (abs(d.x) > abs(d.y)) {
                                        Pos(start.row, start.col + d.x.sign.toInt())
                                    } else {
                                        Pos(start.row + d.y.sign.toInt(), start.col)
                                    }
                                onSwipe(start, target)
                                swiped = true
                            }
                        }
                    }
                },
        ) {
            val dim =
                Path().apply {
                    holes.forEach { addRoundRect(RoundRect(it, CornerRadius(12.dp.toPx()))) }
                }
            clipPath(dim, ClipOp.Difference) {
                drawRect(Color.Black.copy(alpha = 0.6f))
            }
            holes.forEach {
                drawRoundRect(
                    PuzzlerColors.gold.copy(alpha = 0.45f + (0.55f * pulse)),
                    topLeft = it.topLeft,
                    size = it.size,
                    cornerRadius = CornerRadius(12.dp.toPx()),
                    style = Stroke(3.dp.toPx()),
                )
            }
            if (guided != null && board != null) {
                drawSwipeHint(cellRect(guided.a).center, cellRect(guided.b).center, hint, cell)
            }
        }

        // The callout goes beside the spotlight; for board cells, beside the whole board when there's room, so it
        // doesn't hide items.
        val focus = holes.reduceOrNull(::union)
        val avoid = if (step.target is Target.Cells && board != null && focus != null) union(board, focus) else focus
        val gap = with(density) { 16.dp.toPx() }
        val cardTop =
            when {
                avoid == null || focus == null -> (overlayHeight - cardHeight) / 2f
                overlayHeight - avoid.bottom >= cardHeight + (2 * gap) -> avoid.bottom + gap
                avoid.top >= cardHeight + (2 * gap) -> avoid.top - gap - cardHeight
                focus.center.y > overlayHeight / 2f -> focus.top - gap - cardHeight
                else -> focus.bottom + gap
            }.coerceIn(0f, (overlayHeight - cardHeight).coerceAtLeast(0).toFloat())
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = PuzzlerColors.surface,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .offset { IntOffset(0, cardTop.roundToInt()) }
                    .onSizeChanged { cardHeight = it.height }
                    .testTag("tutorial_card"),
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(step.title, fontFamily = PuzzlerFonts.display, fontSize = 20.sp, color = PuzzlerColors.gold)
                    Spacer(Modifier.weight(1f))
                    Text(tutorial.counter, fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
                }
                Spacer(Modifier.padding(top = 6.dp))
                Text(step.text, fontSize = 15.sp, lineHeight = 21.sp)
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (step !is TutorialStep.WhenPassed) {
                        TextButton(onClick = onSkip, modifier = Modifier.testTag("tutorial_skip")) {
                            Text("Skip tutorial", color = Color.White.copy(alpha = 0.7f))
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    if (guided == null) {
                        // "Got it" when nothing follows right away: the end, or only a tip that waits for 100 points.
                        val following = tutorial.steps.getOrNull(tutorial.index + 1)
                        val last = step is TutorialStep.WhenPassed || following == null || following is TutorialStep.WhenPassed
                        Button(onClick = onNext, modifier = Modifier.testTag("tutorial_next")) {
                            Text(if (last) "Got it" else "Next", fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Text("Swipe the highlighted items", fontSize = 12.sp, color = PuzzlerColors.gold, modifier = Modifier.width(160.dp))
                    }
                }
            }
        }
    }
}

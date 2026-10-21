package com.sheen.puzzler.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import com.sheen.puzzler.engine.BoardSnapshot
import com.sheen.puzzler.engine.Piece
import com.sheen.puzzler.engine.PhaseKind
import com.sheen.puzzler.engine.Pos
import com.sheen.puzzler.engine.Special
import kotlin.math.abs
import kotlin.math.sign

/**
 * Draws the board and animates each engine phase: pieces are matched between [previous] and [current] by id,
 * slide from old to new position, cleared pieces shrink away, and new pieces drop in from above.
 */
@Composable
fun BoardCanvas(
    previous: BoardSnapshot,
    current: BoardSnapshot,
    phaseKind: PhaseKind?,
    phaseId: Int,
    phaseMillis: Int,
    selected: Pos?,
    onTap: (Pos) -> Unit,
    onSwipe: (Pos, Pos) -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress = remember { Animatable(1f) }
    LaunchedEffect(phaseId) {
        if (phaseId == 0) return@LaunchedEffect
        progress.snapTo(0f)
        progress.animateTo(1f, tween(phaseMillis, easing = FastOutSlowInEasing))
    }
    val tap by rememberUpdatedState(onTap)
    val swipe by rememberUpdatedState(onSwipe)
    val rows = current.rows
    val cols = current.cols

    Canvas(
        modifier
            .aspectRatio(cols.toFloat() / rows)
            .clipToBounds()
            .testTag("board")
            .pointerInput(rows, cols) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val cellPx = size.width / cols.toFloat()
                    val start = Pos((down.position.y / cellPx).toInt(), (down.position.x / cellPx).toInt())
                    var swiped = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        val d = change.position - down.position
                        if (!swiped && (abs(d.x) > cellPx * 0.35f || abs(d.y) > cellPx * 0.35f)) {
                            val target = if (abs(d.x) > abs(d.y)) {
                                Pos(start.row, start.col + d.x.sign.toInt())
                            } else {
                                Pos(start.row + d.y.sign.toInt(), start.col)
                            }
                            swipe(start, target)
                            swiped = true
                        }
                        change.consume()
                    }
                    if (!swiped && start.row in 0 until rows && start.col in 0 until cols) tap(start)
                }
            },
    ) {
        val t = progress.value
        val cell = size.width / cols
        drawTiles(current, previous, t, cell)
        drawPieces(previous, current, phaseKind, t, cell)
        drawOverlays(previous, current, t, cell)
        if (selected != null) {
            drawRoundRect(
                Color.White,
                topLeft = Offset(selected.col * cell + 2, selected.row * cell + 2),
                size = Size(cell - 4, cell - 4),
                cornerRadius = CornerRadius(cell * 0.18f),
                style = Stroke(width = cell * 0.06f),
            )
        }
    }
}

private fun DrawScope.drawTiles(current: BoardSnapshot, previous: BoardSnapshot, t: Float, cell: Float) {
    for (pos in current.positions()) {
        val c = current[pos]
        if (!c.exists) continue
        val topLeft = Offset(pos.col * cell, pos.row * cell)
        val tile = if ((pos.row + pos.col) % 2 == 0) PuzzlerColors.tileLight else PuzzlerColors.tileDark
        drawRect(tile, topLeft, Size(cell, cell))
        val hadJelly = previous.rows == current.rows && previous[pos].jelly
        val jellyAlpha = when {
            c.jelly -> 1f
            hadJelly -> 1f - t
            else -> 0f
        }
        if (jellyAlpha > 0f) {
            drawRoundRect(
                PuzzlerColors.jelly.copy(alpha = PuzzlerColors.jelly.alpha * jellyAlpha),
                topLeft = topLeft + Offset(cell * 0.06f, cell * 0.06f),
                size = Size(cell * 0.88f, cell * 0.88f),
                cornerRadius = CornerRadius(cell * 0.22f),
            )
        }
    }
}

private fun DrawScope.drawPieces(previous: BoardSnapshot, current: BoardSnapshot, kind: PhaseKind?, t: Float, cell: Float) {
    val prevPos = HashMap<Long, Pos>()
    val prevPiece = HashMap<Long, Piece>()
    if (previous.rows == current.rows && previous.cols == current.cols) {
        for (pos in previous.positions()) previous[pos].piece?.let { prevPos[it.id] = pos; prevPiece[it.id] = it }
    }
    // Count new pieces per column so a refill drops in as a stack from above the board.
    val newInColumn = IntArray(current.cols)
    val currentIds = HashSet<Long>()
    for (pos in current.positions()) {
        val piece = current[pos].piece ?: continue
        currentIds += piece.id
        if (piece.id !in prevPos) newInColumn[pos.col]++
    }

    for (pos in current.positions()) {
        val piece = current[pos].piece ?: continue
        val from = prevPos[piece.id]
        var scale = 1f
        val (row, col) = when {
            from != null -> lerp(from.row.toFloat(), pos.row.toFloat(), t) to lerp(from.col.toFloat(), pos.col.toFloat(), t)
            kind == PhaseKind.FALL -> lerp((pos.row - newInColumn[pos.col]).toFloat(), pos.row.toFloat(), t) to pos.col.toFloat()
            else -> {
                scale = 0.4f + 0.6f * t // a new special pops in where it was made
                pos.row.toFloat() to pos.col.toFloat()
            }
        }
        if (row < -0.5f) continue // still above the board
        drawPiece(piece, Offset((col + 0.5f) * cell, (row + 0.5f) * cell), cell * 0.40f * scale, 1f)
    }

    // Pieces cleared in this phase shrink and fade out.
    if (t < 1f) {
        for ((id, pos) in prevPos) {
            if (id in currentIds) continue
            val shrink = 1f - t
            drawPiece(prevPiece.getValue(id), Offset((pos.col + 0.5f) * cell, (pos.row + 0.5f) * cell), cell * 0.40f * (0.6f + 0.6f * shrink), shrink)
        }
    }
}

private fun DrawScope.drawOverlays(previous: BoardSnapshot, current: BoardSnapshot, t: Float, cell: Float) {
    val samePrev = previous.rows == current.rows && previous.cols == current.cols
    for (pos in current.positions()) {
        val c = current[pos]
        val topLeft = Offset(pos.col * cell, pos.row * cell)
        val hp = when {
            c.isStone -> c.stoneHp
            samePrev && previous[pos].isStone -> previous[pos].stoneHp
            else -> 0
        }
        if (hp > 0) {
            val alpha = if (c.isStone) 1f else 1f - t
            drawStone(topLeft, cell, hp, alpha)
        }
        val wasLocked = samePrev && previous[pos].locked
        if (c.locked || wasLocked) drawLock(topLeft, cell, if (c.locked) 1f else 1f - t)
    }
}

private fun DrawScope.drawStone(topLeft: Offset, cell: Float, hp: Int, alpha: Float) {
    val inset = cell * 0.05f
    val base = if (hp >= 2) PuzzlerColors.stoneHard else PuzzlerColors.stone
    drawRoundRect(
        base.copy(alpha = alpha),
        topLeft = topLeft + Offset(inset, inset),
        size = Size(cell - 2 * inset, cell - 2 * inset),
        cornerRadius = CornerRadius(cell * 0.12f),
    )
    drawRoundRect(
        Color.White.copy(alpha = 0.18f * alpha),
        topLeft = topLeft + Offset(cell * 0.2f, cell * 0.2f),
        size = Size(cell * 0.6f, cell * 0.6f),
        cornerRadius = CornerRadius(cell * 0.08f),
    )
    if (hp == 1) {
        // A crack shows the stone is one hit from breaking.
        val crack = Path().apply {
            moveTo(topLeft.x + cell * 0.3f, topLeft.y + cell * 0.15f)
            lineTo(topLeft.x + cell * 0.5f, topLeft.y + cell * 0.45f)
            lineTo(topLeft.x + cell * 0.4f, topLeft.y + cell * 0.6f)
            lineTo(topLeft.x + cell * 0.65f, topLeft.y + cell * 0.85f)
        }
        drawPath(crack, Color.Black.copy(alpha = 0.5f * alpha), style = Stroke(cell * 0.05f, cap = StrokeCap.Round))
    }
}

private fun DrawScope.drawLock(topLeft: Offset, cell: Float, alpha: Float) {
    val color = PuzzlerColors.lock.copy(alpha = 0.85f * alpha)
    val w = cell * 0.07f
    val a = cell * 0.12f
    val b = cell * 0.88f
    drawLine(color, topLeft + Offset(a, a), topLeft + Offset(b, b), w, StrokeCap.Round)
    drawLine(color, topLeft + Offset(b, a), topLeft + Offset(a, b), w, StrokeCap.Round)
    drawRoundRect(
        color,
        topLeft = topLeft + Offset(cell * 0.06f, cell * 0.06f),
        size = Size(cell * 0.88f, cell * 0.88f),
        cornerRadius = CornerRadius(cell * 0.16f),
        style = Stroke(w),
    )
}

private fun DrawScope.drawPiece(piece: Piece, center: Offset, radius: Float, alpha: Float) {
    if (piece.special == Special.COLOR_BOMB) {
        // Same design and proportions as the launcher icon (docs/SPEC.md §5).
        drawColorBomb(center, radius, alpha)
        return
    }

    val color = PuzzlerColors.pieces[piece.color % PuzzlerColors.pieces.size]
    val path = shapePath(piece.color, center, radius)
    val brush = Brush.radialGradient(
        listOf(lighten(color).copy(alpha = alpha), color.copy(alpha = alpha), darken(color).copy(alpha = alpha)),
        center = center - Offset(radius * 0.3f, radius * 0.3f),
        radius = radius * 1.6f,
    )
    drawPath(path, brush)
    drawPath(path, darken(color).copy(alpha = 0.6f * alpha), style = Stroke(radius * 0.08f))
    // Glossy highlight.
    drawCircle(Color.White.copy(alpha = 0.45f * alpha), radius * 0.16f, center - Offset(radius * 0.35f, radius * 0.35f))

    when (piece.special) {
        Special.STRIPED_H, Special.STRIPED_V -> clipPath(path) {
            val white = Color.White.copy(alpha = 0.75f * alpha)
            for (k in -1..1) {
                val o = k * radius * 0.45f
                if (piece.special == Special.STRIPED_H) {
                    drawLine(white, Offset(center.x - radius, center.y + o), Offset(center.x + radius, center.y + o), radius * 0.16f)
                } else {
                    drawLine(white, Offset(center.x + o, center.y - radius), Offset(center.x + o, center.y + radius), radius * 0.16f)
                }
            }
        }
        Special.BOMB -> {
            drawCircle(Color.White.copy(alpha = 0.9f * alpha), radius * 1.08f, center, style = Stroke(radius * 0.14f))
            drawCircle(Color.White.copy(alpha = 0.9f * alpha), radius * 0.22f, center)
        }
        else -> Unit
    }
}

private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
private fun lighten(c: Color) = Color(c.red + (1 - c.red) * 0.45f, c.green + (1 - c.green) * 0.45f, c.blue + (1 - c.blue) * 0.45f, c.alpha)
private fun darken(c: Color) = Color(c.red * 0.6f, c.green * 0.6f, c.blue * 0.6f, c.alpha)

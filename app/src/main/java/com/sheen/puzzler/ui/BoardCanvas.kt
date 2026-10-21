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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import com.sheen.puzzler.engine.BoardSnapshot
import com.sheen.puzzler.engine.PhaseKind
import com.sheen.puzzler.engine.Piece
import com.sheen.puzzler.engine.Pos
import com.sheen.puzzler.engine.Special
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sign
import kotlin.math.sin

/**
 * Draws the board and animates each engine phase: pieces are matched between [previous] and [current] by id,
 * slide from old to new position, cleared pieces shrink away, and new pieces drop in from above.
 *
 * [hop] (0..1) plays the level-cleared hop, and [collapse] (0..1) the Game Over collapse where the items fall off the
 * board (docs/SPEC.md §5); both sweep across the columns.
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
    hop: Float = 0f,
    collapse: Float = 0f,
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
    val images = rememberItemImages()

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
                            val target =
                                if (abs(d.x) > abs(d.y)) {
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
        drawPieces(images, swept(spritesAt(previous, current, phaseKind, t), rows, cols, hop, collapse), cell)
        drawOverlays(previous, current, t, cell, 1f - collapse)
        if (selected != null) {
            drawRoundRect(
                Color.White,
                topLeft = Offset((selected.col * cell) + 2, (selected.row * cell) + 2),
                size = Size(cell - 4, cell - 4),
                cornerRadius = CornerRadius(cell * 0.18f),
                style = Stroke(width = cell * 0.06f),
            )
        }
    }
}

private fun DrawScope.drawTiles(
    current: BoardSnapshot,
    previous: BoardSnapshot,
    t: Float,
    cell: Float,
) {
    for (pos in current.positions()) {
        val c = current[pos]
        if (!c.exists) continue
        val topLeft = Offset(pos.col * cell, pos.row * cell)
        val tile = if ((pos.row + pos.col) % 2 == 0) PuzzlerColors.tileLight else PuzzlerColors.tileDark
        drawRect(tile, topLeft, Size(cell, cell))
        val hadJelly = previous.rows == current.rows && previous[pos].jelly
        val jellyAlpha =
            when {
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

/** A piece placed for one animation frame, in cell units. [scale] multiplies the piece size. */
private class Sprite(
    val piece: Piece,
    val row: Float,
    val col: Float,
    val scale: Float = 1f,
    val alpha: Float = 1f,
    /** Rotation in degrees. */
    val spin: Float = 0f,
)

/**
 * Where every piece is at time [t] of a phase going from [previous] to [current]: pieces matched by id slide between
 * cells, new pieces drop in from above the board (or pop in, for a special made in place), and cleared pieces shrink
 * and fade where they were.
 */
private fun spritesAt(
    previous: BoardSnapshot,
    current: BoardSnapshot,
    kind: PhaseKind?,
    t: Float,
): List<Sprite> {
    val before = if (previous.rows == current.rows && previous.cols == current.cols) placedById(previous) else emptyMap()
    // Count new pieces per column so a refill drops in as a stack from above the board.
    val newInColumn = IntArray(current.cols)
    for ((pos, piece) in placed(current)) if (piece.id !in before) newInColumn[pos.col]++

    val sprites =
        placed(current).map { (pos, piece) ->
            val from = before[piece.id]?.first
            when {
                from != null -> {
                    Sprite(
                        piece,
                        lerp(from.row.toFloat(), pos.row.toFloat(), t),
                        lerp(from.col.toFloat(), pos.col.toFloat(), t),
                    )
                }

                kind == PhaseKind.FALL -> {
                    Sprite(
                        piece,
                        lerp((pos.row - newInColumn[pos.col]).toFloat(), pos.row.toFloat(), t),
                        pos.col.toFloat(),
                    )
                }

                else -> {
                    Sprite(piece, pos.row.toFloat(), pos.col.toFloat(), scale = 0.4f + (0.6f * t))
                } // a new special pops in
            }
        }
    if (t >= 1f) return sprites
    val currentIds = placed(current).mapTo(HashSet()) { it.second.id }
    val shrink = 1f - t
    val cleared =
        before.values
            .filter { (_, piece) -> piece.id !in currentIds }
            .map { (pos, piece) -> Sprite(piece, pos.row.toFloat(), pos.col.toFloat(), 0.6f + (0.6f * shrink), shrink) }
    return sprites + cleared
}

/** Every piece on [board] with its cell. */
private fun placed(board: BoardSnapshot): List<Pair<Pos, Piece>> =
    board.positions().mapNotNull { pos -> board[pos].piece?.let { pos to it } }.toList()

private fun placedById(board: BoardSnapshot): Map<Long, Pair<Pos, Piece>> = placed(board).associateBy { it.second.id }

/** Each column starts its hop or collapse this much (as a fraction of the whole animation) after the one to its left. */
private const val COLUMN_STAGGER = 0.05f

/** Progress (0..1) of column [col]'s share of a staggered sweep at overall progress [t]. */
private fun columnProgress(
    t: Float,
    col: Float,
    cols: Int,
): Float {
    val span = 1f - (COLUMN_STAGGER * (cols - 1))
    return ((t - (COLUMN_STAGGER * col)) / span).coerceIn(0f, 1f)
}

/** Applies the level-cleared [hop] and the Game Over [collapse] (both 0..1) to the sprites. */
private fun swept(
    sprites: List<Sprite>,
    rows: Int,
    cols: Int,
    hop: Float,
    collapse: Float,
): List<Sprite> {
    if (hop == 0f && collapse == 0f) return sprites
    return sprites.map { sprite ->
        var dy = 0f
        var spin = 0f
        if (hop > 0f) dy -= sin(PI * columnProgress(hop, sprite.col, cols)).toFloat() * HOP_HEIGHT
        if (collapse > 0f) {
            // Falls under gravity past the bottom edge, turning a little one way or the other.
            val f = columnProgress(collapse, sprite.col, cols)
            dy += f * f * (rows + 1)
            spin = f * COLLAPSE_SPIN * (if (sprite.piece.id % 2 == 0L) 1 else -1)
        }
        Sprite(sprite.piece, sprite.row + dy, sprite.col, sprite.scale, sprite.alpha, spin)
    }
}

private fun DrawScope.drawPieces(
    images: List<ImageBitmap>,
    sprites: List<Sprite>,
    cell: Float,
) {
    for (sprite in sprites) {
        if (sprite.row < -0.5f) continue // still above the board
        val center = Offset((sprite.col + 0.5f) * cell, (sprite.row + 0.5f) * cell)
        rotate(sprite.spin, center) { drawPiece(images, sprite.piece, center, cell * 0.40f * sprite.scale, sprite.alpha) }
    }
}

/** Peak of the level-cleared hop, in cells. */
private const val HOP_HEIGHT = 0.25f

/** How far (degrees) an item turns while falling off the board on Game Over. */
private const val COLLAPSE_SPIN = 50f

private fun DrawScope.drawOverlays(
    previous: BoardSnapshot,
    current: BoardSnapshot,
    t: Float,
    cell: Float,
    lockAlpha: Float,
) {
    val samePrev = previous.rows == current.rows && previous.cols == current.cols
    for (pos in current.positions()) {
        val c = current[pos]
        val topLeft = Offset(pos.col * cell, pos.row * cell)
        val hp =
            when {
                c.isStone -> c.stoneHp
                samePrev && previous[pos].isStone -> previous[pos].stoneHp
                else -> 0
            }
        if (hp > 0) {
            val alpha = if (c.isStone) 1f else 1f - t
            drawStone(topLeft, cell, hp, alpha)
        }
        val wasLocked = samePrev && previous[pos].locked
        if (c.locked || wasLocked) drawLock(topLeft, cell, (if (c.locked) 1f else 1f - t) * lockAlpha)
    }
}

private fun DrawScope.drawStone(
    topLeft: Offset,
    cell: Float,
    hp: Int,
    alpha: Float,
) {
    val inset = cell * 0.05f
    val base = if (hp >= 2) PuzzlerColors.stoneHard else PuzzlerColors.stone
    drawRoundRect(
        base.copy(alpha = alpha),
        topLeft = topLeft + Offset(inset, inset),
        size = Size(cell - (2 * inset), cell - (2 * inset)),
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
        val crack =
            Path().apply {
                moveTo(topLeft.x + (cell * 0.3f), topLeft.y + (cell * 0.15f))
                lineTo(topLeft.x + (cell * 0.5f), topLeft.y + (cell * 0.45f))
                lineTo(topLeft.x + (cell * 0.4f), topLeft.y + (cell * 0.6f))
                lineTo(topLeft.x + (cell * 0.65f), topLeft.y + (cell * 0.85f))
            }
        drawPath(crack, Color.Black.copy(alpha = 0.5f * alpha), style = Stroke(cell * 0.05f, cap = StrokeCap.Round))
    }
}

private fun DrawScope.drawLock(
    topLeft: Offset,
    cell: Float,
    alpha: Float,
) {
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

/** Draws [piece] centered on [center]; [radius] is about 0.4 of a cell. Specials get their overlay on top. */
private fun DrawScope.drawPiece(
    images: List<ImageBitmap>,
    piece: Piece,
    center: Offset,
    radius: Float,
    alpha: Float,
) {
    if (piece.special == Special.COLOR_BOMB) {
        // Same design and proportions as the launcher icon (docs/SPEC.md §6).
        drawColorBomb(images, center, radius, alpha)
        return
    }
    val size = radius * 2.25f
    drawItem(images, piece.color, center, size, alpha)

    val box = RoundRect(Rect(center, size / 2 * 0.86f), CornerRadius(size * 0.2f))
    when (piece.special) {
        // Three white stripes across the item, along the line it clears.
        Special.STRIPED_H, Special.STRIPED_V -> {
            clipPath(Path().apply { addRoundRect(box) }) {
                val white = Color.White.copy(alpha = 0.8f * alpha)
                val shade = Color.Black.copy(alpha = 0.25f * alpha)
                for (k in -1..1) {
                    val o = k * size * 0.24f
                    val (from, to) =
                        if (piece.special == Special.STRIPED_H) {
                            Offset(box.left, center.y + o) to Offset(box.right, center.y + o)
                        } else {
                            Offset(center.x + o, box.top) to Offset(center.x + o, box.bottom)
                        }
                    drawLine(shade, from, to, size * 0.11f)
                    drawLine(white, from, to, size * 0.07f)
                }
            }
        }

        Special.BOMB -> {
            drawCircle(Color.White.copy(alpha = 0.9f * alpha), radius * 1.08f, center, style = Stroke(radius * 0.14f))
            drawCircle(Color.White.copy(alpha = 0.9f * alpha), radius * 0.2f, center)
        }

        else -> {
            Unit
        }
    }
}

private fun lerp(
    a: Float,
    b: Float,
    t: Float,
) = a + ((b - a) * t)

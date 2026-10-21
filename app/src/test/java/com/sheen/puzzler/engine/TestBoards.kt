package com.sheen.puzzler.engine

import org.junit.Assert.assertEquals
import kotlin.random.Random

/** Pattern color used for the shapes under test; the background noise only uses colors 0-3. */
const val P = 5

/**
 * A board with no runs anywhere: color (r + 2c) % 4 alternates along rows and changes every step down a column.
 * [marks] paint specific cells with a color, typically [P].
 */
fun testBoard(
    rows: Int = 8,
    cols: Int = 8,
    marks: Map<Pos, Int> = emptyMap(),
    seed: Int = 0,
): Board {
    val grid =
        (0 until rows).map { r ->
            (0 until cols).map { c -> '0' + (marks[Pos(r, c)] ?: ((r + 2 * c) % 4)) }.joinToString("")
        }
    return Board.fromPieces(grid, colors = 6, random = Random(seed))
}

fun marks(
    vararg cells: Pair<Int, Int>,
    color: Int = P,
) = cells.associate { Pos(it.first, it.second) to color }

fun Board.setSpecial(
    pos: Pos,
    special: Special,
) {
    this[pos] = this[pos].copy(piece = this[pos].piece!!.copy(special = special))
}

/** Engine over a prepared board; fails if the engine had to shuffle it (which would break the test setup). */
fun engineFor(
    board: Board,
    moves: Int = 20,
): GameEngine {
    val before = board.snapshot()
    val engine = GameEngine(board, moves)
    assertEquals("test board was reshuffled by the engine", before, board.snapshot())
    return engine
}

fun MoveResult.firstClear(): Phase = phases.first { it.kind == PhaseKind.CLEAR }

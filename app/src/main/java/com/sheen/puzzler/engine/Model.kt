package com.sheen.puzzler.engine

/** Grid coordinate; row 0 is the top of the board. */
data class Pos(
    val row: Int,
    val col: Int,
) {
    fun isAdjacentTo(other: Pos): Boolean =
        (row == other.row && kotlin.math.abs(col - other.col) == 1) ||
            (col == other.col && kotlin.math.abs(row - other.row) == 1)

    override fun toString() = "($row,$col)"
}

enum class Special {
    NONE,

    /** Clears its whole row. */
    STRIPED_H,

    /** Clears its whole column. */
    STRIPED_V,

    /** Clears the surrounding 3x3. */
    BOMB,

    /** Swapped with a piece, clears every piece of that color. Has no color of its own. */
    COLOR_BOMB,

    ;

    val isStriped get() = this == STRIPED_H || this == STRIPED_V
}

/** A piece on the board. [id] is unique per board and lets the UI animate pieces between snapshots. */
data class Piece(
    val id: Long,
    val color: Int,
    val special: Special = Special.NONE,
) {
    companion object {
        const val NO_COLOR = -1
    }
}

/**
 * One board cell. A cell that doesn't [exist] is a hole. A cell with [stoneHp] > 0 is a stone and holds no piece.
 * [jelly] is a layer under the piece; [locked] pins the piece in place until a match or blast includes it.
 */
data class Cell(
    val exists: Boolean = true,
    val piece: Piece? = null,
    val jelly: Boolean = false,
    val stoneHp: Int = 0,
    val locked: Boolean = false,
) {
    val isStone get() = stoneHp > 0

    /** Can hold a piece (not a hole, not a stone). */
    val isPlayable get() = exists && !isStone

    /** Holds a piece that may be swapped and may fall. */
    val isMovable get() = isPlayable && piece != null && !locked
}

enum class Pattern(
    val basePoints: Int,
) {
    LINE3(Scoring.LINE3),
    LINE4(Scoring.LINE4),
    LT(Scoring.LT),
    LINE5(Scoring.LINE5),
}

object Scoring {
    const val LINE3 = 5
    const val LINE4 = 10
    const val LT = 15
    const val LINE5 = 25
    const val PER_SPECIAL_CLEAR = 1
    const val JELLY = 2
    const val STONE = 3

    /** Extra multiplier per cascade step after the first. */
    const val CASCADE_BONUS = 0.5

    /** Cascade step 1 is the player's own move: x1, x1.5, x2, ... */
    fun multiplier(cascadeStep: Int): Double = 1.0 + CASCADE_BONUS * (cascadeStep - 1)

    fun applyMultiplier(
        points: Int,
        cascadeStep: Int,
    ): Int = (points * multiplier(cascadeStep)).toInt()
}

/** Immutable view of the board handed to the UI. */
data class BoardSnapshot(
    val rows: Int,
    val cols: Int,
    val cells: List<Cell>,
) {
    operator fun get(pos: Pos): Cell = cells[pos.row * cols + pos.col]

    operator fun get(
        row: Int,
        col: Int,
    ): Cell = cells[row * cols + col]

    fun positions(): Sequence<Pos> =
        sequence {
            for (r in 0 until rows) for (c in 0 until cols) yield(Pos(r, c))
        }
}

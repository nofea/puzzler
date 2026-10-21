package com.sheen.puzzler.engine

/** Outcome of one clear step, before the cascade multiplier. */
data class ClearOutcome(
    val clearedPieces: Int,
    val specialClears: Int,
    val jellyCleared: Int,
    val stonesDestroyed: Int,
    val specialsFired: Int,
) {
    val bonusPoints: Int
        get() = specialClears * Scoring.PER_SPECIAL_CLEAR + jellyCleared * Scoring.JELLY + stonesDestroyed * Scoring.STONE
}

/** Expands a set of cells through special-piece blasts and applies the clear to the board. */
object Resolver {
    fun row(
        board: Board,
        r: Int,
    ) = (0 until board.cols).map { Pos(r, it) }

    fun column(
        board: Board,
        c: Int,
    ) = (0 until board.rows).map { Pos(it, c) }

    fun square(
        board: Board,
        center: Pos,
        radius: Int,
    ): List<Pos> =
        (center.row - radius..center.row + radius)
            .flatMap { r ->
                (center.col - radius..center.col + radius).map { c -> Pos(r, c) }
            }.filter(board::inBounds)

    /** Most common color on the board; what a color bomb takes out when another blast sets it off. */
    fun dominantColor(board: Board): Int? =
        board
            .positions()
            .mapNotNull { board.colorAt(it) }
            .groupingBy { it }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key

    fun cellsOfColor(
        board: Board,
        color: Int,
    ) = board.positions().filter { board.colorAt(it) == color }.toList()

    /** Cells a single special covers when it fires. */
    fun blast(
        board: Board,
        pos: Pos,
        special: Special,
    ): List<Pos> =
        when (special) {
            Special.NONE -> listOf(pos)
            Special.STRIPED_H -> row(board, pos.row)
            Special.STRIPED_V -> column(board, pos.col)
            Special.BOMB -> square(board, pos, 1)
            Special.COLOR_BOMB -> listOf(pos) + (dominantColor(board)?.let { cellsOfColor(board, it) } ?: emptyList())
        }

    /**
     * Clears [matched] (cells of match groups) plus [blasted] (cells hit directly by a combo), chaining any
     * specials caught along the way. Pieces whose ids are in [alreadyFired] don't fire again.
     * Stones next to matched cells or inside a blast lose one hit point (at most one per clear).
     */
    fun clear(
        board: Board,
        matched: Set<Pos>,
        blasted: Collection<Pos> = emptyList(),
        alreadyFired: Set<Long> = emptySet(),
    ): ClearOutcome {
        val hits = collectHits(board, matched, blasted, alreadyFired)
        var removed = 0
        var specialClears = 0
        var jelly = 0
        for (pos in hits.pieces) {
            val cell = board[pos]
            if (cell.locked) {
                board[pos] = cell.copy(locked = false)
                continue
            }
            removed++
            if (pos !in matched) specialClears++
            if (cell.jelly) jelly++
            board[pos] = cell.copy(piece = null, jelly = false)
        }
        val stones = hits.stones.count { breakStone(board, it) }
        return ClearOutcome(removed, specialClears, jelly, stones, hits.specialsFired)
    }

    /** What one clear hits: pieces to clear (including chained blasts) and stones to crack. */
    private class Hits(
        val pieces: Set<Pos>,
        val stones: Set<Pos>,
        val specialsFired: Int,
    )

    private fun collectHits(
        board: Board,
        matched: Set<Pos>,
        blasted: Collection<Pos>,
        alreadyFired: Set<Long>,
    ): Hits {
        val fired = alreadyFired.toMutableSet()
        val pieces = LinkedHashSet<Pos>()
        val stones = HashSet<Pos>()
        val queue = ArrayDeque<Pos>()
        var specialsFired = 0

        fun add(pos: Pos) {
            if (!board.inBounds(pos)) return
            val cell = board[pos]
            when {
                cell.isStone -> stones += pos
                cell.isPlayable && cell.piece != null && pieces.add(pos) -> queue += pos
            }
        }

        matched.forEach(::add)
        blasted.forEach(::add)
        matched.flatMap(::neighbors).filterTo(stones) { board.inBounds(it) && board[it].isStone }

        while (queue.isNotEmpty()) {
            val pos = queue.removeFirst()
            val piece = board[pos].piece ?: continue
            if (piece.special == Special.NONE || !fired.add(piece.id)) continue
            specialsFired++
            blast(board, pos, piece.special).forEach(::add)
        }
        return Hits(pieces, stones, specialsFired)
    }

    private fun neighbors(pos: Pos) =
        listOf(Pos(pos.row - 1, pos.col), Pos(pos.row + 1, pos.col), Pos(pos.row, pos.col - 1), Pos(pos.row, pos.col + 1))

    /** Takes one hit off the stone at [pos]; true if that broke it. */
    private fun breakStone(
        board: Board,
        pos: Pos,
    ): Boolean {
        val cell = board[pos]
        val hp = cell.stoneHp - 1
        board[pos] = cell.copy(stoneHp = hp.coerceAtLeast(0))
        return hp <= 0
    }
}

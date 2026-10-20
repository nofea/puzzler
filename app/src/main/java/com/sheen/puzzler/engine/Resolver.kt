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

    fun row(board: Board, r: Int) = (0 until board.cols).map { Pos(r, it) }
    fun column(board: Board, c: Int) = (0 until board.rows).map { Pos(it, c) }

    fun square(board: Board, center: Pos, radius: Int): List<Pos> =
        (center.row - radius..center.row + radius).flatMap { r ->
            (center.col - radius..center.col + radius).map { c -> Pos(r, c) }
        }.filter(board::inBounds)

    /** Most common color on the board; what a color bomb takes out when another blast sets it off. */
    fun dominantColor(board: Board): Int? =
        board.positions().mapNotNull { board.colorAt(it) }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key

    fun cellsOfColor(board: Board, color: Int) = board.positions().filter { board.colorAt(it) == color }.toList()

    /** Cells a single special covers when it fires. */
    fun blast(board: Board, pos: Pos, special: Special): List<Pos> = when (special) {
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
        val fired = alreadyFired.toMutableSet()
        val toClear = LinkedHashSet<Pos>()
        val stoneHits = HashSet<Pos>()
        val queue = ArrayDeque<Pos>()
        var specialsFired = 0

        fun add(pos: Pos) {
            if (!board.inBounds(pos)) return
            val cell = board[pos]
            when {
                cell.isStone -> stoneHits += pos
                cell.isPlayable && cell.piece != null && toClear.add(pos) -> queue += pos
            }
        }

        matched.forEach(::add)
        blasted.forEach(::add)
        for (pos in matched) {
            for (n in listOf(Pos(pos.row - 1, pos.col), Pos(pos.row + 1, pos.col), Pos(pos.row, pos.col - 1), Pos(pos.row, pos.col + 1))) {
                if (board.inBounds(n) && board[n].isStone) stoneHits += n
            }
        }

        while (queue.isNotEmpty()) {
            val pos = queue.removeFirst()
            val piece = board[pos].piece ?: continue
            if (piece.special == Special.NONE || !fired.add(piece.id)) continue
            specialsFired++
            blast(board, pos, piece.special).forEach(::add)
        }

        var removed = 0
        var specialClears = 0
        var jelly = 0
        for (pos in toClear) {
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

        var stones = 0
        for (pos in stoneHits) {
            val cell = board[pos]
            val hp = cell.stoneHp - 1
            if (hp <= 0) stones++
            board[pos] = cell.copy(stoneHp = hp.coerceAtLeast(0))
        }

        return ClearOutcome(removed, specialClears, jelly, stones, specialsFired)
    }
}

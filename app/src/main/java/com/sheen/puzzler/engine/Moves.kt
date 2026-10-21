package com.sheen.puzzler.engine

object MoveFinder {
    private fun swapPieces(
        board: Board,
        a: Pos,
        b: Pos,
    ) {
        val pa = board[a].piece
        board[a] = board[a].copy(piece = board[b].piece)
        board[b] = board[b].copy(piece = pa)
    }

    /** True if [a] and [b] are neighboring cells whose pieces may be swapped (whether or not that matches). */
    fun isSwappable(
        board: Board,
        a: Pos,
        b: Pos,
    ): Boolean {
        val neighbors = board.inBounds(a) && board.inBounds(b) && a.isAdjacentTo(b)
        return neighbors && board[a].isMovable && board[b].isMovable
    }

    /** True if swapping [a] and [b] does something: a match, a color bomb, or two specials combining. */
    fun isValidSwap(
        board: Board,
        a: Pos,
        b: Pos,
    ): Boolean {
        if (!isSwappable(board, a, b)) return false
        val sa = board[a].piece!!.special
        val sb = board[b].piece!!.special
        val combines = sa == Special.COLOR_BOMB || sb == Special.COLOR_BOMB || (sa != Special.NONE && sb != Special.NONE)
        return combines || matchesAfterSwap(board, a, b)
    }

    private fun matchesAfterSwap(
        board: Board,
        a: Pos,
        b: Pos,
    ): Boolean {
        swapPieces(board, a, b)
        val match = createsMatchAt(board, a) || createsMatchAt(board, b)
        swapPieces(board, a, b)
        return match
    }

    private fun createsMatchAt(
        board: Board,
        pos: Pos,
    ): Boolean {
        val color = board.colorAt(pos) ?: return false

        fun count(
            dr: Int,
            dc: Int,
        ): Int {
            var n = 0
            var p = Pos(pos.row + dr, pos.col + dc)
            while (board.colorAt(p) == color) {
                n++
                p = Pos(p.row + dr, p.col + dc)
            }
            return n
        }
        return count(0, -1) + count(0, 1) >= 2 || count(-1, 0) + count(1, 0) >= 2
    }

    fun allMoves(board: Board): List<Pair<Pos, Pos>> =
        board
            .positions()
            .flatMap { p ->
                sequenceOf(Pos(p.row, p.col + 1), Pos(p.row + 1, p.col))
                    .filter { isValidSwap(board, p, it) }
                    .map { p to it }
            }.toList()

    fun findMove(board: Board): Pair<Pos, Pos>? =
        board.positions().firstNotNullOfOrNull { p ->
            sequenceOf(Pos(p.row, p.col + 1), Pos(p.row + 1, p.col)).firstOrNull { isValidSwap(board, p, it) }?.let { p to it }
        }

    fun hasMove(board: Board) = findMove(board) != null
}

/** Tries per strategy (rearrange, then recolor) before giving up on a shuffle. */
private const val SHUFFLE_ATTEMPTS = 200

object Shuffler {
    /**
     * Rearranges movable pieces until the board has a valid move and no ready-made match. Obstacles and locked
     * pieces stay put. Falls back to recoloring if rearranging alone can't get there.
     */
    fun shuffle(board: Board) {
        val positions = board.positions().filter { board[it].isMovable }.toList()
        val pieces = positions.map { board[it].piece!! }
        repeat(SHUFFLE_ATTEMPTS) {
            val order = pieces.shuffled(board.random)
            positions.forEachIndexed { i, p -> board[p] = board[p].copy(piece = order[i]) }
            if (!MatchFinder.hasMatch(board) && MoveFinder.hasMove(board)) return
        }
        repeat(SHUFFLE_ATTEMPTS) {
            positions.forEach { board[it] = board[it].copy(piece = null) }
            board.fillWithoutMatches()
            if (!MatchFinder.hasMatch(board) && MoveFinder.hasMove(board)) return
        }
    }
}

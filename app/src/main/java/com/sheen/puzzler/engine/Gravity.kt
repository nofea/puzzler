package com.sheen.puzzler.engine

/**
 * Moves pieces down into empty cells and spawns new pieces at the top of each column.
 * Cells shadowed by stones, holes or locked pieces are filled by a diagonal slide from a neighbor column.
 */
object Gravity {
    /** Topmost playable cell of a column (only holes above it); new pieces appear here. */
    fun isSpawner(
        board: Board,
        pos: Pos,
    ): Boolean = board[pos].isPlayable && (0 until pos.row).all { !board[it, pos.col].exists }

    private fun isEmpty(
        board: Board,
        pos: Pos,
    ) = board[pos].isPlayable && board[pos].piece == null

    private fun move(
        board: Board,
        from: Pos,
        to: Pos,
    ) {
        val piece = board[from].piece
        board[from] = board[from].copy(piece = null)
        board[to] = board[to].copy(piece = piece)
    }

    /** Settles the board. Returns true if anything moved or spawned. */
    fun settle(board: Board): Boolean {
        var changed = false
        while (true) {
            if (verticalPass(board)) {
                changed = true
                continue
            }
            if (diagonalStep(board)) {
                changed = true
                continue
            }
            return changed
        }
    }

    /**
     * Fills empty cells from directly above and spawns at column tops, repeating until nothing changes. Columns are
     * scanned left to right, each bottom-up; the order decides which piece falls where, so it must stay fixed.
     */
    private fun verticalPass(board: Board): Boolean {
        val order = (0 until board.cols).flatMap { c -> (board.rows - 1 downTo 0).map { r -> Pos(r, c) } }
        var changed = false
        do {
            var progress = false
            for (pos in order) {
                if (fillVertically(board, pos)) progress = true
            }
            changed = changed || progress
        } while (progress)
        return changed
    }

    /** Fills [pos] from the cell above it, or with a new piece at the top of a column. True if it filled. */
    private fun fillVertically(
        board: Board,
        pos: Pos,
    ): Boolean {
        if (!isEmpty(board, pos)) return false
        val above = Pos(pos.row - 1, pos.col)
        when {
            pos.row > 0 && board[above].isMovable -> move(board, above, pos)
            isSpawner(board, pos) -> board[pos] = board[pos].copy(piece = board.randomPiece())
            else -> return false
        }
        return true
    }

    /** One diagonal slide into the lowest empty cell that can't be filled from directly above. */
    private fun diagonalStep(board: Board): Boolean =
        (board.rows - 1 downTo 1).any { r -> (0 until board.cols).any { c -> slideDiagonally(board, Pos(r, c)) } }

    private fun slideDiagonally(
        board: Board,
        pos: Pos,
    ): Boolean {
        if (!isEmpty(board, pos) || isSpawner(board, pos)) return false
        if (isEmpty(board, Pos(pos.row - 1, pos.col))) return false // will be fed vertically
        // Alternate preference by column so slides don't all lean one way.
        val sides = if (pos.col % 2 == 0) listOf(-1, 1) else listOf(1, -1)
        val from =
            sides.map { Pos(pos.row - 1, pos.col + it) }.firstOrNull { board.inBounds(it) && board[it].isMovable }
                ?: return false
        move(board, from, pos)
        return true
    }
}

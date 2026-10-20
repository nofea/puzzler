package com.sheen.puzzler.engine

/**
 * Moves pieces down into empty cells and spawns new pieces at the top of each column.
 * Cells shadowed by stones, holes or locked pieces are filled by a diagonal slide from a neighbor column.
 */
object Gravity {

    /** Topmost playable cell of a column (only holes above it); new pieces appear here. */
    fun isSpawner(board: Board, pos: Pos): Boolean =
        board[pos].isPlayable && (0 until pos.row).all { !board[it, pos.col].exists }

    private fun isEmpty(board: Board, pos: Pos) = board[pos].isPlayable && board[pos].piece == null

    private fun move(board: Board, from: Pos, to: Pos) {
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

    private fun verticalPass(board: Board): Boolean {
        var changed = false
        var progress = true
        while (progress) {
            progress = false
            for (c in 0 until board.cols) {
                for (r in board.rows - 1 downTo 0) {
                    val pos = Pos(r, c)
                    if (!isEmpty(board, pos)) continue
                    if (r > 0 && board[r - 1, c].isMovable) {
                        move(board, Pos(r - 1, c), pos)
                        progress = true
                    } else if (isSpawner(board, pos)) {
                        board[pos] = board[pos].copy(piece = board.randomPiece())
                        progress = true
                    }
                }
            }
            changed = changed || progress
        }
        return changed
    }

    /** One diagonal slide into the lowest empty cell that can't be filled from directly above. */
    private fun diagonalStep(board: Board): Boolean {
        for (r in board.rows - 1 downTo 1) {
            for (c in 0 until board.cols) {
                val pos = Pos(r, c)
                if (!isEmpty(board, pos) || isSpawner(board, pos)) continue
                val above = Pos(r - 1, c)
                if (isEmpty(board, above)) continue // will be fed vertically
                // Alternate preference by column so slides don't all lean one way.
                val sides = if (c % 2 == 0) listOf(-1, 1) else listOf(1, -1)
                for (dc in sides) {
                    val from = Pos(r - 1, c + dc)
                    if (board.inBounds(from) && board[from].isMovable) {
                        move(board, from, pos)
                        return true
                    }
                }
            }
        }
        return false
    }
}

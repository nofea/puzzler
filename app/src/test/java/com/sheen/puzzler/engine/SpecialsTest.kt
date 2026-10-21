package com.sheen.puzzler.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpecialsTest {
    @Test
    fun `matched striped piece clears its row`() {
        val board = testBoard(marks = marks(0 to 0, 0 to 1, 1 to 2))
        board.setSpecial(Pos(0, 0), Special.STRIPED_H)
        val clear = engineFor(board).swap(Pos(1, 2), Pos(0, 2)).firstClear()
        // 3 matched (5) + 5 more pieces in the row at +1 each.
        assertEquals(10, clear.scoreDelta)
        assertEquals(1, clear.specialsFired)
        assertTrue((0 until 8).all { clear.snapshot[0, it].piece == null })
    }

    @Test
    fun `matched bomb clears 3x3`() {
        val board = testBoard(marks = marks(3 to 3, 3 to 4, 4 to 5))
        board.setSpecial(Pos(3, 4), Special.BOMB)
        val clear = engineFor(board).swap(Pos(4, 5), Pos(3, 5)).firstClear()
        // Match (3,3)-(3,5); bomb at (3,4) clears rows 2-4, cols 3-5: 9 cells, 3 matched -> 6 extra.
        assertEquals(5 + 6, clear.scoreDelta)
        for (r in 2..4) for (c in 3..5) assertEquals(null, clear.snapshot[r, c].piece)
    }

    @Test
    fun `specials chain`() {
        val board = testBoard(marks = marks(0 to 0, 0 to 1, 1 to 2))
        board.setSpecial(Pos(0, 0), Special.STRIPED_H)
        board.setSpecial(Pos(0, 6), Special.STRIPED_V) // caught by the row blast
        val clear = engineFor(board).swap(Pos(1, 2), Pos(0, 2)).firstClear()
        assertEquals(2, clear.specialsFired)
        assertTrue((0 until 8).all { clear.snapshot[it, 6].piece == null })
        // Row (8) + column (7 more) = 15 pieces, 3 matched: 5 + 12.
        assertEquals(17, clear.scoreDelta)
    }

    @Test
    fun `color bomb swapped with a piece clears that color`() {
        val board = testBoard()
        board[Pos(0, 0)] = Cell(piece = board.newPiece(Piece.NO_COLOR, Special.COLOR_BOMB))
        val target = board[Pos(0, 1)].piece!!.color // (0 + 2) % 4 = 2
        val result = engineFor(board).swap(Pos(0, 0), Pos(0, 1))
        assertTrue(result.valid)
        val clear = result.firstClear()
        assertTrue(clear.snapshot.cells.none { it.piece?.color == target })
        // 16 pieces of color 2 on the noise board plus the bomb itself.
        assertEquals(17, clear.scoreDelta)
    }

    @Test
    fun `two color bombs clear the board`() {
        val board = testBoard()
        board[Pos(0, 0)] = Cell(piece = board.newPiece(Piece.NO_COLOR, Special.COLOR_BOMB))
        board[Pos(0, 1)] = Cell(piece = board.newPiece(Piece.NO_COLOR, Special.COLOR_BOMB))
        val clear = engineFor(board).swap(Pos(0, 0), Pos(0, 1)).firstClear()
        assertTrue(clear.snapshot.cells.all { it.piece == null })
        assertEquals(64, clear.scoreDelta)
    }

    @Test
    fun `color bomb with striped turns the color into stripes`() {
        val board = testBoard()
        board[Pos(0, 0)] = Cell(piece = board.newPiece(Piece.NO_COLOR, Special.COLOR_BOMB))
        board.setSpecial(Pos(0, 1), Special.STRIPED_H)
        val clear = engineFor(board).swap(Pos(0, 0), Pos(0, 1)).firstClear()
        // 16 color-2 pieces each fire a row or column: that's most of the board.
        assertTrue(clear.specialsFired >= 16)
        assertTrue(clear.snapshot.cells.count { it.piece == null } > 40)
    }

    @Test
    fun `bomb plus bomb clears 5x5`() {
        val board = testBoard()
        board.setSpecial(Pos(3, 3), Special.BOMB)
        board.setSpecial(Pos(3, 4), Special.BOMB)
        val result = engineFor(board).swap(Pos(3, 3), Pos(3, 4))
        assertTrue(result.valid)
        val clear = result.firstClear()
        for (r in 1..5) for (c in 2..6) assertEquals(null, clear.snapshot[r, c].piece)
        assertEquals(25, clear.scoreDelta)
    }

    @Test
    fun `striped plus striped clears a cross`() {
        val board = testBoard()
        board.setSpecial(Pos(3, 3), Special.STRIPED_H)
        board.setSpecial(Pos(3, 4), Special.STRIPED_H)
        val clear = engineFor(board).swap(Pos(3, 3), Pos(3, 4)).firstClear()
        assertTrue((0 until 8).all { clear.snapshot[3, it].piece == null })
        assertTrue((0 until 8).all { clear.snapshot[it, 4].piece == null })
        assertEquals(15, clear.scoreDelta)
    }

    @Test
    fun `striped plus bomb clears three rows and three columns`() {
        val board = testBoard()
        board.setSpecial(Pos(3, 3), Special.STRIPED_V)
        board.setSpecial(Pos(3, 4), Special.BOMB)
        val clear = engineFor(board).swap(Pos(3, 3), Pos(3, 4)).firstClear()
        for (r in 2..4) assertTrue((0 until 8).all { clear.snapshot[r, it].piece == null })
        for (c in 3..5) assertTrue((0 until 8).all { clear.snapshot[it, c].piece == null })
        // 3 rows (24) + 3 cols (24) - overlap (9) = 39.
        assertEquals(39, clear.scoreDelta)
    }
}

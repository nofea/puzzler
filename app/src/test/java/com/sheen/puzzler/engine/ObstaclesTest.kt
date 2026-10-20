package com.sheen.puzzler.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ObstaclesTest {

    @Test
    fun `jelly under a match is cleared for 2 points`() {
        val board = testBoard(marks = marks(0 to 0, 0 to 1, 1 to 2))
        board[Pos(0, 0)] = board[Pos(0, 0)].copy(jelly = true)
        val clear = engineFor(board).swap(Pos(1, 2), Pos(0, 2)).firstClear()
        assertEquals(5 + 2, clear.scoreDelta)
        assertFalse(clear.snapshot[0, 0].jelly)
    }

    @Test
    fun `stone next to a match breaks for 3 points`() {
        val board = testBoard(marks = marks(0 to 0, 0 to 1, 1 to 2))
        board[Pos(1, 0)] = Cell(stoneHp = 1)
        val clear = engineFor(board).swap(Pos(1, 2), Pos(0, 2)).firstClear()
        assertEquals(5 + 3, clear.scoreDelta)
        assertFalse(clear.snapshot[1, 0].isStone)
    }

    @Test
    fun `two hit stone survives the first hit`() {
        val board = testBoard(marks = marks(0 to 0, 0 to 1, 1 to 2))
        board[Pos(1, 0)] = Cell(stoneHp = 2)
        val clear = engineFor(board).swap(Pos(1, 2), Pos(0, 2)).firstClear()
        assertEquals(5, clear.scoreDelta)
        assertEquals(1, clear.snapshot[1, 0].stoneHp)
    }

    @Test
    fun `stone is hit once per clear even when touching several matched cells`() {
        // L shape (0,0)-(0,2) + (1,2)-(2,2); the stone at (1,1) touches (0,1) and (1,2).
        val board = testBoard(marks = marks(0 to 0, 0 to 1, 0 to 3, 1 to 2, 2 to 2))
        board[Pos(1, 1)] = Cell(stoneHp = 2)
        val clear = engineFor(board).swap(Pos(0, 3), Pos(0, 2)).firstClear()
        assertEquals(listOf(Pattern.LT), clear.patterns)
        assertEquals(1, clear.snapshot[1, 1].stoneHp)
    }

    @Test
    fun `locked piece is freed by a match and stays put`() {
        val board = testBoard(marks = marks(0 to 0, 0 to 1, 1 to 2))
        board[Pos(0, 0)] = board[Pos(0, 0)].copy(locked = true)
        val id = board[Pos(0, 0)].piece!!.id
        val clear = engineFor(board).swap(Pos(1, 2), Pos(0, 2)).firstClear()
        assertFalse(clear.snapshot[0, 0].locked)
        assertEquals(id, clear.snapshot[0, 0].piece!!.id)
        assertEquals(5, clear.scoreDelta)
    }

    @Test
    fun `locked piece cannot be swapped`() {
        val board = testBoard(marks = marks(0 to 0, 0 to 1, 1 to 2))
        board[Pos(1, 2)] = board[Pos(1, 2)].copy(locked = true)
        assertFalse(MoveFinder.isValidSwap(board, Pos(1, 2), Pos(0, 2)))
    }

    @Test
    fun `stones and holes cannot be swapped`() {
        val board = testBoard(marks = marks(0 to 0, 0 to 1, 1 to 2))
        board[Pos(0, 3)] = Cell(stoneHp = 1)
        board[Pos(1, 3)] = Cell(exists = false)
        assertFalse(MoveFinder.isValidSwap(board, Pos(0, 2), Pos(0, 3)))
        assertFalse(MoveFinder.isValidSwap(board, Pos(1, 2), Pos(1, 3)))
        assertTrue(MoveFinder.isValidSwap(board, Pos(1, 2), Pos(0, 2)))
    }
}

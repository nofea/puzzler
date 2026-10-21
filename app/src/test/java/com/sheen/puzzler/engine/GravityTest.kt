package com.sheen.puzzler.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class GravityTest {
    private fun allFilled(board: Board) = board.positions().all { !board[it].isPlayable || board[it].piece != null }

    @Test
    fun `pieces fall and the column refills from the top`() {
        val board = testBoard()
        val above = board[Pos(2, 4)].piece!!.id
        board[Pos(3, 4)] = board[Pos(3, 4)].copy(piece = null)
        board[Pos(4, 4)] = board[Pos(4, 4)].copy(piece = null)
        assertTrue(Gravity.settle(board))
        assertTrue(allFilled(board))
        assertEquals(above, board[Pos(4, 4)].piece!!.id)
    }

    @Test
    fun `cell under a stone is filled diagonally`() {
        val board = Board.fromLayout(listOf("....", ".S..", "....", "...."), 4, Random(1))
        board.fillWithoutMatches()
        board[Pos(2, 1)] = board[Pos(2, 1)].copy(piece = null)
        board[Pos(3, 1)] = board[Pos(3, 1)].copy(piece = null)
        Gravity.settle(board)
        assertTrue(allFilled(board))
        assertEquals(1, board[Pos(1, 1)].stoneHp)
    }

    @Test
    fun `locked piece does not fall`() {
        val board = Board.fromLayout(listOf("....", ".L..", "....", "...."), 4, Random(2))
        board.fillWithoutMatches()
        val lockedId = board[Pos(1, 1)].piece!!.id
        board[Pos(2, 1)] = board[Pos(2, 1)].copy(piece = null)
        Gravity.settle(board)
        assertTrue(allFilled(board))
        assertEquals(lockedId, board[Pos(1, 1)].piece!!.id)
        assertTrue(board[Pos(1, 1)].locked)
    }

    @Test
    fun `columns topped by holes spawn below the holes`() {
        val board = Board.fromLayout(listOf("#..#", "....", "....", "#..#"), 4, Random(3))
        assertTrue(Gravity.isSpawner(board, Pos(1, 0)))
        assertFalse(Gravity.isSpawner(board, Pos(2, 0)))
        board.fillWithoutMatches()
        board[Pos(1, 0)] = board[Pos(1, 0)].copy(piece = null)
        board[Pos(2, 0)] = board[Pos(2, 0)].copy(piece = null)
        Gravity.settle(board)
        assertTrue(allFilled(board))
        assertEquals(null, board[Pos(0, 0)].piece)
        assertEquals(null, board[Pos(3, 0)].piece)
    }

    @Test
    fun `settling a full board changes nothing`() {
        val board = testBoard()
        val before = board.snapshot()
        assertFalse(Gravity.settle(board))
        assertEquals(before, board.snapshot())
    }
}

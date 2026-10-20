package com.sheen.puzzler.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ShufflerTest {

    @Test
    fun `dead board is shuffled into a playable one`() {
        val board = Board.fromPieces(listOf("0123", "2301", "0123", "2301"), colors = 4, random = Random(5))
        assertNull(MoveFinder.findMove(board))
        Shuffler.shuffle(board)
        assertTrue(MoveFinder.hasMove(board))
        assertFalse(MatchFinder.hasMatch(board))
    }

    @Test
    fun `shuffle keeps obstacles and locked pieces in place`() {
        val board = testBoard(seed = 9)
        board[Pos(2, 2)] = Cell(stoneHp = 2)
        board[Pos(5, 5)] = board[Pos(5, 5)].copy(locked = true)
        val lockedId = board[Pos(5, 5)].piece!!.id
        Shuffler.shuffle(board)
        assertEquals(2, board[Pos(2, 2)].stoneHp)
        assertEquals(lockedId, board[Pos(5, 5)].piece!!.id)
        assertTrue(MoveFinder.hasMove(board))
    }

    @Test
    fun `engine never leaves the player without a move`() {
        val engine = GameEngine(Board.fromLayout(List(8) { "........" }, 6, Random(11)), 300)
        repeat(300) {
            val (a, b) = MoveFinder.findMove(engine.board) ?: throw AssertionError("stuck after $it moves")
            assertTrue(engine.swap(a, b).valid)
            assertFalse(MatchFinder.hasMatch(engine.board))
        }
    }
}

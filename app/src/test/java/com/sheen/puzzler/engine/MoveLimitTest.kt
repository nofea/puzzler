package com.sheen.puzzler.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class MoveLimitTest {
    @Test
    fun `a move limit counts down and ends play`() {
        val engine = GameEngine(Board.fromLayout(List(8) { "........" }, 5, Random(2)), 2)
        assertTrue(engine.limited)
        repeat(2) {
            val (a, b) = MoveFinder.findMove(engine.board)!!
            assertTrue(engine.swap(a, b).valid)
        }
        assertEquals(0, engine.movesLeft)
        assertFalse(engine.canMove)
        val (a, b) = MoveFinder.findMove(engine.board)!!
        assertFalse(engine.swap(a, b).valid)
    }

    @Test
    fun `without a limit the moves only count up`() {
        val engine = GameEngine(Board.fromLayout(List(8) { "........" }, 5, Random(2)), UNLIMITED)
        assertFalse(engine.limited)
        repeat(40) {
            val (a, b) = MoveFinder.findMove(engine.board)!!
            assertTrue(engine.swap(a, b).valid)
        }
        assertEquals(40, engine.movesMade)
        assertTrue(engine.canMove)
    }

    @Test
    fun `goals count the jelly, locks and stones left`() {
        val board = Board.fromLayout(listOf("J.L.", "..S.", "K.T.", "...."), 4, Random(1))
        assertEquals(Goals(jelly = 2, locked = 2, stones = 2), board.goals())
        assertEquals(board.goals(), board.snapshot().goals())
        assertFalse(board.goals().cleared)
        assertTrue(Goals().cleared)
    }
}

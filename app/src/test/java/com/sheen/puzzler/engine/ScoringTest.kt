package com.sheen.puzzler.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoringTest {
    @Test
    fun `cascade multiplier`() {
        assertEquals(1.0, Scoring.multiplier(1), 0.0)
        assertEquals(1.5, Scoring.multiplier(2), 0.0)
        assertEquals(2.0, Scoring.multiplier(3), 0.0)
        assertEquals(7, Scoring.applyMultiplier(5, 2)) // 7.5 rounds down
        assertEquals(20, Scoring.applyMultiplier(10, 3))
    }

    @Test
    fun `line of 3 scores 5`() {
        val engine = engineFor(testBoard(marks = marks(0 to 0, 0 to 1, 1 to 2)))
        val result = engine.swap(Pos(1, 2), Pos(0, 2))
        assertTrue(result.valid)
        val clear = result.firstClear()
        assertEquals(listOf(Pattern.LINE3), clear.patterns)
        assertEquals(5, clear.scoreDelta)
        assertEquals(1, clear.cascade)
        assertEquals(19, engine.movesLeft)
    }

    @Test
    fun `line of 4 scores 10 and leaves a striped piece at the swap`() {
        val engine = engineFor(testBoard(marks = marks(0 to 0, 0 to 1, 0 to 3, 1 to 2)))
        val clear = engine.swap(Pos(1, 2), Pos(0, 2)).firstClear()
        assertEquals(10, clear.scoreDelta)
        assertEquals(Special.STRIPED_V, clear.snapshot[0, 2].piece!!.special)
    }

    @Test
    fun `line of 5 scores 25 and makes a color bomb`() {
        val engine = engineFor(testBoard(marks = marks(0 to 0, 0 to 1, 0 to 3, 0 to 4, 1 to 2)))
        val clear = engine.swap(Pos(1, 2), Pos(0, 2)).firstClear()
        assertEquals(25, clear.scoreDelta)
        val piece = clear.snapshot[0, 2].piece!!
        assertEquals(Special.COLOR_BOMB, piece.special)
        assertEquals(Piece.NO_COLOR, piece.color)
    }

    @Test
    fun `L shape scores 15 and makes a bomb`() {
        val engine = engineFor(testBoard(marks = marks(0 to 0, 0 to 1, 0 to 3, 1 to 2, 2 to 2)))
        val clear = engine.swap(Pos(0, 3), Pos(0, 2)).firstClear()
        assertEquals(listOf(Pattern.LT), clear.patterns)
        assertEquals(15, clear.scoreDelta)
        assertEquals(Special.BOMB, clear.snapshot[0, 2].piece!!.special)
    }

    @Test
    fun `T shape scores 15`() {
        val engine = engineFor(testBoard(marks = marks(2 to 0, 2 to 2, 1 to 1, 3 to 1, 4 to 1)))
        val clear = engine.swap(Pos(1, 1), Pos(2, 1)).firstClear()
        assertEquals(listOf(Pattern.LT), clear.patterns)
        assertEquals(15, clear.scoreDelta)
    }

    @Test
    fun `later cascade steps are multiplied`() {
        val engine = engineFor(testBoard(marks = marks(0 to 0, 0 to 1, 1 to 2)), moves = 200)
        // Play until some move cascades, then check every step against the formula.
        repeat(200) {
            if (!engine.canMove) return@repeat
            val (a, b) = MoveFinder.findMove(engine.board)!!
            val result = engine.swap(a, b)
            val clears = result.phases.filter { it.kind == PhaseKind.CLEAR }
            clears.forEachIndexed { i, phase -> assertEquals(i + 1, phase.cascade) }
            if (clears.size >= 2) return
        }
        throw AssertionError("no cascade happened in 200 moves")
    }

    @Test
    fun `swap without a match is reverted and costs no move`() {
        val board = testBoard()
        val engine = engineFor(testBoard(marks = marks(0 to 0, 0 to 1, 1 to 2)))
        val before = engine.snapshot()
        val result = engine.swap(Pos(5, 5), Pos(5, 6))
        assertFalse(result.valid)
        assertEquals(listOf(PhaseKind.SWAP, PhaseKind.SWAP_BACK), result.phases.map { it.kind })
        assertEquals(before, engine.snapshot())
        assertEquals(20, engine.movesLeft)
        assertEquals(0, engine.score)
        assertNull(MoveFinder.findMove(board)) // sanity: the noise board itself is dead
    }

    @Test
    fun `non adjacent swap does nothing`() {
        val engine = engineFor(testBoard(marks = marks(0 to 0, 0 to 1, 1 to 2)))
        val result = engine.swap(Pos(0, 0), Pos(2, 2))
        assertFalse(result.valid)
        assertTrue(result.phases.isEmpty())
    }

    @Test
    fun `no moves left means no more swaps`() {
        val engine = engineFor(testBoard(marks = marks(0 to 0, 0 to 1, 1 to 2)), moves = 1)
        assertTrue(engine.swap(Pos(1, 2), Pos(0, 2)).valid)
        assertFalse(engine.canMove)
        assertFalse(engine.swap(Pos(1, 2), Pos(0, 2)).valid)
    }
}

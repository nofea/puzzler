package com.sheen.puzzler.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchFinderTest {
    @Test
    fun `noise board has no matches`() {
        assertFalse(MatchFinder.hasMatch(testBoard()))
    }

    @Test
    fun `horizontal line of 3`() {
        val groups = MatchFinder.findGroups(testBoard(marks = marks(0 to 0, 0 to 1, 0 to 2)))
        assertEquals(1, groups.size)
        assertEquals(Pattern.LINE3, groups[0].pattern)
        assertEquals(Special.NONE, groups[0].specialToCreate(null))
    }

    @Test
    fun `vertical line of 4 makes vertical striped`() {
        val g = MatchFinder.findGroups(testBoard(marks = marks(1 to 3, 2 to 3, 3 to 3, 4 to 3))).single()
        assertEquals(Pattern.LINE4, g.pattern)
        assertEquals(Special.STRIPED_V, g.specialToCreate(null))
        assertEquals(Special.STRIPED_H, g.specialToCreate(swapHorizontal = true))
    }

    @Test
    fun `line of 5 makes color bomb`() {
        val g = MatchFinder.findGroups(testBoard(marks = marks(2 to 0, 2 to 1, 2 to 2, 2 to 3, 2 to 4))).single()
        assertEquals(Pattern.LINE5, g.pattern)
        assertEquals(Special.COLOR_BOMB, g.specialToCreate(null))
        assertEquals(Pos(2, 2), g.specialPosition(emptyList()))
    }

    @Test
    fun `L shape merges into one group with bomb at the corner`() {
        val g = MatchFinder.findGroups(testBoard(marks = marks(0 to 0, 0 to 1, 0 to 2, 1 to 2, 2 to 2))).single()
        assertEquals(Pattern.LT, g.pattern)
        assertEquals(5, g.cells.size)
        assertEquals(Special.BOMB, g.specialToCreate(null))
        assertEquals(Pos(0, 2), g.specialPosition(emptyList()))
    }

    @Test
    fun `T shape is LT`() {
        val g = MatchFinder.findGroups(testBoard(marks = marks(2 to 0, 2 to 1, 2 to 2, 3 to 1, 4 to 1))).single()
        assertEquals(Pattern.LT, g.pattern)
        assertEquals(Pos(2, 1), g.specialPosition(emptyList()))
    }

    @Test
    fun `separate runs are separate groups`() {
        val m = marks(0 to 0, 0 to 1, 0 to 2) + marks(5 to 5, 6 to 5, 7 to 5, color = 4)
        val groups = MatchFinder.findGroups(testBoard(marks = m))
        assertEquals(2, groups.size)
        assertTrue(groups.all { it.pattern == Pattern.LINE3 })
    }

    @Test
    fun `color bombs never match`() {
        val board = Board.fromPieces(listOf("***0", "1230", "2301"), colors = 4)
        assertFalse(MatchFinder.hasMatch(board))
    }
}

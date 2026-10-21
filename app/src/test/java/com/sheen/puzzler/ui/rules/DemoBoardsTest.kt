package com.sheen.puzzler.ui.rules

import com.sheen.puzzler.engine.MatchFinder
import com.sheen.puzzler.engine.MoveFinder
import com.sheen.puzzler.engine.Pattern
import com.sheen.puzzler.engine.PhaseKind
import com.sheen.puzzler.engine.Special
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoBoardsTest {

    private val demos = Rules.cards.flatMap { card -> card.demos.map { card.title to it } }

    // The basic line-of-3 demo appears on two cards, so take the first.
    private fun demo(caption: String) = demos.first { it.second.caption == caption }.second

    @Test
    fun `six cards`() {
        assertEquals(listOf("Basics", "Scoring", "Specials", "Combos", "Obstacles", "Limits & stars"), Rules.cards.map { it.title })
    }

    @Test
    fun `every demo board starts calm and its scripted moves play as intended`() {
        for ((card, demo) in demos) {
            val engine = demo.newEngine()
            assertFalse("$card / ${demo.caption} starts with a match", MatchFinder.hasMatch(engine.board))
            assertTrue("$card / ${demo.caption} has no move", MoveFinder.hasMove(engine.board))
            for (move in demo.moves) {
                val result = engine.swap(move.a, move.b)
                assertEquals("$card / ${demo.caption}: ${move.a}-${move.b}", move.valid, result.valid)
            }
        }
    }

    @Test
    fun `demos show what their captions say`() {
        fun firstClear(caption: String) = demo(caption).let { d ->
            d.newEngine().swap(d.moves.last().a, d.moves.last().b).phases.first { it.kind == PhaseKind.CLEAR }
        }
        assertEquals(listOf(Pattern.LINE3), firstClear("Three in a row clears").patterns)
        assertEquals(listOf(Special.STRIPED_V), firstClear("Line of 4 makes a striped item").specialsCreated)
        assertEquals(listOf(Special.BOMB), firstClear("L or T shape makes a bomb").specialsCreated)
        assertEquals(listOf(Special.COLOR_BOMB), firstClear("Line of 5 makes a color bomb").specialsCreated)
        assertTrue(firstClear("A matched striped item clears its row").specialsFired > 0)

        val cascade = demo("Cascade: the second match scores ×1.5").let { it.newEngine().swap(it.moves[0].a, it.moves[0].b) }
        assertTrue("cascade demo has no second step", cascade.maxCascade >= 2)

        val obstacles = demo("Jelly clears, the stone breaks, the chain comes off").let { d ->
            val engine = d.newEngine()
            engine.swap(d.moves[0].a, d.moves[0].b)
            engine.board
        }
        assertEquals(0, obstacles.stoneCount())
        assertEquals(0, obstacles.lockedCount())
        assertEquals(0, obstacles.jellyCount())
    }
}

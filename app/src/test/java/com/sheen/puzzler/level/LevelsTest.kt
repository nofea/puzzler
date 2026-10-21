package com.sheen.puzzler.level

import com.sheen.puzzler.engine.MatchFinder
import com.sheen.puzzler.engine.MoveFinder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LevelsTest {
    // Unit tests run with the module directory as working directory.
    private val levels = LevelParser.parse(File("src/main/assets/levels.json").readText())

    @Test
    fun `twenty levels in three tiers`() {
        assertEquals((1..20).toList(), levels.map { it.id })
        assertTrue(levels.all { it.target == LevelDef.PASS_SCORE })
        // L1-10: no limits; L11-15: move limit; L16-20: move limit and timer.
        assertTrue(levels.take(10).none { it.limited || it.timed })
        assertTrue(levels.subList(10, 15).all { it.limited && !it.timed })
        assertTrue(levels.drop(15).all { it.limited && it.timed })
        levels.filter { it.limited }.forEach { assertTrue("level ${it.id}", it.star2 > it.target && it.star3 > it.star2) }
        levels.filterNot { it.limited }.forEach { assertTrue("level ${it.id}", it.par3 < it.par2) }
    }

    @Test
    fun `each mechanic first appears where its tutorial is`() {
        fun has(
            id: Int,
            ch: Char,
        ) = levels[id - 1].layout.any { ch in it }

        fun firstWith(vararg chars: Char) = levels.first { l -> chars.any { has(l.id, it) } }.id
        assertEquals(3, firstWith('J', 'K'))
        assertEquals(4, firstWith('L', 'K'))
        assertEquals(5, firstWith('S', 'T'))
        assertEquals(7, firstWith('T', '#'))
        assertEquals(8, levels.first { it.colors == 6 }.id)
    }

    @Test
    fun `stars come from moves on unlimited levels and from score on limited ones`() {
        val free = levels.first()
        assertEquals(0, free.stars(score = 99, movesUsed = 1, goalsLeft = 0))
        assertEquals(0, free.stars(score = 500, movesUsed = 1, goalsLeft = 1))
        assertEquals(3, free.stars(score = 100, movesUsed = free.par3, goalsLeft = 0))
        assertEquals(2, free.stars(score = 100, movesUsed = free.par2, goalsLeft = 0))
        assertEquals(1, free.stars(score = 100, movesUsed = free.par2 + 1, goalsLeft = 0))

        val limited = levels.first { it.limited }
        assertEquals(0, limited.stars(score = limited.star3, movesUsed = 1, goalsLeft = 2))
        assertEquals(1, limited.stars(score = 100, movesUsed = 99, goalsLeft = 0))
        assertEquals(2, limited.stars(score = limited.star2, movesUsed = 99, goalsLeft = 0))
        assertEquals(3, limited.stars(score = limited.star3, movesUsed = 99, goalsLeft = 0))
    }

    @Test
    fun `every level starts calm and playable`() {
        for (level in levels) {
            repeat(20) { seed ->
                val engine = level.newEngine(seed.toLong())
                assertFalse("level ${level.id} seed $seed starts with a match", MatchFinder.hasMatch(engine.board))
                assertTrue("level ${level.id} seed $seed has no move", MoveFinder.hasMove(engine.board))
            }
        }
    }

    @Test
    fun `same seed gives the same board`() {
        val level = levels[4]
        assertEquals(level.newEngine().snapshot(), level.newEngine().snapshot())
    }

    @Test
    fun `a greedy player always passes the levels without limits`() {
        for (level in levels.filterNot { it.limited }) {
            val moves = (1..SEEDS).map { Bots.greedyGame(level, it * 7919L, MAX_FREE_MOVES, stopWhenPassed = true).passedAt }
            println("level ${level.id}: moves to pass $moves (★★★ ≤ ${level.par3}, ★★ ≤ ${level.par2})")
            assertTrue("level ${level.id}: a greedy player got stuck: $moves", moves.none { it == null })
        }
    }

    @Test
    fun `a greedy player passes the limited levels less often as they get harder`() {
        val rates =
            levels.filter { it.limited }.associate { level ->
                val games = (1..SEEDS).map { Bots.greedyGame(level, it * 7919L, level.moves, stopWhenPassed = true) }
                level.id to games.count { it.passedAt != null }
            }
        rates.forEach { (id, wins) -> println("level $id: $wins/$SEEDS passed") }
        // About 90% on L11 down to about 50% on L20 (docs/SPEC.md §3); loose bounds for a small sample.
        assertTrue("level 11 passed ${rates[11]}/$SEEDS", rates.getValue(11) >= SEEDS * 6 / 10)
        rates.forEach { (id, wins) -> assertTrue("level $id passed only $wins/$SEEDS", wins >= SEEDS * 2 / 10) }
        assertTrue("level 20 is too easy: ${rates[20]}/$SEEDS", rates.getValue(20) <= SEEDS * 9 / 10)
    }

    private companion object {
        const val SEEDS = 10

        /** A greedy player that hasn't passed a level without limits by now is stuck on an unreachable goal. */
        const val MAX_FREE_MOVES = 150
    }
}

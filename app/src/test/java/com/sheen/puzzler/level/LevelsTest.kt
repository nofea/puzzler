package com.sheen.puzzler.level

import com.sheen.puzzler.engine.GameEngine
import com.sheen.puzzler.engine.MatchFinder
import com.sheen.puzzler.engine.MoveFinder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

class LevelsTest {
    // Unit tests run with the module directory as working directory.
    private val levels = LevelParser.parse(File("src/main/assets/levels.json").readText())

    @Test
    fun `ten levels that get harder`() {
        assertEquals((1..10).toList(), levels.map { it.id })
        assertTrue(levels.all { it.target == LevelDef.PASS_SCORE })
        levels.zipWithNext().forEach { (a, b) ->
            assertTrue("moves shouldn't grow without a timer: ${a.id}->${b.id}", b.moves <= a.moves || (b.timed && !a.timed))
            assertTrue(b.colors >= a.colors)
        }
        levels.forEach { assertTrue(it.star2 > it.target && it.star3 > it.star2) }
        assertTrue(levels.take(5).none { it.timed })
        assertTrue(levels.drop(5).all { it.timed })
    }

    @Test
    fun `stars`() {
        val l = levels.first()
        assertEquals(0, l.stars(99))
        assertEquals(1, l.stars(100))
        assertEquals(2, l.stars(l.star2))
        assertEquals(3, l.stars(l.star3))
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

    /** Plays the move that scores most right now (simulated on a copy), until moves run out. */
    private fun greedyScore(
        level: LevelDef,
        seed: Long,
    ): Int {
        val engine = level.newEngine(seed)
        val rng = Random(seed)
        while (engine.canMove) {
            val moves = MoveFinder.allMoves(engine.board)
            val best =
                moves.maxBy { (a, b) ->
                    val sim = GameEngine(engine.board.copy(Random(rng.nextLong())), 1)
                    sim.swap(a, b).scoreDelta
                }
            engine.swap(best.first, best.second)
        }
        return engine.score
    }

    @Test
    fun `a greedy player can pass every level most of the time`() {
        val seeds = 10
        val report =
            levels.map { level ->
                val scores = (1..seeds).map { greedyScore(level, it * 7919L) }
                Triple(level.id, scores.count { it >= level.target }, scores.average())
            }
        report.forEach { (id, wins, avg) -> println("level $id: $wins/$seeds passed, avg score ${"%.0f".format(avg)}") }
        report.forEach { (id, wins, _) -> assertTrue("level $id passed only $wins/$seeds", wins >= seeds * 6 / 10) }
    }
}

package com.sheen.puzzler.engine

import com.sheen.puzzler.level.Bots
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import kotlin.random.Random

class EndlessTest {
    @Test
    fun `stages every 500 points add colors, then obstacles`() {
        assertEquals(1, Endless.stage(0))
        assertEquals(1, Endless.stage(499))
        assertEquals(2, Endless.stage(500))
        assertEquals(listOf(4, 5, 6, 6, 6), (1..5).map(Endless::colors))
        assertEquals(0.0, Endless.obstacleRate(3), 0.0)
        assertTrue(Endless.obstacleRate(4) > 0.0)
        assertTrue(Endless.obstacleRate(8) > Endless.obstacleRate(5))
    }

    @Test
    fun `a run starts with four colors and no limit`() {
        val engine = Endless.newEngine(Random(1))
        assertEquals(4, engine.board.colors)
        assertTrue(engine.canMove)
        assertTrue(engine.board.goals().cleared)
        val colors =
            engine.board
                .positions()
                .mapNotNull { engine.board.colorAt(it) }
                .toSet()
        assertTrue(colors.all { it < 4 })
    }

    @Test
    fun `colors grow with the stage after a move`() {
        val engine = GameEngine(Board.fromLayout(List(8) { "........" }, 4, Random(3)), UNLIMITED, endless = true, score = 1_000)
        val (a, b) = MoveFinder.findMove(engine.board)!!
        engine.swap(a, b)
        assertEquals(6, engine.board.colors)
    }

    @Test
    fun `obstacles lock items or turn them into stones, without making a match`() {
        var added = 0
        repeat(100) { seed ->
            val board = testBoard(seed = seed)
            val before = board.goals().total
            val n = Endless.addObstacles(board, stage = 8)
            added += n
            assertEquals(before + n, board.goals().total)
            assertFalse(MatchFinder.hasMatch(board))
        }
        // About obstacleRate(8) per move on average.
        assertEquals(Endless.obstacleRate(8) * 100, added.toDouble(), 20.0)
    }

    @Test
    fun `the same seed adds the same obstacles`() {
        val a = testBoard(seed = 6).also { Endless.addObstacles(it, stage = 9) }
        val b = testBoard(seed = 6).also { Endless.addObstacles(it, stage = 9) }
        assertEquals(a.snapshot(), b.snapshot())
    }

    @Test
    fun `obstacles never appear before stage 4`() {
        repeat(100) { seed -> assertEquals(0, Endless.addObstacles(testBoard(seed = seed), stage = 3)) }
    }

    @Test
    fun `a board that no shuffle can fix jams the run`() {
        // Only two movable pieces left: no swap of them can ever line up three.
        val board = Board.fromPieces(listOf("SSS", "S01", "SSS"), colors = 4, random = Random(2))
        assertFalse(Shuffler.shuffle(board))
        val engine = GameEngine(board, UNLIMITED, endless = true)
        assertTrue(engine.jammed)
        assertFalse(engine.canMove)
        assertFalse(engine.swap(Pos(1, 1), Pos(1, 2)).valid)
    }

    @Test
    fun `a restored run keeps its score and moves`() {
        val engine = GameEngine(testBoard(seed = 4), UNLIMITED, endless = true, score = 1234, movesMade = 77)
        assertEquals(1234, engine.score)
        assertEquals(77, engine.movesMade)
        assertEquals(3, engine.stage)
    }

    /**
     * Prints the stage greedy runs reach before jamming (docs/SPEC.md §4: median around stage 8–10, rarely before 6).
     * Slow, so it only runs when asked: `PUZZLER_TUNE=1 ./gradlew :app:testDebugUnitTest --tests '*EndlessTest*' -i`.
     */
    @Test
    fun `tuning - stage greedy runs jam at`() {
        assumeTrue(System.getenv("PUZZLER_TUNE") != null)
        val runs = (System.getenv("PUZZLER_TUNE_SEEDS") ?: "20").toInt()
        val stages =
            (1..runs)
                .map { seed ->
                    val engine = Endless.newEngine(Random(seed * 31L))
                    val rng = Random(seed.toLong())
                    while (engine.canMove && engine.movesMade < MAX_TUNING_MOVES) {
                        val (a, b) = Bots.greedyMove(engine, rng) ?: break
                        engine.swap(a, b)
                    }
                    println(
                        "run $seed: stage ${engine.stage}, ${engine.score} points in ${engine.movesMade} moves, jammed=${engine.jammed}",
                    )
                    engine.stage
                }.sorted()
        println("jam stage p10=${Bots.percentile(stages, 0.1)} median=${Bots.percentile(stages, 0.5)} p90=${Bots.percentile(stages, 0.9)}")
    }

    private companion object {
        const val MAX_TUNING_MOVES = 3_000
    }
}

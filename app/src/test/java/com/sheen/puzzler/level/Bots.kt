package com.sheen.puzzler.level

import com.sheen.puzzler.engine.Board
import com.sheen.puzzler.engine.GameEngine
import com.sheen.puzzler.engine.MoveFinder
import com.sheen.puzzler.engine.Pos
import com.sheen.puzzler.engine.UNLIMITED
import kotlin.random.Random

/** Simulated players used to check and tune level balance (docs/SPEC.md §3 and §4). */
object Bots {
    /** Points a greedy player values each cleared goal at, on top of the move's score. */
    private const val GOAL_WEIGHT = 30

    /** One greedy game: the move made at which the level was passed (null if never), and the score after each move. */
    class Game(
        val passedAt: Int?,
        val scores: List<Int>,
    ) {
        /** Score after [moves] moves (or the last score, if the game stopped earlier). */
        fun scoreAfter(moves: Int) = scores.getOrElse(moves - 1) { scores.lastOrNull() ?: 0 }
    }

    /**
     * The move that's worth most right now: its score plus [GOAL_WEIGHT] per goal it clears, each candidate simulated
     * on a copy of the board so [engine] is untouched.
     */
    fun greedyMove(
        engine: GameEngine,
        rng: Random,
    ): Pair<Pos, Pos>? =
        MoveFinder.allMoves(engine.board).maxByOrNull { (a, b) ->
            val sim = GameEngine(engine.board.copy(Random(rng.nextLong())), 1)
            val goalsBefore = sim.board.goals().total
            val delta = sim.swap(a, b).scoreDelta
            delta + (GOAL_WEIGHT * (goalsBefore - sim.board.goals().total))
        }

    /**
     * Plays [level]'s layout greedily with no move limit, for up to [maxMoves] moves (stopping early once passed if
     * [stopWhenPassed]). The board and refills come from [seed], so different seeds sample different games.
     */
    fun greedyGame(
        level: LevelDef,
        seed: Long,
        maxMoves: Int,
        stopWhenPassed: Boolean,
    ): Game {
        val engine = GameEngine(Board.fromLayout(level.layout, level.colors, Random(seed)), UNLIMITED)
        val rng = Random(seed)
        val scores = mutableListOf<Int>()
        var passedAt: Int? = null
        while (scores.size < maxMoves) {
            val (a, b) = greedyMove(engine, rng) ?: break
            engine.swap(a, b)
            scores += engine.score
            if (passedAt == null && level.passed(engine.score, engine.board.goals().total)) {
                passedAt = scores.size
                if (stopWhenPassed) break
            }
        }
        return Game(passedAt, scores)
    }

    /** Value at fraction [p] (0..1) of the sorted list. */
    fun percentile(
        sorted: List<Int>,
        p: Double,
    ): Int = sorted[((sorted.size - 1) * p).toInt()]
}

package com.sheen.puzzler.engine

import kotlin.random.Random

/**
 * Endless mode's difficulty ramp (docs/SPEC.md §4): the score sets the stage, the stage sets how many colors new
 * pieces use and how often an item turns into an obstacle after a move.
 */
object Endless {
    const val STAGE_POINTS = 500
    const val START_COLORS = 4

    /** First stage where obstacles appear. */
    const val OBSTACLE_STAGE = 4

    /**
     * Obstacles added per move on [OBSTACLE_STAGE] on average, and how much that grows each stage after; past 1, some
     * moves add several. Tuned with EndlessTest so greedy runs jam around stage 8–10.
     */
    const val BASE_RATE = 0.6
    const val RATE_STEP = 0.5

    const val SIZE = 8

    /** A fresh run: a full 8×8 board with [START_COLORS] colors and no limits. */
    fun newEngine(random: Random) =
        GameEngine(Board.fromLayout(List(SIZE) { ".".repeat(SIZE) }, START_COLORS, random), UNLIMITED, endless = true)

    fun stage(score: Int) = (score / STAGE_POINTS) + 1

    fun colors(stage: Int) = (START_COLORS + stage - 1).coerceAtMost(Board.MAX_COLORS)

    /** Average obstacles added per move at [stage]. */
    fun obstacleRate(stage: Int): Double = if (stage < OBSTACLE_STAGE) 0.0 else BASE_RATE + (RATE_STEP * (stage - OBSTACLE_STAGE))

    /**
     * After a move settles: turns random normal items into locked items or 1-hit stones, [obstacleRate] of them on
     * average. Returns how many it added. Never touches specials, and never makes a match (locking keeps colors; a
     * stone only removes a piece).
     */
    fun addObstacles(
        board: Board,
        stage: Int,
    ): Int {
        val rate = obstacleRate(stage)
        val count = rate.toInt() + if (board.random.nextDouble() < rate - rate.toInt()) 1 else 0
        var added = 0
        repeat(count) {
            val candidates =
                board
                    .positions()
                    .filter { board[it].isMovable && board[it].piece!!.special == Special.NONE }
                    .toList()
            if (candidates.isEmpty()) return added
            val pos = candidates[board.random.nextInt(candidates.size)]
            board[pos] =
                if (board.random.nextBoolean()) {
                    board[pos].copy(locked = true)
                } else {
                    Cell(stoneHp = 1)
                }
            added++
        }
        return added
    }
}

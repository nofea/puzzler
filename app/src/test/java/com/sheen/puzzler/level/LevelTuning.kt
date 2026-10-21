package com.sheen.puzzler.level

import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Prints suggested move pars, move limits and star thresholds for every level from many greedy games
 * (docs/SPEC.md §3, Balance). Slow, so it only runs when asked:
 * `PUZZLER_TUNE=1 ./gradlew :app:testDebugUnitTest --tests '*LevelTuning*' -i`.
 */
class LevelTuning {
    private val levels = LevelParser.parse(File("src/main/assets/levels.json").readText())

    @Test
    fun report() {
        assumeTrue(System.getenv("PUZZLER_TUNE") != null)
        val seeds = (System.getenv("PUZZLER_TUNE_SEEDS") ?: "40").toInt()
        val only =
            System
                .getenv("PUZZLER_TUNE_LEVELS")
                ?.split(",")
                ?.map { it.trim().toInt() }
                ?.toSet()
        for (level in levels) {
            if (only != null && level.id !in only) continue
            val games = (1..seeds).map { Bots.greedyGame(level, it * 7919L, MAX_MOVES, stopWhenPassed = false) }
            val passMoves = games.mapNotNull { it.passedAt }.sorted()
            val failed = games.size - passMoves.size
            val line = StringBuilder("level ${level.id}: never passed $failed/$seeds")
            if (passMoves.isNotEmpty()) {
                line.append(", moves to pass p25=${Bots.percentile(passMoves, 0.25)} p50=${Bots.percentile(passMoves, 0.5)}")
                line.append(" p70=${Bots.percentile(passMoves, 0.7)} p90=${Bots.percentile(passMoves, 0.9)} max=${passMoves.last()}")
            }
            if (level.limited) {
                // The limit that gives this level's target pass rate, then score stars among the games passed by then.
                val rate = targetPassRate(level.id)
                val limit = if (passMoves.isEmpty()) level.moves else Bots.percentile(passMoves, rate)
                val passing = games.filter { (it.passedAt ?: Int.MAX_VALUE) <= limit }
                val scores = passing.map { it.scoreAfter(limit) }.sorted()
                line.append("; target ${"%.0f".format(rate * 100)}% -> moves $limit (passed ${passing.size}/$seeds)")
                if (scores.isNotEmpty()) {
                    line.append(", suggest star2=${Bots.percentile(scores, 0.3)} star3=${Bots.percentile(scores, 0.75)}")
                }
            } else if (passMoves.isNotEmpty()) {
                val par3 = maxOf(Bots.percentile(passMoves, 0.25), MIN_PAR3)
                val par2 = maxOf(Bots.percentile(passMoves, 0.7), par3 + MIN_PAR_GAP)
                line.append("; suggest par3=$par3 par2=$par2")
            }
            println(line)
        }
    }

    private companion object {
        const val MAX_MOVES = 120

        /** Move pars never go below these, so ★★★ isn't down to a lucky first move. */
        const val MIN_PAR3 = 3
        const val MIN_PAR_GAP = 2

        /** About 90% of greedy games pass L11, falling steadily to about 50% on L20. */
        fun targetPassRate(id: Int) = 0.9 - (id - 11) * 0.4 / 9
    }
}

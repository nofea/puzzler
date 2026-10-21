package com.sheen.puzzler.level

import com.sheen.puzzler.engine.Board
import com.sheen.puzzler.engine.GameEngine
import com.sheen.puzzler.engine.UNLIMITED
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.random.Random

/**
 * One campaign level as defined in assets/levels.json (docs/SPEC.md §3). [moves] = 0 means no move limit and
 * [timeSeconds] = 0 no timer.
 *
 * Levels without a move limit earn stars by moves used: ★★ within [par2] moves, ★★★ within [par3]. Levels with
 * one earn them by score: [star2] and [star3].
 */
@Serializable
data class LevelDef(
    val id: Int,
    val colors: Int,
    val moves: Int = UNLIMITED,
    val timeSeconds: Int = 0,
    val target: Int = PASS_SCORE,
    val star2: Int = 0,
    val star3: Int = 0,
    val par2: Int = 0,
    val par3: Int = 0,
    val seed: Long,
    val layout: List<String>,
) {
    val timed get() = timeSeconds > 0

    /** True if the level has a move limit, so it can be lost and stars come from the score. */
    val limited get() = moves != UNLIMITED

    /** Passed: every goal cleared and the score at the target. */
    fun passed(
        score: Int,
        goalsLeft: Int,
    ) = score >= target && goalsLeft == 0

    /** Stars for a result: none unless passed, then by moves used or by score depending on the tier. */
    fun stars(
        score: Int,
        movesUsed: Int,
        goalsLeft: Int,
    ): Int =
        when {
            !passed(score, goalsLeft) -> 0
            limited -> 1 + listOf(star2, star3).count { score >= it }
            else -> 1 + listOf(par2, par3).count { movesUsed <= it }
        }

    fun newEngine(seed: Long = this.seed) = GameEngine(Board.fromLayout(layout, colors, Random(seed)), moves)

    companion object {
        const val PASS_SCORE = 100
    }
}

@Serializable
data class LevelPack(
    val levels: List<LevelDef>,
)

object LevelParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(text: String): List<LevelDef> {
        val levels = json.decodeFromString<LevelPack>(text).levels.sortedBy { it.id }
        levels.forEach { level ->
            require(level.layout.isNotEmpty() && level.layout.all { it.length == level.layout.first().length }) {
                "Level ${level.id}: layout rows must be equal length"
            }
            if (level.limited) {
                require(level.star2 >= level.target && level.star3 >= level.star2) { "Level ${level.id}: bad star thresholds" }
            } else {
                require(level.par2 > 0 && level.par3 in 1..level.par2 && !level.timed) { "Level ${level.id}: bad move pars" }
            }
        }
        return levels
    }
}

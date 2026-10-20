package com.sheen.puzzler.level

import com.sheen.puzzler.engine.Board
import com.sheen.puzzler.engine.GameEngine
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.random.Random

/** One level as defined in assets/levels.json. [timeSeconds] = 0 means no timer. */
@Serializable
data class LevelDef(
    val id: Int,
    val colors: Int,
    val moves: Int,
    val timeSeconds: Int = 0,
    val target: Int = PASS_SCORE,
    val star2: Int,
    val star3: Int,
    val seed: Long,
    val layout: List<String>,
) {
    val timed get() = timeSeconds > 0

    fun stars(score: Int): Int = when {
        score >= star3 -> 3
        score >= star2 -> 2
        score >= target -> 1
        else -> 0
    }

    fun newEngine(seed: Long = this.seed) =
        GameEngine(Board.fromLayout(layout, colors, Random(seed)), moves)

    companion object {
        const val PASS_SCORE = 100
    }
}

@Serializable
data class LevelPack(val levels: List<LevelDef>)

object LevelParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(text: String): List<LevelDef> {
        val levels = json.decodeFromString<LevelPack>(text).levels.sortedBy { it.id }
        levels.forEach { level ->
            require(level.layout.isNotEmpty() && level.layout.all { it.length == level.layout.first().length }) {
                "Level ${level.id}: layout rows must be equal length"
            }
            require(level.star2 >= level.target && level.star3 >= level.star2) { "Level ${level.id}: bad star thresholds" }
        }
        return levels
    }
}

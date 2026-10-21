package com.sheen.puzzler.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** One campaign win in the recent-wins list. [at] is epoch milliseconds. */
@Serializable
data class CampaignWin(
    val level: Int,
    val score: Int,
    val moves: Int,
    val stars: Int,
    val at: Long,
)

/** One ended Endless run in the recent-runs list: [jammed], or ended by New run. [at] is epoch milliseconds. */
@Serializable
data class EndlessRunResult(
    val score: Int,
    val moves: Int,
    val stage: Int,
    val jammed: Boolean,
    val at: Long,
)

/** Rules and encoding for the records kept per mode (docs/SPEC.md §5). Pure Kotlin, so it's unit-tested on the JVM. */
object Records {
    /** Recent wins and recent runs keep this many entries, newest last. */
    const val HISTORY_SIZE = 20

    /** A run counts towards best points per move only once it has lasted this many moves. */
    const val PPM_MIN_MOVES = 50

    /** Clearing this campaign level unlocks Endless. */
    const val ENDLESS_UNLOCK_LEVEL = 10

    private val json = Json { ignoreUnknownKeys = true }

    fun <T> capped(list: List<T>) = list.takeLast(HISTORY_SIZE)

    /** Points per move for a run, or null while it's too short to count. */
    fun pointsPerMove(
        score: Int,
        moves: Int,
    ): Double? = if (moves < PPM_MIN_MOVES) null else score.toDouble() / moves

    fun encodeCampaign(list: List<CampaignWin>): String = json.encodeToString(list)

    fun decodeCampaign(text: String?): List<CampaignWin> =
        text?.let { runCatching { json.decodeFromString<List<CampaignWin>>(it) }.getOrNull() }.orEmpty()

    fun encodeEndless(list: List<EndlessRunResult>): String = json.encodeToString(list)

    fun decodeEndless(text: String?): List<EndlessRunResult> =
        text?.let { runCatching { json.decodeFromString<List<EndlessRunResult>>(it) }.getOrNull() }.orEmpty()
}

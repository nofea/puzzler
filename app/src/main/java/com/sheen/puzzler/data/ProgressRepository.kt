package com.sheen.puzzler.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

internal val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "progress")

/** A campaign level's records. [fewestMoves] is 0 until the level is cleared. */
data class LevelRecord(
    val bestScore: Int = 0,
    val fewestMoves: Int = 0,
    val stars: Int = 0,
) {
    val cleared get() = stars > 0
}

/** Endless records (docs/SPEC.md §5). [bestPointsPerMove] is null until a run has lasted [Records.PPM_MIN_MOVES] moves. */
data class EndlessRecords(
    val bestScore: Int = 0,
    val bestScoreMoves: Int = 0,
    val bestPointsPerMove: Double? = null,
    val lifetimePoints: Long = 0,
    val history: List<EndlessRunResult> = emptyList(),
)

/** Everything saved on the device. Campaign and Endless records are kept apart. */
data class Progress(
    val highestUnlocked: Int = 1,
    val levels: Map<Int, LevelRecord> = emptyMap(),
    val campaignHistory: List<CampaignWin> = emptyList(),
    val endless: EndlessRecords = EndlessRecords(),
    val hasSavedRun: Boolean = false,
    val muted: Boolean = false,
    val haptics: Boolean = true,
    val tutorialsSeen: Set<Int> = emptySet(),
    val finaleSeen: Boolean = false,
) {
    fun isUnlocked(id: Int) = id <= highestUnlocked

    fun of(id: Int) = levels[id] ?: LevelRecord()

    /** Sum of every level's best score (docs/SPEC.md §3). */
    val campaignPoints get() = levels.values.sumOf { it.bestScore }

    val totalStars get() = levels.values.sumOf { it.stars }

    val endlessUnlocked get() = of(Records.ENDLESS_UNLOCK_LEVEL).cleared
}

class ProgressRepository(
    private val context: Context,
    private val levelCount: Int,
) {
    private val schemaKey = intPreferencesKey("schema")
    private val mutedKey = booleanPreferencesKey("muted")
    private val hapticsKey = booleanPreferencesKey("haptics")

    // Campaign.
    private val unlockedKey = intPreferencesKey("highest_unlocked")
    private val finaleKey = booleanPreferencesKey("finale_seen")
    private val campaignHistoryKey = stringPreferencesKey("campaign_history")

    private fun bestKey(id: Int) = intPreferencesKey("best_$id")

    private fun fewestKey(id: Int) = intPreferencesKey("fewest_$id")

    private fun starsKey(id: Int) = intPreferencesKey("stars_$id")

    private fun tutorialKey(id: Int) = booleanPreferencesKey("tutorial_seen_$id")

    // Endless.
    private val runKey = stringPreferencesKey("endless_run")
    private val endlessBestKey = intPreferencesKey("endless_best")
    private val endlessBestMovesKey = intPreferencesKey("endless_best_moves")
    private val endlessPpmKey = doublePreferencesKey("endless_best_ppm")
    private val endlessLifetimeKey = longPreferencesKey("endless_lifetime")
    private val endlessHistoryKey = stringPreferencesKey("endless_history")

    val progress: Flow<Progress> =
        context.dataStore.data.map { prefs ->
            Progress(
                highestUnlocked = prefs[unlockedKey] ?: 1,
                levels =
                    (1..levelCount).associateWith {
                        LevelRecord(prefs[bestKey(it)] ?: 0, prefs[fewestKey(it)] ?: 0, prefs[starsKey(it)] ?: 0)
                    },
                campaignHistory = Records.decodeCampaign(prefs[campaignHistoryKey]),
                endless =
                    EndlessRecords(
                        bestScore = prefs[endlessBestKey] ?: 0,
                        bestScoreMoves = prefs[endlessBestMovesKey] ?: 0,
                        bestPointsPerMove = prefs[endlessPpmKey],
                        lifetimePoints = prefs[endlessLifetimeKey] ?: 0,
                        history = Records.decodeEndless(prefs[endlessHistoryKey]),
                    ),
                hasSavedRun = prefs[runKey] != null,
                muted = prefs[mutedKey] ?: false,
                haptics = prefs[hapticsKey] ?: true,
                tutorialsSeen = (1..levelCount).filterTo(HashSet()) { prefs[tutorialKey(it)] == true },
                finaleSeen = prefs[finaleKey] ?: false,
            )
        }

    /**
     * Progress saved by an older version (different levels and rules) is wiped once, on the first launch after
     * updating (docs/SPEC.md §5).
     */
    suspend fun migrate() {
        context.dataStore.edit { prefs ->
            if (prefs[schemaKey] != SCHEMA) {
                prefs.clear()
                prefs[schemaKey] = SCHEMA
            }
        }
    }

    /** Records a campaign win: best score, fewest moves and stars are kept per level, and the next level unlocks. */
    suspend fun recordWin(
        id: Int,
        score: Int,
        moves: Int,
        stars: Int,
        at: Long = System.currentTimeMillis(),
    ) {
        context.dataStore.edit { prefs ->
            prefs[bestKey(id)] = maxOf(prefs[bestKey(id)] ?: 0, score)
            prefs[fewestKey(id)] = (prefs[fewestKey(id)] ?: 0).takeIf { it > 0 }?.let { minOf(it, moves) } ?: moves
            prefs[starsKey(id)] = maxOf(prefs[starsKey(id)] ?: 0, stars)
            val next = (id + 1).coerceAtMost(levelCount)
            prefs[unlockedKey] = maxOf(prefs[unlockedKey] ?: 1, next)
            val history = Records.decodeCampaign(prefs[campaignHistoryKey])
            prefs[campaignHistoryKey] = Records.encodeCampaign(Records.capped(history + CampaignWin(id, score, moves, stars, at)))
        }
    }

    /** The saved Endless run, if any. */
    suspend fun loadRun(): SavedRun? =
        context.dataStore.data
            .first()[runKey]
            ?.let(SavedRun::decode)

    /**
     * After an Endless move: saves the run and updates the records live (docs/SPEC.md §4). [scoreDelta] is what the
     * move scored, added to lifetime points.
     */
    suspend fun saveRun(
        run: SavedRun,
        scoreDelta: Int,
    ) {
        context.dataStore.edit { prefs ->
            prefs[runKey] = run.encode()
            prefs[endlessLifetimeKey] = (prefs[endlessLifetimeKey] ?: 0) + scoreDelta
            if (run.score > (prefs[endlessBestKey] ?: 0)) {
                prefs[endlessBestKey] = run.score
                prefs[endlessBestMovesKey] = run.movesMade
            }
            Records.pointsPerMove(run.score, run.movesMade)?.let { ppm ->
                if (ppm > (prefs[endlessPpmKey] ?: 0.0)) prefs[endlessPpmKey] = ppm
            }
        }
    }

    /** Ends the current Endless run (it jammed, or the player started a new one) and adds it to the recent runs. */
    suspend fun endRun(result: EndlessRunResult) {
        context.dataStore.edit { prefs ->
            prefs.remove(runKey)
            val history = Records.decodeEndless(prefs[endlessHistoryKey])
            prefs[endlessHistoryKey] = Records.encodeEndless(Records.capped(history + result))
        }
    }

    suspend fun setMuted(muted: Boolean) {
        context.dataStore.edit { it[mutedKey] = muted }
    }

    suspend fun setHaptics(enabled: Boolean) {
        context.dataStore.edit { it[hapticsKey] = enabled }
    }

    /** Remembers that a level's in-level tutorial was finished or skipped. [resetCampaign] brings it back. */
    suspend fun markTutorialSeen(id: Int) {
        context.dataStore.edit { it[tutorialKey(id)] = true }
    }

    suspend fun markFinaleSeen() {
        context.dataStore.edit { it[finaleKey] = true }
    }

    /** Locks every level but the first and clears the campaign records, tutorials and finale. Endless keeps its records. */
    suspend fun resetCampaign() {
        context.dataStore.edit { prefs ->
            prefs.removeAll(listOf(unlockedKey, finaleKey, campaignHistoryKey))
            for (id in 1..levelCount) prefs.removeAll(listOf(bestKey(id), fewestKey(id), starsKey(id), tutorialKey(id)))
        }
    }

    /** Clears the current Endless run and the Endless records. */
    suspend fun resetEndless() {
        context.dataStore.edit { prefs ->
            prefs.removeAll(listOf(runKey, endlessBestKey, endlessBestMovesKey, endlessPpmKey, endlessLifetimeKey, endlessHistoryKey))
        }
    }

    /** Wipes everything, settings included. */
    suspend fun reset() {
        context.dataStore.edit {
            it.clear()
            it[schemaKey] = SCHEMA
        }
    }

    private fun MutablePreferences.removeAll(keys: List<Preferences.Key<*>>) = keys.forEach { remove(it) }

    private companion object {
        /** Bump when saved progress can't carry over to a new version of the levels or rules. */
        const val SCHEMA = 2
    }
}

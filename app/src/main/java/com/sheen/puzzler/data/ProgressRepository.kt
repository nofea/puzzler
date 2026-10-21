package com.sheen.puzzler.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "progress")

data class LevelProgress(val bestScore: Int = 0, val stars: Int = 0)

data class Progress(
    val highestUnlocked: Int = 1,
    val levels: Map<Int, LevelProgress> = emptyMap(),
    val muted: Boolean = false,
    val haptics: Boolean = true,
    val tutorialsSeen: Set<Int> = emptySet(),
) {
    fun isUnlocked(id: Int) = id <= highestUnlocked
    fun of(id: Int) = levels[id] ?: LevelProgress()
}

class ProgressRepository(private val context: Context, private val levelCount: Int) {

    private val unlockedKey = intPreferencesKey("highest_unlocked")
    private val mutedKey = booleanPreferencesKey("muted")
    private val hapticsKey = booleanPreferencesKey("haptics")
    private fun bestKey(id: Int) = intPreferencesKey("best_$id")
    private fun starsKey(id: Int) = intPreferencesKey("stars_$id")
    private fun tutorialKey(id: Int) = booleanPreferencesKey("tutorial_seen_$id")

    val progress: Flow<Progress> = context.dataStore.data.map { prefs ->
        Progress(
            highestUnlocked = prefs[unlockedKey] ?: 1,
            levels = (1..levelCount).associateWith { LevelProgress(prefs[bestKey(it)] ?: 0, prefs[starsKey(it)] ?: 0) },
            muted = prefs[mutedKey] ?: false,
            haptics = prefs[hapticsKey] ?: true,
            tutorialsSeen = (1..levelCount).filterTo(HashSet()) { prefs[tutorialKey(it)] == true },
        )
    }

    /** Records a cleared level: keeps the best score and stars, and unlocks the next level. */
    suspend fun recordWin(id: Int, score: Int, stars: Int) {
        context.dataStore.edit { prefs ->
            prefs[bestKey(id)] = maxOf(prefs[bestKey(id)] ?: 0, score)
            prefs[starsKey(id)] = maxOf(prefs[starsKey(id)] ?: 0, stars)
            val next = (id + 1).coerceAtMost(levelCount)
            prefs[unlockedKey] = maxOf(prefs[unlockedKey] ?: 1, next)
        }
    }

    suspend fun setMuted(muted: Boolean) {
        context.dataStore.edit { it[mutedKey] = muted }
    }

    suspend fun setHaptics(enabled: Boolean) {
        context.dataStore.edit { it[hapticsKey] = enabled }
    }

    /** Remembers that a level's in-level tutorial was finished or skipped. [reset] brings it back. */
    suspend fun markTutorialSeen(id: Int) {
        context.dataStore.edit { it[tutorialKey(id)] = true }
    }

    suspend fun reset() {
        context.dataStore.edit { it.clear() }
    }
}

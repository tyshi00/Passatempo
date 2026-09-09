package com.thelightphone.games.blackjack

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.first

/**
 * Lifetime hand tally for Blackjack - wins, losses, and pushes. Persisted across sessions
 * (not just the current visit), since a "scorekeeper" that resets every time you leave the
 * screen isn't really keeping score.
 */
class BlackjackStatsStore(private val dataStore: DataStore<Preferences>) {

    data class Stats(val wins: Int = 0, val losses: Int = 0, val pushes: Int = 0)

    private val winsKey = intPreferencesKey("blackjack_wins")
    private val lossesKey = intPreferencesKey("blackjack_losses")
    private val pushesKey = intPreferencesKey("blackjack_pushes")

    suspend fun load(): Stats {
        val prefs = dataStore.data.first()
        return Stats(
            wins = prefs[winsKey] ?: 0,
            losses = prefs[lossesKey] ?: 0,
            pushes = prefs[pushesKey] ?: 0,
        )
    }

    suspend fun recordWin(): Stats = increment(winsKey)
    suspend fun recordLoss(): Stats = increment(lossesKey)
    suspend fun recordPush(): Stats = increment(pushesKey)

    private suspend fun increment(key: Preferences.Key<Int>): Stats {
        dataStore.edit { prefs -> prefs[key] = (prefs[key] ?: 0) + 1 }
        return load()
    }
}

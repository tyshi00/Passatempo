package com.thelightphone.games.blackjack

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/**
 * Today's hand tally for Blackjack - wins, losses, and pushes. Resets automatically the
 * first time it's touched on a new calendar day, the same convention DailyLimitStore and
 * DailyPlaytimeStore already use, so it stays in step with the daily budget instead of
 * growing indefinitely and crowding the screen.
 */
class BlackjackStatsStore(private val dataStore: DataStore<Preferences>) {

    data class Stats(val wins: Int = 0, val losses: Int = 0, val pushes: Int = 0)

    private val winsKey = intPreferencesKey("blackjack_wins")
    private val lossesKey = intPreferencesKey("blackjack_losses")
    private val pushesKey = intPreferencesKey("blackjack_pushes")
    private val dateKey = stringPreferencesKey("blackjack_stats_date")

    private fun todayString(): String = LocalDate.now().toString()

    /** Today's tally, or all zeros if nothing has been recorded yet today. */
    suspend fun load(): Stats {
        val prefs = dataStore.data.first()
        if (prefs[dateKey] != todayString()) return Stats()
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
        dataStore.edit { prefs ->
            val today = todayString()
            if (prefs[dateKey] != today) {
                // New day - the old counts don't apply anymore, so clear them before
                // applying this increment rather than adding onto yesterday's total.
                prefs[winsKey] = 0
                prefs[lossesKey] = 0
                prefs[pushesKey] = 0
                prefs[dateKey] = today
            }
            prefs[key] = (prefs[key] ?: 0) + 1
        }
        return load()
    }
}

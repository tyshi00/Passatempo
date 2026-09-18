package com.thelightphone.games

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.first

enum class PaddleControlMode { TAP, DRAG }

/**
 * Shared paddle control scheme for Brick Breaker and Pong - one setting controls both
 * games, since they use the same tap-nudge/drag-to-position model, rather than each
 * game getting its own separate toggle.
 */
class ControlModeStore(private val dataStore: DataStore<Preferences>) {

    private val dragModeKey = booleanPreferencesKey("paddle_control_drag")

    suspend fun get(): PaddleControlMode {
        val isDrag = dataStore.data.first()[dragModeKey] ?: false
        return if (isDrag) PaddleControlMode.DRAG else PaddleControlMode.TAP
    }

    /** Flips TAP <-> DRAG and returns the new value. */
    suspend fun toggle(): PaddleControlMode {
        var result = PaddleControlMode.TAP
        dataStore.edit { prefs ->
            val newIsDrag = !(prefs[dragModeKey] ?: false)
            prefs[dragModeKey] = newIsDrag
            result = if (newIsDrag) PaddleControlMode.DRAG else PaddleControlMode.TAP
        }
        return result
    }
}

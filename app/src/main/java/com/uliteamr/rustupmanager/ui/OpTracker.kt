package com.uliteamr.rustupmanager.ui

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import com.uliteamr.rustupmanager.rustup.OpProgress

/**
 * Per-key [OpProgress] slots. Each key owns its own [MutableState], so a card that reads only
 * its key recomposes without dragging the rest of the screen with it.
 */
class OpTracker {
    private val states = mutableMapOf<String, MutableState<OpProgress?>>()

    fun state(key: String): MutableState<OpProgress?> = states.getOrPut(key) { mutableStateOf(null) }

    fun set(key: String, value: OpProgress?) {
        state(key).value = value
    }

    fun reset() {
        states.values.forEach { it.value = null }
    }
}

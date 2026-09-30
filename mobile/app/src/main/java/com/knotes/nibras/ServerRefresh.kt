package com.knotes.nibras

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * "Fetch these pages again" — one signal the server-backed screens all watch.
 *
 * The record browsers have always had a refresh control of their own, but the pages added since —
 * flash cards, highlights, contacts — had none, so the only way to pull new rows was to visit a
 * records page, refresh THAT, and come back. The rail now carries one button for all of them, and
 * this is what it pokes.
 *
 * A counter rather than a boolean: every bump is a distinct value, so a screen keyed on it re-runs
 * even when two refreshes land back to back. A flag would have to be reset by whoever read it,
 * which is a race when three screens are watching.
 */
object ServerRefresh {

    private val _tick = MutableStateFlow(0)
    val tick: StateFlow<Int> = _tick.asStateFlow()

    fun bump() {
        _tick.value = _tick.value + 1
    }
}

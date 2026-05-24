package com.github.appachan.rssreader.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object FlatPattern : NavKey

@Serializable data object AsyncPattern : NavKey

@Serializable data object ReducerPattern : NavKey

@Serializable data object OrbitPattern : NavKey

/**
 * The four UI state-management patterns surfaced in the bottom navigation. [key] is the Nav3
 * destination and [label] the technique name shown in the bar. Declaration order is tab order. The
 * bottom-navigation shell and the [NavDisplay] entry graph both derive from this single list, so
 * adding/removing a pattern is a one-line change.
 */
enum class PatternTab(val key: NavKey, val label: String) {
    Flat(FlatPattern, "Flat"),
    Async(AsyncPattern, "Async"),
    Reducer(ReducerPattern, "Reducer"),
    Orbit(OrbitPattern, "Orbit"),
}

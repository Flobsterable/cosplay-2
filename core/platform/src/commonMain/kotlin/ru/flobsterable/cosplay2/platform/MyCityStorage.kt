package ru.flobsterable.cosplay2.platform

import androidx.compose.runtime.Composable

// Uses the same atomic file implementation as favorites, in a separate file.
@Composable
expect fun rememberMyCityStorage(): FavoritesStorage

fun normalizeCity(city: String): String = city.trim().split(Regex("\\s+")).joinToString(" ")

fun matchesMyCity(eventCity: String, myCity: String): Boolean =
    normalizeCity(myCity).isNotEmpty() &&
        normalizeCity(eventCity).equals(normalizeCity(myCity), ignoreCase = true)

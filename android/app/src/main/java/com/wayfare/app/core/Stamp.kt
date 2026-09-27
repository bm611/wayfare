package com.wayfare.app.core

import java.time.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A trip's passport-stamp drawing, as stored in `trips.cover_art`. The server
 * has already validated and normalised every path to absolute M/L/C/Q/Z on a
 * 0 0 64 64 grid.
 */
@Serializable
data class StampArt(
    val v: Int = 1,
    val label: String = "",
    val paths: List<String> = emptyList(),
    val fallback: Boolean = false,
) {
    fun encode(): String = json.encodeToString(serializer(), this)

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun decode(value: String?): StampArt? =
            value?.let { runCatching { json.decodeFromString(serializer(), it) }.getOrNull() }
    }
}

enum class StampTint { Peach, Sky, Sage, Lilac }

/**
 * How a trip's stamp sits on its card. Ported from `stampStyle` in the web
 * `lib/stamp.ts`; `StampTest` pins the same fixtures as the web and iOS tests.
 */
data class StampStyle(val tint: StampTint, val tilt: Float, val arched: Boolean) {
    companion object {
        private val tilts = floatArrayOf(-3f, 2.5f, -1.5f)

        fun of(tripId: String): StampStyle {
            val sum = tripId.sumOf { it.code }
            return StampStyle(StampTint.entries[sum % 4], tilts[(sum / 4) % 3], (sum / 12) % 2 == 1)
        }
    }
}

private val stampMonths = listOf("JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC")

/** "SEP 2026" from a trip's start date; null for an undated trip. */
fun stampMonth(start: LocalDate?): String? = start?.let { "${stampMonths[it.monthValue - 1]} ${it.year}" }

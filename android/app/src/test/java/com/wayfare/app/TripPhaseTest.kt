package com.wayfare.app

import com.wayfare.app.core.Trip
import com.wayfare.app.core.TripPhase
import com.wayfare.app.core.tripPhase
import org.junit.Assert.assertEquals
import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Test

/** Day boundaries decide which section a trip lands in, so they get fixtures. */
class TripPhaseTest {
    private val today = LocalDate.of(2026, 9, 10)

    private fun trip(start: LocalDate?, end: LocalDate?) = Trip(
        id = "t", ownerId = "u", name = "Lisbon", destination = "Lisbon",
        startDate = start, endDate = end, budget = BigDecimal("1000"), currency = "EUR",
        accent = "clay", shareCode = null, coverPath = null, coverSubject = null,
        coverStatus = "idle", createdAt = "2026-09-01T00:00:00Z",
    )

    @Test fun `a trip with no dates is undated`() {
        assertEquals(TripPhase.Undated, tripPhase(trip(null, null), today))
    }

    @Test fun `the first day of a trip is day one, not day zero`() {
        val phase = tripPhase(trip(today, today.plusDays(4)), today)
        assertEquals(TripPhase.Active(day = 1, total = 5), phase)
    }

    @Test fun `the last day is still active`() {
        val phase = tripPhase(trip(today.minusDays(4), today), today)
        assertEquals(TripPhase.Active(day = 5, total = 5), phase)
    }

    @Test fun `the day after the return date is past`() {
        assertEquals(TripPhase.Past, tripPhase(trip(today.minusDays(9), today.minusDays(1)), today))
    }

    @Test fun `an upcoming trip counts the days until departure`() {
        assertEquals(TripPhase.Upcoming(days = 7), tripPhase(trip(today.plusDays(7), null), today))
    }

    @Test fun `an open-ended trip that has started is a single active day`() {
        assertEquals(TripPhase.Active(day = 1, total = 1), tripPhase(trip(today, null), today))
    }

    @Test fun `a trip with only a return date treats today as the start`() {
        assertEquals(TripPhase.Active(day = 1, total = 4), tripPhase(trip(null, today.plusDays(3)), today))
    }
}

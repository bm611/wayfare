package com.wayfare.app

import com.wayfare.app.core.Category
import com.wayfare.app.core.Expense
import com.wayfare.app.core.SyncState
import com.wayfare.app.core.Trip
import com.wayfare.app.core.dayTotals
import com.wayfare.app.core.routeCode
import com.wayfare.app.core.stampDate
import com.wayfare.app.core.tapeDayLabel
import com.wayfare.app.ui.applyKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Test

/** The furniture the Departure design adds: ticket stubs, tape rules, the keypad. */
class DepartureTest {
    private fun trip(name: String = "Lisbon & Porto", destination: String? = "Lisbon & Porto") = Trip(
        id = "t", ownerId = "u", name = name, destination = destination,
        startDate = null, endDate = null, budget = BigDecimal.ZERO, currency = "EUR",
        accent = "clay", shareCode = null, coverPath = null, coverSubject = null,
        coverStatus = "idle", createdAt = "2026-09-01T00:00:00Z",
    )

    private fun expense(amount: String, on: LocalDate, sync: SyncState = SyncState.Synced) = Expense(
        id = amount + on + sync, tripId = "t", userId = "u", title = "Dinner",
        amount = BigDecimal(amount), originalAmount = null, originalCurrency = null, fxRate = null,
        category = Category.Food, spentOn = on, note = null, createdAt = "2026-09-10T00:00:00Z",
        syncState = sync,
    )

    // MARK: route codes

    @Test fun `a two-part destination becomes a two-leg route`() {
        assertEquals("LIS → POR", routeCode(trip()))
        assertEquals("TOK → KYO", routeCode(trip(destination = "Tokyo and Kyoto")))
        assertEquals("ROM → FLO", routeCode(trip(destination = "Rome, Florence")))
    }

    @Test fun `a single destination is one stub`() {
        assertEquals("TOK", routeCode(trip(destination = "Tokyo")))
    }

    @Test fun `more than two places still fit on a ticket`() {
        assertEquals("BAR → MAD", routeCode(trip(destination = "Barcelona, Madrid, Seville")))
    }

    @Test fun `a blank destination falls back to the trip name`() {
        assertEquals("HON", routeCode(trip(name = "Honeymoon", destination = null)))
        assertEquals("HON", routeCode(trip(name = "Honeymoon", destination = "  ")))
    }

    @Test fun `punctuation and accents never reach the stub`() {
        assertEquals("SAO", routeCode(trip(destination = "São Paulo")))
        assertEquals("TRIP", routeCode(trip(name = "!!!", destination = "!!!")))
    }

    // MARK: tape furniture

    @Test fun `the tape rules its days in capitals`() {
        assertEquals("SATURDAY 12 SEPTEMBER", tapeDayLabel(LocalDate.of(2026, 9, 12)))
    }

    @Test fun `the header chip stamps a short date`() {
        assertEquals("12 SEP", stampDate(LocalDate.of(2026, 9, 12)))
        assertEquals("1 JAN", stampDate(LocalDate.of(2026, 1, 1)))
    }

    // MARK: day by day

    @Test fun `days are totalled newest first and scaled against the heaviest`() {
        val days = dayTotals(
            listOf(
                expense("100", LocalDate.of(2026, 9, 10)),
                expense("50", LocalDate.of(2026, 9, 10)),
                expense("75", LocalDate.of(2026, 9, 11)),
            ),
        )
        assertEquals(listOf(LocalDate.of(2026, 9, 11), LocalDate.of(2026, 9, 10)), days.map { it.date })
        assertEquals(BigDecimal("150"), days[1].amount)
        assertEquals(1f, days[1].share, 0.0001f)
        assertEquals(0.5f, days[0].share, 0.0001f)
    }

    @Test fun `a failed entry is left off the day chart, as it is off every total`() {
        val days = dayTotals(
            listOf(
                expense("100", LocalDate.of(2026, 9, 10)),
                expense("40", LocalDate.of(2026, 9, 11), SyncState.Failed),
            ),
        )
        assertEquals(1, days.size)
        assertEquals(LocalDate.of(2026, 9, 10), days[0].date)
    }

    @Test fun `no expenses means no bars rather than an empty one`() {
        assertTrue(dayTotals(emptyList()).isEmpty())
    }

    // MARK: the keypad

    @Test fun `digits append and a leading zero is replaced`() {
        assertEquals("3", applyKey("", "3"))
        assertEquals("38", applyKey("3", "8"))
        assertEquals("5", applyKey("0", "5"))
    }

    @Test fun `there is only ever one decimal point`() {
        assertEquals("38.", applyKey("38", "."))
        assertEquals("38.", applyKey("38.", "."))
        assertEquals("0.", applyKey("", "."))
    }

    @Test fun `cents stop at two places`() {
        assertEquals("38.5", applyKey("38.", "5"))
        assertEquals("38.50", applyKey("38.5", "0"))
        assertEquals("38.50", applyKey("38.50", "9"))
    }

    @Test fun `backspace walks back, including off the end`() {
        assertEquals("38.", applyKey("38.5", "⌫"))
        assertEquals("", applyKey("3", "⌫"))
        assertEquals("", applyKey("", "⌫"))
    }

    @Test fun `an amount cannot run away past a sensible length`() {
        assertEquals("123456789012", applyKey("123456789012", "3"))
    }
}

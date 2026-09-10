package com.wayfare.app

import com.wayfare.app.core.Category
import com.wayfare.app.core.Expense
import com.wayfare.app.core.SyncState
import com.wayfare.app.core.Trip
import com.wayfare.app.core.budgetSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Test

/** The figures on the budget strip. These are what a traveller acts on. */
class BudgetTest {
    private val today = LocalDate.of(2026, 9, 12)

    private fun trip(budget: String = "1000", start: LocalDate? = LocalDate.of(2026, 9, 10), end: LocalDate? = LocalDate.of(2026, 9, 14)) =
        Trip(
            id = "t", ownerId = "u", name = "Lisbon", destination = "Lisbon",
            startDate = start, endDate = end, budget = BigDecimal(budget), currency = "EUR",
            accent = "clay", shareCode = null, coverPath = null, coverSubject = null,
            coverStatus = "idle", createdAt = "2026-09-01T00:00:00Z",
        )

    private fun expense(amount: String, on: LocalDate = today, sync: SyncState = SyncState.Synced) = Expense(
        id = amount + on + sync, tripId = "t", userId = "u", title = "Dinner",
        amount = BigDecimal(amount), originalAmount = null, originalCurrency = null, fxRate = null,
        category = Category.Food, spentOn = on, note = null, createdAt = "2026-09-10T00:00:00Z",
        syncState = sync,
    )

    @Test fun `spend and remainder are plain arithmetic`() {
        val summary = budgetSummary(trip(), listOf(expense("120.50"), expense("79.50")), today)
        assertEquals(BigDecimal("200.00"), summary.spent)
        assertEquals(BigDecimal("800.00"), summary.remaining)
    }

    @Test fun `a failed entry is kept out of the total`() {
        val summary = budgetSummary(
            trip(),
            listOf(expense("100"), expense("50", sync = SyncState.Failed)),
            today,
        )
        assertEquals(BigDecimal("100.00"), summary.spent)
    }

    @Test fun `a pending entry still counts - it is expected to land`() {
        val summary = budgetSummary(
            trip(),
            listOf(expense("100"), expense("50", sync = SyncState.Pending)),
            today,
        )
        assertEquals(BigDecimal("150.00"), summary.spent)
    }

    @Test fun `days left includes today`() {
        // Sep 10 to Sep 14, standing on Sep 12: today plus two more.
        assertEquals(3L, budgetSummary(trip(), emptyList(), today).daysLeft)
    }

    @Test fun `the daily allowance spreads what is left over the days left`() {
        val summary = budgetSummary(trip(budget = "900"), listOf(expense("300")), today)
        assertEquals(BigDecimal("200.00"), summary.availablePerDay)
    }

    @Test fun `over budget offers zero a day, never a negative allowance`() {
        val summary = budgetSummary(trip(budget = "100"), listOf(expense("250")), today)
        assertEquals(BigDecimal("-150.00"), summary.remaining)
        assertEquals(BigDecimal("0.00"), summary.availablePerDay)
    }

    @Test fun `a trip with no budget has no daily allowance to offer`() {
        assertNull(budgetSummary(trip(budget = "0"), listOf(expense("40")), today).availablePerDay)
    }

    @Test fun `pre-trip bookings count toward the average once the trip starts`() {
        // Flights bought in August, standing on day three.
        val summary = budgetSummary(
            trip(),
            listOf(expense("600", on = LocalDate.of(2026, 8, 1)), expense("150")),
            today,
        )
        assertEquals(3L, summary.elapsedDays)
        assertEquals(BigDecimal("250.00"), summary.perDay)
    }

    @Test fun `an undated trip has no per-day figures`() {
        val summary = budgetSummary(trip(start = null, end = null), listOf(expense("80")), today)
        assertEquals(BigDecimal("80.00"), summary.spent)
        assertNull(summary.perDay)
        assertNull(summary.daysLeft)
        assertNull(summary.availablePerDay)
    }

    @Test fun `a finished trip averages over its whole length`() {
        val past = trip(start = LocalDate.of(2026, 8, 1), end = LocalDate.of(2026, 8, 10))
        val summary = budgetSummary(past, listOf(expense("500", on = LocalDate.of(2026, 8, 2))), today)
        assertEquals(10L, summary.elapsedDays)
        assertEquals(BigDecimal("50.00"), summary.perDay)
        assertNull(summary.daysLeft)
    }
}

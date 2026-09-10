package com.wayfare.app.core

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * The figures on a trip's budget strip, ported from the web app's TripDetail.
 *
 * @param remaining budget minus everything logged; negative once over budget.
 * @param elapsedDays days the trip has been running, counting today.
 * @param perDay average spend across [elapsedDays], or null before the trip starts.
 * @param daysLeft days still to come, counting today; null when the trip is not running.
 * @param availablePerDay what is left to spend each remaining day. Zero once over
 *   budget — the web app does not offer a negative daily allowance.
 */
data class BudgetSummary(
    val spent: BigDecimal,
    val remaining: BigDecimal,
    val elapsedDays: Long,
    val perDay: BigDecimal?,
    val daysLeft: Long?,
    val availablePerDay: BigDecimal?,
)

fun budgetSummary(
    trip: Trip,
    expenses: List<Expense>,
    today: LocalDate = LocalDate.now(),
): BudgetSummary {
    // A failed entry never reached the server, so it is not part of what the
    // trip has cost.
    val spent = expenses.filter { it.syncState != SyncState.Failed }
        .fold(BigDecimal.ZERO) { total, expense -> total + expense.amount }
        .moneyScale()
    val remaining = (trip.budget - spent).moneyScale()
    val phase = tripPhase(trip, today)

    val elapsed = when (phase) {
        is TripPhase.Active -> phase.day
        TripPhase.Past ->
            if (trip.startDate != null && trip.endDate != null) {
                ChronoUnit.DAYS.between(trip.startDate, trip.endDate) + 1
            } else {
                0
            }
        else -> 0
    }

    // Pre-trip bookings still count against the average — the flights were part
    // of the cost of going.
    val perDay = elapsed.takeIf { it > 0 }
        ?.let { spent.divide(BigDecimal.valueOf(it), 2, RoundingMode.HALF_UP) }

    val daysLeft = (phase as? TripPhase.Active)
        ?.takeIf { trip.endDate != null }
        ?.let { it.total - it.day + 1 }

    val availablePerDay = daysLeft
        ?.takeIf { it > 0 && trip.budget.signum() > 0 }
        ?.let { remaining.max(BigDecimal.ZERO).divide(BigDecimal.valueOf(it), 2, RoundingMode.HALF_UP) }

    return BudgetSummary(spent, remaining, elapsed, perDay, daysLeft, availablePerDay)
}

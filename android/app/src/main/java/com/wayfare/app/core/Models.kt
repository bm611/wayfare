package com.wayfare.app.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset

enum class Category(val wireName: String, val label: String) {
    Flights("flights", "Flights"),
    Stays("stays", "Stays"),
    Food("food", "Food"),
    Activities("activities", "Activities"),
    Transport("transport", "Transport"),
    Shopping("shopping", "Shopping"),
    Other("other", "Other");

    companion object {
        fun fromWire(value: String) = entries.firstOrNull { it.wireName == value } ?: Other
    }
}

enum class SyncState { Synced, Pending, Failed }

data class Trip(
    val id: String,
    val ownerId: String,
    val name: String,
    val destination: String?,
    val startDate: LocalDate?,
    val endDate: LocalDate?,
    val budget: BigDecimal,
    val currency: String,
    val accent: String,
    val shareCode: String?,
    val coverPath: String?,
    val coverSubject: String?,
    val coverStatus: String,
    val createdAt: String,
)

data class Expense(
    val id: String,
    val tripId: String,
    val userId: String,
    val title: String,
    val amount: BigDecimal,
    val originalAmount: BigDecimal?,
    val originalCurrency: String?,
    val fxRate: BigDecimal?,
    val category: Category,
    val spentOn: LocalDate,
    val note: String?,
    val createdAt: String,
    val syncState: SyncState = SyncState.Synced,
    val syncError: String? = null,
)

data class TripMember(
    val tripId: String,
    val userId: String,
    val role: String,
    val joinedAt: String,
    val displayName: String?,
    val isYou: Boolean,
)

data class TripSummary(val trip: Trip, val spent: BigDecimal, val entries: Int)

data class TripDraft(
    val name: String,
    val destination: String?,
    val startDate: LocalDate?,
    val endDate: LocalDate?,
    val budget: BigDecimal,
)

data class ExpenseDraft(
    val title: String,
    val amount: BigDecimal,
    val originalAmount: BigDecimal?,
    val originalCurrency: String?,
    val fxRate: BigDecimal?,
    val category: Category,
    val spentOn: LocalDate,
    val note: String?,
)

sealed interface TripPhase {
    data class Upcoming(val days: Long) : TripPhase
    data class Active(val day: Long, val total: Long) : TripPhase
    data object Past : TripPhase
    data object Undated : TripPhase
}

fun tripPhase(trip: Trip, today: LocalDate = LocalDate.now()): TripPhase {
    if (trip.startDate == null && trip.endDate == null) return TripPhase.Undated
    val start = trip.startDate ?: today
    val end = trip.endDate ?: start
    return when {
        today < start -> TripPhase.Upcoming(java.time.temporal.ChronoUnit.DAYS.between(today, start))
        today > end -> TripPhase.Past
        else -> TripPhase.Active(
            day = java.time.temporal.ChronoUnit.DAYS.between(start, today) + 1,
            total = java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1,
        )
    }
}

fun BigDecimal.moneyScale(): BigDecimal = setScale(2, RoundingMode.HALF_UP)

@Serializable
data class TripDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    val name: String,
    val destination: String? = null,
    @SerialName("start_date") val startDate: String? = null,
    @SerialName("end_date") val endDate: String? = null,
    val budget: Double,
    val currency: String,
    val accent: String,
    @SerialName("share_code") val shareCode: String? = null,
    @SerialName("cover_path") val coverPath: String? = null,
    @SerialName("cover_subject") val coverSubject: String? = null,
    @SerialName("cover_status") val coverStatus: String = "idle",
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class TripInsert(
    @SerialName("user_id") val userId: String,
    val name: String,
    val destination: String?,
    @SerialName("start_date") val startDate: String?,
    @SerialName("end_date") val endDate: String?,
    val budget: Double,
    val currency: String = "EUR",
    val accent: String = "clay",
)

@Serializable
data class TripUpdate(
    val name: String,
    val destination: String?,
    @SerialName("start_date") val startDate: String?,
    @SerialName("end_date") val endDate: String?,
    val budget: Double,
)

@Serializable
data class ExpenseDto(
    val id: String,
    @SerialName("trip_id") val tripId: String,
    @SerialName("user_id") val userId: String,
    val title: String,
    val amount: Double,
    @SerialName("original_amount") val originalAmount: Double? = null,
    @SerialName("original_currency") val originalCurrency: String? = null,
    @SerialName("fx_rate") val fxRate: Double? = null,
    val category: String,
    @SerialName("spent_on") val spentOn: String,
    val note: String? = null,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class ExpenseInsert(
    val id: String,
    @SerialName("trip_id") val tripId: String,
    @SerialName("user_id") val userId: String,
    val title: String,
    val amount: Double,
    @SerialName("original_amount") val originalAmount: Double?,
    @SerialName("original_currency") val originalCurrency: String?,
    @SerialName("fx_rate") val fxRate: Double?,
    val category: String,
    @SerialName("spent_on") val spentOn: String,
    val note: String?,
)

@Serializable
data class ExpenseUpdate(
    val title: String,
    val amount: Double,
    @SerialName("original_amount") val originalAmount: Double?,
    @SerialName("original_currency") val originalCurrency: String?,
    @SerialName("fx_rate") val fxRate: Double?,
    val category: String,
    @SerialName("spent_on") val spentOn: String,
    val note: String?,
)

@Serializable
data class CoverDto(
    val id: String,
    @SerialName("cover_path") val coverPath: String? = null,
    @SerialName("cover_status") val coverStatus: String = "idle",
)

@Serializable
data class MemberDto(
    @SerialName("trip_id") val tripId: String,
    @SerialName("user_id") val userId: String,
    val role: String,
    @SerialName("joined_at") val joinedAt: String,
)

@Serializable
data class ProfileDto(val id: String, @SerialName("display_name") val displayName: String? = null)

fun ExpenseDraft.toInsert(id: String, tripId: String, userId: String) = ExpenseInsert(
    id = id,
    tripId = tripId,
    userId = userId,
    title = title,
    amount = amount.moneyScale().toDouble(),
    originalAmount = originalAmount?.moneyScale()?.toDouble(),
    originalCurrency = originalCurrency,
    fxRate = fxRate?.toDouble(),
    category = category.wireName,
    spentOn = spentOn.toString(),
    note = note,
)

fun ExpenseInsert.asPending(now: String = OffsetDateTime.now(ZoneOffset.UTC).toString()) = Expense(
    id = id,
    tripId = tripId,
    userId = userId,
    title = title,
    amount = BigDecimal.valueOf(amount).moneyScale(),
    originalAmount = originalAmount?.let(BigDecimal::valueOf),
    originalCurrency = originalCurrency,
    fxRate = fxRate?.let(BigDecimal::valueOf),
    category = Category.fromWire(category),
    spentOn = LocalDate.parse(spentOn),
    note = note,
    createdAt = now,
    syncState = SyncState.Pending,
)

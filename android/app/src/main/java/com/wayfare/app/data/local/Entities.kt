package com.wayfare.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.wayfare.app.core.Category
import com.wayfare.app.core.Expense
import com.wayfare.app.core.SyncState
import com.wayfare.app.core.Trip
import com.wayfare.app.core.TripMember
import java.math.BigDecimal
import java.time.LocalDate

@Entity(tableName = "trips", primaryKeys = ["accountId", "id"])
data class TripEntity(
    val accountId: String,
    val id: String,
    val ownerId: String,
    val name: String,
    val destination: String?,
    val startDate: String?,
    val endDate: String?,
    val budget: String,
    val currency: String,
    val accent: String,
    val shareCode: String?,
    val coverPath: String?,
    val coverSubject: String?,
    val coverStatus: String,
    val createdAt: String,
)

@Entity(
    tableName = "expenses",
    primaryKeys = ["accountId", "id"],
    indices = [Index("accountId", "tripId")],
)
data class ExpenseEntity(
    val accountId: String,
    val id: String,
    val tripId: String,
    val userId: String,
    val title: String,
    val amount: String,
    val originalAmount: String?,
    val originalCurrency: String?,
    val fxRate: String?,
    val category: String,
    val spentOn: String,
    val note: String?,
    val createdAt: String,
    val syncState: String,
    val syncError: String?,
)

@Entity(tableName = "members", primaryKeys = ["accountId", "tripId", "userId"])
data class MemberEntity(
    val accountId: String,
    val tripId: String,
    val userId: String,
    val role: String,
    val joinedAt: String,
    val displayName: String?,
)

@Entity(tableName = "expense_outbox", indices = [Index("accountId")])
data class OutboxEntity(
    @PrimaryKey val expenseId: String,
    val accountId: String,
    val payload: String,
    val attempts: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
)

fun TripEntity.toDomain() = Trip(
    id, ownerId, name, destination, startDate?.let(LocalDate::parse), endDate?.let(LocalDate::parse),
    budget.toBigDecimal(), currency, accent, shareCode, coverPath, coverSubject, coverStatus, createdAt,
)

fun Trip.toEntity(accountId: String) = TripEntity(
    accountId, id, ownerId, name, destination, startDate?.toString(), endDate?.toString(),
    budget.toPlainString(), currency, accent, shareCode, coverPath, coverSubject, coverStatus, createdAt,
)

fun ExpenseEntity.toDomain() = Expense(
    id, tripId, userId, title, amount.toBigDecimal(), originalAmount?.toBigDecimal(), originalCurrency,
    fxRate?.toBigDecimal(), Category.fromWire(category), LocalDate.parse(spentOn), note, createdAt,
    SyncState.valueOf(syncState), syncError,
)

fun Expense.toEntity(accountId: String) = ExpenseEntity(
    accountId, id, tripId, userId, title, amount.toPlainString(), originalAmount?.toPlainString(),
    originalCurrency, fxRate?.toPlainString(), category.wireName, spentOn.toString(), note, createdAt,
    syncState.name, syncError,
)

fun MemberEntity.toDomain(currentUser: String) = TripMember(
    tripId, userId, role, joinedAt, displayName, userId == currentUser,
)

fun TripMember.toEntity(accountId: String) = MemberEntity(
    accountId, tripId, userId, role, joinedAt, displayName,
)

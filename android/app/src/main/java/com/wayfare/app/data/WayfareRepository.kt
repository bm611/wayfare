package com.wayfare.app.data

import androidx.room.withTransaction
import com.wayfare.app.BuildConfig
import com.wayfare.app.core.Category
import com.wayfare.app.core.Expense
import com.wayfare.app.core.ExpenseDraft
import com.wayfare.app.core.ExpenseDto
import com.wayfare.app.core.ExpenseInsert
import com.wayfare.app.core.ExpenseUpdate
import com.wayfare.app.core.MemberDto
import com.wayfare.app.core.CoverDto
import com.wayfare.app.core.ProfileDto
import com.wayfare.app.core.SyncState
import com.wayfare.app.core.Trip
import com.wayfare.app.core.TripDraft
import com.wayfare.app.core.TripDto
import com.wayfare.app.core.TripInsert
import com.wayfare.app.core.TripMember
import com.wayfare.app.core.TripSummary
import com.wayfare.app.core.TripUpdate
import com.wayfare.app.core.asPending
import com.wayfare.app.core.toInsert
import com.wayfare.app.data.local.ExpenseEntity
import com.wayfare.app.data.local.OutboxEntity
import com.wayfare.app.data.local.WayfareDatabase
import com.wayfare.app.data.local.toDomain
import com.wayfare.app.data.local.toEntity
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.exceptions.BadRequestRestException
import io.github.jan.supabase.exceptions.NotFoundRestException
import io.github.jan.supabase.exceptions.UnauthorizedRestException
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

class WayfareRepository(
    private val supabase: SupabaseClient,
    private val database: WayfareDatabase,
    private val http: HttpClient,
    private val scheduleSync: (String) -> Unit,
) {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = true }

    fun currentUserId(): String? = supabase.auth.currentUserOrNull()?.id

    fun observeTripSummaries(accountId: String): Flow<List<TripSummary>> = combine(
        database.trips().observeAll(accountId),
        database.expenses().observeAll(accountId),
    ) { trips, expenses ->
        // A failed entry never reached the server, so counting it here would make
        // the phone disagree with the web app about what the trip has cost.
        val totals = expenses.filterNot { it.syncState == SyncState.Failed.name }
            .groupBy { it.tripId }
            .mapValues { (_, rows) ->
                rows.fold(BigDecimal.ZERO) { total, row -> total + row.amount.toBigDecimal() } to rows.size
            }
        trips.map { row ->
            val total = totals[row.id]
            TripSummary(row.toDomain(), total?.first ?: BigDecimal.ZERO, total?.second ?: 0)
        }
    }

    fun observeTrip(accountId: String, tripId: String): Flow<Trip?> =
        database.trips().observeOne(accountId, tripId).map { it?.toDomain() }

    fun observeExpenses(accountId: String, tripId: String): Flow<List<Expense>> =
        database.expenses().observeTrip(accountId, tripId).map { rows -> rows.map(ExpenseEntity::toDomain) }

    fun observeMembers(accountId: String, tripId: String): Flow<List<TripMember>> =
        database.members().observeTrip(accountId, tripId).map { rows -> rows.map { it.toDomain(accountId) } }

    suspend fun refreshAll() {
        val accountId = requireUser()
        val trips = supabase.from("trips").select {
            order("created_at", Order.DESCENDING)
        }.decodeList<TripDto>().map(TripDto::toDomain)
        val expenses = supabase.from("expenses").select {
            order("spent_on", Order.DESCENDING)
            order("created_at", Order.DESCENDING)
        }.decodeList<ExpenseDto>().map(ExpenseDto::toDomain)
        database.withTransaction {
            if (trips.isEmpty()) database.trips().deleteAll(accountId) else {
                database.trips().upsert(trips.map { it.toEntity(accountId) })
                database.trips().deleteMissing(accountId, trips.map(Trip::id))
            }
            if (expenses.isEmpty()) database.expenses().deleteAllSynced(accountId) else {
                database.expenses().upsert(expenses.map { it.toEntity(accountId) })
                database.expenses().deleteMissingSynced(accountId, expenses.map(Expense::id))
            }
        }
    }

    suspend fun refreshTrip(tripId: String) {
        val accountId = requireUser()
        val trip = supabase.from("trips").select { filter { eq("id", tripId) } }
            .decodeSingle<TripDto>().toDomain()
        val expenses = supabase.from("expenses").select {
            filter { eq("trip_id", tripId) }
            order("spent_on", Order.DESCENDING)
            order("created_at", Order.DESCENDING)
        }.decodeList<ExpenseDto>().map(ExpenseDto::toDomain)
        database.withTransaction {
            database.trips().upsert(listOf(trip.toEntity(accountId)))
            database.expenses().upsert(expenses.map { it.toEntity(accountId) })
        }
        refreshMembers(tripId)
    }

    suspend fun createTrip(draft: TripDraft): String {
        val accountId = requireUser()
        val created = supabase.from("trips").insert(
            TripInsert(
                accountId, draft.name, draft.destination, draft.startDate?.toString(),
                draft.endDate?.toString(), draft.budget.toDouble(),
            ),
        ) { select() }.decodeSingle<TripDto>().toDomain()
        database.trips().upsert(listOf(created.toEntity(accountId)))
        return created.id
    }

    suspend fun updateTrip(tripId: String, draft: TripDraft) {
        val accountId = requireUser()
        val updated = supabase.from("trips").update(
            TripUpdate(
                draft.name, draft.destination, draft.startDate?.toString(),
                draft.endDate?.toString(), draft.budget.toDouble(),
            ),
        ) {
            select()
            filter { eq("id", tripId) }
        }.decodeSingle<TripDto>().toDomain()
        database.trips().upsert(listOf(updated.toEntity(accountId)))
    }

    suspend fun deleteTrip(tripId: String) {
        val accountId = requireUser()
        supabase.from("trips").delete { filter { eq("id", tripId) } }
        database.trips().delete(accountId, tripId)
    }

    suspend fun createExpense(tripId: String, draft: ExpenseDraft): String {
        val accountId = requireUser()
        val id = UUID.randomUUID().toString()
        val payload = draft.toInsert(id, tripId, accountId)
        database.withTransaction {
            database.expenses().upsert(listOf(payload.asPending().toEntity(accountId)))
            database.outbox().upsert(OutboxEntity(id, accountId, json.encodeToString(payload)))
        }
        scheduleSync(accountId)
        return id
    }

    suspend fun updateExpense(expenseId: String, draft: ExpenseDraft) {
        val accountId = requireUser()
        val updated = supabase.from("expenses").update(
            ExpenseUpdate(
                draft.title, draft.amount.toDouble(), draft.originalAmount?.toDouble(),
                draft.originalCurrency, draft.fxRate?.toDouble(), draft.category.wireName,
                draft.spentOn.toString(), draft.note,
            ),
        ) {
            select()
            filter { eq("id", expenseId) }
        }.decodeSingle<ExpenseDto>().toDomain()
        database.expenses().upsert(listOf(updated.toEntity(accountId)))
    }

    /**
     * Queues a stuck entry for another attempt. The outbox row is rebuilt from
     * what is on disk and keeps the original UUID, so a retry that races a
     * partially-applied insert reconciles instead of duplicating.
     */
    suspend fun retryExpense(expenseId: String) {
        val accountId = requireUser()
        val row = database.expenses().get(accountId, expenseId) ?: return
        val payload = ExpenseInsert(
            id = row.id,
            tripId = row.tripId,
            userId = accountId,
            title = row.title,
            amount = row.amount.toBigDecimal().toDouble(),
            originalAmount = row.originalAmount?.toBigDecimal()?.toDouble(),
            originalCurrency = row.originalCurrency,
            fxRate = row.fxRate?.toBigDecimal()?.toDouble(),
            category = row.category,
            spentOn = row.spentOn,
            note = row.note,
        )
        database.withTransaction {
            database.outbox().upsert(OutboxEntity(row.id, accountId, json.encodeToString(payload)))
            database.expenses().setSyncState(accountId, row.id, SyncState.Pending.name, null)
        }
        scheduleSync(accountId)
    }

    /** Throws away an entry that never made it to the server. Local only. */
    suspend fun discardExpense(expenseId: String) {
        val accountId = requireUser()
        database.withTransaction {
            database.outbox().deleteById(expenseId)
            database.expenses().delete(accountId, expenseId)
        }
    }

    suspend fun deleteExpense(expenseId: String) {
        val accountId = requireUser()
        supabase.from("expenses").delete { filter { eq("id", expenseId) } }
        database.expenses().delete(accountId, expenseId)
    }

    suspend fun syncOutbox(accountId: String): Boolean {
        if (currentUserId() != accountId) return false
        var retry = false
        for (item in database.outbox().pending(accountId)) {
            val payload = json.decodeFromString<ExpenseInsert>(item.payload)
            try {
                val existing = supabase.from("expenses").select { filter { eq("id", payload.id) } }
                    .decodeList<ExpenseDto>()
                val remote = existing.firstOrNull()
                    ?: supabase.from("expenses").insert(payload) { select() }.decodeSingle<ExpenseDto>()
                database.withTransaction {
                    database.expenses().upsert(listOf(remote.toDomain().toEntity(accountId)))
                    database.outbox().delete(item)
                }
            } catch (error: Exception) {
                if (error.isTerminal()) {
                    // Membership was revoked or the trip is gone. Retrying cannot
                    // help, so stop and leave the entry visible and actionable.
                    database.withTransaction {
                        database.outbox().delete(item)
                        database.expenses().setSyncState(
                            accountId, item.expenseId, SyncState.Failed.name, error.readableMessage(),
                        )
                    }
                } else {
                    database.outbox().recordAttempt(item.expenseId)
                    database.expenses().setSyncState(
                        accountId, item.expenseId, SyncState.Pending.name, error.readableMessage(),
                    )
                    retry = true
                }
            }
        }
        return retry
    }

    suspend fun refreshMembers(tripId: String) {
        val accountId = requireUser()
        val members = supabase.from("trip_members").select {
            filter { eq("trip_id", tripId) }
            order("joined_at", Order.ASCENDING)
        }.decodeList<MemberDto>()
        val profiles = supabase.from("profiles").select().decodeList<ProfileDto>()
            .associateBy(ProfileDto::id)
        val domain = members.map {
            TripMember(
                it.tripId, it.userId, it.role, it.joinedAt, profiles[it.userId]?.displayName,
                it.userId == accountId,
            )
        }
        database.withTransaction {
            database.members().deleteTrip(accountId, tripId)
            database.members().upsert(domain.map { it.toEntity(accountId) })
        }
    }

    suspend fun joinTrip(code: String): String {
        val data = supabase.postgrest.rpc(
            "join_trip", buildJsonObject { put("p_code", code.trim().uppercase()) },
        ).data
        return json.decodeFromString(data)
    }

    suspend fun removeMember(tripId: String, userId: String) {
        supabase.from("trip_members").delete {
            filter {
                eq("trip_id", tripId)
                eq("user_id", userId)
            }
        }
        refreshMembers(tripId)
    }

    suspend fun leaveTrip(tripId: String) {
        val accountId = requireUser()
        removeMember(tripId, accountId)
        database.trips().delete(accountId, tripId)
    }

    suspend fun requestCover(tripId: String, regenerate: Boolean = false) {
        val token = supabase.auth.currentSessionOrNull()?.accessToken ?: error("Sign in to generate a cover")
        if (regenerate) {
            // Preserve the current image and never reset a running job.
            supabase.from("trips").update(buildJsonObject { put("cover_status", "idle") }) {
                filter {
                    eq("id", tripId)
                    isIn("cover_status", listOf("ready", "failed"))
                }
            }
        }
        val response = http.post(BuildConfig.COVER_ENDPOINT) {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject { put("tripId", tripId) })
        }
        if (!response.status.isSuccess()) error("Cover request failed: ${response.status.value}")
    }

    /** Show the developing state now rather than a poll interval from now. */
    suspend fun markCoverPending(tripId: String) {
        val accountId = currentUserId() ?: return
        database.trips().markCoverPending(accountId, tripId)
    }

    /** Patches cover columns for the trips still drawing; returns the ones still pending. */
    suspend fun refreshCovers(tripIds: List<String>): List<String> {
        val accountId = requireUser()
        val rows = supabase.from("trips").select(Columns.list("id", "cover_path", "cover_status")) {
            filter { isIn("id", tripIds) }
        }.decodeList<CoverDto>()
        database.withTransaction {
            rows.forEach { database.trips().setCover(accountId, it.id, it.coverPath, it.coverStatus) }
        }
        return rows.filter { it.coverStatus == "pending" }.map(CoverDto::id)
    }

    /**
     * Signing out leaves nothing behind on disk. Caches are keyed by account, so
     * another user signing in on the same device could never read these rows —
     * but a shared phone should not keep a ledger the owner has walked away from.
     */
    suspend fun clearAccount(accountId: String) {
        database.withTransaction {
            database.outbox().deleteAll(accountId)
            database.expenses().deleteAll(accountId)
            database.members().deleteAll(accountId)
            database.trips().deleteAll(accountId)
        }
    }

    fun coverUrl(path: String?): String? = path?.let {
        "${BuildConfig.SUPABASE_URL}/storage/v1/object/public/trip-covers/$it"
    }

    private fun requireUser(): String = currentUserId() ?: error("Not signed in")
}

private fun TripDto.toDomain() = Trip(
    id, userId, name, destination, startDate?.let(LocalDate::parse), endDate?.let(LocalDate::parse),
    BigDecimal.valueOf(budget), currency, accent, shareCode, coverPath, coverSubject, coverStatus, createdAt,
)

private fun ExpenseDto.toDomain() = Expense(
    id, tripId, userId, title, BigDecimal.valueOf(amount), originalAmount?.let(BigDecimal::valueOf),
    originalCurrency, fxRate?.let(BigDecimal::valueOf), Category.fromWire(category),
    LocalDate.parse(spentOn), note, createdAt,
)

/**
 * A rejection the server will keep giving. Membership revoked, the row's trip
 * deleted, or a payload the schema refuses — none of which a later retry fixes.
 */
private fun Exception.isTerminal(): Boolean = this is UnauthorizedRestException ||
    this is NotFoundRestException ||
    this is BadRequestRestException

private fun Exception.readableMessage(): String = message?.takeIf(String::isNotBlank)
    ?: this::class.simpleName
    ?: "Sync failed"

package com.wayfare.app.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface TripDao {
    @Query("SELECT * FROM trips WHERE accountId = :accountId ORDER BY createdAt DESC")
    fun observeAll(accountId: String): Flow<List<TripEntity>>

    @Query("SELECT * FROM trips WHERE accountId = :accountId AND id = :tripId LIMIT 1")
    fun observeOne(accountId: String, tripId: String): Flow<TripEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(items: List<TripEntity>)

    @Query("DELETE FROM trips WHERE accountId = :accountId")
    suspend fun deleteAll(accountId: String)

    @Query("DELETE FROM trips WHERE accountId = :accountId AND id = :tripId")
    suspend fun delete(accountId: String, tripId: String)

    @Query(
        "UPDATE trips SET coverPath = :coverPath, coverStatus = :coverStatus " +
            "WHERE accountId = :accountId AND id = :tripId",
    )
    suspend fun setCover(accountId: String, tripId: String, coverPath: String?, coverStatus: String)

    @Query("UPDATE trips SET coverStatus = 'pending' WHERE accountId = :accountId AND id = :tripId")
    suspend fun markCoverPending(accountId: String, tripId: String)
}

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses WHERE accountId = :accountId ORDER BY spentOn DESC, createdAt DESC")
    fun observeAll(accountId: String): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE accountId = :accountId AND tripId = :tripId ORDER BY spentOn DESC, createdAt DESC")
    fun observeTrip(accountId: String, tripId: String): Flow<List<ExpenseEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(items: List<ExpenseEntity>)

    @Query("DELETE FROM expenses WHERE accountId = :accountId AND tripId = :tripId AND syncState = 'Synced'")
    suspend fun deleteSyncedTrip(accountId: String, tripId: String)

    @Query("DELETE FROM expenses WHERE accountId = :accountId AND syncState = 'Synced'")
    suspend fun deleteAllSynced(accountId: String)

    @Query("SELECT * FROM expenses WHERE accountId = :accountId AND id = :expenseId LIMIT 1")
    suspend fun get(accountId: String, expenseId: String): ExpenseEntity?

    @Query("DELETE FROM expenses WHERE accountId = :accountId AND id = :expenseId")
    suspend fun delete(accountId: String, expenseId: String)

    @Query("UPDATE expenses SET syncState = :state, syncError = :error WHERE accountId = :accountId AND id = :expenseId")
    suspend fun setSyncState(accountId: String, expenseId: String, state: String, error: String?)

    @Query("DELETE FROM expenses WHERE accountId = :accountId")
    suspend fun deleteAll(accountId: String)
}

@Dao
interface MemberDao {
    @Query("SELECT * FROM members WHERE accountId = :accountId AND tripId = :tripId ORDER BY joinedAt")
    fun observeTrip(accountId: String, tripId: String): Flow<List<MemberEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(items: List<MemberEntity>)

    @Query("DELETE FROM members WHERE accountId = :accountId AND tripId = :tripId")
    suspend fun deleteTrip(accountId: String, tripId: String)

    @Query("DELETE FROM members WHERE accountId = :accountId")
    suspend fun deleteAll(accountId: String)
}

@Dao
interface OutboxDao {
    @Query("SELECT * FROM expense_outbox WHERE accountId = :accountId ORDER BY createdAt")
    suspend fun pending(accountId: String): List<OutboxEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: OutboxEntity)

    @Delete
    suspend fun delete(item: OutboxEntity)

    @Query("UPDATE expense_outbox SET attempts = attempts + 1 WHERE expenseId = :expenseId")
    suspend fun recordAttempt(expenseId: String)

    @Query("DELETE FROM expense_outbox WHERE expenseId = :expenseId")
    suspend fun deleteById(expenseId: String)

    @Query("DELETE FROM expense_outbox WHERE accountId = :accountId")
    suspend fun deleteAll(accountId: String)
}

@Database(
    entities = [TripEntity::class, ExpenseEntity::class, MemberEntity::class, OutboxEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class WayfareDatabase : RoomDatabase() {
    abstract fun trips(): TripDao
    abstract fun expenses(): ExpenseDao
    abstract fun members(): MemberDao
    abstract fun outbox(): OutboxDao
}

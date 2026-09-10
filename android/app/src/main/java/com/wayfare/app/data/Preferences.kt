package com.wayfare.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("wayfare_preferences")

class Preferences(private val context: Context) {
    fun paidIn(tripId: String): Flow<String> = context.dataStore.data.map {
        it[stringPreferencesKey("paid_in_$tripId")] ?: "EUR"
    }

    suspend fun rememberPaidIn(tripId: String, currency: String) {
        context.dataStore.edit { it[stringPreferencesKey("paid_in_$tripId")] = currency }
    }

    val pendingInvite: Flow<String?> = context.dataStore.data.map { it[PENDING_INVITE] }

    suspend fun setPendingInvite(code: String?) {
        context.dataStore.edit {
            if (code == null) it.remove(PENDING_INVITE) else it[PENDING_INVITE] = code
        }
    }

    private companion object {
        val PENDING_INVITE = stringPreferencesKey("pending_invite")
    }
}

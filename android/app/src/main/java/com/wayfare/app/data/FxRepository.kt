package com.wayfare.app.data

import android.content.Context
import androidx.core.content.edit
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.math.BigDecimal

data class FxSnapshot(
    val rates: Map<String, BigDecimal>,
    val date: String,
    val fetchedAt: Long,
    val stale: Boolean,
)

/**
 * Currency conversion, anchored to the euro. Ported from `src/lib/fx.ts`.
 *
 * Rates come from Frankfurter (ECB reference rates, no key required) and are
 * cached for twelve hours. If the network is unavailable the app falls back to
 * a bundled snapshot rather than refusing to record a cost — whatever rate was
 * actually used is stored on the expense, so a stale figure stays traceable.
 */
class FxRepository(context: Context, private val http: HttpClient) {
    private val preferences = context.getSharedPreferences("wayfare_fx", Context.MODE_PRIVATE)
    private val inFlight = Mutex()
    private val _snapshot = MutableStateFlow(readCache() ?: fallback())

    /** The rates for the current render, refreshed in place once fresh ones land. */
    val snapshot: StateFlow<FxSnapshot> = _snapshot.asStateFlow()

    fun current(): FxSnapshot = _snapshot.value

    suspend fun refresh(): FxSnapshot = inFlight.withLock {
        val current = _snapshot.value
        if (!current.stale && System.currentTimeMillis() - current.fetchedAt < MAX_AGE) return current
        runCatching { http.get(ENDPOINT).body<Response>() }
            .map { response ->
                FxSnapshot(
                    rates = mapOf("EUR" to BigDecimal.ONE) +
                        response.rates.mapValues { BigDecimal.valueOf(it.value) },
                    date = response.date,
                    fetchedAt = System.currentTimeMillis(),
                    stale = false,
                ).also(::writeCache)
            }
            .getOrDefault(current)
            .also { _snapshot.value = it }
    }

    /** How many units of [to] one unit of [from] buys. */
    fun rateBetween(from: String, to: String, snapshot: FxSnapshot = current()): BigDecimal? =
        com.wayfare.app.core.rateBetween(from, to, snapshot.rates)

    fun convert(amount: BigDecimal, from: String, to: String, snapshot: FxSnapshot = current()): BigDecimal? =
        com.wayfare.app.core.convert(amount, from, to, snapshot.rates)

    private fun readCache(): FxSnapshot? = runCatching {
        val raw = preferences.getString(KEY_JSON, null) ?: return null
        val response = Json.decodeFromString<Response>(raw)
        if (!response.rates.containsKey("USD")) return null
        FxSnapshot(
            rates = mapOf("EUR" to BigDecimal.ONE) + response.rates.mapValues { BigDecimal.valueOf(it.value) },
            date = response.date,
            fetchedAt = preferences.getLong(KEY_TIME, 0),
            stale = false,
        )
    }.getOrNull()

    private fun writeCache(snapshot: FxSnapshot) {
        val response = Response(
            snapshot.date,
            snapshot.rates.filterKeys { it != "EUR" }.mapValues { it.value.toDouble() },
        )
        preferences.edit {
            putString(KEY_JSON, Json.encodeToString(response))
            putLong(KEY_TIME, snapshot.fetchedAt)
        }
    }

    private fun fallback() = FxSnapshot(
        rates = FALLBACK.mapValues { BigDecimal.valueOf(it.value) },
        date = "snapshot",
        fetchedAt = 0,
        stale = true,
    )

    @Serializable private data class Response(val date: String, val rates: Map<String, Double>)

    companion object {
        const val BASE = "EUR"
        private const val ENDPOINT = "https://api.frankfurter.dev/v1/latest?base=EUR"
        private const val KEY_JSON = "snapshot"
        private const val KEY_TIME = "fetched_at"
        private const val MAX_AGE = 12 * 60 * 60 * 1000L

        /**
         * Units of each currency per 1 EUR. Offline snapshot only — ECB reference
         * rates as of 2026-09-02, matching the web app's bundled table. The rate
         * actually used is stored on every expense, so a figure recorded against
         * this table stays traceable.
         */
        private val FALLBACK = mapOf(
            "EUR" to 1.0, "USD" to 1.1578, "GBP" to 0.8587, "CHF" to 0.9424,
            "SEK" to 11.1575, "NOK" to 10.809, "DKK" to 7.475, "PLN" to 4.3265,
            "CZK" to 24.191, "HUF" to 368.2, "RON" to 5.2558, "ISK" to 140.6,
            "TRY" to 55.9145, "JPY" to 184.78, "CNY" to 7.7822, "HKD" to 9.0801,
            "SGD" to 1.4741, "KRW" to 1577.57, "INR" to 109.962, "IDR" to 20511.18,
            "MYR" to 4.6839, "PHP" to 72.415, "THB" to 38.468, "ILS" to 3.5064,
            "AUD" to 1.6199, "NZD" to 1.9868, "CAD" to 1.6122, "BRL" to 5.9635,
            "MXN" to 19.6835, "ZAR" to 18.6341,
        )

        /** Every currency the converter can handle, euro first. */
        val CURRENCIES = FALLBACK.keys.toList()
    }
}

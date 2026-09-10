package com.wayfare.app.data

import com.wayfare.app.core.Trip
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Keeps trip covers moving toward 'ready'. Ported from `useTripCovers.ts`.
 *
 * Generation lives in a Netlify background function that answers 202 and
 * reports back through the row, so this asks for the ones that are missing and
 * then watches the table until they land.
 */
class CoverCoordinator(
    private val repository: WayfareRepository,
    private val scope: CoroutineScope,
) {
    // Asked once per trip per process. The server's claim is the real guard
    // against duplicate work; this only avoids pointless round trips on every
    // emission of the trips flow.
    private val asked = mutableSetOf<String>()
    private val gate = Mutex()
    private var poller: Job? = null

    fun watch(trips: List<Trip>) {
        val wanted = trips.filter { subject(it) != null && (it.coverStatus == "idle" || it.coverStatus == "failed") }
        val pending = trips.filter { it.coverStatus == "pending" }.map(Trip::id)
        if (wanted.isNotEmpty()) scope.launch { request(wanted) }
        if (pending.isNotEmpty()) startPolling(pending)
    }

    /** What the cover is a picture of — a destination, or the trip's own name. */
    private fun subject(trip: Trip): String? =
        trip.destination?.trim()?.ifBlank { null } ?: trip.name.trim().ifBlank { null }

    private suspend fun request(trips: List<Trip>) = gate.withLock {
        // One at a time: a first run with several old trips should not fire a
        // handful of image jobs at once.
        for (trip in trips) {
            if (!asked.add(trip.id)) continue
            val sent = runCatching { repository.requestCover(trip.id) }
            if (sent.isFailure) asked.remove(trip.id) else repository.markCoverPending(trip.id)
        }
    }

    private fun startPolling(tripIds: List<String>) {
        if (poller?.isActive == true) return
        poller = scope.launch {
            var polls = 0
            var watching = tripIds
            while (watching.isNotEmpty() && polls < MAX_POLLS) {
                delay(POLL_MS)
                polls += 1
                watching = runCatching { repository.refreshCovers(watching) }.getOrDefault(watching)
            }
        }
    }

    private companion object {
        const val POLL_MS = 4_000L

        /** Roughly three minutes. Drawing takes ~15s; past this it is not coming. */
        const val MAX_POLLS = 45
    }
}

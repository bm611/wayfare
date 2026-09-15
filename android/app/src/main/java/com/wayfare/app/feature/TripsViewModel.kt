package com.wayfare.app.feature

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wayfare.app.AppContainer
import com.wayfare.app.core.TripDraft
import com.wayfare.app.core.TripSummary
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class TripsUiState(
    val trips: List<TripSummary> = emptyList(),
    val loading: Boolean = true,
    /** Only ever true for a refresh the traveller asked for, so the spinner answers a gesture. */
    val refreshing: Boolean = false,
    val error: String? = null,
)

class TripsViewModel(
    private val accountId: String,
    private val container: AppContainer,
) : ViewModel() {
    private val repository = container.repository
    private val _state = MutableStateFlow(TripsUiState())
    val state: StateFlow<TripsUiState> = _state.asStateFlow()
    private var refreshJob: Job? = null

    init {
        viewModelScope.launch {
            repository.observeTripSummaries(accountId).collect { trips ->
                _state.value = _state.value.copy(trips = trips, loading = false)
                container.covers.watch(trips.map(TripSummary::trip))
            }
        }
        sync(silent = true)
    }

    /** Pull-to-refresh and the menu item: the traveller asked, so show the indicator. */
    fun refresh() = sync(silent = false)

    private fun sync(silent: Boolean) {
        // Foreground and manual refresh share one in-flight request.
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            _state.value = _state.value.copy(refreshing = !silent, error = null)
            runCatching { repository.refreshAll() }
                .onFailure { _state.value = _state.value.copy(error = it.message) }
            _state.value = _state.value.copy(refreshing = false, loading = false)
        }
    }

    suspend fun createTrip(draft: TripDraft): Result<String> = runCatching {
        repository.createTrip(draft)
    }

    suspend fun joinTrip(code: String): Result<String> = runCatching {
        repository.joinTrip(code).also { repository.refreshTrip(it) }
    }

    /**
     * Called when the screen comes back to the foreground, including on the way
     * back from a trip. The ledger catches up quietly: a spinner here would read
     * as the app re-loading a screen the traveller is already looking at.
     */
    fun onResume() {
        container.refreshRates()
        sync(silent = true)
    }
}

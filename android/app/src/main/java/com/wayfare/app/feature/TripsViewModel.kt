package com.wayfare.app.feature

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wayfare.app.AppContainer
import com.wayfare.app.core.TripDraft
import com.wayfare.app.core.TripSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class TripsUiState(
    val trips: List<TripSummary> = emptyList(),
    val loading: Boolean = true,
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

    init {
        viewModelScope.launch {
            repository.observeTripSummaries(accountId).collect { trips ->
                _state.value = _state.value.copy(trips = trips, loading = false)
                container.covers.watch(trips.map(TripSummary::trip))
            }
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(refreshing = true, error = null)
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

    /** Called when the screen comes back to the foreground. */
    fun onResume() {
        container.refreshRates()
        refresh()
    }
}

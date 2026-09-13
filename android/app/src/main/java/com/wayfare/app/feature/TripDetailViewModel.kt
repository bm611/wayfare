package com.wayfare.app.feature

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wayfare.app.AppContainer
import com.wayfare.app.core.Category
import com.wayfare.app.core.Expense
import com.wayfare.app.core.ExpenseDraft
import com.wayfare.app.core.Trip
import com.wayfare.app.core.TripDraft
import com.wayfare.app.core.TripMember
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class TripDetailUiState(
    val trip: Trip? = null,
    val expenses: List<Expense> = emptyList(),
    val members: List<TripMember> = emptyList(),
    val loading: Boolean = true,
    /** Only ever true for a refresh the traveller asked for, so the spinner answers a gesture. */
    val refreshing: Boolean = false,
    val error: String? = null,
    val query: String = "",
    val category: Category? = null,
    val payerId: String? = null,
) {
    val filteredExpenses: List<Expense> get() = expenses.filter { expense ->
        (query.isBlank() || expense.title.contains(query, true) || expense.note?.contains(query, true) == true) &&
            (category == null || expense.category == category) &&
            (payerId == null || expense.userId == payerId)
    }
}

class TripDetailViewModel(
    private val accountId: String,
    private val tripId: String,
    private val container: AppContainer,
) : ViewModel() {
    private val repository = container.repository
    private val _state = MutableStateFlow(TripDetailUiState())
    val state: StateFlow<TripDetailUiState> = _state.asStateFlow()
    private var refreshJob: Job? = null

    init {
        viewModelScope.launch {
            repository.observeTrip(accountId, tripId).collect { trip ->
                _state.value = _state.value.copy(trip = trip, loading = false)
                trip?.let { container.covers.watch(listOf(it)) }
            }
        }
        viewModelScope.launch {
            repository.observeExpenses(accountId, tripId).collect {
                _state.value = _state.value.copy(expenses = it)
            }
        }
        viewModelScope.launch {
            repository.observeMembers(accountId, tripId).collect {
                _state.value = _state.value.copy(members = it)
            }
        }
        sync(silent = true)
    }

    /** Pull-to-refresh and the toolbar button: the traveller asked, so show the indicator. */
    fun refresh() = sync(silent = false)

    private fun sync(silent: Boolean) {
        // A silent sync is housekeeping; it never fights one already on its way.
        if (silent && refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            _state.value = _state.value.copy(refreshing = !silent, error = null)
            runCatching { repository.refreshTrip(tripId) }
                .onFailure { _state.value = _state.value.copy(error = it.message) }
            _state.value = _state.value.copy(refreshing = false, loading = false)
        }
    }

    /**
     * Called when the screen comes back to the foreground, opening the trip
     * included. The ledger catches up quietly rather than announcing itself with
     * a spinner over content that is already on screen.
     */
    fun onResume() {
        container.refreshRates()
        sync(silent = true)
    }

    fun setQuery(value: String) { _state.value = _state.value.copy(query = value) }
    fun setCategory(value: Category?) { _state.value = _state.value.copy(category = value) }
    fun setPayer(value: String?) { _state.value = _state.value.copy(payerId = value) }

    suspend fun updateTrip(draft: TripDraft) = runCatching { repository.updateTrip(tripId, draft) }
    suspend fun deleteTrip() = runCatching { repository.deleteTrip(tripId) }
    suspend fun createExpense(draft: ExpenseDraft) = runCatching { repository.createExpense(tripId, draft) }
    suspend fun updateExpense(id: String, draft: ExpenseDraft) = runCatching { repository.updateExpense(id, draft) }
    suspend fun deleteExpense(id: String) = runCatching { repository.deleteExpense(id) }
    suspend fun retryExpense(id: String) = runCatching { repository.retryExpense(id) }
    suspend fun discardExpense(id: String) = runCatching { repository.discardExpense(id) }
    suspend fun removeMember(userId: String) = runCatching { repository.removeMember(tripId, userId) }
    suspend fun leaveTrip() = runCatching { repository.leaveTrip(tripId) }

    fun memberName(userId: String): String = _state.value.members.firstOrNull { it.userId == userId }
        ?.displayName ?: if (userId == accountId) "You" else "Traveller"
}

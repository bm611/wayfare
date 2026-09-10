package com.wayfare.app.feature

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wayfare.app.AppContainer
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class AuthUiState(
    val initializing: Boolean = true,
    val userId: String? = null,
    val busy: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val recovery: Boolean = false,
)

class AuthViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    init {
        if (!container.configured) {
            _state.value = AuthUiState(
                initializing = false,
                error = "Missing Supabase configuration. Add VITE_SUPABASE_URL and VITE_SUPABASE_PUBLISHABLE_KEY to the repository .env.local file.",
            )
        } else {
            viewModelScope.launch {
                container.supabase.auth.sessionStatus.collect { status ->
                    _state.value = when (status) {
                        is SessionStatus.Authenticated -> _state.value.copy(
                            initializing = false, userId = status.session.user?.id, busy = false, error = null,
                        )
                        SessionStatus.Initializing -> _state.value.copy(initializing = true)
                        is SessionStatus.NotAuthenticated -> _state.value.copy(
                            initializing = false, userId = null, busy = false,
                        )
                        is SessionStatus.RefreshFailure -> _state.value.copy(
                            initializing = false, userId = null, busy = false,
                            error = "Your session expired. Sign in again.",
                        )
                    }
                }
            }
        }
    }

    fun signIn(email: String, password: String) = launchAction {
        container.supabase.auth.signInWith(Email) {
            this.email = email.trim()
            this.password = password
        }
    }

    fun signUp(name: String, email: String, password: String) = launchAction(
        success = "Check your inbox to confirm your account.",
    ) {
        container.supabase.auth.signUpWith(Email) {
            this.email = email.trim()
            this.password = password
            data = buildJsonObject { put("display_name", name.trim()) }
        }
    }

    fun requestReset(email: String) = launchAction(success = "Password reset email sent.") {
        container.supabase.auth.resetPasswordForEmail(email.trim(), redirectUrl = "wayfare://auth")
    }

    fun resendConfirmation(email: String) = launchAction(success = "Confirmation email sent again.") {
        container.supabase.auth.resendEmail(OtpType.Email.SIGNUP, email.trim())
    }

    fun updatePassword(password: String) = launchAction(success = "Password updated.") {
        container.supabase.auth.updateUser { this.password = password }
        _state.value = _state.value.copy(recovery = false)
    }

    fun beginRecovery() {
        _state.value = _state.value.copy(recovery = true)
    }

    fun cancelRecovery() {
        _state.value = _state.value.copy(recovery = false)
    }

    fun signOut() = launchAction {
        val accountId = container.supabase.auth.currentUserOrNull()?.id
        container.supabase.auth.signOut()
        container.preferences.setPendingInvite(null)
        accountId?.let { container.repository.clearAccount(it) }
    }

    fun clearNotice() {
        _state.value = _state.value.copy(error = null, message = null)
    }

    private fun launchAction(success: String? = null, action: suspend () -> Unit) {
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, error = null, message = null)
            runCatching { action() }
                .onSuccess { _state.value = _state.value.copy(busy = false, message = success) }
                .onFailure { error ->
                    _state.value = _state.value.copy(
                        busy = false,
                        error = error.message ?: "Something went wrong. Try again.",
                    )
                }
        }
    }
}

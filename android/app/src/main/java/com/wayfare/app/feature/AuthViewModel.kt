package com.wayfare.app.feature

import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.GetCredentialUnsupportedException
import androidx.credentials.exceptions.NoCredentialException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wayfare.app.AppContainer
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.compose.auth.composable.NativeSignInResult
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
    /** Tracked apart from [busy] so the Google button and the email form spin independently. */
    val googleBusy: Boolean = false,
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
                            initializing = false, userId = status.session.user?.id, busy = false,
                            googleBusy = false, error = null,
                        )
                        SessionStatus.Initializing -> _state.value.copy(initializing = true)
                        is SessionStatus.NotAuthenticated -> _state.value.copy(
                            initializing = false, userId = null, busy = false, googleBusy = false,
                        )
                        is SessionStatus.RefreshFailure -> _state.value.copy(
                            initializing = false, userId = null, busy = false, googleBusy = false,
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

    /** The Credential Manager sheet is up; keep the button from being pressed twice. */
    fun onGoogleFlowStarted() {
        _state.value = _state.value.copy(googleBusy = true, error = null, message = null)
    }

    fun onGoogleResult(result: NativeSignInResult) {
        _state.value = _state.value.copy(googleBusy = false)
        when (result) {
            is NativeSignInResult.Success, NativeSignInResult.ClosedByUser -> Unit
            is NativeSignInResult.NetworkError -> _state.value = _state.value.copy(error = result.message)
            is NativeSignInResult.Error ->
                // Only a device that *cannot* do this is worth silently rerouting. Sending
                // every failure to the browser once hid a "[28444] Developer console is not
                // set up correctly" behind a flow that looked like it half-worked.
                if (result.exception.isNativeUnsupported()) {
                    signInWithGoogleInBrowser()
                } else {
                    _state.value = _state.value.copy(error = result.message)
                }
        }
    }

    /** No Google account on the device, or no Credential Manager to ask — not a setup error. */
    private fun Exception?.isNativeUnsupported() = when (this) {
        is NoCredentialException,
        is GetCredentialProviderConfigurationException,
        is GetCredentialUnsupportedException,
        -> true
        else -> false
    }

    /** Used both as the ComposeAuth fallback and as the recovery path from a native failure. */
    fun signInWithGoogleInBrowser() {
        viewModelScope.launch {
            _state.value = _state.value.copy(googleBusy = true, error = null, message = null)
            val outcome = runCatching { container.supabase.auth.signInWith(Google) }
            // This returns once the Custom Tab is open, not once Google answers — the session
            // arrives later through the wayfare://auth deeplink, so stop spinning now rather
            // than waiting on a hop this coroutine never sees.
            _state.value = _state.value.copy(
                googleBusy = false,
                error = outcome.exceptionOrNull()?.let { it.message ?: "Could not reach Google. Try again." },
            )
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

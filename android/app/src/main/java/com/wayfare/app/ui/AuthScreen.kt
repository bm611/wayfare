package com.wayfare.app.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.wayfare.app.AppContainer
import com.wayfare.app.R
import com.wayfare.app.feature.AuthUiState
import com.wayfare.app.feature.AuthViewModel
import io.github.jan.supabase.compose.auth.composable.GoogleDialogType
import io.github.jan.supabase.compose.auth.composable.rememberSignInWithGoogle
import io.github.jan.supabase.compose.auth.composeAuth

private enum class AuthMode { SignIn, SignUp, Reset }

private val FieldShape = RoundedCornerShape(8.dp)

@Composable
fun AuthScreen(state: AuthUiState, viewModel: AuthViewModel, container: AppContainer) {
    var mode by rememberSaveable { mutableStateOf(AuthMode.SignIn) }
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var localError by rememberSaveable { mutableStateOf<String?>(null) }

    // Credential Manager where the device supports it. ComposeAuth runs `fallback` when it
    // does not; onGoogleResult sends a device that tries and fails down the same path.
    val googleSignIn = container.supabase.composeAuth.rememberSignInWithGoogle(
        onResult = viewModel::onGoogleResult,
        type = GoogleDialogType.BOTTOM_SHEET,
        fallback = { viewModel.signInWithGoogleInBrowser() },
    )

    Box(Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(R.drawable.login_background),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to CanvasWhite.copy(alpha = 0.04f),
                    0.28f to CanvasWhite.copy(alpha = 0.5f),
                    0.48f to CanvasWhite.copy(alpha = 0.82f),
                    1f to CanvasWhite.copy(alpha = 0.9f),
                ),
            ),
        )
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 24.dp, vertical = 24.dp).animateContentSize(),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.Start,
        ) {
        Brand()
        Spacer(Modifier.height(20.dp))
        Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
            Text(
                when (mode) {
                    AuthMode.SignIn -> "Go places.\nKeep count."
                    AuthMode.SignUp -> "Your next chapter."
                    AuthMode.Reset -> "A fresh start."
                },
                style = androidx.compose.material3.MaterialTheme.typography.displaySmall,
            )
            Text(
                when (mode) {
                    AuthMode.SignIn -> "A little ledger for your next big chapter."
                    AuthMode.SignUp -> "Create an account to keep every trip in one place."
                    AuthMode.Reset -> "We’ll email you a secure password reset link."
                },
                Modifier.padding(top = 14.dp), color = Ink.copy(alpha = 0.76f),
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            )
        }
        Spacer(Modifier.height(24.dp))

        if (mode != AuthMode.Reset) {
            GoogleButton(Modifier.fillMaxWidth(), busy = state.googleBusy) {
                localError = null
                viewModel.onGoogleFlowStarted()
                googleSignIn.startFlow()
            }
            Row(
                Modifier.fillMaxWidth().padding(vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HorizontalDivider(Modifier.weight(1f), color = Hairline)
                Text("or", Modifier.padding(horizontal = 12.dp), color = Ash, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                HorizontalDivider(Modifier.weight(1f), color = Hairline)
            }
        }

        if (mode == AuthMode.SignUp) {
            AuthField(name, { name = it }, "Display name", ImeAction.Next)
            Spacer(Modifier.height(12.dp))
        }
        AuthField(
            email, { email = it }, "Email", if (mode == AuthMode.Reset) ImeAction.Done else ImeAction.Next,
            keyboardType = KeyboardType.Email,
        )
        if (mode != AuthMode.Reset) {
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                password, { password = it }, Modifier.fillMaxWidth(), label = { Text("Password") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                shape = FieldShape,
                colors = wayfareFieldColors(),
            )
        }

        (localError ?: state.error)?.let { Notice(it, error = true, modifier = Modifier.padding(top = 14.dp)) }
        state.message?.let { Notice(it, modifier = Modifier.padding(top = 14.dp)) }

        PrimaryButton(
            text = when (mode) {
                AuthMode.SignIn -> "Sign in"
                AuthMode.SignUp -> "Create account"
                AuthMode.Reset -> "Send reset link"
            },
            modifier = Modifier.fillMaxWidth().padding(top = 22.dp),
            busy = state.busy,
        ) {
            localError = when {
                email.isBlank() -> "Enter your email address."
                mode == AuthMode.SignUp && name.isBlank() -> "Enter the name your travel companions will see."
                mode != AuthMode.Reset && password.length < 6 -> "Use a password of at least six characters."
                else -> null
            }
            if (localError == null) when (mode) {
                AuthMode.SignIn -> viewModel.signIn(email, password)
                AuthMode.SignUp -> viewModel.signUp(name, email, password)
                AuthMode.Reset -> viewModel.requestReset(email)
            }
        }

        TextButton(
            onClick = {
                mode = if (mode == AuthMode.SignIn) AuthMode.SignUp else AuthMode.SignIn
                localError = null
                viewModel.clearNotice()
            },
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 6.dp),
        ) {
            Text(
                if (mode == AuthMode.SignIn) "New here? Create an account" else "Back to sign in",
                color = Ink,
                style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
            )
        }
        if (mode == AuthMode.SignIn) {
            TextButton(
                onClick = { mode = AuthMode.Reset; localError = null; viewModel.clearNotice() },
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) { Text("Forgot your password?", color = Ash) }
        }
        if (state.message?.contains("confirm", ignoreCase = true) == true && email.isNotBlank()) {
            TextButton(
                enabled = !state.busy,
                onClick = { viewModel.resendConfirmation(email) },
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) { Text("Resend confirmation", color = Ink, style = androidx.compose.material3.MaterialTheme.typography.labelMedium) }
        }
        }
    }
}

@Composable
private fun AuthField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    imeAction: ImeAction,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value, onChange, Modifier.fillMaxWidth(), label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        shape = FieldShape,
        colors = wayfareFieldColors(),
    )
}

@Composable
fun RecoveryScreen(state: AuthUiState, viewModel: AuthViewModel) {
    var password by rememberSaveable { mutableStateOf("") }
    var confirmation by rememberSaveable { mutableStateOf("") }
    var localError by rememberSaveable { mutableStateOf<String?>(null) }
    Column(
        Modifier.fillMaxSize().background(CanvasWhite).imePadding().padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Brand()
        Text("Choose a new password", Modifier.padding(top = 40.dp), style = androidx.compose.material3.MaterialTheme.typography.displaySmall)
        Text(
            "Once saved, this password works on both Android and the web app.",
            Modifier.padding(top = 10.dp, bottom = 26.dp),
            color = Ash,
        )
        OutlinedTextField(
            password, { password = it }, Modifier.fillMaxWidth(), label = { Text("New password") },
            visualTransformation = PasswordVisualTransformation(), singleLine = true,
            shape = FieldShape,
            colors = wayfareFieldColors(),
        )
        OutlinedTextField(
            confirmation, { confirmation = it }, Modifier.fillMaxWidth().padding(top = 12.dp),
            label = { Text("Confirm password") }, visualTransformation = PasswordVisualTransformation(), singleLine = true,
            shape = FieldShape,
            colors = wayfareFieldColors(),
        )
        (localError ?: state.error)?.let { Notice(it, true, Modifier.padding(top = 14.dp)) }
        PrimaryButton("Save password", Modifier.fillMaxWidth().padding(top = 22.dp), state.busy) {
            localError = when {
                password.length < 6 -> "Use a password of at least six characters."
                password != confirmation -> "Those passwords do not match."
                else -> null
            }
            if (localError == null) viewModel.updatePassword(password)
        }
        TextButton(onClick = viewModel::cancelRecovery, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Cancel", color = Ink)
        }
    }
}

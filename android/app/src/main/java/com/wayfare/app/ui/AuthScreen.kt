package com.wayfare.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wayfare.app.feature.AuthUiState
import com.wayfare.app.feature.AuthViewModel

private enum class AuthMode { SignIn, SignUp, Reset }

@Composable
fun AuthScreen(state: AuthUiState, viewModel: AuthViewModel) {
    var mode by rememberSaveable { mutableStateOf(AuthMode.SignIn) }
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var localError by rememberSaveable { mutableStateOf<String?>(null) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.Start,
    ) {
        Brand()
        Spacer(Modifier.height(48.dp))
        Text(
            when (mode) {
                AuthMode.SignIn -> "Your trips, ready when you are."
                AuthMode.SignUp -> "Make room for the next stamp."
                AuthMode.Reset -> "Find your way back in."
            },
            fontSize = 34.sp,
            lineHeight = 38.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.7).sp,
        )
        Text(
            when (mode) {
                AuthMode.SignIn -> "Sign in to pick up your travel ledger."
                AuthMode.SignUp -> "Create an account to keep every trip in one place."
                AuthMode.Reset -> "We’ll email you a secure password reset link."
            },
            Modifier.padding(top = 10.dp, bottom = 28.dp),
            color = InkSoft,
            lineHeight = 21.sp,
        )

        if (mode == AuthMode.SignUp) {
            OutlinedTextField(
                name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Display name") },
                singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )
            Spacer(Modifier.height(12.dp))
        }
        OutlinedTextField(
            email, { email = it }, Modifier.fillMaxWidth(), label = { Text("Email") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = if (mode == AuthMode.Reset) ImeAction.Done else ImeAction.Next),
        )
        if (mode != AuthMode.Reset) {
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                password, { password = it }, Modifier.fillMaxWidth(), label = { Text("Password") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            )
        }

        (localError ?: state.error)?.let { Notice(it, error = true, modifier = Modifier.padding(top = 14.dp)) }
        state.message?.let { Notice(it, modifier = Modifier.padding(top = 14.dp)) }

        ClayButton(
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
            Text(if (mode == AuthMode.SignIn) "New here? Create an account" else "Back to sign in")
        }
        if (mode == AuthMode.SignIn) {
            TextButton(
                onClick = { mode = AuthMode.Reset; localError = null; viewModel.clearNotice() },
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) { Text("Forgot your password?") }
        }
        if (state.message?.contains("confirm", ignoreCase = true) == true && email.isNotBlank()) {
            TextButton(
                enabled = !state.busy,
                onClick = { viewModel.resendConfirmation(email) },
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) { Text("Resend confirmation") }
        }
    }
}

@Composable
fun RecoveryScreen(state: AuthUiState, viewModel: AuthViewModel) {
    var password by rememberSaveable { mutableStateOf("") }
    var confirmation by rememberSaveable { mutableStateOf("") }
    var localError by rememberSaveable { mutableStateOf<String?>(null) }
    Column(
        Modifier.fillMaxSize().imePadding().padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Brand()
        Text("Choose a new password", Modifier.padding(top = 44.dp), fontSize = 34.sp, fontWeight = FontWeight.SemiBold)
        Text("Once saved, this password works on both Android and the web app.", Modifier.padding(top = 10.dp, bottom = 26.dp), color = InkSoft)
        OutlinedTextField(
            password, { password = it }, Modifier.fillMaxWidth(), label = { Text("New password") },
            visualTransformation = PasswordVisualTransformation(), singleLine = true,
        )
        OutlinedTextField(
            confirmation, { confirmation = it }, Modifier.fillMaxWidth().padding(top = 12.dp),
            label = { Text("Confirm password") }, visualTransformation = PasswordVisualTransformation(), singleLine = true,
        )
        (localError ?: state.error)?.let { Notice(it, true, Modifier.padding(top = 14.dp)) }
        ClayButton("Save password", Modifier.fillMaxWidth().padding(top = 22.dp), state.busy) {
            localError = when {
                password.length < 6 -> "Use a password of at least six characters."
                password != confirmation -> "Those passwords do not match."
                else -> null
            }
            if (localError == null) viewModel.updatePassword(password)
        }
        TextButton(onClick = viewModel::cancelRecovery, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Cancel")
        }
    }
}

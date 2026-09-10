package com.wayfare.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/**
 * Runs [onResume] each time the screen comes back to the foreground. Wayfare is
 * collaborative, so a trip left open on a locked phone should not still be
 * showing yesterday's ledger when it is picked back up.
 */
@Composable
fun OnResume(onResume: () -> Unit) {
    val owner = LocalLifecycleOwner.current
    val handler by rememberUpdatedState(onResume)
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) handler()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
}

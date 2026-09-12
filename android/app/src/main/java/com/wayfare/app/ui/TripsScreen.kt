package com.wayfare.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.ConfirmationNumber
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wayfare.app.AppContainer
import com.wayfare.app.core.TripPhase
import com.wayfare.app.core.tripPhase
import com.wayfare.app.feature.TripsViewModel
import kotlinx.coroutines.launch

@Composable
fun TripsScreen(
    viewModel: TripsViewModel,
    container: AppContainer,
    onOpenTrip: (String) -> Unit,
    onSignOut: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pendingInvite by container.preferences.pendingInvite.collectAsState(initial = null)
    var showTripForm by remember { mutableStateOf(false) }
    var showJoin by remember { mutableStateOf(false) }
    var inviteSeed by remember { mutableStateOf("") }
    var showMenu by remember { mutableStateOf(false) }

    LaunchedEffect(pendingInvite) {
        pendingInvite?.let {
            inviteSeed = it
            showJoin = true
        }
    }

    OnResume(viewModel::onResume)

    Scaffold(containerColor = CanvasWhite) { insets ->
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize().padding(insets),
        ) {
            LazyColumn(
                Modifier.fillMaxSize(),
                // Photographs read as distinct objects only if the canvas between
                // them is wide enough to be read as canvas.
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                // Top nav: wordmark, circular controls, one hairline underneath.
                item {
                    Column {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 8.dp, bottom = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Brand(Modifier.weight(1f))
                            CircleIconButton(Icons.Outlined.ConfirmationNumber, "Join a trip") { showJoin = true }
                            Spacer(Modifier.size(8.dp))
                            Box {
                                CircleIconButton(Icons.Outlined.Person, "Account") { showMenu = true }
                                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                    DropdownMenuItem(
                                        text = { Text("Refresh") },
                                        leadingIcon = { Icon(Icons.Outlined.Refresh, null) },
                                        onClick = { showMenu = false; viewModel.refresh() },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Sign out") },
                                        leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Logout, null) },
                                        onClick = { showMenu = false; onSignOut() },
                                    )
                                }
                            }
                        }
                        HairlineDivider()
                    }
                }
                item {
                    Column(Modifier.padding(horizontal = 24.dp)) {
                        Text("Your trips", style = MaterialTheme.typography.displaySmall)
                        Text(
                            if (state.trips.isEmpty()) "Your first journey is waiting."
                            else "${state.trips.size} ${if (state.trips.size == 1) "trip" else "trips"}. All your plans, in one place.",
                            Modifier.padding(top = 6.dp, bottom = 20.dp),
                            color = Ash,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            PrimaryButton("New trip", Modifier.weight(1f), icon = Icons.Outlined.Add) { showTripForm = true }
                            SecondaryButton("Join friend", Modifier.weight(1f)) { showJoin = true }
                        }
                    }
                }
                state.error?.let { message ->
                    item { Notice(message, true, Modifier.padding(horizontal = 24.dp)) }
                }
                if (state.loading) {
                    item {
                        Column(Modifier.fillMaxWidth().padding(48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Rausch)
                        }
                    }
                } else if (state.trips.isEmpty()) {
                    item {
                        Column(
                            Modifier.fillMaxWidth().padding(horizontal = 24.dp)
                                .clip(RoundedCornerShape(14.dp)).background(SoftCloud).padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                Modifier.size(64.dp).clip(CircleShape).background(CanvasWhite),
                                contentAlignment = Alignment.Center,
                            ) { Icon(Icons.Outlined.Explore, null, tint = Rausch, modifier = Modifier.size(28.dp)) }
                            Text(
                                "Good trips start here",
                                Modifier.padding(top = 18.dp),
                                style = MaterialTheme.typography.titleLarge,
                            )
                            Text(
                                "Add a trip, set a budget, and log each cost as it lands.",
                                Modifier.padding(top = 6.dp),
                                color = Ash,
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                            )
                            TextButton(onClick = { showJoin = true }, Modifier.padding(top = 8.dp)) {
                                Text("I have an invite code", color = Ink, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                } else {
                    val sections = listOf(
                        "Active trips" to state.trips.filter { tripPhase(it.trip) is TripPhase.Active },
                        "Upcoming trips" to state.trips.filter { tripPhase(it.trip) is TripPhase.Upcoming },
                        "Dates open" to state.trips.filter { tripPhase(it.trip) is TripPhase.Undated },
                        "Past trips" to state.trips.filter { tripPhase(it.trip) is TripPhase.Past },
                    )
                    sections.forEach { (title, trips) ->
                        if (trips.isNotEmpty()) {
                            item(key = title) {
                                Row(
                                    Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(title, style = MaterialTheme.typography.headlineSmall)
                                    Text(
                                        trips.size.toString(),
                                        Modifier.padding(start = 8.dp),
                                        color = Ash,
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                }
                            }
                            items(trips, key = { it.trip.id }) { summary ->
                                ListingCard(
                                    summary = summary,
                                    coverUrl = container.repository.coverUrl(summary.trip.coverPath),
                                    modifier = Modifier.padding(horizontal = 24.dp).animateItem(),
                                    onClick = { onOpenTrip(summary.trip.id) },
                                )
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(28.dp)) }
            }
        }
    }

    if (showTripForm) TripFormDialog(
        onDismiss = { showTripForm = false },
        onSave = viewModel::createTrip,
    )
    if (showJoin) JoinTripDialog(
        initialCode = inviteSeed,
        onDismiss = { showJoin = false },
        onJoin = { code ->
            viewModel.joinTrip(code).onSuccess { tripId ->
                container.preferences.setPendingInvite(null)
                showJoin = false
                inviteSeed = ""
                onOpenTrip(tripId)
            }
        },
    )
}

@Composable
private fun JoinTripDialog(
    initialCode: String,
    onDismiss: () -> Unit,
    onJoin: suspend (String) -> Result<*>,
) {
    var code by rememberSaveable(initialCode) { mutableStateOf(initialCode.uppercase().take(8)) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(14.dp),
        title = { Text("Join a trip", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column {
                Text(
                    "Enter the eight-character code your travel companion sent you.",
                    color = Ash,
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    code, { code = it.uppercase().filter(Char::isLetterOrDigit).take(8) },
                    Modifier.fillMaxWidth().padding(top = 16.dp), label = { Text("Invite code") }, singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    colors = wayfareFieldColors(),
                )
                error?.let { Notice(it, true, Modifier.padding(top = 12.dp)) }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy, onClick = {
                if (code.length != 8) error = "Enter all eight characters."
                else scope.launch {
                    busy = true
                    onJoin(code).onFailure { error = it.message ?: "That code did not work." }
                    busy = false
                }
            }) { Text(if (busy) "Joining…" else "Join", color = Rausch, style = MaterialTheme.typography.labelMedium) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Ink, style = MaterialTheme.typography.labelMedium) } },
    )
}

/**
 * Inputs sit on white behind a hairline border and switch to Ink on focus —
 * the system never tints a field with the accent.
 */
@Composable
fun wayfareFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Ink,
    unfocusedBorderColor = Hairline,
    errorBorderColor = ErrorRed,
    focusedTextColor = Charcoal,
    unfocusedTextColor = Ink,
    focusedLabelColor = Ash,
    unfocusedLabelColor = Ash,
    errorLabelColor = ErrorRed,
    cursorColor = Ink,
    focusedContainerColor = CanvasWhite,
    unfocusedContainerColor = CanvasWhite,
)

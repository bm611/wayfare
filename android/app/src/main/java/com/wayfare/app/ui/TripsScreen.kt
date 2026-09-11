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
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wayfare.app.AppContainer
import com.wayfare.app.core.TripPhase
import com.wayfare.app.core.TripSummary
import com.wayfare.app.core.money
import com.wayfare.app.core.tripPhase
import com.wayfare.app.feature.TripsViewModel
import kotlinx.coroutines.launch
import java.math.BigDecimal

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

    Scaffold(containerColor = Paper) { insets ->
        PullToRefreshBox(
            isRefreshing = state.refreshing && !state.loading,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize().padding(insets),
        ) {
            LazyColumn(
                Modifier.fillMaxSize().padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(top = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Brand(Modifier.weight(1f))
                        IconButton(
                            onClick = { showJoin = true },
                            modifier = Modifier.clip(CircleShape).background(PaperDeep).size(48.dp),
                        ) { Icon(Icons.Outlined.ConfirmationNumber, "Join a trip", Modifier.size(20.dp)) }
                        Spacer(Modifier.size(8.dp))
                        Box {
                            IconButton(
                                onClick = { showMenu = true },
                                modifier = Modifier.clip(CircleShape).background(PaperDeep).size(48.dp),
                            ) { Icon(Icons.Outlined.Person, "Account", Modifier.size(20.dp)) }
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
                }
                item {
                    Column {
                        Text("Your next chapter.", style = androidx.compose.material3.MaterialTheme.typography.displaySmall)
                        Text(
                            if (state.trips.isEmpty()) "Your first journey is waiting."
                            else "${state.trips.size} ${if (state.trips.size == 1) "trip" else "trips"}. All your plans, in one place.",
                            Modifier.padding(top = 6.dp, bottom = 18.dp),
                            color = InkSoft,
                            fontSize = 15.sp,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PrimaryButton("New trip", icon = Icons.Outlined.Add) { showTripForm = true }
                            TextButton(onClick = { showJoin = true }, modifier = Modifier.padding(start = 12.dp)) {
                                Text("Join a friend", color = Ink, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
                state.error?.let { message -> item { Notice(message, true) } }
                if (state.loading) {
                    item {
                        Column(Modifier.fillMaxWidth().padding(48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Clay)
                        }
                    }
                } else if (state.trips.isEmpty()) {
                    item {
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(PaperDeep).padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                Modifier.size(72.dp).clip(CircleShape).background(ClayWash),
                                contentAlignment = Alignment.Center,
                            ) { Icon(Icons.Outlined.Explore, null, tint = Clay, modifier = Modifier.size(32.dp)) }
                            Text(
                                "Good trips start here",
                                Modifier.padding(top = 18.dp),
                                fontSize = 19.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                "Add a trip, set a budget, and log each cost as it lands.",
                                Modifier.padding(top = 6.dp),
                                color = InkSoft,
                                fontSize = 14.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                            TextButton(onClick = { showJoin = true }, Modifier.padding(top = 6.dp)) {
                                Text("I have an invite code", color = Clay, fontWeight = FontWeight.SemiBold)
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
                                Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        trips.size.toString(),
                                        Modifier.padding(start = 8.dp),
                                        color = InkFaint,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium,
                                    )
                                }
                            }
                            items(trips, key = { it.trip.id }) { summary ->
                                TicketCard(
                                    summary = summary,
                                    coverUrl = container.repository.coverUrl(summary.trip.coverPath),
                                    modifier = Modifier.animateItem(),
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
        title = { Text("Join a trip", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("Enter the eight-character code your travel companion sent you.", color = InkSoft)
                OutlinedTextField(
                    code, { code = it.uppercase().filter(Char::isLetterOrDigit).take(8) },
                    Modifier.fillMaxWidth().padding(top = 16.dp), label = { Text("Invite code") }, singleLine = true,
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
            }) { Text(if (busy) "Joining…" else "Join", color = Clay, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Ink) } },
    )
}

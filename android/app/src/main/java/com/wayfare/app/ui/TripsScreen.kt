package com.wayfare.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wayfare.app.AppContainer
import com.wayfare.app.core.TripPhase
import com.wayfare.app.core.TripSummary
import com.wayfare.app.core.tripPhase
import com.wayfare.app.feature.TripsViewModel
import java.math.BigDecimal
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
    var showAdd by remember { mutableStateOf(false) }
    var showDeleteAccount by remember { mutableStateOf(false) }

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
                // Top nav: the wordmark, then add and account as circular controls.
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 8.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Brand(Modifier.weight(1f))
                        Box {
                            Box(
                                Modifier.size(48.dp).clip(CircleShape).background(Accent)
                                    .clickable(onClickLabel = "Add a trip") { showAdd = true }
                                    .semantics { contentDescription = "Add a trip" },
                                contentAlignment = Alignment.Center,
                            ) { Icon(Icons.Outlined.Add, null, Modifier.size(20.dp), tint = OnAccent) }
                            DropdownMenu(expanded = showAdd, onDismissRequest = { showAdd = false }) {
                                DropdownMenuItem(
                                    text = { Text("New trip") },
                                    leadingIcon = { Icon(Icons.Outlined.Add, null) },
                                    onClick = { showAdd = false; showTripForm = true },
                                )
                                DropdownMenuItem(
                                    text = { Text("Join with a code") },
                                    leadingIcon = { Icon(Icons.Outlined.ConfirmationNumber, null) },
                                    onClick = { showAdd = false; showJoin = true },
                                )
                            }
                        }
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
                                HorizontalDivider(Modifier.padding(vertical = 4.dp), color = Hairline)
                                DropdownMenuItem(
                                    text = { Text("Delete account", color = ErrorRed) },
                                    leadingIcon = { Icon(Icons.Outlined.DeleteOutline, null, tint = ErrorRed) },
                                    onClick = { showMenu = false; showDeleteAccount = true },
                                )
                            }
                        }
                    }
                }
                item {
                    Column(Modifier.padding(horizontal = 24.dp)) {
                        Text("Your trips", style = MaterialTheme.typography.displaySmall)
                        if (state.trips.isEmpty()) Text(
                            "Your first journey is waiting.",
                            Modifier.padding(top = 6.dp),
                            color = Ash,
                            style = MaterialTheme.typography.bodyLarge,
                        ) else PassportSummary(state.trips, container, Modifier.padding(top = 16.dp))
                    }
                }
                state.error?.let { message ->
                    item { Notice(message, true, Modifier.padding(horizontal = 24.dp)) }
                }
                if (state.loading) {
                    item {
                        Column(Modifier.fillMaxWidth().padding(48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            TripLoadingSkeleton(Modifier.padding(horizontal = 24.dp))
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
                            ) { Icon(Icons.Outlined.Explore, null, tint = AccentInk, modifier = Modifier.size(28.dp)) }
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
                            PrimaryButton("New trip", Modifier.padding(top = 20.dp), icon = Icons.Outlined.Add) { showTripForm = true }
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
                            // Trips still to come get a full card; finished ones are
                            // collected, stamps on a passport page, two across.
                            if (title == "Past trips") {
                                items(trips.chunked(2), key = { row -> row.first().trip.id }) { row ->
                                    Row(
                                        Modifier.fillMaxWidth().padding(horizontal = 24.dp).animateItem(),
                                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    ) {
                                        row.forEach { summary ->
                                            StampTile(summary, Modifier.weight(1f)) { onOpenTrip(summary.trip.id) }
                                        }
                                        if (row.size == 1) Spacer(Modifier.weight(1f))
                                    }
                                }
                            } else {
                                items(trips, key = { it.trip.id }) { summary ->
                                    ListingCard(
                                        summary = summary,
                                        modifier = Modifier.padding(horizontal = 24.dp).animateItem(),
                                        onClick = { onOpenTrip(summary.trip.id) },
                                    )
                                }
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
    if (showDeleteAccount) {
        val accountId = container.repository.currentUserId()
        val owned = state.trips.filter { it.trip.ownerId == accountId }
        DeleteAccountDialog(
            trips = owned.size,
            expenses = owned.sumOf { it.entries },
            onDismiss = { showDeleteAccount = false },
            onDelete = {
                viewModel.deleteAccount().onSuccess {
                    container.preferences.setPendingInvite(null)
                }
            },
        )
    }
}

/**
 * The passport at a glance: stamps collected, places they name, and what they
 * cost, in euros whatever each trip is budgeted in.
 */
@Composable
private fun PassportSummary(trips: List<TripSummary>, container: AppContainer, modifier: Modifier = Modifier) {
    val places = trips.map { stampLabel(it.trip).lowercase() }.toSet().size
    val spent = trips.fold(BigDecimal.ZERO) { total, summary ->
        total + (container.fx.convert(summary.spent, summary.trip.currency, "EUR") ?: BigDecimal.ZERO)
    }
    val stamps = trips.size
    Row(
        modifier.height(IntrinsicSize.Min).clearAndSetSemantics {
            contentDescription = "$stamps ${if (stamps == 1) "trip" else "trips"}, " +
                "$places ${if (places == 1) "place" else "places"}, ${moneyShort(spent)} spent"
        },
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        SummaryStat("$stamps", if (stamps == 1) "STAMP" else "STAMPS")
        VerticalDivider(color = Hairline)
        SummaryStat("$places", if (places == 1) "PLACE" else "PLACES")
        VerticalDivider(color = Hairline)
        SummaryStat(moneyShort(spent), "SPENT")
    }
}

@Composable
private fun SummaryStat(value: String, label: String) {
    Column {
        Text(value, maxLines = 1, style = MaterialTheme.typography.headlineSmall)
        Text(
            label, Modifier.padding(top = 4.dp), color = Ash,
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontSize = 10.sp, letterSpacing = 0.08.em),
        )
    }
}

/**
 * Play requires an app that lets people create an account to let them delete it
 * from inside the app as well.
 *
 * The consequence that catches people out is that a trip they own goes for every
 * traveller on it, so the counts are on screen before the button is reachable.
 */
@Composable
internal fun DeleteAccountDialog(
    trips: Int,
    expenses: Int,
    onDismiss: () -> Unit,
    onDelete: suspend () -> Result<*>,
) {
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        shape = RoundedCornerShape(14.dp),
        title = { Text("Delete your account?", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    if (trips == 0) {
                        "Your account is removed immediately, along with everything stored on it."
                    } else {
                        "Your account is removed immediately, along with the " +
                            "${trips} ${if (trips == 1) "trip" else "trips"} you own and the " +
                            "${expenses} ${if (expenses == 1) "expense" else "expenses"} logged on them."
                    },
                    color = Ash,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "Trips someone else owns stay with them — you just lose access.",
                    color = Ash,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    error ?: "This cannot be undone.",
                    color = ErrorRed,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        confirmButton = {
            TextButton(enabled = !busy, onClick = {
                scope.launch {
                    busy = true
                    error = null
                    // Not the exception text: that is the whole REST exchange, bearer
                    // token included.
                    onDelete().onFailure { error = "Could not delete the account. Check your connection and try again." }
                    busy = false
                }
            }) {
                Text(
                    if (busy) "Deleting…" else "Delete",
                    color = ErrorRed,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = onDismiss) {
                Text("Cancel", color = Ink, style = MaterialTheme.typography.labelMedium)
            }
        },
    )
}

@Composable
internal fun JoinTripDialog(
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
                WayfareInput(
                    code, { code = it.uppercase().filter(Char::isLetterOrDigit).take(8); error = null },
                    "Invite code", Modifier.padding(top = 16.dp), error = error,
                )

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
            }) { Text(if (busy) "Joining…" else "Join", color = AccentInk, style = MaterialTheme.typography.labelMedium) }
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

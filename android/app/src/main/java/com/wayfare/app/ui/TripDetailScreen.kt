package com.wayfare.app.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.wayfare.app.AppContainer
import com.wayfare.app.core.Category
import com.wayfare.app.core.Expense
import com.wayfare.app.core.SyncState
import com.wayfare.app.core.Trip
import com.wayfare.app.core.TripMember
import com.wayfare.app.core.TripPhase
import com.wayfare.app.core.budgetSummary
import com.wayfare.app.core.dateRange
import com.wayfare.app.core.dayLabel
import com.wayfare.app.core.money
import com.wayfare.app.core.tripPhase
import com.wayfare.app.feature.TripDetailViewModel
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripDetailScreen(
    accountId: String,
    viewModel: TripDetailViewModel,
    container: AppContainer,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val trip = state.trip
    val paidIn by container.preferences.paidIn(trip?.id.orEmpty()).collectAsState(initial = "EUR")
    var showExpense by remember { mutableStateOf(false) }
    var editingExpense by remember { mutableStateOf<Expense?>(null) }
    var readOnlyExpense by remember { mutableStateOf<Expense?>(null) }
    var unsyncedExpense by remember { mutableStateOf<Expense?>(null) }
    var showEditTrip by remember { mutableStateOf(false) }
    var showDeleteTrip by remember { mutableStateOf(false) }
    var showShare by remember { mutableStateOf(false) }
    var showActions by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    OnResume(viewModel::onResume)

    Scaffold(
        containerColor = CanvasWhite,
        // The listing-detail chrome: circular controls on white, one hairline
        // underneath, and the trip name carried by the hero rather than the bar.
        topBar = {
            Column {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CircleIconButton(Icons.AutoMirrored.Outlined.ArrowBack, "Back", onClick = onBack)
                    Spacer(Modifier.weight(1f))
                    CircleIconButton(Icons.Outlined.Refresh, "Refresh", onClick = viewModel::refresh)
                    if (trip != null) {
                        CircleIconButton(Icons.Outlined.Group, "Share") { showShare = true }
                        if (trip.ownerId == accountId) {
                            Box {
                                CircleIconButton(Icons.Outlined.MoreHoriz, "Trip options") { showActions = true }
                                DropdownMenu(expanded = showActions, onDismissRequest = { showActions = false }) {
                                    DropdownMenuItem(
                                        text = { Text("Edit trip") },
                                        leadingIcon = { Icon(Icons.Outlined.Edit, null) },
                                        onClick = { showActions = false; showEditTrip = true },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Delete trip", color = ErrorRed) },
                                        leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = ErrorRed) },
                                        onClick = { showActions = false; showDeleteTrip = true },
                                    )
                                }
                            }
                        }
                    }
                }
                HairlineDivider()
            }
        },
        // Mobile collapses the sticky booking panel to a bottom-anchored bar:
        // the figure on the left, the one Rausch action on the right.
        bottomBar = {
            if (trip != null) {
                val summary = budgetSummary(trip, state.expenses)
                Column {
                    HairlineDivider()
                    Surface(color = CanvasWhite) {
                        Row(
                            Modifier.fillMaxWidth().navigationBarsPadding()
                                .padding(horizontal = 24.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f).padding(end = 16.dp)) {
                                Text(
                                    money(summary.spent, trip.currency),
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                )
                                Text(
                                    if (trip.budget.signum() > 0) "of ${money(trip.budget, trip.currency)}" else "logged so far",
                                    color = Ash,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                )
                            }
                            PrimaryButton("Add expense", icon = Icons.Outlined.Add) {
                                editingExpense = null
                                showExpense = true
                            }
                        }
                    }
                }
            }
        },
    ) { insets ->
        when {
            state.loading && trip == null -> Box(Modifier.fillMaxSize().padding(insets), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Rausch)
            }
            trip == null -> Column(Modifier.padding(insets).padding(24.dp)) {
                Notice(state.error ?: "That trip is no longer available.", true)
                TextButton(onClick = onBack) { Text("Back to all trips", color = Ink) }
            }
            else -> PullToRefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize().padding(insets),
            ) {
                TripDetailContent(
                    trip = trip,
                    state = state,
                    viewModel = viewModel,
                    coverUrl = container.repository.coverUrl(trip.coverPath),
                    modifier = Modifier,
                    onOpenExpense = { expense ->
                        when {
                            expense.syncState != SyncState.Synced -> unsyncedExpense = expense
                            expense.userId == accountId -> { editingExpense = expense; showExpense = true }
                            else -> readOnlyExpense = expense
                        }
                    },
                )
            }
        }
    }

    if (trip != null && showExpense) ExpenseFormDialog(
        expense = editingExpense,
        currency = trip.currency,
        initialPaidIn = paidIn,
        fx = container.fx,
        onDismiss = { showExpense = false; editingExpense = null },
        onSave = { draft ->
            editingExpense?.let { viewModel.updateExpense(it.id, draft) } ?: viewModel.createExpense(draft)
        },
        onDelete = editingExpense?.let { expense -> suspend { viewModel.deleteExpense(expense.id) } },
        onRememberCurrency = { container.preferences.rememberPaidIn(trip.id, it) },
    )
    if (trip != null && readOnlyExpense != null) ExpenseFormDialog(
        expense = readOnlyExpense,
        readOnly = true,
        currency = trip.currency,
        fx = container.fx,
        payer = viewModel.memberName(readOnlyExpense!!.userId),
        onDismiss = { readOnlyExpense = null },
        onSave = { Result.failure<Unit>(IllegalStateException("Read only")) },
    )
    unsyncedExpense?.let { expense ->
        UnsyncedExpenseDialog(
            expense = expense,
            currency = trip?.currency ?: "EUR",
            onDismiss = { unsyncedExpense = null },
            onRetry = { scope.launch { viewModel.retryExpense(expense.id); unsyncedExpense = null } },
            onDiscard = { scope.launch { viewModel.discardExpense(expense.id); unsyncedExpense = null } },
        )
    }
    if (trip != null && showEditTrip) TripFormDialog(
        trip,
        onDismiss = { showEditTrip = false },
        onSave = viewModel::updateTrip,
    )
    if (trip != null && showShare) ShareTripDialog(
        trip, state.members, accountId,
        onDismiss = { showShare = false },
        onRemove = viewModel::removeMember,
        onLeave = {
            viewModel.leaveTrip().onSuccess { showShare = false; onBack() }
        },
    )
    if (trip != null && showDeleteTrip) AlertDialog(
        onDismissRequest = { showDeleteTrip = false },
        shape = RoundedCornerShape(14.dp),
        title = { Text("Delete ${trip.name}?", style = MaterialTheme.typography.headlineSmall) },
        text = { Text("The trip and its entire expense ledger will be removed for every traveller.", color = Ash) },
        confirmButton = { TextButton(onClick = { scope.launch { viewModel.deleteTrip().onSuccess { onBack() }; showDeleteTrip = false } }) { Text("Delete", color = ErrorRed, style = MaterialTheme.typography.labelMedium) } },
        dismissButton = { TextButton(onClick = { showDeleteTrip = false }) { Text("Keep trip", color = Ink, style = MaterialTheme.typography.labelMedium) } },
    )
}

@Composable
private fun TripDetailContent(
    trip: Trip,
    state: com.wayfare.app.feature.TripDetailUiState,
    viewModel: TripDetailViewModel,
    coverUrl: String?,
    modifier: Modifier,
    onOpenExpense: (Expense) -> Unit,
) {
    // Failed entries never reached the server, so they stay out of every figure
    // the web app would also show.
    val counted = state.expenses.filter { it.syncState != SyncState.Failed }
    val budget = budgetSummary(trip, state.expenses)
    val phase = tripPhase(trip)

    LazyColumn(
        modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        state.error?.let { item { Notice(it, true, Modifier.padding(horizontal = 24.dp, vertical = 12.dp)) } }
        item { Hero(trip, coverUrl) }
        item {
            BookingPanel(
                trip = trip,
                spent = budget.spent,
                remaining = budget.remaining,
                perDay = budget.perDay,
                availablePerDay = budget.availablePerDay,
                daysLeft = budget.daysLeft,
                entries = counted.size,
                showRemaining = phase is TripPhase.Active && trip.budget.signum() > 0,
                memberCount = state.members.size,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
        }
        if (counted.isNotEmpty()) item {
            CategoryBreakdown(counted, trip.currency, viewModel::setCategory, Modifier.padding(horizontal = 24.dp))
        }
        item {
            Column(Modifier.padding(horizontal = 24.dp)) {
                Text("Ledger", style = MaterialTheme.typography.headlineSmall)
                // The search pill: full 32dp radius, hairline border, one soft shadow.
                OutlinedTextField(
                    state.query, viewModel::setQuery,
                    Modifier.fillMaxWidth().padding(top = 14.dp),
                    placeholder = { Text("Search titles and notes", color = Ash) },
                    leadingIcon = { Icon(Icons.Outlined.Search, null, tint = Ink, modifier = Modifier.size(18.dp)) },
                    singleLine = true,
                    shape = RoundedCornerShape(32.dp),
                    colors = wayfareFieldColors(),
                )
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterMenu(
                        label = state.category?.label ?: "All categories",
                        options = listOf("All categories" to null) + Category.entries.map { it.label to it },
                        onSelect = viewModel::setCategory,
                        modifier = Modifier.weight(1f),
                    )
                    if (state.members.size > 1) FilterMenu(
                        label = state.payerId?.let(viewModel::memberName) ?: "All travellers",
                        options = listOf("All travellers" to null) + state.members.map { (it.displayName ?: "Traveller") to it.userId },
                        onSelect = viewModel::setPayer,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        if (state.filteredExpenses.isEmpty()) item {
            Column(
                Modifier.fillMaxWidth().padding(vertical = 36.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.size(56.dp).clip(CircleShape).background(SoftCloud),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Outlined.Payments, null, tint = Ash, modifier = Modifier.size(24.dp)) }
                Text(
                    if (state.expenses.isEmpty()) "No expenses yet. Add the first one when it lands."
                    else "No entries match these filters.",
                    Modifier.padding(top = 14.dp),
                    color = Ash,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        items(state.filteredExpenses, key = Expense::id) { expense ->
            Box(Modifier.animateItem().padding(horizontal = 24.dp)) {
                ExpenseRow(expense, trip.currency, viewModel.memberName(expense.userId), state.members.size > 1) { onOpenExpense(expense) }
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

/**
 * The hero: one 16:9 photograph at 20dp radius, then the listing title and its
 * facts stacked underneath. Text never sits on the photograph.
 */
@Composable
private fun Hero(trip: Trip, coverUrl: String?) {
    Column(Modifier.padding(horizontal = 24.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(20.dp))) {
            DestinationArtwork(Modifier.fillMaxSize(), trip)
            if (coverUrl != null) {
                AsyncImage(
                    model = coverUrl,
                    contentDescription = trip.destination?.let { "Destination cover for $it" },
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                )
            }
        }
        Text(trip.name, Modifier.padding(top = 16.dp), style = MaterialTheme.typography.titleLarge)
        trip.destination?.takeIf { it.isNotBlank() }?.let {
            MetaLabel(Icons.Outlined.Place, it, Modifier.padding(top = 8.dp))
        }
        MetaLabel(Icons.Outlined.CalendarMonth, dateRange(trip.startDate, trip.endDate), Modifier.padding(top = 4.dp))
        Text(phaseLabel(trip), Modifier.padding(top = 4.dp), color = Ink, style = MaterialTheme.typography.labelMedium)
    }
}

/**
 * The booking panel, inline on a phone: white card, hairline border, the
 * layered lift, and the figure set large at the top the way a nightly price is.
 */
@Composable
private fun BookingPanel(
    trip: Trip,
    spent: BigDecimal,
    remaining: BigDecimal,
    perDay: BigDecimal?,
    availablePerDay: BigDecimal?,
    daysLeft: Long?,
    entries: Int,
    showRemaining: Boolean,
    memberCount: Int,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(14.dp)
    val over = showRemaining && remaining.signum() < 0
    Column(
        modifier.fillMaxWidth()
            .panelElevation(shape)
            .clip(shape)
            .background(CanvasWhite)
            .border(1.dp, Hairline, shape)
            .padding(24.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                money(if (showRemaining) remaining.abs() else spent, trip.currency),
                color = if (over) ErrorRed else Ink,
                fontSize = 32.sp,
                lineHeight = 38.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.6).sp,
            )
            Text(
                if (showRemaining) if (over) " over" else " left" else " spent",
                Modifier.padding(bottom = 3.dp),
                color = Ash,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        Text(
            if (trip.budget.signum() > 0) "${money(spent, trip.currency)} spent of ${money(trip.budget, trip.currency)}"
            else "No budget set — just keeping count.",
            Modifier.padding(top = 4.dp),
            color = Ash,
            style = MaterialTheme.typography.bodyMedium,
        )
        if (trip.budget.signum() > 0) {
            Spacer(Modifier.height(16.dp))
            BudgetMeter(spent, trip.budget)
        }
        availablePerDay?.let {
            Spacer(Modifier.height(16.dp))
            Text("${money(it, trip.currency)} available per day", style = MaterialTheme.typography.titleMedium)
            Text(
                "Across $daysLeft ${if (daysLeft == 1L) "day" else "days"}, including today.",
                Modifier.padding(top = 2.dp),
                color = Ash,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        // The rules strip: hairline above, facts in a row, nothing shouting.
        Spacer(Modifier.height(20.dp))
        HairlineDivider()
        Row(Modifier.fillMaxWidth().padding(top = 16.dp)) {
            PanelFact(if (remaining.signum() < 0) "Over budget" else "Remaining", if (trip.budget.signum() > 0) money(remaining.abs(), trip.currency) else "—", Modifier.weight(1f))
            PanelFact("Daily average", perDay?.let { money(it, trip.currency) } ?: "—", Modifier.weight(1f))
            PanelFact("Entries", entries.toString(), Modifier.weight(1f))
        }
        if (memberCount > 1) Text(
            "Group budget · includes everyone's expenses. This tracks spending, not who owes whom.",
            Modifier.padding(top = 16.dp),
            color = Ash,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun PanelFact(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, color = Ash, style = MaterialTheme.typography.bodySmall)
        Text(value, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.labelMedium)
    }
}

/**
 * The amenity grid: a 24dp outline glyph, a 16sp label, and a hairline between
 * every row. Category glyphs stay monochrome — the palette allows one accent.
 */
@Composable
private fun CategoryBreakdown(
    expenses: List<Expense>,
    currency: String,
    onSelect: (Category) -> Unit,
    modifier: Modifier = Modifier,
) {
    val total = expenses.fold(BigDecimal.ZERO) { sum, item -> sum + item.amount }
    val groups = expenses.groupBy(Expense::category).mapValues { (_, rows) -> rows.fold(BigDecimal.ZERO) { sum, item -> sum + item.amount } }
        .toList().sortedByDescending { it.second }
    Column(modifier.fillMaxWidth()) {
        Text("Spending by category", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        groups.forEachIndexed { index, (category, amount) ->
            if (index > 0) HairlineDivider()
            Row(
                Modifier.fillMaxWidth().clickable { onSelect(category) }.padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(categoryIcon(category), null, Modifier.size(24.dp), tint = Ink)
                Text(category.label, Modifier.weight(1f).padding(start = 16.dp), style = MaterialTheme.typography.bodyLarge)
                val percent = if (total.signum() == 0) 0 else amount.multiply(BigDecimal(100)).divide(total, 0, RoundingMode.HALF_UP).toInt()
                Text("$percent%", color = Ash, style = MaterialTheme.typography.bodyMedium)
                Text(money(amount, currency), Modifier.padding(start = 14.dp), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

/**
 * The review-card row: a circular glyph where an avatar would sit, the title in
 * 16/600, its date and payer in 14/500 ash, and no border of its own.
 */
@Composable
private fun ExpenseRow(expense: Expense, currency: String, payer: String, shared: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(SoftCloud),
            contentAlignment = Alignment.Center,
        ) { Icon(categoryIcon(expense.category), expense.category.label, Modifier.size(18.dp), tint = Ink) }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(expense.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(dayLabel(expense.spentOn), payer.takeIf { shared }).joinToString(" · "),
                color = Ash,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(money(expense.amount, currency), style = MaterialTheme.typography.titleMedium)
            SyncBadge(expense.syncState)
        }
    }
}

/** An outlined pill: the system's secondary control, never a filled one. */
@Composable
private fun <T> FilterMenu(label: String, options: List<Pair<String, T>>, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        SecondaryButton(label, Modifier.fillMaxWidth(), pill = true, icon = null) { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (text, value) -> DropdownMenuItem(text = { Text(text) }, onClick = { onSelect(value); open = false }) }
        }
    }
}

@Composable
private fun ShareTripDialog(
    trip: Trip,
    members: List<TripMember>,
    accountId: String,
    onDismiss: () -> Unit,
    onRemove: suspend (String) -> Result<*>,
    onLeave: suspend () -> Result<*>,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }
    val link = "https://getwayfare.netlify.app/join/${trip.shareCode}"
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(14.dp),
        title = { Text("Share ${trip.name}", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column {
                trip.shareCode?.let {
                    Text("Invite code", color = Ash, style = MaterialTheme.typography.bodyMedium)
                    Text(it, Modifier.padding(vertical = 10.dp), fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp)
                    PrimaryButton("Share invite link", Modifier.fillMaxWidth()) {
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, "Join ${trip.name} on Wayfare: $link")
                        }, "Share trip"))
                    }
                }
                Text("On this trip", Modifier.padding(top = 22.dp, bottom = 6.dp), color = Ash, style = MaterialTheme.typography.bodyMedium)
                members.forEachIndexed { index, member ->
                    if (index > 0) HairlineDivider()
                    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(40.dp).clip(CircleShape).background(SoftCloud),
                            contentAlignment = Alignment.Center,
                        ) { Text((member.displayName ?: "T").take(1).uppercase(), style = MaterialTheme.typography.titleMedium) }
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(member.displayName ?: "Traveller", style = MaterialTheme.typography.titleMedium)
                            Text(if (member.role == "owner") "Organiser" else "Member", color = Ash, style = MaterialTheme.typography.bodyMedium)
                        }
                        if (trip.ownerId == accountId && member.userId != accountId) {
                            CircleIconButton(Icons.Outlined.Delete, "Remove ${member.displayName ?: "traveller"}") {
                                scope.launch { onRemove(member.userId).onFailure { error = it.message } }
                            }
                        }
                    }
                }
                error?.let { Notice(it, true, Modifier.padding(top = 12.dp)) }
                if (trip.ownerId != accountId) TextButton(
                    onClick = { scope.launch { onLeave().onFailure { error = it.message } } },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                ) { Text("Leave this trip", color = ErrorRed, style = MaterialTheme.typography.labelMedium) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done", color = Ink, style = MaterialTheme.typography.labelMedium) } },
    )
}

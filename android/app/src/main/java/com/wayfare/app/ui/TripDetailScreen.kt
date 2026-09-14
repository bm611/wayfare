package com.wayfare.app.ui

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.outlined.Info
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FilterList
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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

// A tap-revealed panel (search, filters, breakdown) fades and expands in on an
// ease-out; the exit is quicker, matching the asymmetric feel of a released
// control rather than a held one.
private val revealEnter = fadeIn(tween(180, easing = EaseOutStrong)) + expandVertically(tween(180, easing = EaseOutStrong))
private val revealExit = fadeOut(tween(140, easing = EaseOutStrong)) + shrinkVertically(tween(140, easing = EaseOutStrong))

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
        // The listing-detail chrome: circular controls on white, with the trip
        // name carried by the hero rather than the bar.
        topBar = {
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
                                    text = { Text(if (trip.coverPath == null) "Generate destination cover" else "Regenerate destination cover") },
                                    enabled = !state.requestingCover,
                                    onClick = { showActions = false; viewModel.regenerateCover() },
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
        },
        floatingActionButton = {
            if (trip != null) {
                PrimaryButton(
                    "Add expense",
                    icon = Icons.Outlined.Add,
                ) {
                    editingExpense = null
                    showExpense = true
                }
            }
        },
    ) { insets ->
        when {
            state.loading && trip == null -> Box(Modifier.fillMaxSize().padding(insets), contentAlignment = Alignment.Center) {
                TripLoadingSkeleton(Modifier.padding(24.dp), showCover = false)
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
                    modifier = Modifier,
                    onSetBudget = { showEditTrip = true },
                    isOwner = trip.ownerId == accountId,
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
    modifier: Modifier,
    onSetBudget: () -> Unit,
    isOwner: Boolean,
    onOpenExpense: (Expense) -> Unit,
) {
    // Failed entries never reached the server, so they stay out of every figure
    // the web app would also show.
    val counted = state.expenses.filter { it.syncState != SyncState.Failed }
    val budget = budgetSummary(trip, state.expenses)
    var searchOpen by remember { mutableStateOf(false) }
    var filtersOpen by remember { mutableStateOf(false) }
    // A control stays on screen while it is narrowing the ledger, and hiding it
    // clears it, so a search or filter can never keep working out of sight.
    val showSearch = searchOpen || state.query.isNotBlank()
    val showFilters = filtersOpen || state.category != null || state.payerId != null
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LazyColumn(
        modifier.fillMaxSize(),
        state = listState,
        // Room for the Add expense button (48dp plus its 16dp margin) and a gap,
        // so the last right-aligned amount can scroll clear of it.
        contentPadding = PaddingValues(bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        state.error?.let { item { Notice(it, true, Modifier.padding(horizontal = 24.dp, vertical = 12.dp)) } }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TripHeader(trip)
                if (state.requestingCover || trip.coverStatus == "pending") {
                    Notice("Creating your destination cover…", modifier = Modifier.padding(horizontal = 24.dp))
                } else if (trip.coverStatus == "failed") {
                    Notice("The cover could not be generated. Try again from trip options.", modifier = Modifier.padding(horizontal = 24.dp))
                }
            }
        }
        item {
            BookingPanel(
                trip = trip,
                spent = budget.spent,
                remaining = budget.remaining,
                availablePerDay = budget.availablePerDay,
                daysLeft = budget.daysLeft,
                memberCount = state.members.size,
                isOwner = isOwner,
                onSetBudget = onSetBudget,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
        }
        if (counted.isNotEmpty()) item {
            Column(Modifier.padding(horizontal = 24.dp)) {
                Text(
                    "Spending by category",
                    Modifier.semantics { heading() },
                    style = MaterialTheme.typography.headlineSmall,
                )
                CategoryBreakdown(counted, trip.currency, {
                    viewModel.setCategory(it)
                    filtersOpen = true
                    // Header, budget panel, then this breakdown: the ledger controls follow.
                    val ledgerIndex = (if (state.error != null) 1 else 0) + 3
                    scope.launch { listState.animateScrollToItem(ledgerIndex) }
                })
            }
        }
        item {
            Column(Modifier.padding(horizontal = 24.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("Expenses", Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
                    CircleIconButton(
                        Icons.Outlined.Search,
                        if (showSearch) "Hide search" else "Show search",
                        active = showSearch,
                    ) {
                        if (showSearch) {
                            searchOpen = false
                            viewModel.setQuery("")
                        } else {
                            searchOpen = true
                        }
                    }
                    CircleIconButton(
                        Icons.Outlined.FilterList,
                        if (showFilters) "Hide filters" else "Show filters",
                        active = showFilters,
                    ) {
                        if (showFilters) {
                            filtersOpen = false
                            viewModel.setCategory(null)
                            viewModel.setPayer(null)
                        } else {
                            filtersOpen = true
                        }
                    }
                }
                AnimatedVisibility(showSearch, enter = revealEnter, exit = revealExit) {
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
                }
                AnimatedVisibility(showFilters, enter = revealEnter, exit = revealExit) {
                    Column(Modifier.fillMaxWidth().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterMenu(
                            label = state.category?.label ?: "All categories",
                            options = listOf("All categories" to null) + Category.entries.map { it.label to it },
                            onSelect = viewModel::setCategory,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (state.members.size > 1) FilterMenu(
                            label = state.payerId?.let(viewModel::memberName) ?: "All travellers",
                            options = listOf("All travellers" to null) + state.members.map { (it.displayName ?: "Traveller") to it.userId },
                            onSelect = viewModel::setPayer,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
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
    }
}

@Composable
private fun TripHeader(trip: Trip) {
    Column(Modifier.padding(horizontal = 24.dp)) { TripIdentity(trip, showPhase = true) }
}

@Composable
private fun BookingPanel(
    trip: Trip,
    spent: BigDecimal,
    remaining: BigDecimal,
    availablePerDay: BigDecimal?,
    daysLeft: Long?,
    memberCount: Int,
    isOwner: Boolean,
    onSetBudget: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasBudget = trip.budget.signum() > 0
    val finished = tripPhase(trip) == TripPhase.Past
    val over = hasBudget && remaining.signum() < 0
    val showBalance = hasBudget && !finished
    var infoOpen by remember { mutableStateOf(false) }
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(SoftCloud).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (showBalance) if (over) "Over budget" else "Left to spend" else "Total spent", Modifier.weight(1f), color = Ash, style = MaterialTheme.typography.bodyMedium)
            if (memberCount > 1) CircleIconButton(Icons.Outlined.Info, "About the group budget") { infoOpen = true }
        }
        Text(money(if (showBalance) remaining.abs() else spent, trip.currency), color = if (over) ErrorRed else Ink, style = MaterialTheme.typography.displaySmall)
        if (hasBudget) {
            Text(
                if (finished) "${money(remaining.abs(), trip.currency)} ${if (over) "over" else "under"} budget"
                else "${money(spent, trip.currency)} spent of ${money(trip.budget, trip.currency)}",
                color = Ash, style = MaterialTheme.typography.bodyMedium,
            )
            BudgetMeter(spent, trip.budget, trackColor = Hairline)
        } else if (isOwner) {
            TextButton(onClick = onSetBudget) { Text("Set a budget", color = Ink) }
        }
        if (!over && availablePerDay != null && daysLeft != null) {
            Text("${money(availablePerDay, trip.currency)} available per day · $daysLeft ${if (daysLeft == 1L) "day" else "days"} left, including today", color = Ash, style = MaterialTheme.typography.bodyMedium)
        }
    }
    if (infoOpen) AlertDialog(
        onDismissRequest = { infoOpen = false },
        title = { Text("Group budget") },
        text = { Text("Includes everyone's expenses. This tracks spending, not who owes whom.") },
        confirmButton = { TextButton(onClick = { infoOpen = false }) { Text("Done") } },
    )
}

/**
 * A horizontal bar chart: each category's glyph, label, share and amount above
 * a bar sized to its share of the total. Bars use the palette's one accent.
 */
@Composable
private fun CategoryBreakdown(
    expenses: List<Expense>,
    currency: String,
    onSelect: (Category) -> Unit,
    modifier: Modifier = Modifier,
) {
    val largeText = LocalDensity.current.fontScale > 1.3f
    val total = expenses.fold(BigDecimal.ZERO) { sum, item -> sum + item.amount }
    val groups = expenses.groupBy(Expense::category).mapValues { (_, rows) -> rows.fold(BigDecimal.ZERO) { sum, item -> sum + item.amount } }
        .toList().sortedByDescending { it.second }
    Column(modifier.fillMaxWidth().padding(top = 8.dp)) {
        groups.forEach { (category, amount) ->
            val percent = if (total.signum() == 0) 0 else amount.multiply(BigDecimal(100)).divide(total, 0, RoundingMode.HALF_UP).toInt()
            val share = if (total.signum() == 0) 0f else amount.divide(total, 4, RoundingMode.HALF_UP).toFloat()
            Column(Modifier.fillMaxWidth().clickable { onSelect(category) }.padding(vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(categoryIcon(category), null, Modifier.size(24.dp), tint = Ink)
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(category.label, style = MaterialTheme.typography.bodyLarge)
                        if (largeText) Text("${money(amount, currency)} · $percent%", color = Ash, style = MaterialTheme.typography.bodyMedium)
                    }
                    if (!largeText) {
                        Text("$percent%", color = Ash, style = MaterialTheme.typography.bodyMedium)
                        Text(money(amount, currency), Modifier.padding(start = 14.dp), style = MaterialTheme.typography.labelMedium)
                    }
                }
                Box(Modifier.padding(top = 8.dp).fillMaxWidth().height(8.dp).clip(CircleShape).background(SoftCloud)) {
                    Box(Modifier.widthIn(min = 8.dp).fillMaxWidth(share).fillMaxHeight().clip(CircleShape).background(AccentInk))
                }
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
    val largeText = LocalDensity.current.fontScale > 1.3f
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(SoftCloud),
            contentAlignment = Alignment.Center,
        ) { Icon(categoryIcon(expense.category), expense.category.label, Modifier.size(18.dp), tint = Ink) }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(expense.title, style = MaterialTheme.typography.titleMedium, maxLines = if (largeText) Int.MAX_VALUE else 2, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(dayLabel(expense.spentOn), payer.takeIf { shared }).joinToString(" · "),
                color = Ash,
                style = MaterialTheme.typography.bodyMedium,
            )
            if (largeText) {
                Text(money(expense.amount, currency), style = MaterialTheme.typography.titleMedium)
                SyncBadge(expense.syncState)
            }
        }
        if (!largeText) Column(horizontalAlignment = Alignment.End) {
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
    var removing by remember { mutableStateOf<TripMember?>(null) }
    var leaving by remember { mutableStateOf(false) }
    val link = "https://getwayfare.netlify.app/join/${trip.shareCode}"
    FormSheet(
        onDismissRequest = onDismiss,
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
                                removing = member
                            }
                        }
                    }
                }
                error?.let { Notice(it, true, Modifier.padding(top = 12.dp)) }
                if (trip.ownerId != accountId) TextButton(
                    onClick = { leaving = true },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                ) { Text("Leave this trip", color = ErrorRed, style = MaterialTheme.typography.labelMedium) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done", color = Ink, style = MaterialTheme.typography.labelMedium) } },
    )
    removing?.let { member ->
        AlertDialog(
            onDismissRequest = { removing = null },
            shape = RoundedCornerShape(14.dp),
            title = { Text("Remove ${member.displayName ?: "this traveller"}?", style = MaterialTheme.typography.headlineSmall) },
            text = { Text("They will lose access to this shared ledger.", color = Ash) },
            confirmButton = {
                TextButton(onClick = {
                    removing = null
                    scope.launch { onRemove(member.userId).onFailure { error = it.message } }
                }) { Text("Remove", color = ErrorRed, style = MaterialTheme.typography.labelMedium) }
            },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Keep", color = Ink, style = MaterialTheme.typography.labelMedium) } },
        )
    }
    if (leaving) AlertDialog(
        onDismissRequest = { leaving = false },
        shape = RoundedCornerShape(14.dp),
        title = { Text("Leave ${trip.name}?", style = MaterialTheme.typography.headlineSmall) },
        text = { Text("You will lose access to this shared ledger until someone invites you again.", color = Ash) },
        confirmButton = {
            TextButton(onClick = {
                leaving = false
                scope.launch { onLeave().onFailure { error = it.message } }
            }) { Text("Leave trip", color = ErrorRed, style = MaterialTheme.typography.labelMedium) }
        },
        dismissButton = { TextButton(onClick = { leaving = false }) { Text("Stay", color = Ink, style = MaterialTheme.typography.labelMedium) } },
    )
}

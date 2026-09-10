package com.wayfare.app.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
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
    val scope = rememberCoroutineScope()

    OnResume(viewModel::onResume)

    Scaffold(
        containerColor = Paper,
        topBar = {
            TopAppBar(
                title = { Brand() },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = viewModel::refresh) { Icon(Icons.Outlined.Refresh, "Refresh") }
                    if (trip != null) {
                        IconButton(onClick = { showShare = true }) { Icon(Icons.Outlined.Group, "Share") }
                        if (trip.ownerId == accountId) {
                            IconButton(onClick = { showEditTrip = true }) { Icon(Icons.Outlined.Edit, "Edit trip") }
                            IconButton(onClick = { showDeleteTrip = true }) { Icon(Icons.Outlined.Delete, "Delete trip") }
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (trip != null) ExtendedFloatingActionButton(
                onClick = { editingExpense = null; showExpense = true },
                containerColor = Clay,
                contentColor = Color.White,
                icon = { Icon(Icons.Outlined.Add, null) },
                text = { Text("Add expense") },
            )
        },
    ) { insets ->
        when {
            state.loading && trip == null -> Box(Modifier.fillMaxSize().padding(insets), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Clay)
            }
            trip == null -> Column(Modifier.padding(insets).padding(20.dp)) {
                Notice(state.error ?: "That trip is no longer available.", true)
                TextButton(onClick = onBack) { Text("Back to all trips") }
            }
            else -> PullToRefreshBox(
                isRefreshing = state.refreshing && !state.loading,
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize().padding(insets),
            ) {
                TripDetailContent(
                    trip = trip,
                    accountId = accountId,
                    state = state,
                    viewModel = viewModel,
                    coverUrl = container.repository.coverUrl(trip.coverPath),
                    modifier = Modifier,
                    onOpenExpense = { expense ->
                        when {
                            // Editing a row the server has never seen would PATCH
                            // an id that is not there yet.
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
        title = { Text("Delete ${trip.name}?") },
        text = { Text("The trip and its entire expense ledger will be removed for every traveller.") },
        confirmButton = { TextButton(onClick = { scope.launch { viewModel.deleteTrip().onSuccess { onBack() }; showDeleteTrip = false } }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { showDeleteTrip = false }) { Text("Keep trip") } },
    )
}

@Composable
private fun TripDetailContent(
    trip: Trip,
    accountId: String,
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
    val spent = budget.spent
    val remaining = budget.remaining
    val phase = tripPhase(trip)
    val perDay = budget.perDay
    val daysLeft = budget.daysLeft
    val availablePerDay = budget.availablePerDay

    LazyColumn(
        modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        state.error?.let { item { Notice(it, true, Modifier.padding(top = 12.dp)) } }
        item {
            Surface(
                Modifier.fillMaxWidth().padding(top = 14.dp),
                shape = RoundedCornerShape(24.dp), color = Card, shadowElevation = 3.dp,
            ) {
                Column {
                    if (coverUrl != null) {
                        AsyncImage(
                            model = coverUrl,
                            contentDescription = trip.destination?.let { "Destination cover for $it" },
                            modifier = Modifier.fillMaxWidth().height(145.dp),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        )
                    }
                    Column(Modifier.padding(20.dp)) {
                        Text(phaseLabel(trip), color = Clay, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text(trip.name, Modifier.padding(top = 5.dp), fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            listOfNotNull(trip.destination, dateRange(trip.startDate, trip.endDate)).joinToString(" · "),
                            color = InkSoft,
                            fontSize = 13.sp,
                        )
                        HorizontalDivider(Modifier.padding(vertical = 18.dp), color = Line)
                        val showRemaining = phase is TripPhase.Active && trip.budget.signum() > 0
                        Text(if (showRemaining) if (remaining.signum() < 0) "Over budget" else "Budget remaining" else "Total spent", color = InkSoft)
                        Text(
                            money(if (showRemaining) remaining.abs() else spent, trip.currency),
                            Modifier.padding(top = 3.dp), fontSize = 36.sp, fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            if (trip.budget.signum() > 0) "${money(spent, trip.currency)} spent of ${money(trip.budget, trip.currency)}"
                            else "No budget set — just keeping count.",
                            color = InkSoft, fontSize = 13.sp,
                        )
                        availablePerDay?.let {
                            Text("${money(it, trip.currency)} available/day remaining", Modifier.padding(top = 15.dp), fontWeight = FontWeight.Medium)
                            Text("Across $daysLeft ${if (daysLeft == 1L) "day" else "days"}, including today.", color = InkSoft, fontSize = 12.sp)
                        }
                        Spacer(Modifier.height(16.dp))
                        BudgetMeter(spent, trip.budget)
                        Row(Modifier.padding(top = 17.dp), horizontalArrangement = Arrangement.spacedBy(26.dp)) {
                            DetailMetric("REMAINING", if (trip.budget.signum() > 0) money(remaining.abs(), trip.currency) else "—")
                            DetailMetric("AVG / DAY", perDay?.let { money(it, trip.currency) } ?: "—")
                            DetailMetric("ENTRIES", counted.size.toString())
                        }
                        if (state.members.size > 1) Text(
                            "Group budget · includes everyone’s expenses. This tracks spending, not who owes whom.",
                            Modifier.padding(top = 16.dp), color = InkSoft, fontSize = 12.sp,
                        )
                    }
                }
            }
        }
        if (counted.isNotEmpty()) item {
            CategoryBreakdown(counted, trip.currency, viewModel::setCategory)
        }
        item {
            Text("The ledger", Modifier.padding(top = 16.dp), fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                state.query, viewModel::setQuery, Modifier.fillMaxWidth().padding(top = 10.dp),
                label = { Text("Search titles and notes") }, singleLine = true,
            )
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
        if (state.filteredExpenses.isEmpty()) item {
            Text(
                if (state.expenses.isEmpty()) "No expenses yet. Add the first one when it lands."
                else "No entries match these filters.",
                Modifier.fillMaxWidth().padding(vertical = 34.dp), color = InkSoft,
            )
        }
        items(state.filteredExpenses, key = Expense::id) { expense ->
            ExpenseRow(expense, trip.currency, viewModel.memberName(expense.userId), state.members.size > 1) { onOpenExpense(expense) }
        }
        item { Spacer(Modifier.height(96.dp)) }
    }
}

@Composable
private fun DetailMetric(label: String, value: String) {
    Column {
        Text(label, color = InkFaint, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.7.sp)
        Text(value, Modifier.padding(top = 3.dp), fontWeight = FontWeight.Medium, fontSize = 12.sp)
    }
}

@Composable
private fun CategoryBreakdown(expenses: List<Expense>, currency: String, onSelect: (Category) -> Unit) {
    val total = expenses.fold(BigDecimal.ZERO) { sum, item -> sum + item.amount }
    val groups = expenses.groupBy(Expense::category).mapValues { (_, rows) -> rows.fold(BigDecimal.ZERO) { sum, item -> sum + item.amount } }
        .toList().sortedByDescending { it.second }
    Surface(Modifier.fillMaxWidth().padding(top = 14.dp), shape = RoundedCornerShape(18.dp), color = Color.Transparent, border = androidx.compose.foundation.BorderStroke(1.dp, Line)) {
        Column(Modifier.padding(16.dp)) {
            Text("Spending by category", fontWeight = FontWeight.SemiBold)
            groups.forEach { (category, amount) ->
                Row(
                    Modifier.fillMaxWidth().clickable { onSelect(category) }.padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(9.dp).background(CategoryColors.getValue(category.wireName), CircleShape))
                    Text(category.label, Modifier.weight(1f).padding(start = 10.dp))
                    val percent = if (total.signum() == 0) 0 else amount.multiply(BigDecimal(100)).divide(total, 0, RoundingMode.HALF_UP).toInt()
                    Text("$percent%", color = InkFaint, fontSize = 12.sp)
                    Text(money(amount, currency), Modifier.padding(start = 12.dp), fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun ExpenseRow(expense: Expense, currency: String, payer: String, shared: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(38.dp).background(CategoryColors.getValue(expense.category.wireName).copy(alpha = 0.13f), CircleShape),
            contentAlignment = Alignment.Center,
        ) { Box(Modifier.size(9.dp).background(CategoryColors.getValue(expense.category.wireName), CircleShape)) }
        Column(Modifier.weight(1f).padding(horizontal = 11.dp)) {
            Text(expense.title, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(dayLabel(expense.spentOn), payer.takeIf { shared }).joinToString(" · "),
                color = InkSoft, fontSize = 12.sp,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(money(expense.amount, currency), fontWeight = FontWeight.SemiBold)
            SyncBadge(expense.syncState)
        }
    }
}

@Composable
private fun <T> FilterMenu(label: String, options: List<Pair<String, T>>, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { open = true }, Modifier.fillMaxWidth()) { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
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
        title = { Text("Share ${trip.name}") },
        text = {
            Column {
                trip.shareCode?.let {
                    Text("INVITE CODE", color = InkFaint, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Text(it, Modifier.padding(vertical = 10.dp), fontSize = 27.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 4.sp)
                    ClayButton("Share invite link", Modifier.fillMaxWidth()) {
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, "Join ${trip.name} on Wayfare: $link")
                        }, "Share trip"))
                    }
                }
                Text("ON THIS TRIP", Modifier.padding(top = 22.dp, bottom = 6.dp), color = InkFaint, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                members.forEach { member ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(member.displayName ?: "Traveller", Modifier.weight(1f))
                        Text(if (member.role == "owner") "Organiser" else "Member", color = InkSoft, fontSize = 12.sp)
                        if (trip.ownerId == accountId && member.userId != accountId) IconButton(onClick = {
                            scope.launch { onRemove(member.userId).onFailure { error = it.message } }
                        }) { Icon(Icons.Outlined.Delete, "Remove ${member.displayName ?: "traveller"}") }
                    }
                }
                error?.let { Notice(it, true) }
                if (trip.ownerId != accountId) OutlinedButton(
                    onClick = { scope.launch { onLeave().onFailure { error = it.message } } },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                ) { Text("Leave this trip", color = ClayDeep) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

@Composable
private fun UnsyncedExpenseDialog(
    expense: Expense,
    currency: String,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onDiscard: () -> Unit,
) {
    val failed = expense.syncState == SyncState.Failed
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (failed) "This entry did not save" else "Still saving") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("${expense.title} · ${money(expense.amount, currency)}", fontWeight = FontWeight.Medium)
                Text(
                    if (failed) {
                        "It is kept here so nothing is lost, but it is not counted in the trip total " +
                            "and other travellers cannot see it yet."
                    } else {
                        "It is saved on this phone and will sync as soon as there is a connection. " +
                            "You can edit it once it lands."
                    },
                    color = InkSoft,
                    fontSize = 13.sp,
                )
                expense.syncError?.let { Notice(it, error = failed) }
            }
        },
        confirmButton = { TextButton(onClick = onRetry) { Text("Try again") } },
        dismissButton = {
            Row {
                if (failed) TextButton(onClick = onDiscard) { Text("Discard", color = ClayDeep) }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        },
    )
}

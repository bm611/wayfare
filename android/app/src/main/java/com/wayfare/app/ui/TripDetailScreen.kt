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
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Wallet
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
        containerColor = Paper,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        trip?.name.orEmpty(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") }
                },
                actions = {
                    IconButton(onClick = viewModel::refresh) { Icon(Icons.Outlined.Refresh, "Refresh") }
                    if (trip != null) {
                        IconButton(onClick = { showShare = true }) { Icon(Icons.Outlined.Group, "Share") }
                        if (trip.ownerId == accountId) {
                            Box {
                                IconButton(onClick = { showActions = true }) { Icon(Icons.Outlined.MoreHoriz, "Trip options") }
                                DropdownMenu(expanded = showActions, onDismissRequest = { showActions = false }) {
                                    DropdownMenuItem(
                                        text = { Text("Edit trip") },
                                        leadingIcon = { Icon(Icons.Outlined.Edit, null) },
                                        onClick = { showActions = false; showEditTrip = true },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Delete trip", color = ClayDeep) },
                                        leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = ClayDeep) },
                                        onClick = { showActions = false; showDeleteTrip = true },
                                    )
                                }
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Paper),
            )
        },
        bottomBar = {
            if (trip != null) {
                Surface(color = Paper) {
                    Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 10.dp)) {
                        PrimaryButton("Add expense", Modifier.fillMaxWidth(), icon = Icons.Outlined.Add) {
                            editingExpense = null
                            showExpense = true
                        }
                    }
                }
            }
        },
    ) { insets ->
        when {
            state.loading && trip == null -> Box(Modifier.fillMaxSize().padding(insets), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Clay)
            }
            trip == null -> Column(Modifier.padding(insets).padding(20.dp)) {
                Notice(state.error ?: "That trip is no longer available.", true)
                TextButton(onClick = onBack) { Text("Back to all trips", color = Clay) }
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
        title = { Text("Delete ${trip.name}?", fontWeight = FontWeight.Bold) },
        text = { Text("The trip and its entire expense ledger will be removed for every traveller.") },
        confirmButton = { TextButton(onClick = { scope.launch { viewModel.deleteTrip().onSuccess { onBack() }; showDeleteTrip = false } }) { Text("Delete", color = Clay, fontWeight = FontWeight.SemiBold) } },
        dismissButton = { TextButton(onClick = { showDeleteTrip = false }) { Text("Keep trip", color = Ink) } },
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
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        state.error?.let { item { Notice(it, true, Modifier.padding(top = 12.dp)) } }
        item {
            HeroCard(trip, coverUrl, phase)
        }
        item {
            BudgetCard(
                trip = trip,
                spent = spent,
                remaining = remaining,
                perDay = perDay,
                availablePerDay = availablePerDay,
                daysLeft = daysLeft,
                entries = counted.size,
                showRemaining = phase is TripPhase.Active && trip.budget.signum() > 0,
                memberCount = state.members.size,
            )
        }
        if (counted.isNotEmpty()) item {
            CategoryBreakdown(counted, trip.currency, viewModel::setCategory)
        }
        item {
            Text("Ledger", Modifier.padding(top = 4.dp), fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                state.query, viewModel::setQuery, Modifier.fillMaxWidth().padding(top = 12.dp),
                placeholder = { Text("Search titles and notes", color = InkFaint) },
                leadingIcon = { Icon(Icons.Outlined.Search, null, tint = InkFaint) },
                singleLine = true,
                shape = RoundedCornerShape(50),
            )
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
            Column(
                Modifier.fillMaxWidth().padding(vertical = 36.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.size(60.dp).clip(CircleShape).background(PaperDeep),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Outlined.Payments, null, tint = InkFaint, modifier = Modifier.size(26.dp)) }
                Text(
                    if (state.expenses.isEmpty()) "No expenses yet. Add the first one when it lands."
                    else "No entries match these filters.",
                    Modifier.padding(top = 14.dp),
                    color = InkSoft,
                    fontSize = 14.sp,
                )
            }
        }
        items(state.filteredExpenses, key = Expense::id) { expense ->
            Box(Modifier.animateItem()) {
                ExpenseRow(expense, trip.currency, viewModel.memberName(expense.userId), state.members.size > 1) { onOpenExpense(expense) }
            }
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
private fun HeroCard(trip: Trip, coverUrl: String?, phase: TripPhase) {
    Column {
        Box(Modifier.fillMaxWidth().height(250.dp).clip(RoundedCornerShape(24.dp))) {
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
        Text(
            phaseLabel(trip),
            Modifier.padding(top = 16.dp),
            color = Clay,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.3.sp,
        )
        Text(
            trip.name,
            Modifier.padding(top = 4.dp),
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.4).sp,
        )
        Text(
            listOfNotNull(trip.destination, dateRange(trip.startDate, trip.endDate)).joinToString(" · ").ifBlank { "Dates open" },
            Modifier.padding(top = 4.dp),
            color = InkSoft,
            fontSize = 14.sp,
        )
    }
}

@Composable
private fun BudgetCard(
    trip: Trip,
    spent: BigDecimal,
    remaining: BigDecimal,
    perDay: BigDecimal?,
    availablePerDay: BigDecimal?,
    daysLeft: Long?,
    entries: Int,
    showRemaining: Boolean,
    memberCount: Int,
) {
    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = ClayWash,
        contentColor = Ink,
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(34.dp).clip(CircleShape).background(Card),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Outlined.Wallet, null, tint = Clay, modifier = Modifier.size(18.dp)) }
                Text(
                    if (showRemaining) if (remaining.signum() < 0) "Over budget" else "Budget remaining" else "Total spent",
                    Modifier.padding(start = 10.dp),
                    color = InkSoft,
                    fontSize = 14.sp,
                )
            }
            Text(
                money(if (showRemaining) remaining.abs() else spent, trip.currency),
                Modifier.padding(top = 12.dp),
                color = if (showRemaining && remaining.signum() < 0) ClayDeep else Ink,
                fontSize = 38.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp,
            )
            Text(
                if (trip.budget.signum() > 0) "${money(spent, trip.currency)} spent of ${money(trip.budget, trip.currency)}"
                else "No budget set — just keeping count.",
                Modifier.padding(top = 2.dp),
                color = InkSoft,
                fontSize = 13.sp,
            )
            availablePerDay?.let {
                Text(
                    "${money(it, trip.currency)} available/day remaining",
                    Modifier.padding(top = 14.dp),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                )
                Text(
                    "Across $daysLeft ${if (daysLeft == 1L) "day" else "days"}, including today.",
                    Modifier.padding(top = 2.dp),
                    color = InkSoft,
                    fontSize = 12.sp,
                )
            }
            if (trip.budget.signum() > 0) {
                Spacer(Modifier.height(16.dp))
                BudgetMeter(spent, trip.budget, Modifier.clip(RoundedCornerShape(50)))
            }
            HorizontalDivider(Modifier.padding(vertical = 18.dp), color = LineSoft)
            Row(Modifier.fillMaxWidth()) {
                DetailMetric(if (remaining.signum() < 0) "Over budget" else "Remaining", if (trip.budget.signum() > 0) money(remaining.abs(), trip.currency) else "—", Modifier.weight(1f))
                DetailMetric("Daily average", perDay?.let { money(it, trip.currency) } ?: "—", Modifier.weight(1f))
                DetailMetric("Entries", entries.toString(), Modifier.weight(1f))
            }
            if (memberCount > 1) Text(
                "Group budget · includes everyone’s expenses. This tracks spending, not who owes whom.",
                Modifier.padding(top = 16.dp), color = InkSoft, fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun DetailMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, color = InkSoft, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        Text(value, Modifier.padding(top = 4.dp), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}

@Composable
private fun CategoryBreakdown(expenses: List<Expense>, currency: String, onSelect: (Category) -> Unit) {
    val total = expenses.fold(BigDecimal.ZERO) { sum, item -> sum + item.amount }
    val groups = expenses.groupBy(Expense::category).mapValues { (_, rows) -> rows.fold(BigDecimal.ZERO) { sum, item -> sum + item.amount } }
        .toList().sortedByDescending { it.second }
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = Color.Transparent, border = androidx.compose.foundation.BorderStroke(1.dp, LineSoft)) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Outlined.TrendingUp, null, tint = Ink, modifier = Modifier.size(18.dp))
                Text("Spending by category", Modifier.padding(start = 8.dp), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            }
            groups.forEach { (category, amount) ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onSelect(category) }.padding(vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(categoryIcon(category), null, Modifier.size(22.dp), tint = CategoryColors.getValue(category.wireName))
                    Text(category.label, Modifier.weight(1f).padding(start = 12.dp), fontSize = 15.sp)
                    val percent = if (total.signum() == 0) 0 else amount.multiply(BigDecimal(100)).divide(total, 0, RoundingMode.HALF_UP).toInt()
                    Text("$percent%", color = InkFaint, fontSize = 12.sp)
                    Text(money(amount, currency), Modifier.padding(start = 12.dp), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                }
            }
        }
    }
}

@Composable
private fun ExpenseRow(expense: Expense, currency: String, payer: String, shared: Boolean, onClick: () -> Unit) {
    Column {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(48.dp).background(CategoryColors.getValue(expense.category.wireName).copy(alpha = 0.10f), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center,
            ) { Icon(categoryIcon(expense.category), expense.category.label, Modifier.size(23.dp), tint = CategoryColors.getValue(expense.category.wireName)) }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(expense.title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(dayLabel(expense.spentOn), payer.takeIf { shared }).joinToString(" · "),
                    color = InkSoft, fontSize = 13.sp,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(money(expense.amount, currency), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                SyncBadge(expense.syncState)
            }
        }
        HorizontalDivider(color = LineSoft)
    }
}

@Composable
private fun <T> FilterMenu(label: String, options: List<Pair<String, T>>, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(
            onClick = { open = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(50),
        ) {
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            Icon(Icons.Outlined.KeyboardArrowDown, null, Modifier.size(18.dp), tint = Ink)
        }
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
        title = { Text("Share ${trip.name}", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                trip.shareCode?.let {
                    Text("INVITE CODE", color = InkFaint, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Text(it, Modifier.padding(vertical = 10.dp), fontSize = 27.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp)
                    PrimaryButton("Share invite link", Modifier.fillMaxWidth()) {
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, "Join ${trip.name} on Wayfare: $link")
                        }, "Share trip"))
                    }
                }
                Text("ON THIS TRIP", Modifier.padding(top = 22.dp, bottom = 6.dp), color = InkFaint, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                members.forEach { member ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(38.dp).clip(CircleShape).background(PaperDeep),
                            contentAlignment = Alignment.Center,
                        ) { Text((member.displayName ?: "T").take(1).uppercase(), fontWeight = FontWeight.SemiBold, fontSize = 15.sp) }
                        Text(member.displayName ?: "Traveller", Modifier.weight(1f).padding(start = 12.dp), fontSize = 15.sp)
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
                    shape = RoundedCornerShape(50),
                ) { Text("Leave this trip", color = ClayDeep) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done", color = Clay, fontWeight = FontWeight.SemiBold) } },
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
    ExpenseSheet(
        onDismissRequest = onDismiss,
        title = { Text(if (failed) "This entry did not save" else "Still saving", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("${expense.title} · ${money(expense.amount, currency)}", fontWeight = FontWeight.SemiBold)
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
        confirmButton = { TextButton(onClick = onRetry) { Text("Try again", color = Clay, fontWeight = FontWeight.SemiBold) } },
        dismissButton = {
            Row {
                if (failed) TextButton(onClick = onDiscard) { Text("Discard", color = ClayDeep) }
                TextButton(onClick = onDismiss) { Text("Close", color = Ink) }
            }
        },
    )
}

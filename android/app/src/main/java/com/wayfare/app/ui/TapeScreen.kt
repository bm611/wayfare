package com.wayfare.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wayfare.app.AppContainer
import com.wayfare.app.core.Category
import com.wayfare.app.core.Expense
import com.wayfare.app.core.SyncState
import com.wayfare.app.core.money
import com.wayfare.app.core.routeCode
import com.wayfare.app.core.stampDate
import com.wayfare.app.core.tapeDayLabel
import com.wayfare.app.feature.TripDetailViewModel
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate

/**
 * The tape: every line in the order it happened, day by day, amounts running out
 * to the right margin. The overview answers how the trip is going; this answers
 * what actually happened.
 */
@Composable
fun TapeScreen(
    accountId: String,
    viewModel: TripDetailViewModel,
    container: AppContainer,
    openAddOnLaunch: Boolean,
    onBack: () -> Unit,
    bottomBar: @Composable () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val trip = state.trip
    var searching by remember { mutableStateOf(false) }
    // Arriving from a "+" elsewhere in the app opens the sheet straight away,
    // but only once: coming back from the sheet must not reopen it.
    var adding by rememberSaveable { mutableStateOf(openAddOnLaunch) }
    var editingExpense by remember { mutableStateOf<Expense?>(null) }
    var readOnlyExpense by remember { mutableStateOf<Expense?>(null) }
    var unsyncedExpense by remember { mutableStateOf<Expense?>(null) }
    val scope = rememberCoroutineScope()
    // The currency last used on this trip, so a second coffee in Lisbon does not
    // have to be re-declared as euros.
    val paidIn by container.preferences.paidIn(trip?.id.orEmpty())
        .collectAsState(initial = trip?.currency ?: "EUR")

    OnResume(viewModel::onResume)

    // Category ranking is a property of the whole trip, so a filtered view keeps
    // the same dot colour a line had before the filter was applied.
    val ranks = remember(state.expenses) {
        state.expenses.filter { it.syncState != SyncState.Failed }
            .groupBy(Expense::category)
            .mapValues { (_, rows) -> rows.fold(BigDecimal.ZERO) { sum, row -> sum + row.amount } }
            .toList().sortedByDescending { it.second }
            .mapIndexed { rank, (category, _) -> category to rank }
            .toMap()
    }

    Scaffold(
        containerColor = Night,
        bottomBar = bottomBar,
        topBar = {
            Column {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    CircleIconButton(Icons.AutoMirrored.Outlined.ArrowBack, "Back", onClick = onBack)
                    Column(Modifier.weight(1f).padding(start = 6.dp)) {
                        trip?.let { MonoLabel(routeCode(it), color = Slate, tracking = 2.sp) }
                        Text("The tape", style = MaterialTheme.typography.headlineSmall)
                    }
                    CircleIconButton(
                        if (searching) Icons.Outlined.Close else Icons.Outlined.Search,
                        if (searching) "Close search" else "Search the tape",
                    ) {
                        searching = !searching
                        if (!searching) viewModel.setQuery("")
                    }
                    trip?.let {
                        Box(
                            Modifier.height(32.dp).clip(RoundedCornerShape(8.dp))
                                .border(1.dp, Outline, RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            MonoLabel(
                                "${it.currency} · ${stampDate(LocalDate.now())}",
                                color = Slate,
                                tracking = 1.sp,
                            )
                        }
                    }
                }
                AnimatedVisibility(searching) {
                    OutlinedTextField(
                        state.query, viewModel::setQuery,
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        placeholder = { Text("Search titles and notes") },
                        leadingIcon = { Icon(Icons.Outlined.Search, null, Modifier.size(18.dp), tint = Slate) },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = wayfareFieldColors(),
                    )
                }
            }
        },
    ) { insets ->
        when {
            state.loading && trip == null -> Box(
                Modifier.fillMaxSize().padding(insets),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator(color = Amber) }

            trip == null -> Column(Modifier.padding(insets).padding(24.dp)) {
                Notice(state.error ?: "That trip is no longer available.", true)
                TextButton(onClick = onBack) { Text("Back to all trips", color = Steel) }
            }

            else -> FabScaffold(
                modifier = Modifier.fillMaxSize().padding(insets),
                fab = { AddLineFab { adding = true } },
            ) {
                PullToRefreshBox(
                    isRefreshing = state.refreshing,
                    onRefresh = viewModel::refresh,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Tape(state, viewModel, trip.currency, ranks) { expense ->
                        when {
                            expense.syncState != SyncState.Synced -> unsyncedExpense = expense
                            expense.userId == accountId -> editingExpense = expense
                            else -> readOnlyExpense = expense
                        }
                    }
                }
            }
        }
    }

    if (trip != null && adding) AddExpenseSheet(
        currency = trip.currency,
        initialPaidIn = paidIn,
        fx = container.fx,
        onDismiss = { adding = false },
        onSave = viewModel::createExpense,
        onRememberCurrency = { container.preferences.rememberPaidIn(trip.id, it) },
    )
    if (trip != null) editingExpense?.let { expense ->
        AddExpenseSheet(
            expense = expense,
            currency = trip.currency,
            initialPaidIn = paidIn,
            fx = container.fx,
            onDismiss = { editingExpense = null },
            onSave = { draft -> viewModel.updateExpense(expense.id, draft) },
            onDelete = { viewModel.deleteExpense(expense.id) },
            onRememberCurrency = { container.preferences.rememberPaidIn(trip.id, it) },
        )
    }
    if (trip != null) readOnlyExpense?.let { expense ->
        ReadOnlyExpenseSheet(
            expense = expense,
            currency = trip.currency,
            payer = viewModel.memberName(expense.userId),
            onDismiss = { readOnlyExpense = null },
        )
    }
    unsyncedExpense?.let { expense ->
        UnsyncedExpenseDialog(
            expense = expense,
            currency = trip?.currency ?: "EUR",
            onDismiss = { unsyncedExpense = null },
            onRetry = { scope.launch { viewModel.retryExpense(expense.id); unsyncedExpense = null } },
            onDiscard = { scope.launch { viewModel.discardExpense(expense.id); unsyncedExpense = null } },
        )
    }
}

@Composable
private fun Tape(
    state: com.wayfare.app.feature.TripDetailUiState,
    viewModel: TripDetailViewModel,
    currency: String,
    ranks: Map<Category, Int>,
    onOpen: (Expense) -> Unit,
) {
    val lines = state.filteredExpenses.sortedWith(
        compareByDescending<Expense> { it.spentOn }.thenByDescending { it.createdAt },
    )
    val byDay = lines.groupBy(Expense::spentOn)

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Text(
                if (state.expenses.isEmpty()) "Nothing printed yet."
                else "${lines.size} ${if (lines.size == 1) "line" else "lines"}, in the order they happened.",
                Modifier.padding(horizontal = 24.dp).padding(top = 4.dp, bottom = 10.dp),
                color = Slate,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        item { Filters(state, viewModel) }
        if (lines.isEmpty()) {
            item {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    MonoLabel("— TAPE IS BLANK —", color = Slate)
                    Text(
                        if (state.expenses.isEmpty()) "Add the first line when it lands."
                        else "No lines match these filters.",
                        Modifier.padding(top = 10.dp),
                        color = Slate,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        byDay.forEach { (day, entries) ->
            item(key = "day-$day") {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 20.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MonoLabel(tapeDayLabel(day), tracking = 1.6.sp)
                    DashedRule(Modifier.weight(1f).padding(horizontal = 10.dp))
                    Text(
                        money(entries.fold(BigDecimal.ZERO) { sum, row -> sum + row.amount }, currency),
                        color = Amber,
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp),
                    )
                }
            }
            items(entries, key = Expense::id) { expense ->
                TapeLine(
                    expense = expense,
                    currency = currency,
                    tint = categoryTint(ranks[expense.category] ?: 4),
                    modifier = Modifier.padding(horizontal = 24.dp).animateItem(),
                ) { onOpen(expense) }
            }
        }
        if (lines.isNotEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(top = 24.dp), contentAlignment = Alignment.Center) {
                    MonoLabel("— END OF TAPE —", color = Slate)
                }
            }
        }
        item { Spacer(Modifier.height(96.dp)) }
    }
}

/** One printed line: a category dot, the title, the leader, the amount. */
@Composable
private fun TapeLine(
    expense: Expense,
    currency: String,
    tint: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 9.dp)) {
        DottedLeaderRow(
            label = expense.title,
            amount = money(expense.amount, currency),
            leading = { CategoryDot(tint) },
            amountColor = if (expense.syncState == SyncState.Failed) ErrorRed else Paper,
        )
        Row(
            Modifier.fillMaxWidth().padding(start = 13.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                buildString {
                    append(expense.category.label.uppercase())
                    expense.originalCurrency?.let { append(" · ${expense.originalAmount} $it") }
                },
                color = Slate,
                style = MonoLabelStyle.copy(letterSpacing = 1.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (expense.syncState != SyncState.Synced) {
                Spacer(Modifier.size(8.dp))
                SyncBadge(expense.syncState)
            }
        }
    }
}

/** Filter pills for category, and the traveller filter when a trip is shared. */
@Composable
private fun Filters(state: com.wayfare.app.feature.TripDetailUiState, viewModel: TripDetailViewModel) {
    var payerMenu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterPill("All ${state.expenses.size}", state.category == null) { viewModel.setCategory(null) }
        Category.entries.forEach { category ->
            FilterPill(category.label, state.category == category) { viewModel.setCategory(category) }
        }
        if (state.members.size > 1) {
            Box {
                FilterPill(
                    state.payerId?.let(viewModel::memberName) ?: "Everyone",
                    state.payerId != null,
                ) { payerMenu = true }
                DropdownMenu(expanded = payerMenu, onDismissRequest = { payerMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Everyone") },
                        onClick = { viewModel.setPayer(null); payerMenu = false },
                    )
                    state.members.forEach { member ->
                        DropdownMenuItem(
                            text = { Text(member.displayName ?: "Traveller") },
                            onClick = { viewModel.setPayer(member.userId); payerMenu = false },
                        )
                    }
                }
            }
        }
    }
}

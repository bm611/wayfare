package com.wayfare.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.text.input.VisualTransformation
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import com.wayfare.app.core.Category
import com.wayfare.app.core.Expense
import com.wayfare.app.core.ExpenseDraft
import com.wayfare.app.core.SyncState
import com.wayfare.app.core.Trip
import com.wayfare.app.core.TripDraft
import com.wayfare.app.core.money
import com.wayfare.app.data.FxRepository
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripFormDialog(
    trip: Trip? = null,
    onDismiss: () -> Unit,
    onSave: suspend (TripDraft) -> Result<*>,
) {
    var name by rememberSaveable(trip?.id) { mutableStateOf(trip?.name.orEmpty()) }
    var destination by rememberSaveable(trip?.id) { mutableStateOf(trip?.destination.orEmpty()) }
    var start by rememberSaveable(trip?.id) { mutableStateOf(trip?.startDate) }
    var end by rememberSaveable(trip?.id) { mutableStateOf(trip?.endDate) }
    var budget by rememberSaveable(trip?.id) { mutableStateOf(trip?.budget?.stripTrailingZeros()?.toPlainString().orEmpty()) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var fieldErrors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var busy by remember { mutableStateOf(false) }
    var discard by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val dirty = name != trip?.name.orEmpty() || destination != trip?.destination.orEmpty() ||
        start != trip?.startDate || end != trip?.endDate || budget != trip?.budget?.stripTrailingZeros()?.toPlainString().orEmpty()

    fun dismiss() { if (busy) return; if (dirty) discard = true else onDismiss() }

    FormSheet(
        onDismissRequest = ::dismiss,
        canDismiss = !dirty && !busy,
        title = { Text(if (trip == null) "Where are you headed?" else "Update your trip", fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                error?.let { Notice(it, true) }
                WayfareInput(name, { name = it.take(80); fieldErrors = fieldErrors - "name" }, "Trip name", error = fieldErrors["name"])
                WayfareInput(destination, { destination = it.take(120) }, "Destination")
                DateField("Departure", start, { start = it; fieldErrors = fieldErrors - "dates" }, Modifier.fillMaxWidth())
                DateField("Return", end, { end = it; fieldErrors = fieldErrors - "dates" }, Modifier.fillMaxWidth(), error = fieldErrors["dates"])
                WayfareInput(
                    budget, { budget = it; fieldErrors = fieldErrors - "budget" }, "Budget in EUR",
                    error = fieldErrors["budget"], helper = "Leave blank to track spend without a limit.",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )

            }
        },
        confirmButton = {
            PrimaryButton(
                text = if (trip == null) "Start the ledger" else "Save changes",
                busy = busy,
            ) {
                val amount = budget.ifBlank { "0" }.toBigDecimalOrNull()
                error = null
                fieldErrors = when {
                    name.isBlank() -> mapOf("name" to "Give the trip a name.")
                    amount == null || amount.signum() < 0 -> mapOf("budget" to "Budget must be zero or more.")
                    start != null && end != null && end!! < start!! -> mapOf("dates" to "Return must be on or after departure.")
                    else -> emptyMap()
                }
                if (fieldErrors.isEmpty()) scope.launch {
                    busy = true
                    onSave(TripDraft(name.trim(), destination.trim().ifBlank { null }, start, end, amount!!))
                        .onSuccess { onDismiss() }
                        .onFailure { error = it.message ?: "Could not save the trip." }
                    busy = false
                }
            }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = ::dismiss) { Text("Cancel") } },
    )
    if (discard) AlertDialog(
        onDismissRequest = { discard = false },
        shape = RoundedCornerShape(14.dp),
        title = { Text("Discard your changes?") },
        text = { Text("The details you entered have not been saved.") },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Discard") } },
        dismissButton = { TextButton(onClick = { discard = false }) { Text("Keep editing") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseFormDialog(
    expense: Expense? = null,
    readOnly: Boolean = false,
    currency: String = "EUR",
    initialPaidIn: String = currency,
    fx: FxRepository,
    payer: String? = null,
    onDismiss: () -> Unit,
    onSave: suspend (ExpenseDraft) -> Result<*>,
    onDelete: (suspend () -> Result<*>)? = null,
    onRememberCurrency: suspend (String) -> Unit = {},
) {
    if (readOnly && expense != null) {
        FormSheet(
            onDismissRequest = onDismiss,
            title = { Text(expense.title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(money(expense.amount, currency), style = androidx.compose.material3.MaterialTheme.typography.headlineMedium)
                    Text("Paid by ${payer ?: "another traveller"}. Only the person who added this expense can edit it.", color = Ash)
                    Text("${expense.category.label} · ${expense.spentOn}")
                    expense.originalCurrency?.let { Text("Originally ${expense.originalAmount} $it") }
                    expense.note?.let { Text(it) }
                }
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text("Close", color = Ink, fontWeight = FontWeight.SemiBold) } },
        )
        return
    }

    val snapshot by fx.snapshot.collectAsStateWithLifecycle()
    var title by rememberSaveable(expense?.id) { mutableStateOf(expense?.title.orEmpty()) }
    var amount by rememberSaveable(expense?.id) { mutableStateOf((expense?.originalAmount ?: expense?.amount)?.stripTrailingZeros()?.toPlainString().orEmpty()) }
    var paidIn by rememberSaveable(expense?.id, initialPaidIn) { mutableStateOf(expense?.originalCurrency ?: initialPaidIn) }
    var category by rememberSaveable(expense?.id) { mutableStateOf(expense?.category ?: Category.Food) }
    var spentOn by rememberSaveable(expense?.id) { mutableStateOf(expense?.spentOn ?: LocalDate.now()) }
    var note by rememberSaveable(expense?.id) { mutableStateOf(expense?.note.orEmpty()) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var fieldErrors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var busy by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var discard by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val parsed = amount.toBigDecimalOrNull()
    val converted = parsed?.let { fx.convert(it, paidIn, currency, snapshot) }
    val dirty = title != expense?.title.orEmpty() || amount != (expense?.originalAmount ?: expense?.amount)?.stripTrailingZeros()?.toPlainString().orEmpty() ||
        paidIn != (expense?.originalCurrency ?: initialPaidIn) || category != (expense?.category ?: Category.Food) ||
        spentOn != (expense?.spentOn ?: LocalDate.now()) || note != expense?.note.orEmpty()

    fun dismiss() {
        if (busy) return
        if (dirty) discard = true else onDismiss()
    }

    FormSheet(
        onDismissRequest = ::dismiss,
        canDismiss = !dirty && !busy,
        title = { Text(if (expense == null) "What did it cost?" else "Fix the details", fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                error?.let { Notice(it, true) }
                WayfareInput(
                    amount, { amount = it; fieldErrors = fieldErrors - "amount" }, "Amount paid",
                    error = fieldErrors["amount"], keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                CurrencyPicker(paidIn, { paidIn = it; fieldErrors = fieldErrors - "currency" })
                fieldErrors["currency"]?.let { FieldError(it) }
                if (paidIn != currency && converted != null) {
                    Notice("≈ ${money(converted, currency)} · 1 $paidIn = ${fx.rateBetween(paidIn, currency, snapshot)?.setScale(4, java.math.RoundingMode.HALF_UP)} $currency${if (snapshot.stale) " · offline rate" else ""}")
                }
                WayfareInput(title, { title = it.take(120); fieldErrors = fieldErrors - "title" }, "What did you spend on?", error = fieldErrors["title"])
                Text("Category", color = Ash)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Category.entries.forEach { item ->
                        FilterChip(
                            selected = category == item,
                            onClick = { category = item },
                            label = { Text(item.label) },
                            leadingIcon = { Icon(categoryIcon(item), null, Modifier.size(18.dp)) },
                            shape = RoundedCornerShape(50),
                            colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Ink,
                                selectedLabelColor = CanvasWhite,
                                selectedLeadingIconColor = CanvasWhite,
                            ),
                        )
                    }
                }
                DateField("Date", spentOn, { spentOn = it ?: LocalDate.now() }, Modifier.fillMaxWidth())
                WayfareInput(note, { note = it.take(500) }, "Note (optional)", minLines = 2, maxLines = 4)
                if (expense != null && onDelete != null) {
                    SecondaryButton("Delete entry", Modifier.fillMaxWidth(), pill = true) { confirmDelete = true }
                }
            }
        },
        confirmButton = {
            PrimaryButton(
                text = if (expense == null) "Add to ledger" else "Save changes",
                busy = busy,
            ) {
                val rate = if (paidIn == currency) null else fx.rateBetween(paidIn, currency, snapshot)
                error = null
                fieldErrors = when {
                    parsed == null || parsed.signum() <= 0 -> mapOf("amount" to "Enter an amount above zero.")
                    title.isBlank() -> mapOf("title" to "What was it for?")
                    paidIn != currency && rate == null -> mapOf("currency" to "No rate is available for $paidIn to $currency.")
                    else -> emptyMap()
                }
                if (fieldErrors.isEmpty()) scope.launch {
                    busy = true
                    val draft = ExpenseDraft(
                        title.trim(), converted ?: parsed!!.setScale(2, java.math.RoundingMode.HALF_UP),
                        if (paidIn == currency) null else parsed!!.setScale(2, java.math.RoundingMode.HALF_UP),
                        paidIn.takeIf { it != currency }, rate, category, spentOn, note.trim().ifBlank { null },
                    )
                    onSave(draft).onSuccess {
                        onRememberCurrency(paidIn)
                        onDismiss()
                    }.onFailure { error = it.message ?: "Could not save that expense." }
                    busy = false
                }
            }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = ::dismiss) { Text("Cancel") } },
    )
    if (confirmDelete && onDelete != null) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        shape = RoundedCornerShape(14.dp),
        title = { Text("Delete this entry?") },
        text = { Text("This cannot be undone.") },
        confirmButton = { TextButton(onClick = { scope.launch { onDelete().onSuccess { onDismiss() }.onFailure { error = it.message }; confirmDelete = false } }) { Text("Delete", color = ErrorRed, fontWeight = FontWeight.SemiBold) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep") } },
    )
    if (discard) AlertDialog(
        onDismissRequest = { discard = false },
        shape = RoundedCornerShape(14.dp),
        title = { Text("Discard your changes?") },
        text = { Text("The expense has not been saved.") },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Discard") } },
        dismissButton = { TextButton(onClick = { discard = false }) { Text("Keep editing") } },
    )
}

/** An entry that never reached the server: kept, explained, retried or discarded. */
@Composable
fun UnsyncedExpenseDialog(
    expense: Expense,
    currency: String,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onDiscard: () -> Unit,
) {
    val failed = expense.syncState == SyncState.Failed
    FormSheet(
        onDismissRequest = onDismiss,
        title = { Text(if (failed) "This expense was not saved" else "Waiting to sync", fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        expense.title,
                        Modifier.weight(1f).padding(end = 12.dp),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(money(expense.amount, currency), style = MaterialTheme.typography.titleMedium)
                }
                Text(
                    if (failed) "It is kept on this device so nothing is lost, but it is not counted in the trip total and other travellers cannot see it yet."
                    else "It is saved on this device and will sync as soon as there is a connection. You can edit it once it lands.",
                    color = Ash,
                    style = MaterialTheme.typography.bodyMedium,
                )
                expense.syncError?.let { Notice(it, error = failed) }
            }
        },
        confirmButton = { PrimaryButton("Try again", onClick = onRetry) },
        dismissButton = {
            Row {
                if (failed) TextButton(onClick = onDiscard) { Text("Discard", color = ErrorRed, fontWeight = FontWeight.SemiBold) }
                TextButton(onClick = onDismiss) { Text("Close", color = Ink) }
            }
        },
    )
}

/**
 * Every form in the app arrives the same way: a sheet up from the bottom edge,
 * title, scrolling body, then the actions pinned under it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FormSheet(
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    text: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable () -> Unit = {},
    canDismiss: Boolean = true,
) {
    val currentCanDismiss by rememberUpdatedState(canDismiss)
    val currentOnDismiss by rememberUpdatedState(onDismissRequest)
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { value ->
            if (value == SheetValue.Hidden && !currentCanDismiss) {
                currentOnDismiss()
                false
            } else true
        },
    )
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        sheetMaxWidth = Dp.Unspecified,
        containerColor = CanvasWhite,
        contentColor = Ink,
    ) {
        Column(Modifier.fillMaxWidth()) {
            androidx.compose.material3.ProvideTextStyle(MaterialTheme.typography.headlineSmall) {
                Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 20.dp)) { title() }
            }
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
            ) { text() }
            FlowRow(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                dismissButton()
                confirmButton()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(label: String, value: LocalDate?, onChange: (LocalDate?) -> Unit, modifier: Modifier = Modifier, error: String? = null) {
    var open by remember { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, color = Ash, style = MaterialTheme.typography.bodyMedium)
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .border(1.dp, if (error != null) ErrorRed else Hairline, RoundedCornerShape(8.dp))
                .clickable { open = true }.padding(16.dp)
                .semantics { contentDescription = "$label, ${value ?: "No date selected"}" },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(value?.let { com.wayfare.app.core.shortDate(it) } ?: "Add a date", Modifier.weight(1f), color = if (value == null) Ash else Ink)
            Icon(Icons.Outlined.CalendarMonth, null, Modifier.size(18.dp), tint = Ash)
        }
        error?.let { FieldError(it) }
    }
    if (open) {
        val millis = value?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
        val picker = rememberDatePickerState(initialSelectedDateMillis = millis)
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    onChange(picker.selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() })
                    open = false
                }) { Text("Use date", color = Ink, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                Row {
                    if (value != null) TextButton(onClick = { onChange(null); open = false }) { Text("Clear") }
                    TextButton(onClick = { open = false }) { Text("Cancel") }
                }
            },
        ) { DatePicker(picker) }
    }
}

@Composable
private fun CurrencyPicker(value: String, onChange: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column {
        Text("Paid in", color = Ash, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
        SecondaryButton(value, pill = true) { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            FxRepository.CURRENCIES.forEach { currency ->
                DropdownMenuItem(text = { Text(currency) }, onClick = { onChange(currency); open = false })
            }
        }
    }
}

@Composable
internal fun FieldError(message: String) {
    Text(message, Modifier.semantics { liveRegion = LiveRegionMode.Polite }, color = ErrorRed, style = MaterialTheme.typography.bodySmall)
}

/** Persistent labels and local feedback, shared by trip, expense and auth forms. */
@Composable
internal fun WayfareInput(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    error: String? = null,
    helper: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    minLines: Int = 1,
    maxLines: Int = 1,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(error) { if (error != null) focus.requestFocus() }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, color = Ash, style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(
            value, onChange,
            Modifier.fillMaxWidth().focusRequester(focus).semantics { contentDescription = label },
            isError = error != null, singleLine = maxLines == 1, minLines = minLines, maxLines = maxLines,
            keyboardOptions = keyboardOptions, visualTransformation = visualTransformation,
            shape = RoundedCornerShape(8.dp), colors = wayfareFieldColors(),
        )
        if (error != null) FieldError(error)
        else if (helper != null) Text(helper, color = Ash, style = MaterialTheme.typography.bodySmall)
    }
}

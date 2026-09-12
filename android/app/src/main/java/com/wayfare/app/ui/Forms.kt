package com.wayfare.app.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import com.wayfare.app.core.Category
import com.wayfare.app.core.Expense
import com.wayfare.app.core.ExpenseDraft
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
    var busy by remember { mutableStateOf(false) }
    var discard by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val dirty = name != trip?.name.orEmpty() || destination != trip?.destination.orEmpty() ||
        start != trip?.startDate || end != trip?.endDate || budget != trip?.budget?.stripTrailingZeros()?.toPlainString().orEmpty()

    fun dismiss() { if (dirty && !busy) discard = true else onDismiss() }

    FormSheet(
        onDismissRequest = ::dismiss,
        canDismiss = !dirty && !busy,
        title = { Text(if (trip == null) "Where are you headed?" else "Update your trip", fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                error?.let { Notice(it, true) }
                OutlinedTextField(name, { name = it.take(80) }, Modifier.fillMaxWidth(), label = { Text("Trip name") }, singleLine = true, shape = RoundedCornerShape(8.dp), colors = wayfareFieldColors())
                OutlinedTextField(destination, { destination = it.take(120) }, Modifier.fillMaxWidth(), label = { Text("Destination") }, singleLine = true, shape = RoundedCornerShape(8.dp), colors = wayfareFieldColors())
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DateField("Depart", start, { start = it }, Modifier.weight(1f))
                    DateField("Return", end, { end = it }, Modifier.weight(1f))
                }
                OutlinedTextField(
                    budget, { budget = it }, Modifier.fillMaxWidth(), label = { Text("Budget in EUR") },
                    placeholder = { Text("2500") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    supportingText = { Text("Leave blank to track spend without a limit.") },
                    shape = RoundedCornerShape(8.dp), colors = wayfareFieldColors(),
                )
            }
        },
        confirmButton = {
            PrimaryButton(
                text = if (trip == null) "Start the ledger" else "Save changes",
                busy = busy,
            ) {
                val amount = budget.ifBlank { "0" }.toBigDecimalOrNull()
                error = when {
                    name.isBlank() -> "Give the trip a name."
                    amount == null || amount.signum() < 0 -> "Budget must be zero or more."
                    start != null && end != null && end!! < start!! -> "The return date lands before departure."
                    else -> null
                }
                if (error == null) scope.launch {
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
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        amount, { amount = it }, Modifier.weight(1f), label = { Text("Amount") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(8.dp), colors = wayfareFieldColors(),
                    )
                    CurrencyPicker(paidIn, { paidIn = it })
                }
                if (paidIn != currency && converted != null) {
                    Notice("≈ ${money(converted, currency)} · 1 $paidIn = ${fx.rateBetween(paidIn, currency, snapshot)?.setScale(4, java.math.RoundingMode.HALF_UP)} $currency${if (snapshot.stale) " · offline rate" else ""}")
                }
                OutlinedTextField(title, { title = it.take(120) }, Modifier.fillMaxWidth(), label = { Text("For") }, singleLine = true, shape = RoundedCornerShape(8.dp), colors = wayfareFieldColors())
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
                OutlinedTextField(
                    note, { note = it.take(500) }, Modifier.fillMaxWidth(), label = { Text("Note (optional)") },
                    minLines = 2, maxLines = 4,
                    shape = RoundedCornerShape(8.dp), colors = wayfareFieldColors(),
                )
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
                error = when {
                    parsed == null || parsed.signum() <= 0 -> "Enter an amount above zero."
                    title.isBlank() -> "What was it for?"
                    paidIn != currency && rate == null -> "No rate is available for $paidIn to $currency."
                    else -> null
                }
                if (error == null) scope.launch {
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
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                dismissButton()
                confirmButton()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(label: String, value: LocalDate?, onChange: (LocalDate?) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value?.toString().orEmpty(),
        onValueChange = {},
        modifier = modifier.clickable { open = true },
        enabled = false,
        label = { Text(label) },
        trailingIcon = { Icon(Icons.Outlined.CalendarMonth, null) },
        shape = RoundedCornerShape(8.dp),
        // Disabled only so the picker owns the tap: it still has to read as a
        // live field, so it keeps the hairline border and ink text.
        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
            disabledTextColor = Ink,
            disabledBorderColor = Hairline,
            disabledLabelColor = Ash,
            disabledTrailingIconColor = Ash,
            disabledContainerColor = CanvasWhite,
        ),
    )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerSheet(
    value: LocalDate?,
    onDismiss: () -> Unit,
    allowClear: Boolean = true,
    onChange: (LocalDate?) -> Unit,
) {
    val millis = value?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
    val picker = rememberDatePickerState(initialSelectedDateMillis = millis)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                onChange(picker.selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() })
            }) { Text("Use date", color = Ink, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = {
            Row {
                if (allowClear && value != null) TextButton(onClick = { onChange(null) }) { Text("Clear") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    ) { DatePicker(picker) }
}

@Composable
private fun CurrencyPicker(value: String, onChange: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column {
        Text("Paid in", color = Ash, style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
        SecondaryButton(value, pill = true) { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            FxRepository.CURRENCIES.forEach { currency ->
                DropdownMenuItem(text = { Text(currency) }, onClick = { onChange(currency); open = false })
            }
        }
    }
}

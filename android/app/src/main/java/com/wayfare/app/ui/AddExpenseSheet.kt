package com.wayfare.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wayfare.app.core.Category
import com.wayfare.app.core.Expense
import com.wayfare.app.core.ExpenseDraft
import com.wayfare.app.core.money
import com.wayfare.app.core.symbolFor
import com.wayfare.app.data.FxRepository
import kotlinx.coroutines.launch
import java.math.RoundingMode
import java.time.LocalDate

/**
 * Adding a line. The amount is typed on a keypad rather than a keyboard, because
 * an amount is the one field that is always a number and always the first thing
 * you know — the sheet opens straight onto it.
 *
 * The design's payer control ("You paid / Sam paid / Split it") is deliberately
 * absent: this is one person's tape, and there is nobody to split with.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseSheet(
    expense: Expense? = null,
    currency: String,
    initialPaidIn: String = currency,
    fx: FxRepository,
    onDismiss: () -> Unit,
    onSave: suspend (ExpenseDraft) -> Result<*>,
    onDelete: (suspend () -> Result<*>)? = null,
    onRememberCurrency: suspend (String) -> Unit = {},
) {
    val snapshot by fx.snapshot.collectAsStateWithLifecycle()
    var amount by rememberSaveable(expense?.id) {
        mutableStateOf((expense?.originalAmount ?: expense?.amount)?.stripTrailingZeros()?.toPlainString().orEmpty())
    }
    var title by rememberSaveable(expense?.id) { mutableStateOf(expense?.title.orEmpty()) }
    var paidIn by rememberSaveable(expense?.id, initialPaidIn) { mutableStateOf(expense?.originalCurrency ?: initialPaidIn) }
    var category by rememberSaveable(expense?.id) { mutableStateOf(expense?.category ?: Category.Food) }
    var spentOn by rememberSaveable(expense?.id) { mutableStateOf(expense?.spentOn ?: LocalDate.now()) }
    var note by rememberSaveable(expense?.id) { mutableStateOf(expense?.note.orEmpty()) }
    var showNote by rememberSaveable(expense?.id) { mutableStateOf(!expense?.note.isNullOrBlank()) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var discard by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val parsed = amount.toBigDecimalOrNull()
    val converted = parsed?.let { fx.convert(it, paidIn, currency, snapshot) }
    val dirty = title != expense?.title.orEmpty() ||
        amount != (expense?.originalAmount ?: expense?.amount)?.stripTrailingZeros()?.toPlainString().orEmpty() ||
        paidIn != (expense?.originalCurrency ?: initialPaidIn) || category != (expense?.category ?: Category.Food) ||
        spentOn != (expense?.spentOn ?: LocalDate.now()) || note != expense?.note.orEmpty()

    fun dismiss() {
        if (busy) return
        if (dirty) discard = true else onDismiss()
    }

    val currentCanDismiss by rememberUpdatedState(!dirty && !busy)
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { value ->
            if (value == SheetValue.Hidden && !currentCanDismiss) {
                discard = true
                false
            } else true
        },
    )

    ModalBottomSheet(
        onDismissRequest = ::dismiss,
        sheetState = sheetState,
        sheetMaxWidth = Dp.Unspecified,
        containerColor = CardNavy,
        contentColor = Paper,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        scrimColor = NightDeep.copy(alpha = .72f),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding().imePadding()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (expense == null) "Add a line" else "Fix the line",
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.headlineSmall,
                )
                CircleIconButton(Icons.Outlined.Close, "Close", raised = true, onClick = ::dismiss)
            }

            error?.let { Notice(it, true, Modifier.padding(top = 14.dp)) }

            // The figure, set the way a till prints it: big, amber, monospaced.
            Column(
                Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    symbolFor(paidIn).trim() + amount.ifBlank { "0" },
                    color = if (amount.isBlank()) Amber.copy(alpha = .45f) else Amber,
                    fontFamily = PlexMono,
                    fontWeight = FontWeight.Medium,
                    fontSize = 54.sp,
                    lineHeight = 56.sp,
                    letterSpacing = (-2).sp,
                    maxLines = 1,
                )
                // The title sits where a receipt puts the merchant: under the figure.
                BasicTextField(
                    value = title,
                    onValueChange = { title = it.take(120) },
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    textStyle = MaterialTheme.typography.titleMedium.copy(
                        color = Paper,
                        textAlign = TextAlign.Center,
                    ),
                    singleLine = true,
                    cursorBrush = SolidColor(Amber),
                    decorationBox = { field ->
                        Box(contentAlignment = Alignment.Center) {
                            if (title.isEmpty()) {
                                Text(
                                    "What was it for?",
                                    color = Slate,
                                    style = MaterialTheme.typography.titleMedium,
                                )
                            }
                            field()
                        }
                    },
                )
                if (paidIn != currency && converted != null) {
                    Text(
                        "≈ ${money(converted, currency)} · 1 $paidIn = ${fx.rateBetween(paidIn, currency, snapshot)?.setScale(4, RoundingMode.HALF_UP)} $currency" +
                            if (snapshot.stale) " · offline rate" else "",
                        Modifier.padding(top = 6.dp),
                        color = Slate,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            // Category first, because it is the one chip you always set.
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Category.entries.forEach { item ->
                    FilterPill(item.label, category == item) { category = item }
                }
            }

            // Then the three facts the design leaves out but the ledger needs.
            Row(
                Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DatePill(spentOn, Modifier.weight(1f)) { spentOn = it }
                CurrencyPill(paidIn, Modifier.weight(1f)) { paidIn = it }
                FilterPill(
                    if (note.isBlank()) "Note" else "Note ✓",
                    showNote,
                    Modifier.weight(1f),
                ) { showNote = !showNote }
            }

            AnimatedVisibility(showNote) {
                BasicTextField(
                    value = note,
                    onValueChange = { note = it.take(500) },
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.dp, Outline, RoundedCornerShape(16.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = Paper),
                    maxLines = 3,
                    cursorBrush = SolidColor(Amber),
                    decorationBox = { field ->
                        if (note.isEmpty()) {
                            Text("Anything worth remembering", color = Slate, style = MaterialTheme.typography.bodyMedium)
                        }
                        field()
                    },
                )
            }

            Keypad(
                Modifier.padding(top = 14.dp),
                onKey = { key -> amount = applyKey(amount, key) },
            )

            PrimaryButton(
                text = if (expense == null) "Print it to the tape" else "Save the line",
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
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
                        title.trim(),
                        converted ?: parsed!!.setScale(2, RoundingMode.HALF_UP),
                        if (paidIn == currency) null else parsed!!.setScale(2, RoundingMode.HALF_UP),
                        paidIn.takeIf { it != currency }, rate, category, spentOn,
                        note.trim().ifBlank { null },
                    )
                    onSave(draft).onSuccess {
                        onRememberCurrency(paidIn)
                        onDismiss()
                    }.onFailure { error = it.message ?: "Could not save that line." }
                    busy = false
                }
            }

            if (expense != null && onDelete != null) {
                TextButton(
                    onClick = { confirmDelete = true },
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                ) { Text("Delete this line", color = ErrorRed, fontWeight = FontWeight.Bold) }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (confirmDelete && onDelete != null) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        shape = RoundedCornerShape(28.dp),
        containerColor = CardNavy,
        title = { Text("Delete this line?", style = MaterialTheme.typography.headlineSmall) },
        text = { Text("This cannot be undone.", color = Slate) },
        confirmButton = {
            TextButton(onClick = {
                scope.launch {
                    onDelete().onSuccess { onDismiss() }.onFailure { error = it.message }
                    confirmDelete = false
                }
            }) { Text("Delete", color = ErrorRed, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep", color = Steel) } },
    )
    if (discard) AlertDialog(
        onDismissRequest = { discard = false },
        shape = RoundedCornerShape(28.dp),
        containerColor = CardNavy,
        title = { Text("Discard this line?", style = MaterialTheme.typography.headlineSmall) },
        text = { Text("It has not been printed to the tape.", color = Slate) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Discard", color = ErrorRed, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = { discard = false }) { Text("Keep editing", color = Steel) } },
    )
}

/**
 * Applies one keypad press to the amount. Kept pure and separate so the rules —
 * one decimal point, at most two decimals, no runaway leading zeroes — are
 * readable in one place rather than scattered through the key handlers.
 */
internal fun applyKey(current: String, key: String): String = when {
    key == "⌫" -> current.dropLast(1)
    key == "." -> if (current.contains('.')) current else if (current.isEmpty()) "0." else "$current."
    current.substringAfter('.', "").length >= 2 && current.contains('.') -> current
    current == "0" -> key
    current.length >= 12 -> current
    else -> current + key
}

private val KEYS = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", ".", "0", "⌫")

@Composable
private fun Keypad(modifier: Modifier = Modifier, onKey: (String) -> Unit) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        KEYS.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { key -> Key(key, Modifier.weight(1f)) { onKey(key) } }
            }
        }
    }
}

@Composable
private fun Key(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    Box(
        modifier.height(54.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (pressed) Amber.copy(alpha = .22f) else CardRaised)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (pressed) Amber else Paper,
            fontFamily = PlexMono,
            fontSize = 22.sp,
        )
    }
}

@Composable
private fun DatePill(value: LocalDate, modifier: Modifier = Modifier, onChange: (LocalDate) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val today = LocalDate.now()
    FilterPill(
        when (value) {
            today -> "Today"
            today.minusDays(1) -> "Yesterday"
            else -> com.wayfare.app.core.shortDate(value)
        },
        value != today,
        modifier,
    ) { open = true }
    // A line always happened on some day, so clearing the date is not offered.
    if (open) DatePickerSheet(value, { open = false }, allowClear = false) { picked ->
        picked?.let(onChange)
        open = false
    }
}

@Composable
private fun CurrencyPill(value: String, modifier: Modifier = Modifier, onChange: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        FilterPill(value, false, Modifier.fillMaxWidth()) { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            FxRepository.CURRENCIES.forEach { currency ->
                DropdownMenuItem(text = { Text(currency) }, onClick = { onChange(currency); open = false })
            }
        }
    }
}

/** A line someone else added: readable, never editable. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadOnlyExpenseSheet(expense: Expense, currency: String, payer: String, onDismiss: () -> Unit) {
    FormSheet(
        onDismissRequest = onDismiss,
        title = { Text(expense.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FigureWithQualifier(money(expense.amount, currency), null, size = 38.sp)
                Text(
                    "Printed by $payer. Only the traveller who added a line can change it.",
                    color = Slate,
                    style = MaterialTheme.typography.bodyMedium,
                )
                MonoLabel(
                    "${expense.category.label.uppercase()} · ${com.wayfare.app.core.tapeDayLabel(expense.spentOn)}",
                    color = Slate,
                )
                expense.originalCurrency?.let {
                    Text("Originally ${expense.originalAmount} $it", color = Slate, style = MaterialTheme.typography.bodyMedium)
                }
                expense.note?.let { Text(it, color = Paper, style = MaterialTheme.typography.bodyMedium) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close", color = Amber, fontWeight = FontWeight.Bold) } },
    )
}

/** A line that never reached the server: kept, explained, retried or discarded. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnsyncedExpenseDialog(
    expense: Expense,
    currency: String,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onDiscard: () -> Unit,
) {
    val failed = expense.syncState == com.wayfare.app.core.SyncState.Failed
    FormSheet(
        onDismissRequest = onDismiss,
        title = { Text(if (failed) "This line did not print" else "Still printing") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DottedLeaderRow(expense.title, money(expense.amount, currency))
                Text(
                    if (failed) {
                        "It is kept here so nothing is lost, but it is not counted in the trip total " +
                            "and other travellers cannot see it yet."
                    } else {
                        "It is saved on this phone and will sync as soon as there is a connection. " +
                            "You can edit it once it lands."
                    },
                    color = Slate,
                    style = MaterialTheme.typography.bodyMedium,
                )
                expense.syncError?.let { Notice(it, error = failed) }
            }
        },
        confirmButton = { PrimaryButton("Try again", onClick = onRetry) },
        dismissButton = {
            Row {
                if (failed) TextButton(onClick = onDiscard) { Text("Discard", color = ErrorRed, fontWeight = FontWeight.Bold) }
                TextButton(onClick = onDismiss) { Text("Close", color = Steel) }
            }
        },
    )
}

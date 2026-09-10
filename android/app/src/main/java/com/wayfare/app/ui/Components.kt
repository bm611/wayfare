package com.wayfare.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wayfare.app.core.SyncState
import com.wayfare.app.core.money
import com.wayfare.app.core.Trip
import com.wayfare.app.core.TripPhase
import com.wayfare.app.core.TripSummary
import com.wayfare.app.core.tripPhase
import java.math.BigDecimal

@Composable
fun Brand(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(29.dp).clip(RoundedCornerShape(8.dp)).background(Clay),
            contentAlignment = Alignment.Center,
        ) {
            Text("W", color = Color.White, fontWeight = FontWeight.Black, fontSize = 15.sp)
        }
        Text(
            "wayfare",
            modifier = Modifier.padding(start = 9.dp),
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.4).sp,
        )
    }
}

@Composable
fun ClayButton(text: String, modifier: Modifier = Modifier, busy: Boolean = false, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        enabled = !busy,
        colors = ButtonDefaults.buttonColors(containerColor = Clay, contentColor = Color.White),
        shape = RoundedCornerShape(16.dp),
    ) {
        if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
        else Text(text, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun Notice(message: String, error: Boolean = false, modifier: Modifier = Modifier) {
    Text(
        message,
        modifier = modifier.fillMaxWidth().background(
            if (error) ClayWash else PaperDeep, RoundedCornerShape(13.dp),
        ).padding(13.dp),
        color = if (error) ClayDeep else InkSoft,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    )
}

@Composable
fun TicketCard(summary: TripSummary, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val trip = summary.trip
    androidx.compose.material3.Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = Card,
        shadowElevation = 3.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, LineSoft),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(
                        trip.name,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        trip.destination ?: phaseLabel(trip),
                        modifier = Modifier.padding(top = 4.dp),
                        color = InkSoft,
                        fontSize = 13.sp,
                    )
                }
                Text(tripCode(trip), color = Clay, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            }
            Spacer(Modifier.height(18.dp))
            DashedDivider()
            Spacer(Modifier.height(15.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Metric("SPENT", money(summary.spent, trip.currency), Modifier.weight(1f))
                Metric("BUDGET", money(trip.budget, trip.currency), Modifier.weight(1f))
            }
            Spacer(Modifier.height(14.dp))
            BudgetMeter(summary.spent, trip.budget)
        }
    }
}

@Composable
private fun Metric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, color = InkFaint, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
        Text(value, modifier = Modifier.padding(top = 3.dp), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun BudgetMeter(spent: BigDecimal, budget: BigDecimal, modifier: Modifier = Modifier) {
    val ratio = if (budget.signum() <= 0) 0f else spent.divide(budget, 4, java.math.RoundingMode.HALF_UP).toFloat()
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(30) { index ->
            val active = index < (ratio.coerceAtMost(1f) * 30).toInt()
            Box(
                Modifier.weight(1f).height(13.dp).clip(RoundedCornerShape(2.dp))
                    .background(if (active) Clay else PaperDeep),
            )
        }
    }
}

@Composable
fun DashedDivider() {
    Canvas(Modifier.fillMaxWidth().height(1.dp)) {
        drawLine(
            color = Line,
            start = androidx.compose.ui.geometry.Offset.Zero,
            end = androidx.compose.ui.geometry.Offset(size.width, 0f),
            strokeWidth = 2f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(9f, 8f)),
        )
    }
}

@Composable
fun SyncBadge(state: SyncState) {
    if (state == SyncState.Synced) return
    Row(
        Modifier.background(ClayWash, RoundedCornerShape(20.dp)).padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.material3.Icon(Icons.Outlined.Sync, null, Modifier.size(12.dp), tint = Clay)
        Text(if (state == SyncState.Pending) "Pending" else "Failed", Modifier.padding(start = 4.dp), color = ClayDeep, fontSize = 11.sp)
    }
}

fun phaseLabel(trip: Trip): String = when (val phase = tripPhase(trip)) {
    is TripPhase.Active -> "Day ${phase.day} of ${phase.total}"
    is TripPhase.Upcoming -> "In ${phase.days} ${if (phase.days == 1L) "day" else "days"}"
    TripPhase.Past -> "Past trip"
    TripPhase.Undated -> "Dates open"
}

fun tripCode(trip: Trip): String {
    val words = (trip.destination ?: trip.name).replace(Regex("[^A-Za-z ]"), " ").trim().split(Regex("\\s+")).filter(String::isNotBlank)
    return when {
        words.size >= 3 -> words.take(3).joinToString("") { it.take(1) }
        words.size == 2 -> words[0].take(2) + words[1].take(1)
        else -> words.firstOrNull()?.take(3)?.padEnd(3, 'X') ?: "WYF"
    }.uppercase()
}

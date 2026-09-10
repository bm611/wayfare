package com.wayfare.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.wayfare.app.core.SyncState
import com.wayfare.app.core.Trip
import com.wayfare.app.core.TripPhase
import com.wayfare.app.core.TripSummary
import com.wayfare.app.core.dateRange
import com.wayfare.app.core.money
import com.wayfare.app.core.tripPhase
import java.math.BigDecimal
import java.math.RoundingMode

@Composable
fun Brand(modifier: Modifier = Modifier, showWordmark: Boolean = true) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(34.dp).clip(RoundedCornerShape(10.dp))
                .background(Brush.linearGradient(listOf(GradientStart, GradientEnd))),
            contentAlignment = Alignment.Center,
        ) {
            Text("W", color = Color.White, fontWeight = FontWeight.Black, fontSize = 17.sp)
        }
        if (showWordmark) {
            Text(
                "wayfare",
                modifier = Modifier.padding(start = 9.dp),
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp,
            )
        }
    }
}

@Composable
fun PrimaryButton(
    text: String,
    modifier: Modifier = Modifier,
    busy: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(54.dp),
        enabled = enabled && !busy,
        colors = ButtonDefaults.buttonColors(
            containerColor = Rausch,
            contentColor = Color.White,
            disabledContainerColor = Rausch.copy(alpha = 0.4f),
            disabledContentColor = Color.White,
        ),
        shape = RoundedCornerShape(50),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
    ) {
        if (busy) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
        } else {
            Text(text, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        }
    }
}

@Composable
fun Notice(message: String, error: Boolean = false, modifier: Modifier = Modifier) {
    Text(
        message,
        modifier = modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (error) RauschWash else PaperDeep)
            .padding(14.dp),
        color = if (error) RauschDark else InkSoft,
        fontSize = 13.sp,
        lineHeight = 19.sp,
    )
}

@Composable
fun TicketCard(
    summary: TripSummary,
    coverUrl: String?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val trip = summary.trip
    Column(
        modifier.fillMaxWidth().padding(bottom = 6.dp).clickable(onClick = onClick),
    ) {
        Box(
            Modifier.fillMaxWidth().height(196.dp).clip(RoundedCornerShape(16.dp)),
        ) {
            if (coverUrl != null) {
                AsyncImage(
                    model = coverUrl,
                    contentDescription = trip.destination?.let { "Destination cover for $it" },
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(
                    Modifier.fillMaxSize().background(placeholderBrush(trip)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        trip.name.trim().take(1).uppercase().ifBlank { "W" },
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 56.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Surface(
                color = Color.White.copy(alpha = 0.94f),
                shape = RoundedCornerShape(50),
                modifier = Modifier.padding(12.dp),
            ) {
                Row(
                    Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(6.dp).background(phaseColor(trip), CircleShape))
                    Text(
                        phaseLabel(trip),
                        Modifier.padding(start = 6.dp),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Ink,
                    )
                }
            }
            Surface(
                color = Color.White.copy(alpha = 0.94f),
                shape = RoundedCornerShape(50),
                modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
            ) {
                Text(
                    tripCode(trip),
                    Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    color = Rausch,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                )
            }
        }
        Text(
            trip.name,
            Modifier.padding(top = 12.dp),
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            listOfNotNull(trip.destination, dateRange(trip.startDate, trip.endDate)).joinToString(" · ")
                .ifBlank { "Dates open" },
            Modifier.padding(top = 2.dp),
            color = InkSoft,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(money(summary.spent, trip.currency), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            if (trip.budget.signum() > 0) {
                Text(
                    " of ${money(trip.budget, trip.currency)}",
                    color = InkSoft,
                    fontSize = 14.sp,
                )
            } else {
                Text(" logged", color = InkSoft, fontSize = 14.sp)
            }
        }
        if (trip.budget.signum() > 0) {
            Spacer(Modifier.height(8.dp))
            BudgetMeter(summary.spent, trip.budget)
        }
    }
}

private val PlaceholderGradients = listOf(
    listOf(Color(0xFF3D6EB4), Color(0xFF6C5CA8)),
    listOf(Color(0xFFB0724A), Color(0xFFC08A24)),
    listOf(Color(0xFF52843F), Color(0xFF3D6EB4)),
    listOf(Color(0xFFB94E73), Color(0xFFE31C5F)),
    listOf(Color(0xFF6C5CA8), Color(0xFF3D6EB4)),
    listOf(Color(0xFFC08A24), Color(0xFFB94E73)),
)

private fun placeholderBrush(trip: Trip): Brush {
    val pair = PlaceholderGradients[(trip.id.hashCode() and 0x7fffffff) % PlaceholderGradients.size]
    return Brush.linearGradient(pair)
}

@Composable
private fun phaseColor(trip: Trip): Color = when (tripPhase(trip)) {
    is TripPhase.Active -> Success
    is TripPhase.Upcoming -> Rausch
    else -> InkFaint
}

@Composable
fun BudgetMeter(spent: BigDecimal, budget: BigDecimal, modifier: Modifier = Modifier) {
    val ratio = if (budget.signum() <= 0) 0f
    else spent.divide(budget, 4, RoundingMode.HALF_UP).toFloat().coerceIn(0f, 1f)
    Box(
        modifier.fillMaxWidth().height(6.dp)
            .clip(RoundedCornerShape(50)).background(LineSoft),
    ) {
        if (ratio > 0f) {
            Box(
                Modifier.fillMaxHeight().fillMaxWidth(ratio)
                    .clip(RoundedCornerShape(50)).background(Rausch),
            )
        }
    }
}

@Composable
fun SyncBadge(state: SyncState) {
    if (state == SyncState.Synced) return
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(RauschWash)
            .padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Sync, null, Modifier.size(12.dp), tint = Rausch)
        Text(
            if (state == SyncState.Pending) "Pending" else "Failed",
            Modifier.padding(start = 4.dp),
            color = RauschDark,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
fun DashedDivider() {
    Spacer(Modifier.fillMaxWidth().height(1.dp).background(LineSoft))
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

package com.wayfare.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.outlined.FlightTakeoff
import androidx.compose.material.icons.outlined.Hotel
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.DirectionsTransit
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.NorthEast
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.wayfare.app.R
import com.wayfare.app.core.Category
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
            Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(Ink),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.FlightTakeoff, null, Modifier.size(22.dp), tint = Paper)
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
    icon: ImageVector? = null,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) .97f else 1f, spring(stiffness = 600f), label = "Button press")
    Button(
        onClick = onClick,
        modifier = modifier.height(54.dp).graphicsLayer { scaleX = scale; scaleY = scale },
        interactionSource = interactionSource,
        enabled = enabled && !busy,
        colors = ButtonDefaults.buttonColors(
            containerColor = Clay,
            contentColor = OnClay,
            disabledContainerColor = Clay.copy(alpha = 0.45f),
            disabledContentColor = OnClay,
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
    ) {
        if (busy) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = OnClay)
        } else {
            icon?.let {
                Icon(it, null, Modifier.size(20.dp))
                Spacer(Modifier.size(8.dp))
            }
            Text(text, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        }
    }
}

/**
 * Deliberately not a [PrimaryButton] in a different colour: Google requires its mark on a
 * neutral surface, and the quieter treatment keeps the email form the primary path.
 */
@Composable
fun GoogleButton(modifier: Modifier = Modifier, busy: Boolean = false, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) .97f else 1f, spring(stiffness = 600f), label = "Google press")
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(54.dp).graphicsLayer { scaleX = scale; scaleY = scale },
        interactionSource = interactionSource,
        enabled = !busy,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Card,
            contentColor = Ink,
            disabledContainerColor = Card,
            disabledContentColor = InkSoft,
        ),
        border = BorderStroke(1.dp, Line),
        shape = RoundedCornerShape(16.dp),
    ) {
        if (busy) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = InkSoft)
        } else {
            Image(painterResource(R.drawable.ic_google), null, Modifier.size(20.dp))
            Spacer(Modifier.size(10.dp))
            Text("Continue with Google", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        }
    }
}

@Composable
fun Notice(message: String, error: Boolean = false, modifier: Modifier = Modifier) {
    Text(
        message,
        modifier = modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (error) ClayWash else PaperDeep)
            .padding(14.dp),
        color = if (error) ClayDeep else InkSoft,
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
    val shape = RoundedCornerShape(24.dp)
    // One card, clipped first so the cover, the border and the press ripple all
    // stop at the same radius: the photograph is the card's own top edge rather
    // than a tile floating above a separate block of text.
    Column(
        modifier.fillMaxWidth()
            .clip(shape)
            .background(Card)
            .border(1.dp, Line, shape)
            .clickable(onClick = onClick),
    ) {
        Box(Modifier.fillMaxWidth().height(190.dp)) {
            DestinationArtwork(Modifier.fillMaxSize(), trip)
            if (coverUrl != null) {
                AsyncImage(
                    model = coverUrl,
                    contentDescription = trip.destination?.let { "Destination cover for $it" },
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            Surface(
                color = Card.copy(alpha = 0.94f),
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
                color = Card.copy(alpha = 0.94f),
                shape = RoundedCornerShape(50),
                modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
            ) {
                Icon(Icons.Outlined.NorthEast, null, Modifier.padding(10.dp).size(18.dp), tint = Ink)
            }
        }
        Column(Modifier.padding(16.dp)) {
            Text(
                trip.name,
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
}

@Composable
fun DestinationArtwork(modifier: Modifier = Modifier, trip: Trip? = null) {
    val coastal = ((trip?.id?.hashCode() ?: 0) and 1) == 0
    val sky = if (coastal) Color(0xFFE8EEE9) else Color(0xFFF3E6DB)
    val far = if (coastal) Color(0xFF93B2A5) else Color(0xFFCDA58B)
    val near = if (coastal) Color(0xFF527F77) else Color(0xFF976F60)
    Canvas(modifier.background(sky)) {
        drawCircle(Color(0xFFFFF8E8), size.height * .17f, Offset(size.width * .76f, size.height * .28f))
        val hills = Path().apply {
            moveTo(0f, size.height * .72f)
            cubicTo(size.width * .22f, size.height * .05f, size.width * .4f, size.height * .85f, size.width * .65f, size.height * .52f)
            quadraticTo(size.width * .86f, size.height * .27f, size.width, size.height * .52f)
            lineTo(size.width, size.height); lineTo(0f, size.height); close()
        }
        drawPath(hills, far)
        val foreground = Path().apply {
            moveTo(0f, size.height * .85f)
            cubicTo(size.width * .3f, size.height * .48f, size.width * .6f, size.height * 1.1f, size.width, size.height * .63f)
            lineTo(size.width, size.height); lineTo(0f, size.height); close()
        }
        drawPath(foreground, near)
    }
}

fun categoryIcon(category: Category): ImageVector = when (category) {
    Category.Flights -> Icons.Outlined.FlightTakeoff
    Category.Stays -> Icons.Outlined.Hotel
    Category.Food -> Icons.Outlined.Restaurant
    Category.Activities -> Icons.Outlined.PhotoCamera
    Category.Transport -> Icons.Outlined.DirectionsTransit
    Category.Shopping -> Icons.Outlined.ShoppingBag
    Category.Other -> Icons.AutoMirrored.Outlined.ReceiptLong
}

@Composable
private fun phaseColor(trip: Trip): Color = when (tripPhase(trip)) {
    is TripPhase.Active -> Success
    is TripPhase.Upcoming -> Clay
    else -> InkFaint
}

@Composable
fun BudgetMeter(spent: BigDecimal, budget: BigDecimal, modifier: Modifier = Modifier) {
    val ratio = if (budget.signum() <= 0) 0f
    else spent.divide(budget, 4, RoundingMode.HALF_UP).toFloat().coerceIn(0f, 1f)
    val animatedRatio by animateFloatAsState(ratio, tween(450), label = "Budget progress")
    Box(
        modifier.fillMaxWidth().height(6.dp)
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(ratio, 0f..1f) }
            .clip(RoundedCornerShape(50)).background(LineSoft),
    ) {
        if (animatedRatio > 0f) {
            Box(
                Modifier.fillMaxHeight().fillMaxWidth(animatedRatio)
                    .clip(RoundedCornerShape(50)).background(Clay),
            )
        }
    }
}

@Composable
fun SyncBadge(state: SyncState) {
    if (state == SyncState.Synced) return
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(ClayWash)
            .padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Sync, null, Modifier.size(12.dp), tint = Clay)
        Text(
            if (state == SyncState.Pending) "Pending" else "Failed",
            Modifier.padding(start = 4.dp),
            color = ClayDeep,
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

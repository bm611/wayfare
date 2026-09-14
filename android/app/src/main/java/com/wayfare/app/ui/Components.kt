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
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.zIndex
import androidx.compose.ui.layout.layout
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
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
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
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
import kotlin.math.roundToInt

/**
 * The system's signature three-layer lift: one tight shadow that reads as a
 * hairline and one wider, softer one under it. Reserved for panels and sheets
 * that float above the canvas — never for listing cards.
 */
fun Modifier.panelElevation(shape: Shape): Modifier = this
    .shadow(8.dp, shape, ambientColor = Color.Black.copy(alpha = .10f), spotColor = Color.Black.copy(alpha = .10f))
    .shadow(2.dp, shape, ambientColor = Color.Black.copy(alpha = .04f), spotColor = Color.Black.copy(alpha = .04f))

@Composable
fun Brand(modifier: Modifier = Modifier, showWordmark: Boolean = true) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.FlightTakeoff, null, Modifier.size(28.dp), tint = Rausch)
        if (showWordmark) {
            Text(
                "wayfare",
                modifier = Modifier.padding(start = 8.dp),
                color = Rausch,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.6).sp,
            )
        }
    }
}

/**
 * The Rausch CTA. One per surface: the moment the whole grayscale palette exists
 * to set up. Pressing scales to 0.98 rather than tinting or lifting.
 */
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
    val scale by animateFloatAsState(if (pressed && android.animation.ValueAnimator.areAnimatorsEnabled()) .98f else 1f, spring(stiffness = 600f), label = "Button press")
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp).graphicsLayer { scaleX = scale; scaleY = scale },
        interactionSource = interactionSource,
        enabled = enabled && !busy,
        colors = ButtonDefaults.buttonColors(
            containerColor = Rausch,
            contentColor = Color.White,
            disabledContainerColor = SoftCloud,
            disabledContentColor = Stone,
        ),
        shape = RoundedCornerShape(8.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 24.dp, vertical = 12.dp),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
    ) {
        if (busy) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
        } else {
            icon?.let {
                Icon(it, null, Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
            }
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** White, hairline-bordered, ink label. Every action that is not the one CTA. */
@Composable
fun SecondaryButton(
    text: String,
    modifier: Modifier = Modifier,
    pill: Boolean = false,
    icon: ImageVector? = null,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && android.animation.ValueAnimator.areAnimatorsEnabled()) .98f else 1f, spring(stiffness = 600f), label = "Secondary press")
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp).graphicsLayer { scaleX = scale; scaleY = scale },
        interactionSource = interactionSource,
        colors = ButtonDefaults.outlinedButtonColors(containerColor = CanvasWhite, contentColor = Ink),
        border = BorderStroke(1.dp, Hairline),
        shape = RoundedCornerShape(if (pill) 20.dp else 8.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    ) {
        icon?.let {
            Icon(it, null, Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/**
 * Google requires its mark on a neutral surface, which is also exactly what a
 * secondary action looks like here — so it reuses the outlined treatment.
 */
@Composable
fun GoogleButton(modifier: Modifier = Modifier, busy: Boolean = false, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && android.animation.ValueAnimator.areAnimatorsEnabled()) .98f else 1f, spring(stiffness = 600f), label = "Google press")
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp).graphicsLayer { scaleX = scale; scaleY = scale },
        interactionSource = interactionSource,
        enabled = !busy,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = CanvasWhite,
            contentColor = Ink,
            disabledContainerColor = CanvasWhite,
            disabledContentColor = Mute,
        ),
        border = BorderStroke(1.dp, Hairline),
        shape = RoundedCornerShape(8.dp),
    ) {
        if (busy) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Ash)
        } else {
            Image(painterResource(R.drawable.ic_google), null, Modifier.size(18.dp))
            Spacer(Modifier.size(10.dp))
            Text("Continue with Google", style = MaterialTheme.typography.labelLarge)
        }
    }
}

/**
 * The circular icon button that recurs throughout the system — back, share,
 * options, carousel controls. Always 50%, never any other geometry.
 */
@Composable
fun CircleIconButton(
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    onPhotograph: Boolean = false,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && android.animation.ValueAnimator.areAnimatorsEnabled()) .98f else 1f, spring(stiffness = 600f), label = "Icon press")
    Box(
        modifier
            .size(48.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            // On photography a 4dp white ring separates the button from whatever
            // colour happens to sit behind it. A toggle that is on takes an Ink
            // ring, the same switch to Ink a focused field makes.
            .background(if (active || onPhotograph) CanvasWhite else SoftCloud)
            .then(
                when {
                    active -> Modifier.border(1.5.dp, Ink, CircleShape)
                    onPhotograph -> Modifier.border(1.dp, Hairline, CircleShape)
                    else -> Modifier
                },
            )
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, Modifier.size(18.dp), tint = Ink)
    }
}

@Composable
fun Notice(message: String, error: Boolean = false, modifier: Modifier = Modifier) {
    Text(
        message,
        modifier = modifier.fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (error) CanvasWhite else SoftCloud)
            .then(if (error) Modifier.border(1.dp, ErrorRed, RoundedCornerShape(8.dp)) else Modifier)
            .padding(14.dp),
        color = if (error) ErrorRed else Ash,
        style = MaterialTheme.typography.bodyMedium,
    )
}

/** A photo-led card with a content-sized overlay and Material touch feedback. */
@Composable
fun ListingCard(
    summary: TripSummary,
    coverUrl: String?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val trip = summary.trip
    val destination = trip.destination?.trim().orEmpty()
    val isPrint = trip.coverPath?.endsWith("-print.jpg") == true
    // The cover stays untouched apart from the countdown; the numbers ride in a
    // drawer tucked under it. Only covers without lettering need the place
    // named in words.
    val drawer = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp)
    Column(modifier.fillMaxWidth().clickable(onClickLabel = "Open trip", onClick = onClick)) {
        Box(
            Modifier.fillMaxWidth().zIndex(1f)
                .shadow(4.dp, RoundedCornerShape(28.dp))
                .clip(RoundedCornerShape(28.dp)).background(CanvasWhite).padding(4.dp)
                .clip(RoundedCornerShape(24.dp)).aspectRatio(3f / 2f),
        ) {
            DestinationArtwork(Modifier.fillMaxSize(), trip)
            if (coverUrl != null) AsyncImage(
                model = coverUrl,
                contentDescription = if (isPrint) destination.ifEmpty { trip.name } else null,
                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
            )
            Text(
                phaseLabel(trip), color = Ink, style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(12.dp).clip(RoundedCornerShape(12.dp))
                    .background(CanvasWhite).padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
        // Tucked 12dp up under the card, so its top edge disappears behind it.
        Row(
            Modifier.padding(horizontal = 14.dp).fillMaxWidth()
                .layout { measurable, constraints ->
                    val tuck = 12.dp.roundToPx()
                    val placeable = measurable.measure(constraints)
                    layout(placeable.width, placeable.height - tuck) { placeable.place(0, -tuck) }
                }
                .clip(drawer).background(SoftCloud).border(1.dp, Hairline, drawer)
                .padding(start = 18.dp, end = 18.dp, top = 26.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val ash = Ash
            Text(
                if (trip.coverStatus == "pending") AnnotatedString("Creating your cover…") else buildAnnotatedString {
                    if (!isPrint) withStyle(SpanStyle(color = ash)) { append("${destination.ifEmpty { trip.name }} · ") }
                    withStyle(SpanStyle(color = RauschDeep, fontWeight = FontWeight.Bold)) { append(money(summary.spent, trip.currency)) }
                    withStyle(SpanStyle(color = ash)) { append(" spent") }
                },
                Modifier.weight(1f), color = Ash, maxLines = 1,
                style = MaterialTheme.typography.bodyMedium,
            )
            Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, Modifier.size(20.dp), tint = Ink)
        }
    }
}

/** One small fact beside a glyph, kept on one line. */
@Composable
fun MetaLabel(icon: ImageVector, text: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(14.dp), tint = Ash)
        Text(
            text,
            Modifier.padding(start = 6.dp),
            color = Ash,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Stands in for the cover photograph until one is generated. Deliberately
 * grayscale: a placeholder should read as an unloaded image, not as a second
 * accent colour competing with Rausch.
 */
@Composable
fun DestinationArtwork(modifier: Modifier = Modifier, trip: Trip? = null) {
    val coastal = ((trip?.id?.hashCode() ?: 0) and 1) == 0
    val sky = SoftCloud
    val far = if (coastal) Color(0xFFE4E4E4) else Color(0xFFEBEBEB)
    val near = if (coastal) Color(0xFFD2D2D2) else Color(0xFFDCDCDC)
    Canvas(modifier.background(sky)) {
        drawCircle(Color(0xFFF0F0F0), size.height * .17f, Offset(size.width * .76f, size.height * .28f))
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

/** A compact budget rail with an optional percentage readout on detail views. */
@Composable
fun BudgetMeter(
    spent: BigDecimal,
    budget: BigDecimal,
    modifier: Modifier = Modifier,
    trackColor: Color = SoftCloud,
    progressColor: Color = Rausch,
    showLabel: Boolean = false,
) {
    val rawRatio = if (budget.signum() <= 0) 0f
    else spent.divide(budget, 4, RoundingMode.HALF_UP).toFloat().coerceAtLeast(0f)
    val ratio = rawRatio.coerceAtMost(1f)
    val percentage = (rawRatio * 100).roundToInt()
    val animatedRatio by animateFloatAsState(ratio, tween(450), label = "Budget progress")
    Column(modifier.fillMaxWidth()) {
        if (showLabel) {
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                Text("Budget used", Modifier.weight(1f), color = Ash, style = MaterialTheme.typography.bodySmall)
                Text("$percentage%", color = Ash, style = MaterialTheme.typography.labelMedium)
            }
        }
        Box(
            Modifier.fillMaxWidth().height(4.dp)
                .semantics { progressBarRangeInfo = ProgressBarRangeInfo(ratio, 0f..1f) }
                .clip(CircleShape).background(trackColor),
        ) {
            if (animatedRatio > 0f) {
                Box(
                    Modifier.fillMaxHeight().fillMaxWidth(animatedRatio)
                        .clip(CircleShape).background(if (rawRatio > 1f) ErrorRed else progressColor),
                )
            }
        }
    }
}

@Composable
fun SyncBadge(state: SyncState) {
    if (state == SyncState.Synced) return
    Row(
        Modifier.clip(RoundedCornerShape(14.dp)).background(SoftCloud)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Sync, null, Modifier.size(11.dp), tint = if (state == SyncState.Failed) ErrorRed else Ash)
        Text(
            if (state == SyncState.Pending) "Pending" else "Failed",
            Modifier.padding(start = 4.dp),
            color = if (state == SyncState.Failed) ErrorRed else Ash,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
fun HairlineDivider(modifier: Modifier = Modifier) {
    Spacer(modifier.fillMaxWidth().height(1.dp).background(Hairline))
}

fun phaseLabel(trip: Trip): String = when (val phase = tripPhase(trip)) {
    is TripPhase.Active -> "Day ${phase.day} of ${phase.total}"
    is TripPhase.Upcoming -> "In ${phase.days} ${if (phase.days == 1L) "day" else "days"}"
    TripPhase.Past -> "Past trip"
    TripPhase.Undated -> "Dates open"
}

@Composable
fun TripIdentity(trip: Trip, showPhase: Boolean = false) {
    val destination = trip.destination?.trim().orEmpty()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
            if (destination.isNotEmpty()) Icon(Icons.Outlined.Place, null, Modifier.padding(top = 4.dp).size(18.dp), tint = Ash)
            Text(destination.ifEmpty { trip.name }, style = MaterialTheme.typography.headlineSmall)
        }
        if (destination.isNotEmpty() && !destination.equals(trip.name.trim(), ignoreCase = true)) {
            Text(trip.name, color = Ash, style = MaterialTheme.typography.bodyMedium)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            MetaLabel(Icons.Outlined.CalendarMonth, dateRange(trip.startDate, trip.endDate))
            if (showPhase && tripPhase(trip) != TripPhase.Undated) {
                Text(phaseLabel(trip), color = Ash, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
fun TripLoadingSkeleton(modifier: Modifier = Modifier, showCover: Boolean = true) {
    if (showCover) {
        Column(
            modifier.fillMaxWidth().clip(RoundedCornerShape(32.dp)).background(SoftCloud)
                .clearAndSetSemantics { contentDescription = "Loading trips" }.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.fillMaxWidth().aspectRatio(3f / 2f))
            Spacer(Modifier.fillMaxWidth(.65f).height(24.dp).clip(RoundedCornerShape(4.dp)).background(Hairline))
        }
        return
    }
    Column(modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = if (showCover) "Loading trips" else "Loading trip details" }, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Spacer(Modifier.fillMaxWidth(.65f).height(24.dp).clip(RoundedCornerShape(4.dp)).background(SoftCloud))
        Spacer(Modifier.fillMaxWidth(.45f).height(16.dp).clip(RoundedCornerShape(4.dp)).background(SoftCloud))
        Spacer(Modifier.fillMaxWidth().height(if (showCover) 4.dp else 180.dp).clip(RoundedCornerShape(14.dp)).background(SoftCloud))
    }
}

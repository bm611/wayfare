package com.wayfare.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.wayfare.app.core.StampStyle
import com.wayfare.app.core.StampTint
import com.wayfare.app.core.Trip
import com.wayfare.app.core.stampMonth

/** Matches `--stamp-*` in the web index.css; the dark tints are the portfolio's. */
private fun tintColor(tint: StampTint, dark: Boolean) = when (tint) {
    StampTint.Peach -> if (dark) Color(0xFF4E2F1C) else Color(0xFFFCD5B9)
    StampTint.Sky -> if (dark) Color(0xFF1C3645) else Color(0xFFC7E7F9)
    StampTint.Sage -> if (dark) Color(0xFF223928) else Color(0xFFCDEAD2)
    StampTint.Lilac -> if (dark) Color(0xFF332B44) else Color(0xFFE1D8F9)
}

/**
 * The ink a stamp is pressed in: its own tint, taken deep enough to read as
 * text on it (and pale enough on the dark tints). Matches `--stamp-*-ink`.
 */
private fun inkColor(tint: StampTint, dark: Boolean) = when (tint) {
    StampTint.Peach -> if (dark) Color(0xFFF6C7A6) else Color(0xFF8A4424)
    StampTint.Sky -> if (dark) Color(0xFFA9D6F0) else Color(0xFF1F5A7A)
    StampTint.Sage -> if (dark) Color(0xFFB5DFBE) else Color(0xFF2F6B3C)
    StampTint.Lilac -> if (dark) Color(0xFFD0C3F5) else Color(0xFF5B4596)
}

/**
 * The place a stamp names: the model's label once it has drawn one, until then
 * the place as typed, less any region after a comma.
 */
fun stampLabel(trip: Trip): String =
    trip.coverArt?.takeIf { trip.coverStatus == "ready" }?.label?.ifBlank { null }
        ?: (trip.destination?.substringBefore(',')?.trim()?.ifBlank { null } ?: trip.name).uppercase()

/**
 * A trip's passport stamp: a tinted card with a dashed inner rule, the drawing,
 * the place and the month, all pressed in the tint's own ink and set at the
 * trip's own angle. Mirrors the web
 * `Stamp.tsx`. Until the drawing lands it is the same stamp, empty, with its
 * rule breathing while the server draws.
 */
@Composable
fun Stamp(trip: Trip, modifier: Modifier = Modifier) {
    val style = remember(trip.id) { StampStyle.of(trip.id) }
    val dark = isSystemInDarkTheme()
    val tint = tintColor(style.tint, dark)
    val ink = inkColor(style.tint, dark)
    val art = trip.coverArt?.takeIf { trip.coverStatus == "ready" }
    // Already validated server side; a path that still fails to parse is skipped, not fatal.
    val paths = remember(art) {
        art?.paths.orEmpty().mapNotNull { runCatching { PathParser().parsePathString(it).toPath() }.getOrNull() }
    }
    val label = stampLabel(trip)

    val outer = stampShape(style.arched, 18)
    val inner = stampShape(style.arched, 13)

    // Presses onto the card once, not every time the list scrolls it back in.
    var pressed by rememberSaveable(trip.id) { mutableStateOf(false) }
    val press = remember { Animatable(if (pressed) 1f else 0f) }
    LaunchedEffect(Unit) {
        press.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
        pressed = true
    }
    val inked by animateFloatAsState(if (paths.isEmpty()) 0f else 1f, tween(360), label = "ink")
    val breathing = if (trip.coverStatus == "pending") {
        rememberInfiniteTransition(label = "developing").animateFloat(
            1f, 0.35f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "rule",
        ).value
    } else 1f

    Column(
        modifier
            .clearAndSetSemantics { }
            .graphicsLayer {
                rotationZ = style.tilt
                alpha = press.value
                val grow = 1.12f - 0.12f * press.value
                scaleX = grow; scaleY = grow
            }
            .width(104.dp)
            .clip(outer)
            .background(tint)
            .drawWithContent {
                drawContent()
                val inset = 5.dp.toPx()
                translate(inset, inset) {
                    drawOutline(
                        inner.createOutline(Size(size.width - 2 * inset, size.height - 2 * inset), layoutDirection, this),
                        color = ink.copy(alpha = 0.35f * breathing),
                        style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.5.dp.toPx(), 3.dp.toPx()))),
                    )
                }
            }
            .padding(start = 10.dp, end = 10.dp, top = 14.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Canvas(Modifier.size(52.dp).graphicsLayer { alpha = inked }) {
            val unit = size.width / 64f
            scale(unit, unit, pivot = Offset.Zero) {
                val stroke = Stroke(1.75f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                paths.forEach { drawPath(it, ink, style = stroke) }
            }
        }
        Text(
            label, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.02.em),
        )
        // An undated stamp still gets its date line, so it never looks unfinished.
        Text(
            stampMonth(trip.startDate ?: trip.endDate) ?: "DATES OPEN", color = ink.copy(alpha = 0.75f),
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontSize = 10.sp, letterSpacing = 0.08.em),
        )
    }
}

/** Square, or an arch whose top is a full semicircle, as on the web stamps. */
private fun stampShape(arched: Boolean, corner: Int) = if (arched) {
    RoundedCornerShape(CornerSize(50), CornerSize(50), CornerSize(corner.dp), CornerSize(corner.dp))
} else {
    RoundedCornerShape(corner.dp)
}

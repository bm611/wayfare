package com.wayfare.app.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wayfare.app.R

// Airbnb design language. One accent, one type family, disciplined grayscale for
// everything else — the photography is meant to carry the colour. Token names
// match `design.md` and the iOS `Palette` so the two clients stay in step.

/** The signature coral-pink. Primary CTAs and the active-tab indicator only. */
val Rausch = Color(0xFFFF385C)
/** Pressed and active states of anything filled with [Rausch]. */
val RauschDeep = Color(0xFFE00B41)
/** Product-tier accents. The only colours allowed beside [Rausch]. */
val PlusMagenta = Color(0xFF92174D)
val LuxePurple = Color(0xFF460479)

val CanvasWhite = Color(0xFFFFFFFF)
/** Subsurface tint for sections that should step back from the white canvas. */
val SoftCloud = Color(0xFFF7F7F7)
/** The 1dp workhorse: every card-to-card and row-to-row divider. */
val Hairline = Color(0xFFDDDDDD)

/** The system's near-black. Roughly 90% of all text, and never pure #000000. */
val Ink = Color(0xFF222222)
/** Focused input text and one-step-down emphasis. */
val Charcoal = Color(0xFF3F3F3F)
/** Secondary labels and subtitle copy. */
val Ash = Color(0xFF6A6A6A)
/** Disabled controls and low-priority metadata. */
val Mute = Color(0xFF929292)
/** Tertiary dividers, icon strokes, placeholder avatars. */
val Stone = Color(0xFFC1C1C1)

val ErrorRed = Color(0xFFC13515)
val DeepError = Color(0xFFB32505)
/** Legal and informational links — the one non-monochrome link colour. */
val InfoBlue = Color(0xFF428BFF)

private val WayfareColors = lightColorScheme(
    primary = Rausch,
    onPrimary = CanvasWhite,
    primaryContainer = Rausch,
    onPrimaryContainer = CanvasWhite,
    secondary = Ink,
    onSecondary = CanvasWhite,
    background = CanvasWhite,
    onBackground = Ink,
    surface = CanvasWhite,
    onSurface = Ink,
    surfaceVariant = SoftCloud,
    onSurfaceVariant = Ash,
    surfaceContainer = SoftCloud,
    // Dialogs, sheets and menus are white cards on a white canvas; the hairline
    // border and the layered shadow do the separating, never a tinted surface.
    surfaceContainerHigh = CanvasWhite,
    surfaceContainerHighest = CanvasWhite,
    outline = Hairline,
    outlineVariant = Hairline,
    error = ErrorRed,
    onError = CanvasWhite,
)

/**
 * Airbnb Cereal VF is proprietary; Manrope is the closest face already bundled
 * with both clients. Weight 400 is deliberately mapped to 500 so text that never
 * names a weight still lands on the system's body weight rather than a lighter one.
 */
private val Cereal = FontFamily(
    Font(R.font.manrope, weight = FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.manrope, weight = FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.manrope, weight = FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.manrope, weight = FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
)

// One family carries 11sp badges through 32sp display. Display sizes compress
// tracking to feel chiselled; body sizes stay at zero tracking for readability.
private val WayfareTypography = Typography(
    // Page display, e.g. "Your trips."
    displaySmall = TextStyle(
        fontFamily = Cereal, fontWeight = FontWeight.Bold,
        fontSize = 32.sp, lineHeight = 38.sp, letterSpacing = (-0.6).sp,
    ),
    // Section heading.
    headlineMedium = TextStyle(
        fontFamily = Cereal, fontWeight = FontWeight.Bold,
        fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.5).sp,
    ),
    // Subsection heading / content divider.
    headlineSmall = TextStyle(
        fontFamily = Cereal, fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp, lineHeight = 26.sp, letterSpacing = (-0.44).sp,
    ),
    // Listing title.
    titleLarge = TextStyle(
        fontFamily = Cereal, fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp, lineHeight = 24.sp, letterSpacing = (-0.18).sp,
    ),
    // Subtitle bold: host name, city name.
    titleMedium = TextStyle(
        fontFamily = Cereal, fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp, lineHeight = 20.sp,
    ),
    // Body medium — the system's "regular".
    bodyLarge = TextStyle(
        fontFamily = Cereal, fontWeight = FontWeight.Medium,
        fontSize = 16.sp, lineHeight = 22.sp,
    ),
    // Caption medium: metadata and subtitle lines.
    bodyMedium = TextStyle(
        fontFamily = Cereal, fontWeight = FontWeight.Medium,
        fontSize = 14.sp, lineHeight = 20.sp,
    ),
    // Caption small: dates, micro-metadata.
    bodySmall = TextStyle(
        fontFamily = Cereal, fontWeight = FontWeight.Medium,
        fontSize = 13.sp, lineHeight = 18.sp,
    ),
    // Button large.
    labelLarge = TextStyle(
        fontFamily = Cereal, fontWeight = FontWeight.Medium,
        fontSize = 16.sp, lineHeight = 20.sp,
    ),
    // Caption bold: numeric stats, small-text emphasis.
    labelMedium = TextStyle(
        fontFamily = Cereal, fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp, lineHeight = 20.sp,
    ),
    // Compact badge. Sentence case: the system allows no uppercase above 8sp.
    labelSmall = TextStyle(
        fontFamily = Cereal, fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp, lineHeight = 14.sp,
    ),
)

/**
 * The one uppercase role in the system, reserved for price footnotes and
 * decimal tails. Nothing larger than this may be set in capitals.
 */
val SuperscriptStyle = TextStyle(
    fontFamily = Cereal, fontWeight = FontWeight.Bold,
    fontSize = 8.sp, lineHeight = 10.sp, letterSpacing = 0.32.sp,
)

val WayfareShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),   // inline tags and chips
    small = RoundedCornerShape(8.dp),        // buttons, inputs, dropdowns
    medium = RoundedCornerShape(14.dp),      // listing photography, containers, badges
    large = RoundedCornerShape(20.dp),       // pill buttons, hero images, booking panel
    extraLarge = RoundedCornerShape(32.dp),  // search pill, extra-large containers
)

@Composable
fun WayfareTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = WayfareColors,
        typography = WayfareTypography,
        shapes = WayfareShapes,
        content = content,
    )
}

package com.wayfare.app.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.LocalContentColor
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

// Semantic getters let all existing screens follow the system appearance.
val CanvasWhite: Color @Composable get() = MaterialTheme.colorScheme.surface
val SoftCloud: Color @Composable get() = MaterialTheme.colorScheme.surfaceVariant
val Hairline: Color @Composable get() = MaterialTheme.colorScheme.outline
val Ink: Color @Composable get() = MaterialTheme.colorScheme.onSurface
val Charcoal: Color @Composable get() = MaterialTheme.colorScheme.onSurface
val Ash: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
val Mute: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
val Stone: Color @Composable get() = MaterialTheme.colorScheme.outline
val ErrorRed: Color @Composable get() = MaterialTheme.colorScheme.error
val DeepError: Color @Composable get() = MaterialTheme.colorScheme.error
val InfoBlue = Color(0xFF428BFF)

private val WayfareColors = lightColorScheme(
    primary = Rausch, onPrimary = Color.White,
    primaryContainer = Rausch, onPrimaryContainer = Color.White,
    secondary = Color(0xFF222222), onSecondary = Color.White,
    background = Color.White, onBackground = Color(0xFF222222),
    surface = Color.White, onSurface = Color(0xFF222222),
    surfaceVariant = Color(0xFFF7F7F7), onSurfaceVariant = Color(0xFF6A6A6A),
    surfaceContainer = Color(0xFFF7F7F7),
    surfaceContainerHigh = Color.White, surfaceContainerHighest = Color.White,
    outline = Color(0xFFDDDDDD), outlineVariant = Color(0xFFDDDDDD),
    error = Color(0xFFC13515), onError = Color.White,
)
private val WayfareDarkColors = darkColorScheme(
    primary = Rausch, onPrimary = Color.White,
    primaryContainer = Rausch, onPrimaryContainer = Color.White,
    secondary = Color(0xFFF3F3F2), onSecondary = Color(0xFF181A1B),
    background = Color(0xFF181A1B), onBackground = Color(0xFFF3F3F2),
    surface = Color(0xFF181A1B), onSurface = Color(0xFFF3F3F2),
    surfaceVariant = Color(0xFF242729), onSurfaceVariant = Color(0xFFB6B9BB),
    surfaceContainer = Color(0xFF242729),
    surfaceContainerHigh = Color(0xFF242729), surfaceContainerHighest = Color(0xFF242729),
    outline = Color(0xFF44484B), outlineVariant = Color(0xFF44484B),
    error = Color(0xFFFF927D), onError = Color(0xFF181A1B),
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
    val colors = if (isSystemInDarkTheme()) WayfareDarkColors else WayfareColors
    MaterialTheme(
        colorScheme = colors,
        typography = WayfareTypography,
        shapes = WayfareShapes,
        content = { CompositionLocalProvider(LocalContentColor provides colors.onSurface, content = content) },
    )
}

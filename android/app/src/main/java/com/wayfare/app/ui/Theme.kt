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

// Mirrors the web app's "boarding pass" palette (src/index.css): warm paper,
// ink, and a single clay accent. Values are the CSS custom properties verbatim
// so the two clients stay in step.
val Clay = Color(0xFFB5543C)
val ClayDeep = Color(0xFF8D3D29)
val ClayWash = Color(0xFFF4E3DC)
/** Accent buttons ink their label in warmed-off-white, never pure white. */
val OnClay = Color(0xFFFFF8F4)

val Paper = Color(0xFFF7F2E9)
val PaperDeep = Color(0xFFEFE7D9)
val Card = Color(0xFFFFFCF6)
val Ink = Color(0xFF1A1714)
val InkSoft = Color(0xFF6F6459)
val InkFaint = Color(0xFF786D61)
val Line = Color(0xFFE3D9CA)
val LineSoft = Color(0xFFEFE7DB)

// The one status colour the web palette has no token for, borrowed from the
// activities category so it sits on paper rather than glowing off it.
val Success = Color(0xFF55713F)

val CategoryColors = mapOf(
    "flights" to Color(0xFF40697D),
    "stays" to Color(0xFF8A5A44),
    "food" to Color(0xFFA8761F),
    "activities" to Color(0xFF55713F),
    "transport" to Color(0xFF66628A),
    "shopping" to Color(0xFF9D4F61),
    "other" to Color(0xFF7A736A),
)

private val WayfareColors = lightColorScheme(
    primary = Clay,
    onPrimary = OnClay,
    primaryContainer = ClayWash,
    onPrimaryContainer = ClayDeep,
    secondary = Ink,
    onSecondary = Paper,
    background = Paper,
    onBackground = Ink,
    surface = Card,
    onSurface = Ink,
    surfaceVariant = PaperDeep,
    onSurfaceVariant = InkSoft,
    surfaceContainer = PaperDeep,
    // Dialogs and menus lift off the warm ground as card stock.
    surfaceContainerHigh = Card,
    surfaceContainerHighest = Card,
    outline = Line,
    outlineVariant = LineSoft,
    error = ClayDeep,
    onError = OnClay,
)

private val TravelFont = FontFamily(
    Font(R.font.manrope, weight = FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.manrope, weight = FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.manrope, weight = FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.manrope, weight = FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
)

private val WayfareTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = TravelFont, fontWeight = FontWeight.Bold,
        fontSize = 34.sp, lineHeight = 38.sp, letterSpacing = (-0.6).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = TravelFont, fontWeight = FontWeight.Bold,
        fontSize = 28.sp, lineHeight = 32.sp, letterSpacing = (-0.4).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = TravelFont, fontWeight = FontWeight.Bold,
        fontSize = 22.sp, lineHeight = 27.sp, letterSpacing = (-0.2).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = TravelFont, fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp, lineHeight = 24.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = TravelFont, fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp, lineHeight = 21.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = TravelFont, fontWeight = FontWeight.Normal,
        fontSize = 16.sp, lineHeight = 23.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = TravelFont, fontWeight = FontWeight.Normal,
        fontSize = 14.sp, lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = TravelFont, fontWeight = FontWeight.Normal,
        fontSize = 13.sp, lineHeight = 18.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = TravelFont, fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp, lineHeight = 20.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = TravelFont, fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.2.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = TravelFont, fontWeight = FontWeight.Bold,
        fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.4.sp,
    ),
)

val WayfareShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
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

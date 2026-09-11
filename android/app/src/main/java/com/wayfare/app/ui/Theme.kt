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

// Airbnb-inspired palette. Rausch is the signature brand accent, kept warm and
// confident against an almost-white canvas with hairline dividers.
val Rausch = Color(0xFFD92D50)
val RauschDeep = Color(0xFFE31C5F)
val RauschDark = Color(0xFFC13515)
val RauschWash = Color(0xFFFFEEF1)
val GradientStart = Color(0xFFE61E4D)
val GradientEnd = Color(0xFFD70466)

val Paper = Color(0xFFFFFFFF)
val PaperDeep = Color(0xFFF7F7F7)
val Card = Color(0xFFFFFFFF)
val Ink = Color(0xFF222222)
val InkSoft = Color(0xFF6A6A6A)
val InkFaint = Color(0xFF929292)
val Line = Color(0xFFDDDDDD)
val LineSoft = Color(0xFFEBEBEB)

val Success = Color(0xFF008A05)
val Star = Color(0xFFFFB400)

val CategoryColors = mapOf(
    "flights" to Color(0xFF3D6EB4),
    "stays" to Color(0xFFB0724A),
    "food" to Color(0xFFC08A24),
    "activities" to Color(0xFF52843F),
    "transport" to Color(0xFF6C5CA8),
    "shopping" to Color(0xFFB94E73),
    "other" to Color(0xFF7A736A),
)

private val WayfareColors = lightColorScheme(
    primary = Rausch,
    onPrimary = Color.White,
    primaryContainer = RauschWash,
    onPrimaryContainer = RauschDark,
    secondary = Ink,
    onSecondary = Color.White,
    background = Paper,
    onBackground = Ink,
    surface = Card,
    onSurface = Ink,
    surfaceVariant = PaperDeep,
    onSurfaceVariant = InkSoft,
    surfaceContainer = PaperDeep,
    surfaceContainerHigh = PaperDeep,
    surfaceContainerHighest = PaperDeep,
    outline = Line,
    outlineVariant = LineSoft,
    error = RauschDark,
    onError = Color.White,
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

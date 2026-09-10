package com.wayfare.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Paper = Color(0xFFF7F2E9)
val PaperDeep = Color(0xFFEFE7D9)
val Card = Color(0xFFFFFCF6)
val Ink = Color(0xFF1A1714)
val InkSoft = Color(0xFF6F6459)
val InkFaint = Color(0xFF786D61)
val Line = Color(0xFFE3D9CA)
val LineSoft = Color(0xFFEFE7DB)
val Clay = Color(0xFFB5543C)
val ClayDeep = Color(0xFF8D3D29)
val ClayWash = Color(0xFFF4E3DC)

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
    onPrimary = Color.White,
    primaryContainer = ClayWash,
    onPrimaryContainer = ClayDeep,
    background = Paper,
    onBackground = Ink,
    surface = Card,
    onSurface = Ink,
    surfaceVariant = PaperDeep,
    onSurfaceVariant = InkSoft,
    outline = Line,
    error = ClayDeep,
)

@Composable
fun WayfareTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = WayfareColors, content = content)
}

package com.kivan.carmelit.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Carmelit red: the only accent. Reserved for urgency and the selected direction. */
val Accent = Color(0xFFE5322D)

private val Scheme = darkColorScheme(
    background = Color.Black,
    onBackground = Color(0xFFF2F2F2),
    surface = Color.Black,
    onSurface = Color(0xFFF2F2F2),
    surfaceVariant = Color(0xFF141414),
    onSurfaceVariant = Color(0xFF9A9A9A),
    surfaceContainer = Color(0xFF141414),
    surfaceContainerHigh = Color(0xFF1C1C1C),
    outline = Color(0xFF3A3A3A),
    outlineVariant = Color(0xFF262626),
    primary = Accent,
    onPrimary = Color.White,
    secondaryContainer = Accent,
    onSecondaryContainer = Color.White,
    error = Color(0xFFFF6E6E),
)

/** Tabular figures so ticking digits and time columns keep their width. */
val Tabular = TextStyle(fontFeatureSettings = "tnum")

/** The hero countdown number. */
val Hero = TextStyle(fontSize = 120.sp, lineHeight = 124.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum")

/** Small, spaced, dim section label ("LEAVE IN", "LATER", "ROUTE"). */
@Composable
fun eyebrow(): TextStyle = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.5.sp)

@Composable
fun CarmelitTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme) {
        // Sets LocalContentColor; without a Surface, Text defaults to black on black.
        Surface(color = Scheme.background, contentColor = Scheme.onBackground, modifier = Modifier.fillMaxSize()) {
            content()
        }
    }
}

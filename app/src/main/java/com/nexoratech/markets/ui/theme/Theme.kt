package com.nexoratech.markets.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val NexoraDarkScheme = darkColorScheme(
    primary = Teal400,
    onPrimary = Ink900,
    primaryContainer = Teal400,
    onPrimaryContainer = Ink900,
    secondary = Teal200,
    onSecondary = Ink900,
    tertiary = Amber400,
    background = Ink900,
    onBackground = TextPrimary,
    surface = Ink800,
    onSurface = TextPrimary,
    surfaceVariant = Ink700,
    onSurfaceVariant = TextSecondary,
    surfaceContainer = Ink800,
    surfaceContainerHigh = Ink700,
    surfaceContainerHighest = Ink700,
    outline = Ink600,
    error = Sell400,
    onError = Ink900,
)

/**
 * Nexora Markets is dark-first by design — trading desks run dark.
 * Light system theme intentionally maps to the same dark identity.
 */
@Composable
fun NexoraMarketsTheme(content: @Composable () -> Unit) {
    isSystemInDarkTheme() // respected: future light variant can hook here
    MaterialTheme(
        colorScheme = NexoraDarkScheme,
        typography = NexoraTypography,
        content = content,
    )
}

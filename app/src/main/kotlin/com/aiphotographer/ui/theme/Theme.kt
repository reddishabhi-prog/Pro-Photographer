package com.aiphotographer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * A camera app is always dark: anything bright on screen reflects in the
 * user's eyes and in the subject's glasses, and it fights the live preview for
 * attention. So there is one scheme, and it is dark.
 */

val Amber = Color(0xFFE8B84B)
val AmberDim = Color(0xFF8A6E2B)
val Ink = Color(0xFF0B0B0F)
val Paper = Color(0xFFF2F2F7)
val Smoke = Color(0xFF9A9AA5)
val Critical = Color(0xFFFF6B5A)
val Good = Color(0xFF5BD98A)

private val scheme = darkColorScheme(
    primary = Amber,
    onPrimary = Ink,
    secondary = AmberDim,
    onSecondary = Paper,
    background = Ink,
    onBackground = Paper,
    surface = Ink,
    onSurface = Paper,
    surfaceVariant = Color(0xFF1C1C22),
    onSurfaceVariant = Smoke,
    error = Critical,
    onError = Ink,
)

private val typography = Typography(
    bodyLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.6.sp),
    labelMedium = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.8.sp),
    titleMedium = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
)

@Composable
fun AIPhotographerTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = scheme,
        typography = typography,
        content = content,
    )
}

package com.beto.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Sistema de diseño de Beto para UI propias (Modo Compañero, Modo Guía sheet, etc.).
 *
 * Tokens (UX-01 / D-18 / D-19):
 *  - Tipografía mínima 22sp body, 28sp hero. Line-height 1.4x para legibilidad.
 *  - Paleta high-contrast: texto negro (#000) sobre superficie blanca (≥21:1 ratio).
 *  - Primary azul (#1976D2) — mismo color del estado LISTENING. Coherencia visual.
 *
 * **Toda UI propia DEBE usar `BetoTheme {}` y los `MaterialTheme.typography` / `colorScheme`.**
 * Prohibido definir `fontSize` o colores hardcoded en componentes individuales.
 */

private val BetoColorsLight = lightColorScheme(
    primary = Color(0xFF1976D2),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFBBDEFB),
    onPrimaryContainer = Color(0xFF0D47A1),
    secondary = Color(0xFF388E3C),
    onSecondary = Color.White,
    background = Color.White,
    onBackground = Color(0xFF000000),
    surface = Color.White,
    onSurface = Color(0xFF212121),
    surfaceVariant = Color(0xFFF5F5F5),
    onSurfaceVariant = Color(0xFF424242),
    error = Color(0xFFD32F2F),
    onError = Color.White,
    outline = Color(0xFF424242),
)

// Dark mode reservado v2 — adultos mayores prefieren light + alto contraste.
private val BetoColorsDark = darkColorScheme(
    primary = Color(0xFF90CAF9),
    onPrimary = Color(0xFF0D47A1),
    background = Color(0xFF121212),
    onBackground = Color.White,
    surface = Color(0xFF1E1E1E),
    onSurface = Color.White,
    error = Color(0xFFEF9A9A),
    onError = Color(0xFFB71C1C),
)

val BetoTypography: Typography = Typography(
    displayLarge = TextStyle(fontSize = 32.sp, lineHeight = 44.sp, fontWeight = FontWeight.SemiBold),
    headlineLarge = TextStyle(fontSize = 28.sp, lineHeight = 38.sp, fontWeight = FontWeight.SemiBold),
    headlineMedium = TextStyle(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 22.sp, lineHeight = 32.sp),
    bodyMedium = TextStyle(fontSize = 22.sp, lineHeight = 30.sp),
    bodySmall = TextStyle(fontSize = 18.sp, lineHeight = 26.sp),
    labelLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun BetoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) BetoColorsDark else BetoColorsLight
    MaterialTheme(
        colorScheme = colors,
        typography = BetoTypography,
        content = content,
    )
}

package com.nesoncalo.debrun.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7BD88F),
    onPrimary = Color(0xFF003912),
    primaryContainer = Color(0xFF17522A),
    onPrimaryContainer = Color(0xFFA8F0B8),
    secondary = Color(0xFF88B4E8),
    background = Color(0xFF0D1014),
    onBackground = Color(0xFFE6E9EE),
    surface = Color(0xFF14181E),
    onSurface = Color(0xFFE6E9EE),
    surfaceVariant = Color(0xFF20262F),
    onSurfaceVariant = Color(0xFFAEB6C2),
    error = Color(0xFFFF8A80)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF1E7A3C),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF2F5D8C),
    background = Color(0xFFF7F9FB),
    surface = Color(0xFFFFFFFF)
)

private val Mono = FontFamily.Monospace

private val AppTypography = Typography().let { base ->
    base.copy(
        headlineSmall = base.headlineSmall.copy(fontFamily = Mono, letterSpacing = 0.sp),
        titleMedium = base.titleMedium.copy(fontFamily = Mono),
        bodyMedium = base.bodyMedium.copy(fontSize = 14.sp),
        labelSmall = TextStyle(fontFamily = Mono, fontSize = 11.sp, color = Color(0xFF9AA4B2))
    )
}

@Composable
fun DebRunTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = AppTypography,
        content = content
    )
}

package com.clickdownloader.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.clickdownloader.core.model.AppThemeMode

private val DarkColors = darkColorScheme(
    primary = Color(0xFF4C9AFF),
    onPrimary = Color(0xFF001D36),
    secondary = Color(0xFF70D7A5),
    background = Color(0xFF08111F),
    surface = Color(0xFF101B2B),
    surfaceVariant = Color(0xFF1A283B),
    error = Color(0xFFFFB4AB),
)

private val AmoledColors = DarkColors.copy(
    background = Color.Black,
    surface = Color(0xFF070707),
    surfaceVariant = Color(0xFF151515),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF0067C7),
    secondary = Color(0xFF006C4B),
    background = Color(0xFFF7F9FC),
    surface = Color.White,
    surfaceVariant = Color(0xFFE3EAF4),
    error = Color(0xFFBA1A1A),
)

@Composable
fun ClickDownloaderTheme(
    themeMode: AppThemeMode,
    content: @Composable () -> Unit,
) {
    val colors = when (themeMode) {
        AppThemeMode.SYSTEM -> if (isSystemInDarkTheme()) DarkColors else LightColors
        AppThemeMode.LIGHT -> LightColors
        AppThemeMode.DARK -> DarkColors
        AppThemeMode.AMOLED -> AmoledColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}


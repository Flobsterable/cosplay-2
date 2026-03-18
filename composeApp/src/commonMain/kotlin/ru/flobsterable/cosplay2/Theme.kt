package ru.flobsterable.cosplay2

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFFAA3A4A),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF3A556A),
    background = Color(0xFFF7F1EA),
    onBackground = Color(0xFF1F1A17),
    surface = Color(0xFFFFFBF7),
    onSurface = Color(0xFF201B18),
    surfaceVariant = Color(0xFFEADFD5),
    onSurfaceVariant = Color(0xFF5D5149)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFF8A7A),
    secondary = Color(0xFFA2C9E4),
    background = Color(0xFF151312),
    onBackground = Color(0xFFF8EFE8),
    surface = Color(0xFF211D1B),
    onSurface = Color(0xFFF8EFE8),
    surfaceVariant = Color(0xFF3C3430),
    onSurfaceVariant = Color(0xFFD7C3B7)
)

@Composable
fun CosplayTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = Typography(),
        content = content
    )
}

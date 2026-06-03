package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = NetflixRed,
    secondary = GoldPremium,
    tertiary = HotAccent,
    background = DarkGreyBg,
    surface = DarkSurface,
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onBackground = WhiteText,
    onSurface = WhiteText,
    surfaceVariant = DarkCard,
    onSurfaceVariant = LightGrey
)

private val AmoledColorScheme = darkColorScheme(
    primary = NetflixRed,
    secondary = GoldPremium,
    tertiary = HotAccent,
    background = Color(0xFF000000),
    surface = Color(0xFF000000),
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onBackground = WhiteText,
    onSurface = WhiteText,
    surfaceVariant = Color(0xFF000000),
    onSurfaceVariant = LightGrey
)

@Composable
fun MyApplicationTheme(
    useAmoledMode: Boolean = false,
    content: @Composable () -> Unit
) {
    val colors = if (useAmoledMode) AmoledColorScheme else DarkColorScheme
    MaterialTheme(
        colorScheme = colors,
        typography = Typography,
        content = content
    )
}

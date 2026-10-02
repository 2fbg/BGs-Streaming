package com.example.presentation.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

val MKRed = Color(0xFFE50914)
val MKRedDark = Color(0xFFB81D24)
val MKGold = Color(0xFFFFD700)
val MKBackgroundDark = Color(0xFF121212)
val MKAmoledBlack = Color(0xFF000000)
val MKSurfaceDark = Color(0xFF1E1E1E)
val MKCardDark = Color(0xFF262626)

private val MKDarkColorScheme = darkColorScheme(
    primary = MKRed,
    onPrimary = Color.White,
    primaryContainer = MKRedDark,
    onPrimaryContainer = Color.White,
    secondary = MKGold,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF382F00),
    onSecondaryContainer = MKGold,
    background = MKBackgroundDark,
    onBackground = Color(0xFFEEEEEE),
    surface = MKSurfaceDark,
    onSurface = Color(0xFFEEEEEE),
    surfaceVariant = MKCardDark,
    onSurfaceVariant = Color(0xFFCCCCCC)
)

private val MKAmoledColorScheme = darkColorScheme(
    primary = MKRed,
    onPrimary = Color.White,
    primaryContainer = MKRedDark,
    onPrimaryContainer = Color.White,
    secondary = MKGold,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF222222),
    onSecondaryContainer = MKGold,
    background = MKAmoledBlack,
    onBackground = Color.White,
    surface = MKAmoledBlack,
    onSurface = Color.White,
    surfaceVariant = Color(0xFF151515),
    onSurfaceVariant = Color(0xFFDDDDDD)
)

@Composable
fun MK21Theme(
    useAmoledMode: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (useAmoledMode) MKAmoledColorScheme else MKDarkColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = colorScheme.background.toArgb()
                window.navigationBarColor = colorScheme.background.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}

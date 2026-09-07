package com.krrishkumar.focustimer.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Composable
fun FocusTimerTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit
) {
    val appColors = if (darkTheme) DarkAppColors else LightAppColors

    val materialScheme = if (darkTheme) {
        darkColorScheme(
            primary = appColors.accent,
            onPrimary = appColors.onAccent,
            background = appColors.background,
            onBackground = appColors.textPrimary,
            surface = appColors.surface,
            onSurface = appColors.textPrimary,
            surfaceVariant = appColors.surfaceRaised,
            onSurfaceVariant = appColors.textSecondary,
            outline = appColors.border
        )
    } else {
        lightColorScheme(
            primary = appColors.accent,
            onPrimary = appColors.onAccent,
            background = appColors.background,
            onBackground = appColors.textPrimary,
            surface = appColors.surface,
            onSurface = appColors.textPrimary,
            surfaceVariant = appColors.surfaceRaised,
            onSurfaceVariant = appColors.textSecondary,
            outline = appColors.border
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        LaunchedEffect(darkTheme) {
            val window = (view.context as Activity).window
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    CompositionLocalProvider(LocalAppColors provides appColors) {
        MaterialTheme(
            colorScheme = materialScheme,
            typography = Typography,
            content = content
        )
    }
}

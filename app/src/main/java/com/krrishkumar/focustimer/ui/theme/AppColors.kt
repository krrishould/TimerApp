package com.krrishkumar.focustimer.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class AppColors(
    val background: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val border: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val accent: Color,
    val onAccent: Color
)

val DarkAppColors = AppColors(
    background = Color(0xFF0A0A0C),
    surface = Color(0xFF151519),
    surfaceRaised = Color(0xFF1D1D23),
    border = Color(0xFF2A2A31),
    textPrimary = Color(0xFFF5F5F7),
    textSecondary = Color(0xFF9A9AA5),
    textMuted = Color(0xFF63636D),
    accent = Color(0xFF8B7CFF),
    onAccent = Color(0xFF1A1530)
)

val LightAppColors = AppColors(
    background = Color(0xFFFAFAFA),
    surface = Color(0xFFFFFFFF),
    surfaceRaised = Color(0xFFEFEFF3),
    border = Color(0xFFE2E2E7),
    textPrimary = Color(0xFF17171B),
    textSecondary = Color(0xFF6B6B75),
    textMuted = Color(0xFFA3A3AC),
    accent = Color(0xFF6C5CE7),
    onAccent = Color(0xFFFFFFFF)
)

val LocalAppColors = staticCompositionLocalOf { DarkAppColors }

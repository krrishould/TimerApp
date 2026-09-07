package com.krrishkumar.focustimer.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Space Grotesk, used only for the large timer digit displays
val TimerDisplayStyle = TextStyle(
    fontFamily = SpaceGrotesk,
    fontWeight = FontWeight.Medium,
    fontSize = 88.sp,
    letterSpacing = 0.sp
)

val Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = SpaceGrotesk,
        fontWeight = FontWeight.Medium,
        fontSize = 80.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = JakartaSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp
    ),
    titleLarge = TextStyle(
        fontFamily = JakartaSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp
    ),
    titleMedium = TextStyle(
        fontFamily = JakartaSans,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        letterSpacing = 0.2.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = JakartaSans,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = JakartaSans,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp
    ),
    labelLarge = TextStyle(
        fontFamily = JakartaSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        letterSpacing = 0.4.sp
    ),
    labelMedium = TextStyle(
        fontFamily = JakartaSans,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        letterSpacing = 0.3.sp
    )
)

package com.krrishkumar.focustimer.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/** Multipliers for the five user-selectable text size steps; index 2 is the default. */
val TEXT_SIZE_STEPS = listOf(0.78f, 0.88f, 1f, 1.15f, 1.32f)

fun textSizeScale(level: Int): Float = TEXT_SIZE_STEPS[level.coerceIn(0, TEXT_SIZE_STEPS.lastIndex)]

// Space Grotesk advances: digits are roughly 0.62em, the colon far narrower.
private fun emWidth(text: String): Float =
    text.sumOf { if (it == ':') 0.30 else 0.62 }.toFloat()

/**
 * Font size for focus mode, where the time is the only thing on screen and should
 * fill it. Derived from the space actually available rather than a fixed value, so
 * a short landscape window gets a large clock instead of the cramped compact size.
 *
 * The user's size preference stretches how much height it aims for; width stays a
 * hard cap so the biggest step can never push digits off screen.
 */
fun focusDigitSize(text: String, maxWidth: Dp, maxHeight: Dp, scale: Float = 1f): TextUnit {
    val widthLimited = (maxWidth.value * 0.92f) / emWidth(text)
    val heightLimited = maxHeight.value * 0.45f * scale
    return minOf(widthLimited, heightLimited).coerceIn(48f, 240f).sp
}

/**
 * Caps a preferred size to what actually fits the available width. Needed because the
 * base sizes scale up on big screens and the user can scale them further still.
 */
fun fittedDigitSize(text: String, maxWidth: Dp, preferred: TextUnit): TextUnit {
    val fits = (maxWidth.value * 0.94f) / emWidth(text)
    return minOf(preferred.value, fits).sp
}

/**
 * Renders a time string one character at a time so only the characters that actually
 * change animate. Crossfading the whole string made the entire display blink each second.
 */
@Composable
fun AnimatedTimeText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        text.forEachIndexed { index, char ->
            AnimatedContent(
                targetState = char,
                transitionSpec = { fadeIn(tween(140)) togetherWith fadeOut(tween(140)) },
                label = "time_char_$index"
            ) { value ->
                Text(text = value.toString(), style = style, color = color, maxLines = 1)
            }
        }
    }
}

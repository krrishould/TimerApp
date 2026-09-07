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

/**
 * Font size for focus mode, where the time is the only thing on screen and should
 * fill it. Derived from the space actually available rather than a fixed value, so
 * a short landscape window gets a large clock instead of the cramped compact size.
 */
fun focusDigitSize(text: String, maxWidth: Dp, maxHeight: Dp): TextUnit {
    // Space Grotesk advances: digits are roughly 0.62em, the colon far narrower.
    val ems = text.sumOf { if (it == ':') 0.30 else 0.62 }.toFloat()
    val widthLimited = (maxWidth.value * 0.92f) / ems
    val heightLimited = maxHeight.value * 0.45f
    return minOf(widthLimited, heightLimited).coerceIn(48f, 190f).sp
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

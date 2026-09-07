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

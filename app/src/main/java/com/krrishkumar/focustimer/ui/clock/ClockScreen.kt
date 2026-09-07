package com.krrishkumar.focustimer.ui.clock

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.krrishkumar.focustimer.ui.components.AnimatedTimeText
import com.krrishkumar.focustimer.ui.components.fittedDigitSize
import com.krrishkumar.focustimer.ui.components.focusDigitSize
import com.krrishkumar.focustimer.ui.components.textSizeScale
import com.krrishkumar.focustimer.ui.theme.LocalAppColors
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val TIME_24H_SECONDS = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.getDefault())
private val TIME_24H = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
private val TIME_12H_SECONDS = DateTimeFormatter.ofPattern("h:mm:ss", Locale.getDefault())
private val TIME_12H = DateTimeFormatter.ofPattern("h:mm", Locale.getDefault())
private val MERIDIEM = DateTimeFormatter.ofPattern("a", Locale.getDefault())
private val DATE_FORMAT = DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault())

@Composable
fun ClockScreen(
    is24Hour: Boolean,
    showSeconds: Boolean,
    textSizeLevel: Int,
    modifier: Modifier = Modifier,
    focusMode: Boolean = false
) {
    val colors = LocalAppColors.current

    // Re-reads the clock on each second boundary rather than every 1000ms from an
    // arbitrary start point, so the display flips in step with the real second.
    val now by produceState(initialValue = LocalDateTime.now()) {
        while (true) {
            delay(1000 - (System.currentTimeMillis() % 1000))
            value = LocalDateTime.now()
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val compact = maxHeight < 520.dp
        val timeFormat = when {
            is24Hour && showSeconds -> TIME_24H_SECONDS
            is24Hour -> TIME_24H
            showSeconds -> TIME_12H_SECONDS
            else -> TIME_12H
        }
        val timeText = now.format(timeFormat)
        // The row sits inside the Column's 32dp horizontal padding, and the AM/PM
        // suffix needs its own share of what's left.
        val contentWidth = maxWidth - 64.dp
        val widthBudget = if (is24Hour) contentWidth else contentWidth * 0.76f
        val scale = textSizeScale(textSizeLevel)
        val digitSize = if (focusMode) {
            focusDigitSize(timeText, widthBudget, maxHeight, scale)
        } else {
            val base = when {
                compact -> 40f
                maxWidth >= 600.dp -> 116f
                else -> 78f
            }
            fittedDigitSize(timeText, widthBudget, (base * scale).sp)
        }
        val meridiemSize = if (focusMode) digitSize * 0.32f else if (compact) 16.sp else 26.sp
        val gapSmall = if (compact) 6.dp else 16.dp

        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp)
        ) {
            Spacer(Modifier.weight(1f))

            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.Bottom) {
                    AnimatedTimeText(
                        text = timeText,
                        style = MaterialTheme.typography.displayLarge.copy(fontSize = digitSize),
                        color = colors.textPrimary
                    )
                    if (!is24Hour) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = now.format(MERIDIEM),
                            style = MaterialTheme.typography.displayLarge.copy(fontSize = meridiemSize),
                            color = colors.textSecondary,
                            maxLines = 1,
                            modifier = Modifier.padding(bottom = if (compact) 4.dp else 10.dp)
                        )
                    }
                }

                if (!focusMode) {
                    Spacer(Modifier.height(gapSmall))
                    Text(
                        text = now.format(DATE_FORMAT),
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.textSecondary
                    )
                }
            }

            Spacer(Modifier.weight(1f))
        }
    }
}

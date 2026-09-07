package com.krrishkumar.focustimer.ui.stopwatch

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.krrishkumar.focustimer.data.SessionRepository
import com.krrishkumar.focustimer.ui.components.AnimatedTimeText
import com.krrishkumar.focustimer.ui.components.focusDigitSize
import com.krrishkumar.focustimer.ui.components.PauseIcon
import com.krrishkumar.focustimer.ui.components.PlayIcon
import com.krrishkumar.focustimer.ui.components.ResetIcon
import com.krrishkumar.focustimer.ui.theme.LocalAppColors

@Composable
fun StopwatchScreen(
    repository: SessionRepository,
    modifier: Modifier = Modifier,
    focusMode: Boolean = false
) {
    val viewModel: StopwatchViewModel = viewModel(factory = StopwatchViewModel.Factory(repository))
    val state by viewModel.uiState.collectAsState()
    val colors = LocalAppColors.current

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val compact = maxHeight < 520.dp
        val timeText = formatMillis(state.elapsedMillis)
        val digitSize = if (focusMode) {
            // minus the Column's 28dp horizontal padding on each side
            focusDigitSize(timeText, maxWidth - 56.dp, maxHeight)
        } else {
            if (compact) 44.sp else 76.sp
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.weight(1f))

            AnimatedTimeText(
                text = timeText,
                style = MaterialTheme.typography.displayLarge.copy(fontSize = digitSize),
                color = colors.textPrimary
            )

            Spacer(Modifier.weight(1f))

            if (!focusMode) {
                Text(
                    text = "Finish",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.textSecondary,
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { viewModel.finishAndLog() }
                        .padding(10.dp)
                )

                Spacer(Modifier.height(if (compact) 10.dp else 18.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircleButton(48.dp, colors.surfaceRaised, { viewModel.reset() }) {
                        ResetIcon(color = colors.textSecondary)
                    }
                    CircleButton(64.dp, colors.accent, {
                        if (state.isRunning) viewModel.pause() else viewModel.start()
                    }) {
                        if (state.isRunning) PauseIcon(colors.onAccent) else PlayIcon(colors.onAccent)
                    }
                }

                Spacer(Modifier.height(if (compact) 12.dp else 28.dp))
            }
        }
    }
}

@Composable
private fun CircleButton(
    size: Dp,
    background: Color,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .size(size)
            .background(background, CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

private fun formatMillis(millis: Long): String {
    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}

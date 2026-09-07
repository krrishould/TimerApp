package com.krrishkumar.focustimer.ui.timer

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.krrishkumar.focustimer.data.SessionRepository
import com.krrishkumar.focustimer.ui.components.AnimatedTimeText
import com.krrishkumar.focustimer.ui.components.fittedDigitSize
import com.krrishkumar.focustimer.ui.components.focusDigitSize
import com.krrishkumar.focustimer.ui.components.textSizeScale
import com.krrishkumar.focustimer.ui.components.PauseIcon
import com.krrishkumar.focustimer.ui.components.PlayIcon
import com.krrishkumar.focustimer.ui.components.ResetIcon
import com.krrishkumar.focustimer.ui.theme.AppColors
import com.krrishkumar.focustimer.ui.theme.LocalAppColors

@Composable
fun TimerScreen(
    repository: SessionRepository,
    textSizeLevel: Int,
    modifier: Modifier = Modifier,
    focusMode: Boolean = false
) {
    val viewModel: TimerViewModel = viewModel(factory = TimerViewModel.Factory(repository))
    val state by viewModel.uiState.collectAsState()
    val colors = LocalAppColors.current
    var editing by remember { mutableStateOf(false) }

    // Editing only makes sense for the plain timer while it's idle.
    val canEdit = !state.pomodoroMode && !state.isRunning && !focusMode
    LaunchedEffect(canEdit) { if (!canEdit) editing = false }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val compact = maxHeight < 520.dp
        val timeText = formatMillis(state.remainingMillis)
        val scale = textSizeScale(textSizeLevel)
        val contentWidth = maxWidth - 56.dp // the Column's 28dp padding on each side
        val digitSize = if (focusMode) {
            focusDigitSize(timeText, contentWidth, maxHeight, scale)
        } else {
            // Bigger on tablets, where a phone-sized number looks lost.
            val base = when {
                compact -> 48f
                maxWidth >= 600.dp -> 132f
                else -> 92f
            }
            fittedDigitSize(timeText, contentWidth, (base * scale).sp)
        }
        val timeStyle = MaterialTheme.typography.displayLarge.copy(fontSize = digitSize)

        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.weight(1f))

            if (state.pomodoroMode && !focusMode) {
                Text(
                    text = if (state.phase == TimerPhase.WORK) "FOCUS" else "BREAK",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.textSecondary
                )
                Spacer(Modifier.height(if (compact) 4.dp else 14.dp))
            }

            if (editing) {
                MinutesEditor(
                    initialMinutes = state.timerMinutes,
                    textStyle = timeStyle,
                    colors = colors,
                    onCommit = { minutes ->
                        viewModel.setTimerMinutes(minutes)
                        editing = false
                    }
                )
            } else {
                AnimatedTimeText(
                    text = timeText,
                    style = timeStyle,
                    color = colors.textPrimary,
                    modifier = if (canEdit) {
                        Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { editing = true }
                    } else {
                        Modifier
                    }
                )
            }

            if (canEdit && !editing) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Tap the time to change it",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textMuted
                )
            }

            Spacer(Modifier.weight(1f))

            if (!focusMode) {
                AnimatedVisibility(visible = state.pomodoroMode) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = if (compact) 8.dp else 20.dp)
                    ) {
                        DurationInput(
                            label = "Focus",
                            minutes = state.workMinutes,
                            maxMinutes = 180,
                            compact = compact,
                            onCommit = viewModel::setWorkMinutes,
                            modifier = Modifier.weight(1f)
                        )
                        DurationInput(
                            label = "Break",
                            minutes = state.breakMinutes,
                            maxMinutes = 60,
                            compact = compact,
                            onCommit = viewModel::setBreakMinutes,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                ModeChip(
                    label = "Pomodoro",
                    selected = state.pomodoroMode,
                    colors = colors,
                    onClick = { viewModel.togglePomodoroMode() }
                )

                Spacer(Modifier.height(if (compact) 12.dp else 22.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircleButton(
                        size = 48.dp,
                        background = colors.surfaceRaised,
                        onClick = { viewModel.reset() }
                    ) {
                        ResetIcon(color = colors.textSecondary)
                    }
                    CircleButton(
                        size = 64.dp,
                        background = colors.accent,
                        onClick = { if (state.isRunning) viewModel.pause() else viewModel.start() }
                    ) {
                        if (state.isRunning) {
                            PauseIcon(color = colors.onAccent)
                        } else {
                            PlayIcon(color = colors.onAccent)
                        }
                    }
                }

                Spacer(Modifier.height(if (compact) 12.dp else 28.dp))
            }
        }
    }
}

@Composable
private fun MinutesEditor(
    initialMinutes: Int,
    textStyle: TextStyle,
    colors: AppColors,
    onCommit: (Int) -> Unit
) {
    var text by remember { mutableStateOf(initialMinutes.toString()) }
    // onFocusChanged fires once with isFocused=false before the request lands; without this
    // guard the editor would commit and close itself the instant it appeared.
    var hasFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    fun commit() = onCommit(text.toIntOrNull() ?: initialMinutes)

    Row(verticalAlignment = Alignment.Bottom) {
        BasicTextField(
            value = text,
            onValueChange = { new -> if (new.length <= 3 && new.all { it.isDigit() }) text = new },
            textStyle = textStyle.copy(color = colors.textPrimary, textAlign = TextAlign.Center),
            singleLine = true,
            cursorBrush = SolidColor(colors.accent),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { commit() }),
            modifier = Modifier
                .width(160.dp)
                .focusRequester(focusRequester)
                .onFocusChanged { focusState ->
                    if (focusState.isFocused) hasFocused = true
                    else if (hasFocused) commit()
                }
        )
        Text(
            text = "min",
            style = MaterialTheme.typography.titleLarge,
            color = colors.textSecondary,
            modifier = Modifier.padding(bottom = 12.dp)
        )
    }
}

@Composable
private fun ModeChip(label: String, selected: Boolean, colors: AppColors, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(
                if (selected) colors.accent else colors.surfaceRaised,
                RoundedCornerShape(50)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 22.dp, vertical = 10.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) colors.onAccent else colors.textSecondary
        )
    }
}

@Composable
private fun CircleButton(
    size: androidx.compose.ui.unit.Dp,
    background: androidx.compose.ui.graphics.Color,
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

@Composable
private fun DurationInput(
    label: String,
    minutes: Int,
    maxMinutes: Int,
    compact: Boolean,
    onCommit: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalAppColors.current
    var text by remember(minutes) { mutableStateOf(minutes.toString()) }
    var hasFocused by remember { mutableStateOf(false) }

    fun commit() {
        val parsed = text.toIntOrNull()?.coerceIn(1, maxMinutes) ?: minutes
        text = parsed.toString()
        onCommit(parsed)
    }

    Column(
        modifier = modifier
            .background(colors.surfaceRaised, RoundedCornerShape(if (compact) 14.dp else 18.dp))
            .padding(vertical = if (compact) 6.dp else 12.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
        Spacer(Modifier.height(if (compact) 2.dp else 6.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            BasicTextField(
                value = text,
                onValueChange = { new -> if (new.length <= 3 && new.all { it.isDigit() }) text = new },
                textStyle = TextStyle(
                    color = colors.textPrimary,
                    fontSize = if (compact) MaterialTheme.typography.bodyLarge.fontSize
                    else MaterialTheme.typography.titleLarge.fontSize,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                ),
                singleLine = true,
                cursorBrush = SolidColor(colors.accent),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { commit() }),
                modifier = Modifier
                    .width(if (compact) 32.dp else 40.dp)
                    .onFocusChanged { focusState ->
                        if (focusState.isFocused) hasFocused = true
                        else if (hasFocused) commit()
                    }
            )
            Text("m", style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
        }
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

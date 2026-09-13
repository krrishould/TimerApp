package com.krrishkumar.focustimer.ui.timer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.krrishkumar.focustimer.engine.TimerPhase
import com.krrishkumar.focustimer.ui.components.AnimatedTimeText
import com.krrishkumar.focustimer.ui.components.fittedDigitSize
import com.krrishkumar.focustimer.ui.components.focusDigitSize
import com.krrishkumar.focustimer.ui.components.measuredTextWidth
import com.krrishkumar.focustimer.ui.components.textSizeScale
import com.krrishkumar.focustimer.ui.components.NameDialog
import com.krrishkumar.focustimer.ui.components.PencilIcon
import com.krrishkumar.focustimer.ui.components.PauseIcon
import com.krrishkumar.focustimer.ui.components.PlayIcon
import com.krrishkumar.focustimer.ui.components.ResetIcon
import com.krrishkumar.focustimer.ui.theme.AppColors
import com.krrishkumar.focustimer.ui.theme.LocalAppColors

@Composable
fun TimerScreen(
    textSizeLevel: Int,
    claimBreakAfterMinutes: Int,
    modifier: Modifier = Modifier,
    focusMode: Boolean = false
) {
    val viewModel: TimerViewModel = viewModel(factory = TimerViewModel.Factory)
    val state by viewModel.uiState.collectAsState()
    val colors = LocalAppColors.current
    var editing by remember { mutableStateOf(false) }
    var naming by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    // How far the finger travels per minute. Loose enough to be controllable, tight
    // enough that a long timer doesn't need a marathon drag.
    val dragStepPx = with(LocalDensity.current) { 14.dp.toPx() }

    // Typing only makes sense for the plain timer while it's idle; dragging works in
    // Pomodoro too, where it adjusts whichever period is showing.
    val canDrag = !state.isRunning && !state.inOvertime && !focusMode
    val canEdit = canDrag && !state.pomodoroMode
    val canClaimEarly = state.pomodoroMode && state.phase == TimerPhase.WORK &&
        !state.inOvertime && state.workedMillis > 0 &&
        state.workedMillis >= claimBreakAfterMinutes * 60 * 1000L
    LaunchedEffect(canEdit) { if (!canEdit) editing = false }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val compact = maxHeight < 520.dp
        val timeText = if (state.inOvertime) {
            "+" + formatMillis(state.overtimeMillis)
        } else {
            formatMillis(state.remainingMillis)
        }
        // In focus mode a tap pauses, and with no buttons on screen the dimmed digits
        // are the only sign that it worked.
        val timeColor = (if (state.inOvertime) colors.overtime else colors.textPrimary)
            .copy(alpha = if (focusMode && !state.isRunning) 0.4f else 1f)
        val scale = textSizeScale(textSizeLevel)
        val contentWidth = maxWidth - 56.dp // the Column's 28dp padding on each side
        // Bigger on tablets, where a phone-sized number looks lost.
        val base = when {
            compact -> 48f
            maxWidth >= 600.dp -> 132f
            else -> 92f
        }
        val digitSize = if (focusMode) {
            focusDigitSize(timeText, contentWidth, maxHeight, scale)
        } else {
            fittedDigitSize(timeText, contentWidth, (base * scale).sp)
        }
        val timeStyle = MaterialTheme.typography.displayLarge.copy(fontSize = digitSize)

        // The editor is a different shape from the running clock — a few digits beside a
        // "min" label — so it fits itself to the width left over. Sizing it from the
        // clock's fit let the digits overflow the field and clip.
        val minLabelWidth = 56.dp
        val editorWidth = contentWidth - minLabelWidth

        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.weight(1f))

            if (!focusMode) {
                ActivityRow(
                    name = state.activityName,
                    colors = colors,
                    onClick = { naming = true }
                )
                Spacer(Modifier.height(if (compact) 6.dp else 16.dp))
            }

            if (state.pomodoroMode && !focusMode) {
                Text(
                    text = when {
                        state.inOvertime -> "OVERTIME"
                        state.phase == TimerPhase.WORK -> "FOCUS"
                        else -> "BREAK"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = if (state.inOvertime) colors.overtime else colors.textSecondary
                )
                Spacer(Modifier.height(if (compact) 4.dp else 14.dp))
            }

            if (editing) {
                MinutesEditor(
                    initialMinutes = state.timerMinutes,
                    textStyle = timeStyle,
                    availableWidth = editorWidth,
                    preferredSize = (base * scale).sp,
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
                    color = timeColor,
                    modifier = Modifier
                        .then(
                            if (canEdit) {
                                Modifier.clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { editing = true }
                            } else {
                                Modifier
                            }
                        )
                        .then(
                            if (canDrag) {
                                Modifier.dragToAdjustMinutes(dragStepPx) { step ->
                                    viewModel.adjustCurrentMinutes(step)
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            } else {
                                Modifier
                            }
                        )
                )
            }

            val hint = when {
                editing -> null
                canEdit -> "Tap to type, or drag up and down"
                canDrag -> "Drag up and down to adjust"
                else -> null
            }
            if (hint != null) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = hint,
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textMuted
                )
            }

            Spacer(Modifier.weight(1f))

            if (!focusMode) {
                AnimatedVisibility(visible = state.inOvertime) {
                    Box(
                        modifier = Modifier
                            .padding(bottom = if (compact) 10.dp else 20.dp)
                            .background(colors.overtime, RoundedCornerShape(50))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { viewModel.claimBreak() }
                            .padding(horizontal = 24.dp, vertical = 11.dp)
                    ) {
                        Text(
                            text = "Claim break",
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.onAccent
                        )
                    }
                }

                // Early break: quieter than the overtime button, since nothing is overdue.
                AnimatedVisibility(visible = canClaimEarly) {
                    Box(
                        modifier = Modifier
                            .padding(bottom = if (compact) 10.dp else 20.dp)
                            .background(colors.surfaceRaised, RoundedCornerShape(50))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { viewModel.claimBreak() }
                            .padding(horizontal = 24.dp, vertical = 11.dp)
                    ) {
                        Text(
                            text = "Claim break",
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.textPrimary
                        )
                    }
                }

                AnimatedVisibility(visible = state.pomodoroMode && !state.inOvertime) {
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

    if (naming) {
        NameDialog(
            title = "Name this activity",
            placeholder = "e.g. Physics revision",
            initialValue = state.activityName ?: "",
            onSave = {
                viewModel.setActivityName(it)
                naming = false
            },
            onDismiss = { naming = false }
        )
    }
}

/**
 * Vertical drag on the time, used as a coarse dial: up adds minutes, down removes them.
 * Steps are emitted during the drag rather than on release, so the number tracks the
 * finger, and the drag is consumed so the tap-to-type click doesn't also fire.
 */
private fun Modifier.dragToAdjustMinutes(
    stepPx: Float,
    onStep: (Int) -> Unit
): Modifier = pointerInput(stepPx) {
    var carried = 0f
    detectVerticalDragGestures(
        onDragEnd = { carried = 0f },
        onDragCancel = { carried = 0f }
    ) { change, dragAmount ->
        change.consume()
        carried -= dragAmount
        while (carried >= stepPx) {
            onStep(1)
            carried -= stepPx
        }
        while (carried <= -stepPx) {
            onStep(-1)
            carried += stepPx
        }
    }
}

/** Subtle, tappable line showing what this stretch of time is being spent on. */
@Composable
private fun ActivityRow(name: String?, colors: AppColors, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(6.dp)
    ) {
        Text(
            text = name ?: "Add activity",
            style = MaterialTheme.typography.labelMedium,
            color = if (name != null) colors.textSecondary else colors.textMuted
        )
        PencilIcon(color = colors.textMuted)
    }
}

@Composable
private fun MinutesEditor(
    initialMinutes: Int,
    textStyle: TextStyle,
    availableWidth: Dp,
    preferredSize: TextUnit,
    colors: AppColors,
    onCommit: (Int) -> Unit
) {
    var text by remember { mutableStateOf(initialMinutes.toString()) }

    // Hold room for three digits even when fewer are typed, so the field doesn't twitch
    // on every keystroke; a fourth widens it once, shrinking the type only if it must.
    val digits = "8".repeat(maxOf(3, text.length))
    val digitSize = fittedDigitSize(digits, availableWidth, preferredSize)
    val fieldWidth = measuredTextWidth(digits, digitSize) + 16.dp
    // onFocusChanged fires once with isFocused=false before the request lands; without this
    // guard the editor would commit and close itself the instant it appeared.
    var hasFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    fun commit() = onCommit(text.toIntOrNull() ?: initialMinutes)

    Row(verticalAlignment = Alignment.Bottom) {
        BasicTextField(
            value = text,
            onValueChange = { new -> if (new.length <= 4 && new.all { it.isDigit() }) text = new },
            textStyle = textStyle.copy(
                fontSize = digitSize,
                color = colors.textPrimary,
                textAlign = TextAlign.Center
            ),
            singleLine = true,
            cursorBrush = SolidColor(colors.accent),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { commit() }),
            modifier = Modifier
                .width(fieldWidth)
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

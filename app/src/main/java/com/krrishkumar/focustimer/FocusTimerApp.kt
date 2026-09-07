package com.krrishkumar.focustimer

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.krrishkumar.focustimer.data.SessionRepository
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.krrishkumar.focustimer.ui.components.GearIcon
import com.krrishkumar.focustimer.ui.components.observeDoubleTap
import com.krrishkumar.focustimer.ui.components.TEXT_SIZE_STEPS
import com.krrishkumar.focustimer.ui.clock.ClockScreen
import com.krrishkumar.focustimer.ui.history.HistoryScreen
import com.krrishkumar.focustimer.ui.stopwatch.StopwatchScreen
import com.krrishkumar.focustimer.ui.theme.AppColors
import com.krrishkumar.focustimer.ui.theme.LocalAppColors
import com.krrishkumar.focustimer.ui.timer.PomodoroEndBehavior
import com.krrishkumar.focustimer.ui.timer.TimerScreen
import kotlinx.coroutines.delay

private val tabLabels = listOf("Timer", "Stopwatch", "Clock", "History")

@Composable
fun FocusTimerApp(
    repository: SessionRepository,
    isDarkTheme: Boolean,
    onThemeChange: (Boolean) -> Unit,
    is24Hour: Boolean,
    on24HourChange: (Boolean) -> Unit,
    showSeconds: Boolean,
    onShowSecondsChange: (Boolean) -> Unit,
    textSizeLevel: Int,
    onTextSizeLevelChange: (Int) -> Unit,
    endBehavior: PomodoroEndBehavior,
    onEndBehaviorChange: (PomodoroEndBehavior) -> Unit,
    keepIncompleteCycles: Boolean,
    onKeepIncompleteCyclesChange: (Boolean) -> Unit,
    logStopwatchOnPause: Boolean,
    onLogStopwatchOnPauseChange: (Boolean) -> Unit,
    focusGestureEnabled: Boolean,
    onFocusGestureChange: (Boolean) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var showSettings by remember { mutableStateOf(false) }
    var isFocusMode by remember { mutableStateOf(false) }
    var showExitHint by remember { mutableStateOf(false) }
    val colors = LocalAppColors.current

    // History has no single "big number", so focus mode doesn't apply there.
    val focusable = selectedTab != 3
    val inFocus = isFocusMode && focusable

    BackHandler(enabled = inFocus) { isFocusMode = false }

    LaunchedEffect(inFocus) {
        if (inFocus) {
            showExitHint = true
            delay(2600)
            showExitHint = false
        } else {
            showExitHint = false
        }
    }

    val view = LocalView.current
    DisposableEffect(inFocus) {
        view.keepScreenOn = inFocus

        // Focus mode goes fully immersive: the status/navigation bars slide away so
        // notifications aren't sitting above the time. A swipe brings them back
        // temporarily without leaving focus mode.
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (inFocus) {
            controller?.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }

        onDispose {
            view.keepScreenOn = false
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .observeDoubleTap(enabled = focusGestureEnabled && focusable) {
                isFocusMode = !isFocusMode
            }
    ) {
        Scaffold(
            containerColor = colors.background,
            topBar = {
                if (!inFocus) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Box(
                            modifier = Modifier
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { showSettings = true }
                                // The gear is a drawing, so it needs a spoken name of its own.
                                .semantics { contentDescription = "Settings" }
                                .padding(12.dp)
                        ) {
                            GearIcon(color = colors.textSecondary)
                        }
                    }
                }
            },
            bottomBar = {
                if (!inFocus) {
                    NavigationBar(
                        containerColor = colors.surface,
                        tonalElevation = 0.dp,
                        modifier = Modifier.height(84.dp)
                    ) {
                        tabLabels.forEachIndexed { index, label ->
                            NavigationBarItem(
                                selected = selectedTab == index,
                                onClick = { selectedTab = index },
                                icon = {},
                                label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    unselectedTextColor = colors.textMuted,
                                    indicatorColor = colors.surface
                                )
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            when (selectedTab) {
                0 -> TimerScreen(
                    repository,
                    textSizeLevel,
                    endBehavior,
                    keepIncompleteCycles,
                    Modifier.padding(innerPadding),
                    focusMode = inFocus
                )
                1 -> StopwatchScreen(
                    repository,
                    textSizeLevel,
                    logStopwatchOnPause,
                    Modifier.padding(innerPadding),
                    focusMode = inFocus
                )
                2 -> ClockScreen(is24Hour, showSeconds, textSizeLevel, Modifier.padding(innerPadding), focusMode = inFocus)
                3 -> HistoryScreen(repository, Modifier.padding(innerPadding))
            }
        }

        // Tells you how to get back out; fades away so it doesn't linger on the bare screen.
        AnimatedVisibility(
            visible = inFocus && showExitHint,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(600)),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 56.dp)
        ) {
            Text(
                text = "Double-tap to exit focus mode",
                style = MaterialTheme.typography.labelMedium,
                color = colors.textMuted
            )
        }
    }

    if (showSettings) {
        SettingsDialog(
            isDark = isDarkTheme,
            onThemeSelect = onThemeChange,
            is24Hour = is24Hour,
            on24HourSelect = on24HourChange,
            showSeconds = showSeconds,
            onShowSecondsChange = onShowSecondsChange,
            textSizeLevel = textSizeLevel,
            onTextSizeLevelChange = onTextSizeLevelChange,
            endBehavior = endBehavior,
            onEndBehaviorChange = onEndBehaviorChange,
            keepIncompleteCycles = keepIncompleteCycles,
            onKeepIncompleteCyclesChange = onKeepIncompleteCyclesChange,
            logStopwatchOnPause = logStopwatchOnPause,
            onLogStopwatchOnPauseChange = onLogStopwatchOnPauseChange,
            focusGestureEnabled = focusGestureEnabled,
            onFocusGestureChange = onFocusGestureChange,
            canEnterFocus = focusable,
            onEnterFocus = {
                showSettings = false
                isFocusMode = true
            },
            onDismiss = { showSettings = false }
        )
    }
}

@Composable
private fun SettingsDialog(
    isDark: Boolean,
    onThemeSelect: (Boolean) -> Unit,
    is24Hour: Boolean,
    on24HourSelect: (Boolean) -> Unit,
    showSeconds: Boolean,
    onShowSecondsChange: (Boolean) -> Unit,
    textSizeLevel: Int,
    onTextSizeLevelChange: (Int) -> Unit,
    endBehavior: PomodoroEndBehavior,
    onEndBehaviorChange: (PomodoroEndBehavior) -> Unit,
    keepIncompleteCycles: Boolean,
    onKeepIncompleteCyclesChange: (Boolean) -> Unit,
    logStopwatchOnPause: Boolean,
    onLogStopwatchOnPauseChange: (Boolean) -> Unit,
    focusGestureEnabled: Boolean,
    onFocusGestureChange: (Boolean) -> Unit,
    canEnterFocus: Boolean,
    onEnterFocus: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalAppColors.current
    var showFocusHint by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .width(300.dp)
                .heightIn(max = 460.dp)
                .background(colors.surface, RoundedCornerShape(24.dp))
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
        ) {
            Text("Appearance", style = MaterialTheme.typography.titleLarge, color = colors.textPrimary)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SegmentedOption("Light", selected = !isDark, onClick = { onThemeSelect(false) }, modifier = Modifier.weight(1f))
                SegmentedOption("Dark", selected = isDark, onClick = { onThemeSelect(true) }, modifier = Modifier.weight(1f))
            }

            Spacer(Modifier.height(28.dp))

            Text("Clock", style = MaterialTheme.typography.titleLarge, color = colors.textPrimary)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SegmentedOption("12-hour", selected = !is24Hour, onClick = { on24HourSelect(false) }, modifier = Modifier.weight(1f))
                SegmentedOption("24-hour", selected = is24Hour, onClick = { on24HourSelect(true) }, modifier = Modifier.weight(1f))
            }

            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Show seconds", style = MaterialTheme.typography.bodyLarge, color = colors.textSecondary)
                Spacer(Modifier.weight(1f))
                Switch(
                    checked = showSeconds,
                    onCheckedChange = onShowSecondsChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = colors.onAccent,
                        checkedTrackColor = colors.accent,
                        uncheckedThumbColor = colors.textMuted,
                        uncheckedTrackColor = colors.surfaceRaised,
                        uncheckedBorderColor = colors.border
                    )
                )
            }

            Spacer(Modifier.height(28.dp))

            Text("Pomodoro", style = MaterialTheme.typography.titleLarge, color = colors.textPrimary)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SegmentedOption(
                    "Overtime",
                    selected = endBehavior == PomodoroEndBehavior.OVERTIME,
                    onClick = { onEndBehaviorChange(PomodoroEndBehavior.OVERTIME) },
                    modifier = Modifier.weight(1f)
                )
                SegmentedOption(
                    "Auto break",
                    selected = endBehavior == PomodoroEndBehavior.AUTO_BREAK,
                    onClick = { onEndBehaviorChange(PomodoroEndBehavior.AUTO_BREAK) },
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = if (endBehavior == PomodoroEndBehavior.OVERTIME) {
                    "When focus ends the timer keeps counting up, and the extra time still counts as work."
                } else {
                    "When focus ends the break starts on its own."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textMuted
            )

            Spacer(Modifier.height(28.dp))

            Text("Tracking", style = MaterialTheme.typography.titleLarge, color = colors.textPrimary)
            Spacer(Modifier.height(14.dp))
            ToggleRow(
                label = "Keep incomplete cycles",
                checked = keepIncompleteCycles,
                onCheckedChange = onKeepIncompleteCyclesChange,
                colors = colors
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "A timer you reset part-way through is still recorded, once it has run a minute.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textMuted
            )
            Spacer(Modifier.height(14.dp))
            ToggleRow(
                label = "Log stopwatch on pause",
                checked = logStopwatchOnPause,
                onCheckedChange = onLogStopwatchOnPauseChange,
                colors = colors
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Pausing records the run, so walking away without finishing doesn't lose it.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textMuted
            )

            Spacer(Modifier.height(28.dp))

            Text("Text size", style = MaterialTheme.typography.titleLarge, color = colors.textPrimary)
            Spacer(Modifier.height(14.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                TEXT_SIZE_STEPS.forEachIndexed { index, step ->
                    val selected = index == textSizeLevel
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                if (selected) colors.accent else colors.surfaceRaised,
                                RoundedCornerShape(12.dp)
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onTextSizeLevelChange(index) }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "A",
                            fontSize = (11 + index * 3).sp,
                            color = if (selected) colors.onAccent else colors.textSecondary
                        )
                    }
                }
            }

            Spacer(Modifier.height(28.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Focus", style = MaterialTheme.typography.titleLarge, color = colors.textPrimary)
                Spacer(Modifier.width(8.dp))
                InfoBadge(colors = colors, onClick = { showFocusHint = !showFocusHint })
                Spacer(Modifier.weight(1f))
                Switch(
                    checked = focusGestureEnabled,
                    onCheckedChange = onFocusGestureChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = colors.onAccent,
                        checkedTrackColor = colors.accent,
                        uncheckedThumbColor = colors.textMuted,
                        uncheckedTrackColor = colors.surfaceRaised,
                        uncheckedBorderColor = colors.border
                    )
                )
            }

            if (showFocusHint) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Double-tap anywhere to enter or exit focus mode. Only the time stays on screen.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textMuted
                )
            }

            if (canEnterFocus) {
                Spacer(Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.accent, RoundedCornerShape(16.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onEnterFocus
                        )
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Enter focus mode",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.onAccent
                    )
                }
            }
        }
    }
}

/** A labelled switch, as used by the Tracking settings. */
@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    colors: AppColors
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = colors.textSecondary)
        Spacer(Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.onAccent,
                checkedTrackColor = colors.accent,
                uncheckedThumbColor = colors.textMuted,
                uncheckedTrackColor = colors.surfaceRaised,
                uncheckedBorderColor = colors.border
            )
        )
    }
}

/** Small circular "i" badge — the project avoids the material-icons-extended dependency. */
@Composable
private fun InfoBadge(colors: AppColors, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(20.dp)
            .border(1.dp, colors.textMuted, CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "i",
            style = MaterialTheme.typography.labelMedium,
            color = colors.textMuted
        )
    }
}

@Composable
private fun SegmentedOption(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalAppColors.current
    Box(
        modifier = modifier
            .background(if (selected) colors.accent else colors.surfaceRaised, RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (selected) colors.onAccent else colors.textSecondary,
            style = MaterialTheme.typography.titleMedium
        )
    }
}

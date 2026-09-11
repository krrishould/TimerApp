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
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.krrishkumar.focustimer.ui.components.observeTaps
import com.krrishkumar.focustimer.ui.components.TEXT_SIZE_STEPS
import com.krrishkumar.focustimer.ui.clock.ClockScreen
import com.krrishkumar.focustimer.ui.history.HistoryScreen
import com.krrishkumar.focustimer.ui.stopwatch.StopwatchScreen
import com.krrishkumar.focustimer.ui.theme.AppColors
import com.krrishkumar.focustimer.ui.theme.LocalAppColors
import com.krrishkumar.focustimer.ui.timer.PomodoroEndBehavior
import com.krrishkumar.focustimer.ui.timer.TimerScreen
import com.krrishkumar.focustimer.ui.timer.TimerViewModel
import com.krrishkumar.focustimer.ui.stopwatch.StopwatchViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.delay

private val tabLabels = listOf("Timer", "Stopwatch", "Clock", "History")

/** Minutes of focus before an early break is offered; 0 means straight away. */
private val CLAIM_BREAK_OPTIONS = listOf(0, 5, 10, 15, 20)

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
    claimBreakAfterMinutes: Int,
    onClaimBreakAfterChange: (Int) -> Unit,
    keepIncompleteCycles: Boolean,
    onKeepIncompleteCyclesChange: (Boolean) -> Unit,
    logStopwatchOnPause: Boolean,
    onLogStopwatchOnPauseChange: (Boolean) -> Unit,
    focusGestureEnabled: Boolean,
    onFocusGestureChange: (Boolean) -> Unit
) {
    // Saveable, not just remembered: a rotation recreates the activity, and plain
    // remember would drop the user back onto the Timer tab mid-task.
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var isFocusMode by rememberSaveable { mutableStateOf(false) }
    var showExitHint by remember { mutableStateOf(false) }
    val colors = LocalAppColors.current

    // History has no single "big number", so focus mode doesn't apply there.
    val focusable = selectedTab != 3
    val inFocus = isFocusMode && focusable

    BackHandler(enabled = inFocus) { isFocusMode = false }

    // The same instances the Timer and Stopwatch screens use, so a tap in focus mode can
    // drive whichever one is showing.
    val timerViewModel: TimerViewModel = viewModel(factory = TimerViewModel.Factory(repository))
    val stopwatchViewModel: StopwatchViewModel =
        viewModel(factory = StopwatchViewModel.Factory(repository))
    // The tap observer keeps its first callbacks, so they read this rather than the parameter.
    val gestureEnabled by rememberUpdatedState(focusGestureEnabled)
    val tabHasControls = selectedTab == 0 || selectedTab == 1

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
    // A timer the screen dims out on isn't much use, so the display stays awake for as
    // long as the app is open. Leaving the app hands control back to the system.
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    DisposableEffect(inFocus) {
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
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .observeTaps(
                enabled = focusable,
                onSingleTap = {
                    if (isFocusMode) {
                        when (selectedTab) {
                            0 -> timerViewModel.toggleRunning()
                            1 -> stopwatchViewModel.toggleRunning()
                        }
                    }
                },
                onDoubleTap = { if (gestureEnabled) isFocusMode = !isFocusMode }
            )
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
                    claimBreakAfterMinutes,
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
                text = when {
                    tabHasControls && focusGestureEnabled -> "Tap to pause or resume · Double-tap to exit"
                    tabHasControls -> "Tap to pause or resume · Back to exit"
                    focusGestureEnabled -> "Double-tap to exit focus mode"
                    else -> "Press back to exit focus mode"
                },
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
            claimBreakAfterMinutes = claimBreakAfterMinutes,
            onClaimBreakAfterChange = onClaimBreakAfterChange,
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
    claimBreakAfterMinutes: Int,
    onClaimBreakAfterChange: (Int) -> Unit,
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
            ToggleRow(
                label = "Start break automatically",
                checked = endBehavior == PomodoroEndBehavior.AUTO_BREAK,
                onCheckedChange = { on ->
                    onEndBehaviorChange(
                        if (on) PomodoroEndBehavior.AUTO_BREAK else PomodoroEndBehavior.OVERTIME
                    )
                },
                colors = colors
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (endBehavior == PomodoroEndBehavior.AUTO_BREAK) {
                    "When focus ends, the break timer starts on its own."
                } else {
                    "When focus ends, the timer keeps counting up until you claim your break, and the extra time counts as work."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textMuted
            )
            Spacer(Modifier.height(18.dp))
            Text(
                "Claim a break after",
                style = MaterialTheme.typography.bodyLarge,
                color = colors.textSecondary
            )
            Spacer(Modifier.height(10.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                CLAIM_BREAK_OPTIONS.forEach { minutes ->
                    SegmentedOption(
                        label = if (minutes == 0) "Any" else "${minutes}m",
                        selected = minutes == claimBreakAfterMinutes,
                        onClick = { onClaimBreakAfterChange(minutes) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = if (claimBreakAfterMinutes == 0) {
                    "You can take your break at any point in a focus session."
                } else {
                    "You can take your break early once you've focused for $claimBreakAfterMinutes minutes."
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
                    text = "Double-tap anywhere to enter or exit focus mode. Only the time stays on screen, and a single tap pauses or resumes it.",
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

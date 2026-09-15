package com.krrishkumar.focustimer

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.krrishkumar.focustimer.data.SessionRepository
import com.krrishkumar.focustimer.engine.PomodoroEndBehavior
import com.krrishkumar.focustimer.ui.clock.ClockScreen
import com.krrishkumar.focustimer.ui.components.AppMenuButton
import com.krrishkumar.focustimer.ui.components.observeTaps
import com.krrishkumar.focustimer.ui.history.HistoryScreen
import com.krrishkumar.focustimer.ui.settings.SettingsScreen
import com.krrishkumar.focustimer.ui.stopwatch.StopwatchScreen
import com.krrishkumar.focustimer.ui.stopwatch.StopwatchViewModel
import com.krrishkumar.focustimer.ui.theme.LocalAppColors
import com.krrishkumar.focustimer.ui.timer.TimerScreen
import com.krrishkumar.focustimer.ui.timer.TimerViewModel
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
    claimBreakAfterMinutes: Int,
    onClaimBreakAfterChange: (Int) -> Unit,
    keepIncompleteCycles: Boolean,
    onKeepIncompleteCyclesChange: (Boolean) -> Unit,
    logStopwatchOnPause: Boolean,
    onLogStopwatchOnPauseChange: (Boolean) -> Unit,
    focusGestureEnabled: Boolean,
    onFocusGestureChange: (Boolean) -> Unit,
    requestedTab: Int? = null,
    onRequestedTabHandled: () -> Unit = {}
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
    val inFocus = isFocusMode && focusable && !showSettings

    BackHandler(enabled = inFocus) { isFocusMode = false }

    // The same instances the Timer and Stopwatch screens use, so a tap in focus mode can
    // drive whichever one is showing.
    val timerViewModel: TimerViewModel = viewModel(factory = TimerViewModel.Factory)
    val stopwatchViewModel: StopwatchViewModel = viewModel(factory = StopwatchViewModel.Factory)
    // The tap observer keeps its first callbacks, so they read this rather than the parameter.
    val gestureEnabled by rememberUpdatedState(focusGestureEnabled)
    val tabHasControls = selectedTab == 0 || selectedTab == 1

    // Opened from a timer or stopwatch notification: show the screen it came from.
    LaunchedEffect(requestedTab) {
        if (requestedTab != null) {
            selectedTab = requestedTab
            showSettings = false
            onRequestedTabHandled()
        }
    }

    // Asked the first time something starts, when it's clear why a notification helps.
    // Without it the timer still ends and chimes on time; it just can't show controls.
    val context = LocalContext.current
    val app = context.applicationContext as AlltimeApp
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            app.timerEngine.resync()
            app.stopwatchEngine.resync()
        }
    }
    val timerModel by app.timerEngine.state.collectAsState()
    val stopwatchModel by app.stopwatchEngine.state.collectAsState()
    val somethingRunning = timerModel.isRunning || stopwatchModel.isRunning
    LaunchedEffect(somethingRunning) {
        val needsAsking = somethingRunning &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED &&
            !app.preferences.wasNotificationPermissionAsked()
        if (needsAsking) {
            app.preferences.setNotificationPermissionAsked()
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

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

    if (showSettings) {
        SettingsScreen(
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
            onBack = { showSettings = false }
        )
        return
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
                        AppMenuButton(
                            canEnterFocus = focusable,
                            isDark = isDarkTheme,
                            onThemeChange = onThemeChange,
                            onEnterFocus = { isFocusMode = true },
                            onOpenSettings = { showSettings = true }
                        )
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
                    textSizeLevel = textSizeLevel,
                    claimBreakAfterMinutes = claimBreakAfterMinutes,
                    modifier = Modifier.padding(innerPadding),
                    focusMode = inFocus
                )
                1 -> StopwatchScreen(
                    textSizeLevel = textSizeLevel,
                    modifier = Modifier.padding(innerPadding),
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
}

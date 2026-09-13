package com.krrishkumar.focustimer

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.krrishkumar.focustimer.data.AppPreferences
import com.krrishkumar.focustimer.engine.PomodoroEndBehavior
import com.krrishkumar.focustimer.ui.theme.FocusTimerTheme

class MainActivity : ComponentActivity() {

    private lateinit var preferences: AppPreferences
    private val app: AlltimeApp get() = application as AlltimeApp

    /** A tab to jump to, set when the app is opened from a timer or stopwatch notification. */
    private val requestedTab = mutableStateOf<Int?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        preferences = app.preferences
        if (!preferences.isDarkMode()) {
            setTheme(R.style.Theme_FocusTimer_Light)
        }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Only on a fresh launch: after a rotation, the tab the user was on should win.
        if (savedInstanceState == null) takeRequestedTab(intent)

        setContent {
            var isDarkTheme by remember { mutableStateOf(preferences.isDarkMode()) }
            var is24Hour by remember { mutableStateOf(preferences.is24HourClock()) }
            var showSeconds by remember { mutableStateOf(preferences.isClockSecondsShown()) }
            var textSizeLevel by remember { mutableIntStateOf(preferences.getTextSizeLevel()) }
            var endBehavior by remember {
                mutableStateOf(PomodoroEndBehavior.from(preferences.getPomodoroEndBehavior()))
            }
            var keepIncompleteCycles by remember {
                mutableStateOf(preferences.isKeepIncompleteCycles())
            }
            var logStopwatchOnPause by remember {
                mutableStateOf(preferences.isLogStopwatchOnPause())
            }
            var claimBreakAfter by remember {
                mutableIntStateOf(preferences.getClaimBreakAfterMinutes())
            }
            var focusGestureEnabled by remember { mutableStateOf(preferences.isFocusGestureEnabled()) }

            FocusTimerTheme(darkTheme = isDarkTheme) {
                FocusTimerApp(
                    repository = app.repository,
                    isDarkTheme = isDarkTheme,
                    onThemeChange = { enabled ->
                        isDarkTheme = enabled
                        preferences.setDarkMode(enabled)
                    },
                    is24Hour = is24Hour,
                    on24HourChange = { enabled ->
                        is24Hour = enabled
                        preferences.set24HourClock(enabled)
                    },
                    showSeconds = showSeconds,
                    onShowSecondsChange = { enabled ->
                        showSeconds = enabled
                        preferences.setClockSecondsShown(enabled)
                    },
                    textSizeLevel = textSizeLevel,
                    onTextSizeLevelChange = { level ->
                        textSizeLevel = level
                        preferences.setTextSizeLevel(level)
                    },
                    endBehavior = endBehavior,
                    onEndBehaviorChange = { behavior ->
                        endBehavior = behavior
                        preferences.setPomodoroEndBehavior(behavior.name)
                    },
                    claimBreakAfterMinutes = claimBreakAfter,
                    onClaimBreakAfterChange = { minutes ->
                        claimBreakAfter = minutes
                        preferences.setClaimBreakAfterMinutes(minutes)
                    },
                    keepIncompleteCycles = keepIncompleteCycles,
                    onKeepIncompleteCyclesChange = { enabled ->
                        keepIncompleteCycles = enabled
                        preferences.setKeepIncompleteCycles(enabled)
                    },
                    logStopwatchOnPause = logStopwatchOnPause,
                    onLogStopwatchOnPauseChange = { enabled ->
                        logStopwatchOnPause = enabled
                        preferences.setLogStopwatchOnPause(enabled)
                    },
                    focusGestureEnabled = focusGestureEnabled,
                    onFocusGestureChange = { enabled ->
                        focusGestureEnabled = enabled
                        preferences.setFocusGestureEnabled(enabled)
                    },
                    requestedTab = requestedTab.value,
                    onRequestedTabHandled = { requestedTab.value = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        takeRequestedTab(intent)
    }

    override fun onStart() {
        super.onStart()
        app.isInForeground = true
        // Settle anything that finished while the app was away before the first frame.
        app.timerEngine.catchUp()
    }

    override fun onStop() {
        super.onStop()
        app.isInForeground = false
    }

    private fun takeRequestedTab(intent: Intent?) {
        if (intent?.hasExtra(EXTRA_TAB) != true) return
        requestedTab.value = intent.getIntExtra(EXTRA_TAB, 0)
        // Consumed, so recreating the activity doesn't jump back to it.
        intent.removeExtra(EXTRA_TAB)
    }

    companion object {
        const val EXTRA_TAB = "tab"
    }
}

package com.krrishkumar.focustimer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.krrishkumar.focustimer.data.AppPreferences
import com.krrishkumar.focustimer.data.SessionRepository
import com.krrishkumar.focustimer.ui.theme.FocusTimerTheme

class MainActivity : ComponentActivity() {

    private lateinit var preferences: AppPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        preferences = AppPreferences(applicationContext)
        if (!preferences.isDarkMode()) {
            setTheme(R.style.Theme_FocusTimer_Light)
        }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repository = SessionRepository(applicationContext)

        setContent {
            var isDarkTheme by remember { mutableStateOf(preferences.isDarkMode()) }
            var is24Hour by remember { mutableStateOf(preferences.is24HourClock()) }
            var focusGestureEnabled by remember { mutableStateOf(preferences.isFocusGestureEnabled()) }

            FocusTimerTheme(darkTheme = isDarkTheme) {
                FocusTimerApp(
                    repository = repository,
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
                    focusGestureEnabled = focusGestureEnabled,
                    onFocusGestureChange = { enabled ->
                        focusGestureEnabled = enabled
                        preferences.setFocusGestureEnabled(enabled)
                    }
                )
            }
        }
    }
}

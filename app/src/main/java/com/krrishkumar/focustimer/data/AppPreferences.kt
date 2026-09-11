package com.krrishkumar.focustimer.data

import android.content.Context
import android.text.format.DateFormat

class AppPreferences(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isDarkMode(): Boolean = prefs.getBoolean(KEY_DARK_MODE, true)

    fun setDarkMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DARK_MODE, enabled).apply()
    }

    /** Defaults to whatever the device itself uses, so the clock starts out familiar. */
    fun is24HourClock(): Boolean =
        prefs.getBoolean(KEY_24_HOUR_CLOCK, DateFormat.is24HourFormat(appContext))

    fun set24HourClock(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_24_HOUR_CLOCK, enabled).apply()
    }

    fun isFocusGestureEnabled(): Boolean = prefs.getBoolean(KEY_FOCUS_GESTURE, true)

    fun setFocusGestureEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_FOCUS_GESTURE, enabled).apply()
    }

    fun isClockSecondsShown(): Boolean = prefs.getBoolean(KEY_CLOCK_SECONDS, true)

    fun setClockSecondsShown(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CLOCK_SECONDS, enabled).apply()
    }

    /** Record a partly-run timer when it's abandoned, rather than losing the time. */
    fun isKeepIncompleteCycles(): Boolean = prefs.getBoolean(KEY_KEEP_INCOMPLETE, true)

    fun setKeepIncompleteCycles(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_KEEP_INCOMPLETE, enabled).apply()
    }

    /** Record the stopwatch on pause, so forgetting to hit Finish doesn't lose it. */
    fun isLogStopwatchOnPause(): Boolean = prefs.getBoolean(KEY_STOPWATCH_ON_PAUSE, true)

    fun setLogStopwatchOnPause(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_STOPWATCH_ON_PAUSE, enabled).apply()
    }

    /** Stored as the enum name; see ui.timer.PomodoroEndBehavior. */
    fun getPomodoroEndBehavior(): String =
        prefs.getString(KEY_POMODORO_END, DEFAULT_POMODORO_END) ?: DEFAULT_POMODORO_END

    fun setPomodoroEndBehavior(name: String) {
        prefs.edit().putString(KEY_POMODORO_END, name).apply()
    }

    /** Minutes of focus before an early break can be claimed; 0 means any time. */
    fun getClaimBreakAfterMinutes(): Int =
        prefs.getInt(KEY_CLAIM_BREAK_AFTER, DEFAULT_CLAIM_BREAK_AFTER).coerceAtLeast(0)

    fun setClaimBreakAfterMinutes(minutes: Int) {
        prefs.edit().putInt(KEY_CLAIM_BREAK_AFTER, minutes.coerceAtLeast(0)).apply()
    }

    /** 0..4, where 2 is the default middle step. */
    fun getTextSizeLevel(): Int =
        prefs.getInt(KEY_TEXT_SIZE_LEVEL, DEFAULT_TEXT_SIZE_LEVEL).coerceIn(0, 4)

    fun setTextSizeLevel(level: Int) {
        prefs.edit().putInt(KEY_TEXT_SIZE_LEVEL, level.coerceIn(0, 4)).apply()
    }

    companion object {
        private const val PREFS_NAME = "settings"
        private const val KEY_DARK_MODE = "dark_mode"
        private const val KEY_24_HOUR_CLOCK = "clock_24_hour"
        private const val KEY_FOCUS_GESTURE = "focus_gesture_enabled"
        private const val KEY_CLOCK_SECONDS = "clock_show_seconds"
        private const val KEY_TEXT_SIZE_LEVEL = "text_size_level"
        const val DEFAULT_TEXT_SIZE_LEVEL = 2
        private const val KEY_POMODORO_END = "pomodoro_end_behavior"
        private const val DEFAULT_POMODORO_END = "OVERTIME"
        private const val KEY_KEEP_INCOMPLETE = "keep_incomplete_cycles"
        private const val KEY_STOPWATCH_ON_PAUSE = "log_stopwatch_on_pause"
        private const val KEY_CLAIM_BREAK_AFTER = "claim_break_after_minutes"
        private const val DEFAULT_CLAIM_BREAK_AFTER = 10
    }
}

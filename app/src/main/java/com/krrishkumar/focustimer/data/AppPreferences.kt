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
    }
}

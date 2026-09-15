package com.krrishkumar.focustimer.data

import android.content.Context
import android.content.SharedPreferences
import android.text.format.DateFormat

class AppPreferences(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Set by sync; told when a setting that follows the account changes on this device. */
    @Volatile
    var onSyncedSettingChanged: () -> Unit = {}

    fun isDarkMode(): Boolean = prefs.getBoolean(KEY_DARK_MODE, true)

    fun setDarkMode(enabled: Boolean) = editSynced { putBoolean(KEY_DARK_MODE, enabled) }

    /** Defaults to whatever the device itself uses, so the clock starts out familiar. */
    fun is24HourClock(): Boolean =
        prefs.getBoolean(KEY_24_HOUR_CLOCK, DateFormat.is24HourFormat(appContext))

    fun set24HourClock(enabled: Boolean) = editSynced { putBoolean(KEY_24_HOUR_CLOCK, enabled) }

    fun isFocusGestureEnabled(): Boolean = prefs.getBoolean(KEY_FOCUS_GESTURE, true)

    fun setFocusGestureEnabled(enabled: Boolean) = editSynced { putBoolean(KEY_FOCUS_GESTURE, enabled) }

    fun isClockSecondsShown(): Boolean = prefs.getBoolean(KEY_CLOCK_SECONDS, true)

    fun setClockSecondsShown(enabled: Boolean) = editSynced { putBoolean(KEY_CLOCK_SECONDS, enabled) }

    /** Record a partly-run timer when it's abandoned, rather than losing the time. */
    fun isKeepIncompleteCycles(): Boolean = prefs.getBoolean(KEY_KEEP_INCOMPLETE, true)

    fun setKeepIncompleteCycles(enabled: Boolean) = editSynced { putBoolean(KEY_KEEP_INCOMPLETE, enabled) }

    /** Record the stopwatch on pause, so forgetting to hit Finish doesn't lose it. */
    fun isLogStopwatchOnPause(): Boolean = prefs.getBoolean(KEY_STOPWATCH_ON_PAUSE, true)

    fun setLogStopwatchOnPause(enabled: Boolean) = editSynced { putBoolean(KEY_STOPWATCH_ON_PAUSE, enabled) }

    /** Stored as the enum name; see engine.PomodoroEndBehavior. */
    fun getPomodoroEndBehavior(): String =
        prefs.getString(KEY_POMODORO_END, DEFAULT_POMODORO_END) ?: DEFAULT_POMODORO_END

    fun setPomodoroEndBehavior(name: String) = editSynced { putString(KEY_POMODORO_END, name) }

    /** Minutes of focus before an early break can be claimed; 0 means any time. */
    fun getClaimBreakAfterMinutes(): Int =
        prefs.getInt(KEY_CLAIM_BREAK_AFTER, DEFAULT_CLAIM_BREAK_AFTER).coerceAtLeast(0)

    fun setClaimBreakAfterMinutes(minutes: Int) =
        editSynced { putInt(KEY_CLAIM_BREAK_AFTER, minutes.coerceAtLeast(0)) }

    /** Whether the notification permission prompt has been shown, so it's only asked once. */
    fun wasNotificationPermissionAsked(): Boolean = prefs.getBoolean(KEY_NOTIFICATION_ASKED, false)

    fun setNotificationPermissionAsked() {
        prefs.edit().putBoolean(KEY_NOTIFICATION_ASKED, true).apply()
    }

    /** 0..4, where 2 is the default middle step. Kept per device: a tablet wants a different size. */
    fun getTextSizeLevel(): Int =
        prefs.getInt(KEY_TEXT_SIZE_LEVEL, DEFAULT_TEXT_SIZE_LEVEL).coerceIn(0, 4)

    fun setTextSizeLevel(level: Int) {
        prefs.edit().putInt(KEY_TEXT_SIZE_LEVEL, level.coerceIn(0, 4)).apply()
    }

    // ------------------------------------------------------------ sync side

    /** The settings that follow the account, as last set on this device. Unset ones are left out. */
    fun syncedSettings(): Map<String, Any> =
        SYNCED_KEYS.mapNotNull { key -> prefs.all[key]?.let { key to it } }.toMap()

    /** When a synced setting last changed on this device, or arrived from another one. */
    fun settingsUpdatedAt(): Long = prefs.getLong(KEY_SETTINGS_UPDATED, 0L)

    /**
     * Writes settings from another device if they're newer than this device's. Returns true if
     * they were applied, which fires any listeners so the screen picks them up.
     */
    fun applyRemoteSettings(values: Map<String, Any?>, updatedAt: Long): Boolean {
        if (updatedAt <= settingsUpdatedAt()) return false
        val editor = prefs.edit()
        values.forEach { (key, value) ->
            if (key !in SYNCED_KEYS || value == null) return@forEach
            when {
                key in INT_KEYS && value is Number -> editor.putInt(key, value.toInt())
                value is Boolean -> editor.putBoolean(key, value)
                value is String -> editor.putString(key, value)
            }
        }
        editor.putLong(KEY_SETTINGS_UPDATED, updatedAt).apply()
        return true
    }

    fun registerListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.registerOnSharedPreferenceChangeListener(listener)

    fun unregisterListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.unregisterOnSharedPreferenceChangeListener(listener)

    private fun editSynced(change: SharedPreferences.Editor.() -> Unit) {
        prefs.edit().apply(change).putLong(KEY_SETTINGS_UPDATED, System.currentTimeMillis()).apply()
        onSyncedSettingChanged()
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
        private const val KEY_NOTIFICATION_ASKED = "notification_permission_asked"
        private const val KEY_SETTINGS_UPDATED = "synced_settings_updated_at"

        /** Settings that follow the account. Text size and the permission prompt stay per device. */
        private val SYNCED_KEYS = setOf(
            KEY_DARK_MODE, KEY_24_HOUR_CLOCK, KEY_FOCUS_GESTURE, KEY_CLOCK_SECONDS,
            KEY_POMODORO_END, KEY_KEEP_INCOMPLETE, KEY_STOPWATCH_ON_PAUSE, KEY_CLAIM_BREAK_AFTER
        )
        private val INT_KEYS = setOf(KEY_CLAIM_BREAK_AFTER)
    }
}

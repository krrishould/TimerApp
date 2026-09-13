package com.krrishkumar.focustimer.engine

import android.content.SharedPreferences

enum class TimerPhase { WORK, BREAK }

/** What happens when a focus period runs out. */
enum class PomodoroEndBehavior {
    /** Keep counting upwards until the user claims their break. */
    OVERTIME,

    /** Start the break straight away. */
    AUTO_BREAK;

    companion object {
        fun from(name: String): PomodoroEndBehavior =
            entries.firstOrNull { it.name == name } ?: OVERTIME
    }
}

/**
 * Everything the timer needs to carry on after the app is gone. A running countdown is
 * stored as the moment it ends rather than as time left, so nothing has to keep ticking
 * in the background: the time left is worked out whenever something needs it.
 *
 * Times are wall-clock, since that survives a reboot and is what alarms and notification
 * chronometers use. The trade-off is that setting the phone's clock by hand shifts a
 * running timer.
 */
data class TimerModel(
    /** false = plain countdown timer, true = Focus/Break pomodoro cycling */
    val pomodoroMode: Boolean = false,
    val phase: TimerPhase = TimerPhase.WORK,
    val timerMinutes: Int = 25,
    val workMinutes: Int = 25,
    val breakMinutes: Int = 5,
    /** Name applied to logged sessions; stays set until the user changes it. */
    val activityName: String? = null,
    /** When the running countdown reaches zero; null unless counting down. */
    val endsAt: Long? = null,
    /** Time left on a countdown paused part-way; null when idle or running. */
    val pausedRemaining: Long? = null,
    /** True once a focus period has run out and the timer is counting upwards. */
    val inOvertime: Boolean = false,
    /** When overtime would have begun had it never been paused; null unless it's running. */
    val overtimeSince: Long? = null,
    /** Overtime banked while paused. */
    val overtimeBanked: Long = 0L,
    /** When the current period first started, used as the logged session's start. */
    val phaseStartedAt: Long = 0L
) {
    val phaseMillis: Long
        get() = when {
            !pomodoroMode -> timerMinutes
            phase == TimerPhase.WORK -> workMinutes
            else -> breakMinutes
        } * 60 * 1000L

    val isRunning: Boolean get() = endsAt != null || overtimeSince != null

    /** Running, paused part-way, or in overtime: anything but sitting at a fresh start. */
    val isActive: Boolean get() = isRunning || pausedRemaining != null || inOvertime

    fun remainingAt(now: Long): Long = when {
        inOvertime -> 0L
        endsAt != null -> (endsAt - now).coerceAtLeast(0L)
        pausedRemaining != null -> pausedRemaining
        else -> phaseMillis
    }

    fun overtimeAt(now: Long): Long =
        if (overtimeSince != null) (now - overtimeSince).coerceAtLeast(0L) else overtimeBanked
}

internal fun SharedPreferences.nullableLong(key: String): Long? =
    if (contains(key)) getLong(key, 0L) else null

internal fun SharedPreferences.Editor.putNullableLong(key: String, value: Long?) {
    if (value != null) putLong(key, value) else remove(key)
}

/** "4:05" or "1:04:05", for notification text. */
internal fun formatClock(millis: Long): String {
    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

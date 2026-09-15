package com.krrishkumar.focustimer.engine

import android.content.Context
import androidx.core.content.edit
import com.krrishkumar.focustimer.data.AppPreferences
import com.krrishkumar.focustimer.data.Category
import com.krrishkumar.focustimer.data.Segment
import com.krrishkumar.focustimer.data.SessionRepository
import com.krrishkumar.focustimer.data.SessionType
import com.krrishkumar.focustimer.data.activeMillis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * The one source of truth for the countdown timer, shared by the screen, the notification
 * buttons and the end-of-period alarm. It lives as long as the process and saves itself on
 * every change, so a timer carries on when the app is closed or killed.
 *
 * Each period also keeps the stretches it actually ran, so History can show exactly when it
 * played and when it was paused.
 *
 * Everything here runs on the main thread: the UI calls in from there, and so do the
 * broadcast receivers.
 */
class TimerEngine(
    context: Context,
    private val repository: SessionRepository,
    private val preferences: AppPreferences,
    private val alarms: AlarmScheduler,
    private val notifier: EngineNotifier,
    private val alerts: Alerts,
    private val scope: CoroutineScope,
    private val isAppInForeground: () -> Boolean
) {
    private val store = context.getSharedPreferences(STORE_NAME, Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<TimerModel> = _state

    init {
        // Re-arm the alarm and notification from the saved state. An alarm whose moment
        // has already passed fires straight away, which settles the finished period.
        publish(_state.value)
    }

    /** Re-posts the notification and re-arms the alarm, e.g. after a reboot. */
    fun resync() = publish(_state.value, forceAlarm = true)

    fun start() {
        catchUp()
        val now = now()
        mutate { s ->
            when {
                s.isRunning -> s
                s.inOvertime -> s.copy(
                    overtimeSince = now - s.overtimeBanked,
                    overtimeBanked = 0L,
                    runningSince = now
                )
                else -> {
                    val remaining = s.remainingAt(now)
                    val fresh = s.pausedRemaining == null
                    if (remaining <= 0L) {
                        s
                    } else {
                        s.copy(
                            endsAt = now + remaining,
                            pausedRemaining = null,
                            // Resuming keeps the original start, so the logged session
                            // doesn't claim it began mid-way.
                            phaseStartedAt = if (fresh) now else s.phaseStartedAt,
                            segments = if (fresh) emptyList() else s.segments,
                            runningSince = now
                        )
                    }
                }
            }
        }
    }

    fun pause() {
        catchUp()
        val now = now()
        mutate { s ->
            when {
                s.endsAt != null -> s.copy(
                    endsAt = null,
                    pausedRemaining = s.remainingAt(now),
                    segments = s.segmentsUntil(now),
                    runningSince = null
                )
                s.overtimeSince != null -> s.copy(
                    overtimeSince = null,
                    overtimeBanked = s.overtimeAt(now),
                    segments = s.segmentsUntil(now),
                    runningSince = null
                )
                else -> s
            }
        }
    }

    fun toggleRunning() {
        catchUp()
        if (_state.value.isRunning) pause() else start()
    }

    fun reset() {
        catchUp()
        val now = now()
        val s = _state.value
        logOvertime(s, now)
        logIncomplete(s, now)
        mutate { it.idle(TimerPhase.WORK) }
    }

    fun togglePomodoroMode() {
        catchUp()
        val now = now()
        val s = _state.value
        logOvertime(s, now)
        logIncomplete(s, now)
        mutate { it.idle(TimerPhase.WORK).copy(pomodoroMode = !it.pomodoroMode) }
    }

    /**
     * Ends the focus period and starts the break, from overtime or early. Whatever was
     * worked is recorded as one session.
     */
    fun claimBreak() {
        catchUp()
        val now = now()
        val s = _state.value
        if (!s.pomodoroMode || s.phase != TimerPhase.WORK) return
        if (s.inOvertime) {
            logOvertime(s, now)
        } else if (s.isActive) {
            val worked = s.segmentsUntil(now)
            // Floored at a minute like other partial sessions, which would read as "0m".
            if (worked.activeMillis() >= 60_000L) log(SessionType.POMODORO_WORK, worked, s.category)
        }
        mutate {
            it.idle(TimerPhase.BREAK).copy(
                endsAt = now + it.breakMinutes * 60 * 1000L,
                phaseStartedAt = now,
                runningSince = now
            )
        }
    }

    fun setCategory(category: Category?) = mutate { it.copy(category = category) }

    /** Called when a category is deleted, so it isn't used for the next session. */
    fun clearCategory(categoryId: Long) = mutate {
        if (it.category?.id == categoryId) it.copy(category = null) else it
    }

    /** Bounded only by what the four-digit editor can express, a little under a week. */
    fun setTimerMinutes(minutes: Int) =
        setMinutes { it.copy(timerMinutes = minutes.coerceIn(1, MAX_TIMER_MINUTES)) }

    fun setWorkMinutes(minutes: Int) = setMinutes { it.copy(workMinutes = minutes.coerceIn(1, 180)) }

    fun setBreakMinutes(minutes: Int) = setMinutes { it.copy(breakMinutes = minutes.coerceIn(1, 60)) }

    /** Nudges whichever duration is showing by whole minutes. */
    fun adjustCurrentMinutes(delta: Int) {
        val s = _state.value
        when {
            !s.pomodoroMode -> setTimerMinutes(s.timerMinutes + delta)
            s.phase == TimerPhase.WORK -> setWorkMinutes(s.workMinutes + delta)
            else -> setBreakMinutes(s.breakMinutes + delta)
        }
    }

    /** Called when the end-of-period alarm goes off. */
    fun onAlarm() {
        catchUp()
        // The alarm has been used up, so arm it again for whatever is counting down now,
        // even if that happens to be the same moment.
        alarms.sync(_state.value.endsAt, force = true)
    }

    /**
     * Completes every period whose end has passed: several, if the app was away long
     * enough. Only a period that ended moments ago gets the chime; one that finished an
     * hour ago is recorded quietly.
     */
    fun catchUp() {
        val now = now()
        var s = _state.value
        var changed = false
        while (true) {
            val endsAt = s.endsAt ?: break
            if (now < endsAt - DUE_TOLERANCE_MS) break
            s = completePeriod(s, endsAt, alert = now - endsAt < RECENT_MS)
            changed = true
        }
        if (changed) {
            val settled = s
            mutate { settled }
        }
    }

    private fun completePeriod(s: TimerModel, endedAt: Long, alert: Boolean): TimerModel {
        if (!s.pomodoroMode) {
            // Closed at the scheduled end rather than now, so the run is exactly its length.
            log(SessionType.TIMER, s.segmentsUntil(endedAt), s.category)
            if (alert) announce(Alert.TIMER_DONE, s)
            return s.idle(TimerPhase.WORK)
        }

        if (s.phase == TimerPhase.WORK) {
            val behavior = PomodoroEndBehavior.from(preferences.getPomodoroEndBehavior())
            return if (behavior == PomodoroEndBehavior.OVERTIME) {
                // Nothing is logged yet, and the running stretch simply carries on: focus and
                // the overtime after it are recorded together once the break is claimed.
                if (alert) announce(Alert.FOCUS_DONE_OVERTIME, s)
                s.idle(TimerPhase.WORK).copy(
                    inOvertime = true,
                    overtimeSince = endedAt,
                    segments = s.segments,
                    runningSince = s.runningSince ?: s.phaseStartedAt
                )
            } else {
                log(SessionType.POMODORO_WORK, s.segmentsUntil(endedAt), s.category)
                if (alert) announce(Alert.FOCUS_DONE_BREAK, s)
                // Anchored to when focus actually ended rather than to now, so catching up
                // late doesn't hand out a longer break.
                s.idle(TimerPhase.BREAK).copy(
                    endsAt = endedAt + s.breakMinutes * 60 * 1000L,
                    phaseStartedAt = endedAt,
                    runningSince = endedAt
                )
            }
        }

        log(SessionType.POMODORO_BREAK, s.segmentsUntil(endedAt), null)
        if (alert) announce(Alert.BREAK_DONE, s)
        // Hand control back rather than looping into another focus period.
        return s.idle(TimerPhase.WORK)
    }

    private fun announce(alert: Alert, s: TimerModel) {
        alerts.play()
        // On screen, the app itself shows what happened; a heads-up on top would be noise.
        if (!isAppInForeground()) notifier.showAlert(alert, s)
    }

    /** Writes a pending focus-plus-overtime stretch as a single session. */
    private fun logOvertime(s: TimerModel, now: Long) {
        if (!s.inOvertime) return
        log(SessionType.POMODORO_WORK, s.segmentsUntil(now), s.category)
    }

    /**
     * Records a countdown abandoned part-way, so the time isn't lost. Skipped in overtime
     * (that is logged in full by [logOvertime]) and for breaks, and floored at a minute
     * since anything shorter would display as "0m".
     */
    private fun logIncomplete(s: TimerModel, now: Long) {
        if (!preferences.isKeepIncompleteCycles() || s.inOvertime) return
        if (s.pomodoroMode && s.phase == TimerPhase.BREAK) return
        if (s.endsAt == null && s.pausedRemaining == null) return

        val ran = s.segmentsUntil(now)
        if (ran.activeMillis() < 60_000L) return

        val type = if (s.pomodoroMode) SessionType.POMODORO_WORK else SessionType.TIMER
        log(type, ran, s.category)
    }

    private fun log(type: SessionType, segments: List<Segment>, category: Category?) {
        if (segments.isEmpty()) return
        scope.launch { repository.logSession(type, segments, category?.id) }
    }

    /** A duration change; a countdown that isn't running starts over at the new length. */
    private fun setMinutes(apply: (TimerModel) -> TimerModel) = mutate { current ->
        val updated = apply(current)
        if (!updated.isRunning && !updated.inOvertime) updated.copy(pausedRemaining = null) else updated
    }

    private fun mutate(change: (TimerModel) -> TimerModel) {
        val before = _state.value
        val after = change(before)
        if (after == before) return
        _state.value = after
        save(after)
        publish(after)
    }

    private fun publish(s: TimerModel, forceAlarm: Boolean = false) {
        alarms.sync(s.endsAt, force = forceAlarm)
        notifier.showTimer(s)
    }

    // Committed synchronously: these writes are rare, and a receiver's process can be
    // killed moments after it returns.
    private fun save(s: TimerModel) = store.edit(commit = true) {
        putBoolean(K_POMODORO, s.pomodoroMode)
        putString(K_PHASE, s.phase.name)
        putInt(K_TIMER_MINUTES, s.timerMinutes)
        putInt(K_WORK_MINUTES, s.workMinutes)
        putInt(K_BREAK_MINUTES, s.breakMinutes)
        putCategory(K_CATEGORY, s.category)
        remove(K_LEGACY_ACTIVITY)
        putNullableLong(K_ENDS_AT, s.endsAt)
        putNullableLong(K_PAUSED_REMAINING, s.pausedRemaining)
        putBoolean(K_IN_OVERTIME, s.inOvertime)
        putNullableLong(K_OVERTIME_SINCE, s.overtimeSince)
        putLong(K_OVERTIME_BANKED, s.overtimeBanked)
        putLong(K_PHASE_STARTED, s.phaseStartedAt)
        putString(K_SEGMENTS, encodeSegments(s.segments))
        putNullableLong(K_RUNNING_SINCE, s.runningSince)
    }

    private fun load(): TimerModel = TimerModel(
        pomodoroMode = store.getBoolean(K_POMODORO, false),
        phase = TimerPhase.entries.firstOrNull { it.name == store.getString(K_PHASE, null) }
            ?: TimerPhase.WORK,
        timerMinutes = store.getInt(K_TIMER_MINUTES, 25),
        workMinutes = store.getInt(K_WORK_MINUTES, 25),
        breakMinutes = store.getInt(K_BREAK_MINUTES, 5),
        category = store.category(K_CATEGORY),
        endsAt = store.nullableLong(K_ENDS_AT),
        pausedRemaining = store.nullableLong(K_PAUSED_REMAINING),
        inOvertime = store.getBoolean(K_IN_OVERTIME, false),
        overtimeSince = store.nullableLong(K_OVERTIME_SINCE),
        overtimeBanked = store.getLong(K_OVERTIME_BANKED, 0L),
        phaseStartedAt = store.getLong(K_PHASE_STARTED, 0L),
        segments = decodeSegments(store.getString(K_SEGMENTS, null)),
        runningSince = store.nullableLong(K_RUNNING_SINCE)
    )

    private fun now() = System.currentTimeMillis()

    companion object {
        const val MAX_TIMER_MINUTES = 9999

        /**
         * How early a period counts as finished. Matches the display, which reads 00:00
         * for the final second, and absorbs an alarm arriving a touch early.
         */
        const val DUE_TOLERANCE_MS = 1000L

        /** A period that ended longer ago than this is recorded without a chime. */
        private const val RECENT_MS = 60_000L

        private const val STORE_NAME = "timer_state"
        private const val K_POMODORO = "pomodoro_mode"
        private const val K_PHASE = "phase"
        private const val K_TIMER_MINUTES = "timer_minutes"
        private const val K_WORK_MINUTES = "work_minutes"
        private const val K_BREAK_MINUTES = "break_minutes"
        private const val K_CATEGORY = "category"
        /** The free-text name from before categories; cleared on the next save. */
        private const val K_LEGACY_ACTIVITY = "activity_name"
        private const val K_ENDS_AT = "ends_at"
        private const val K_PAUSED_REMAINING = "paused_remaining"
        private const val K_IN_OVERTIME = "in_overtime"
        private const val K_OVERTIME_SINCE = "overtime_since"
        private const val K_OVERTIME_BANKED = "overtime_banked"
        private const val K_PHASE_STARTED = "phase_started_at"
        private const val K_SEGMENTS = "segments"
        private const val K_RUNNING_SINCE = "running_since"
    }
}

/** Back to a stopped, fresh period in [phase]; durations, mode and category are kept. */
private fun TimerModel.idle(phase: TimerPhase) = copy(
    phase = phase,
    endsAt = null,
    pausedRemaining = null,
    inOvertime = false,
    overtimeSince = null,
    overtimeBanked = 0L,
    segments = emptyList(),
    runningSince = null
)

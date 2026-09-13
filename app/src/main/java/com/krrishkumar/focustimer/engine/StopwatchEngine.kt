package com.krrishkumar.focustimer.engine

import android.content.Context
import androidx.core.content.edit
import com.krrishkumar.focustimer.data.AppPreferences
import com.krrishkumar.focustimer.data.SessionRepository
import com.krrishkumar.focustimer.data.SessionType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** The stopwatch as it's saved: a start moment rather than a running count. */
data class StopwatchModel(
    /** When the stopwatch would have started had it never been paused; null while stopped. */
    val runningSince: Long? = null,
    val pausedElapsed: Long = 0L,
    val sessionStartedAt: Long = 0L,
    /** History row this run has already written, so later pauses update it instead of adding more. */
    val sessionId: Long? = null,
    /** Name applied to logged sessions; stays set until the user changes it. */
    val activityName: String? = null
) {
    val isRunning: Boolean get() = runningSince != null

    fun elapsedAt(now: Long): Long =
        if (runningSince != null) (now - runningSince).coerceAtLeast(0L) else pausedElapsed
}

/**
 * The stopwatch's source of truth, shared by the screen and the notification buttons, and
 * saved on every change so a run survives the app being closed. Main thread only.
 */
class StopwatchEngine(
    context: Context,
    private val repository: SessionRepository,
    private val preferences: AppPreferences,
    private val notifier: EngineNotifier,
    private val scope: CoroutineScope
) {
    private val store = context.getSharedPreferences(STORE_NAME, Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<StopwatchModel> = _state

    /** Holds the row id for one run, so every write for that run agrees on it. */
    private class Run(var sessionId: Long?)

    /**
     * The run that history writes belong to. A reset or finish swaps in a fresh one, so a
     * write still in flight can't attach itself to the next run.
     */
    private var currentRun = Run(_state.value.sessionId)

    /** Serialises writes so a quick pause-resume-pause can't insert two rows. */
    private val writeLock = Mutex()

    init {
        notifier.showStopwatch(_state.value)
    }

    fun resync() = notifier.showStopwatch(_state.value)

    fun start() {
        val now = now()
        mutate { s ->
            if (s.isRunning) {
                s
            } else {
                s.copy(
                    runningSince = now - s.pausedElapsed,
                    pausedElapsed = 0L,
                    sessionStartedAt = if (s.pausedElapsed == 0L) now else s.sessionStartedAt
                )
            }
        }
    }

    fun pause() {
        val s = _state.value
        if (!s.isRunning) return
        val elapsed = s.elapsedAt(now())
        mutate { it.copy(runningSince = null, pausedElapsed = elapsed) }
        if (preferences.isLogStopwatchOnPause()) record(elapsed)
    }

    fun toggleRunning() {
        if (_state.value.isRunning) pause() else start()
    }

    fun setActivityName(name: String) = mutate { it.copy(activityName = name.trim().ifBlank { null }) }

    fun finishAndLog() {
        record(_state.value.elapsedAt(now()))
        endRun()
    }

    fun reset() {
        // Whatever was already written stays: that time was genuinely spent, and it can be
        // removed from History if it wasn't wanted.
        endRun()
    }

    private fun endRun() {
        currentRun = Run(null)
        // The activity name outlives the run it was set on.
        mutate { StopwatchModel(activityName = it.activityName) }
    }

    /** Writes this run's row the first time, and revises it on every later call. */
    private fun record(elapsed: Long) {
        if (elapsed <= 0L) return
        val run = currentRun
        val snapshot = _state.value
        scope.launch {
            writeLock.withLock {
                val existing = run.sessionId
                if (existing != null) {
                    repository.updateDuration(existing, elapsed)
                    // The name may have been set after the row was first written.
                    repository.updateLabel(existing, snapshot.activityName)
                } else {
                    val id = repository.logSessionReturningId(
                        SessionType.STOPWATCH, snapshot.sessionStartedAt, elapsed, snapshot.activityName
                    )
                    run.sessionId = id
                    if (run === currentRun) mutate { it.copy(sessionId = id) }
                }
            }
        }
    }

    private fun mutate(change: (StopwatchModel) -> StopwatchModel) {
        val before = _state.value
        val after = change(before)
        if (after == before) return
        _state.value = after
        store.edit(commit = true) {
            putNullableLong(K_RUNNING_SINCE, after.runningSince)
            putLong(K_PAUSED_ELAPSED, after.pausedElapsed)
            putLong(K_SESSION_STARTED, after.sessionStartedAt)
            putNullableLong(K_SESSION_ID, after.sessionId)
            putString(K_ACTIVITY, after.activityName)
        }
        notifier.showStopwatch(after)
    }

    private fun load() = StopwatchModel(
        runningSince = store.nullableLong(K_RUNNING_SINCE),
        pausedElapsed = store.getLong(K_PAUSED_ELAPSED, 0L),
        sessionStartedAt = store.getLong(K_SESSION_STARTED, 0L),
        sessionId = store.nullableLong(K_SESSION_ID),
        activityName = store.getString(K_ACTIVITY, null)
    )

    private fun now() = System.currentTimeMillis()

    private companion object {
        const val STORE_NAME = "stopwatch_state"
        const val K_RUNNING_SINCE = "running_since"
        const val K_PAUSED_ELAPSED = "paused_elapsed"
        const val K_SESSION_STARTED = "session_started_at"
        const val K_SESSION_ID = "session_id"
        const val K_ACTIVITY = "activity_name"
    }
}

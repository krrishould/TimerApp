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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** The stopwatch as it's saved: the stretches it has run, not a running count. */
data class StopwatchModel(
    /** Stretches that have already run and been paused. */
    val segments: List<Segment> = emptyList(),
    /** When the stretch running right now began; null while stopped. */
    val segmentStart: Long? = null,
    /** History row this run has already written, so later pauses update it instead of adding more. */
    val sessionId: Long? = null,
    /** Category applied to logged sessions; stays set until the user changes it. */
    val category: Category? = null
) {
    val isRunning: Boolean get() = segmentStart != null

    /** Running, or paused with time on the clock. */
    val hasTime: Boolean get() = isRunning || segments.isNotEmpty()

    fun segmentsUntil(now: Long): List<Segment> =
        if (segmentStart != null && now > segmentStart) segments + Segment(segmentStart, now) else segments

    fun elapsedAt(now: Long): Long = segmentsUntil(now).activeMillis()
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

    fun start() = mutate { if (it.isRunning) it else it.copy(segmentStart = now()) }

    fun pause() {
        val s = _state.value
        if (!s.isRunning) return
        val ran = s.segmentsUntil(now())
        mutate { it.copy(segments = ran, segmentStart = null) }
        if (preferences.isLogStopwatchOnPause()) record(ran)
    }

    fun toggleRunning() {
        if (_state.value.isRunning) pause() else start()
    }

    fun setCategory(category: Category?) = mutate { it.copy(category = category) }

    /** Called when a category is deleted, so it isn't used for the next session. */
    fun clearCategory(categoryId: Long) = mutate {
        if (it.category?.id == categoryId) it.copy(category = null) else it
    }

    fun finishAndLog() {
        record(_state.value.segmentsUntil(now()))
        endRun()
    }

    fun reset() {
        // Whatever was already written stays: that time was genuinely spent, and it can be
        // removed from History if it wasn't wanted.
        endRun()
    }

    private fun endRun() {
        currentRun = Run(null)
        // The category outlives the run it was set on.
        mutate { StopwatchModel(category = it.category) }
    }

    /** Writes this run's row the first time, and rewrites it on every later call. */
    private fun record(segments: List<Segment>) {
        if (segments.activeMillis() <= 0L) return
        val run = currentRun
        val categoryId = _state.value.category?.id
        scope.launch {
            writeLock.withLock {
                val existing = run.sessionId
                if (existing != null) {
                    repository.replaceSession(existing, segments, categoryId)
                } else {
                    val id = repository.logSession(SessionType.STOPWATCH, segments, categoryId)
                        ?: return@withLock
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
            putString(K_SEGMENTS, encodeSegments(after.segments))
            putNullableLong(K_SEGMENT_START, after.segmentStart)
            putNullableLong(K_SESSION_ID, after.sessionId)
            putCategory(K_CATEGORY, after.category)
            LEGACY_KEYS.forEach { remove(it) }
        }
        notifier.showStopwatch(after)
    }

    private fun load(): StopwatchModel {
        var segments = decodeSegments(store.getString(K_SEGMENTS, null))
        var segmentStart = store.nullableLong(K_SEGMENT_START)
        // A run saved before segments existed: carry its time over as one stretch.
        if (segments.isEmpty() && segmentStart == null) {
            segmentStart = store.nullableLong("running_since")
            val paused = store.getLong("paused_elapsed", 0L)
            val started = store.getLong("session_started_at", 0L)
            if (segmentStart == null && paused > 0L && started > 0L) {
                segments = listOf(Segment(started, started + paused))
            }
        }
        return StopwatchModel(
            segments = segments,
            segmentStart = segmentStart,
            sessionId = store.nullableLong(K_SESSION_ID),
            category = store.category(K_CATEGORY)
        )
    }

    private fun now() = System.currentTimeMillis()

    private companion object {
        const val STORE_NAME = "stopwatch_state"
        const val K_SEGMENTS = "segments"
        const val K_SEGMENT_START = "segment_start"
        const val K_SESSION_ID = "session_id"
        const val K_CATEGORY = "category"
        val LEGACY_KEYS = listOf("running_since", "paused_elapsed", "session_started_at", "activity_name")
    }
}

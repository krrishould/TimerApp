package com.krrishkumar.focustimer.ui.stopwatch

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.krrishkumar.focustimer.data.SessionRepository
import com.krrishkumar.focustimer.data.SessionType
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class StopwatchUiState(
    val elapsedMillis: Long = 0L,
    val isRunning: Boolean = false,
    /** Name applied to logged sessions; stays set until the user changes it. */
    val activityName: String? = null
)

class StopwatchViewModel(private val repository: SessionRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(StopwatchUiState())
    val uiState: StateFlow<StopwatchUiState> = _uiState

    private var tickJob: Job? = null
    private var baseElapsedRealtime: Long = 0L
    private var accumulatedMillis: Long = 0L
    private var sessionStartTimeMillis: Long = 0L

    /**
     * Row this run has already written, if any. One run keeps one row and updates it,
     * so pausing repeatedly can never count the same stretch twice.
     */
    private var currentSessionId: Long? = null

    /** Serialises writes so a quick pause-resume-pause can't insert two rows. */
    private val writeLock = Mutex()

    /** Bumped whenever a run ends, so a write still in flight can't leak into the next one. */
    private var runId: Int = 0

    /** Set by the screen from the stored preferences. */
    var logOnPause: Boolean = true

    fun start() {
        if (_uiState.value.isRunning) return
        if (_uiState.value.elapsedMillis == 0L) {
            sessionStartTimeMillis = System.currentTimeMillis()
        }
        baseElapsedRealtime = SystemClock.elapsedRealtime()
        _uiState.update { it.copy(isRunning = true) }
        tickJob = viewModelScope.launch {
            while (true) {
                delay(200)
                val elapsedSinceStart = SystemClock.elapsedRealtime() - baseElapsedRealtime
                _uiState.update { it.copy(elapsedMillis = accumulatedMillis + elapsedSinceStart) }
            }
        }
    }

    fun pause() {
        tickJob?.cancel()
        accumulatedMillis = _uiState.value.elapsedMillis
        _uiState.update { it.copy(isRunning = false) }
        if (logOnPause) recordRun(accumulatedMillis)
    }

    fun toggleRunning() {
        if (_uiState.value.isRunning) pause() else start()
    }

    fun setActivityName(name: String) {
        _uiState.update { it.copy(activityName = name.trim().ifBlank { null }) }
    }

    fun finishAndLog() {
        tickJob?.cancel()
        val elapsed = _uiState.value.elapsedMillis
        val label = _uiState.value.activityName
        recordRun(elapsed, endsRun = true)
        accumulatedMillis = 0L
        // The activity name outlives the session it was set on.
        _uiState.update { StopwatchUiState(activityName = label) }
    }

    fun reset() {
        tickJob?.cancel()
        // Whatever was already written stays: that time was genuinely spent, and it can
        // be removed from History if it wasn't wanted.
        endRun()
        accumulatedMillis = 0L
        _uiState.update { StopwatchUiState(activityName = it.activityName) }
    }

    /**
     * Writes this run's row the first time and revises it on every later call, so the
     * table holds one row per run rather than one per pause.
     */
    private fun recordRun(elapsed: Long, endsRun: Boolean = false) {
        if (elapsed <= 0) {
            if (endsRun) endRun()
            return
        }
        val label = _uiState.value.activityName
        val startedAt = sessionStartTimeMillis
        val thisRun = runId
        viewModelScope.launch {
            writeLock.withLock {
                // A reset or finish while this was queued belongs to the previous run.
                val existing = if (thisRun == runId) currentSessionId else null
                if (existing != null) {
                    repository.updateDuration(existing, elapsed)
                    // The name may have been set after the row was first written.
                    repository.updateLabel(existing, label)
                } else {
                    val id = repository.logSessionReturningId(
                        SessionType.STOPWATCH, startedAt, elapsed, label
                    )
                    // Only adopt the row if the run it belongs to is still the current one.
                    if (thisRun == runId) currentSessionId = id
                }
                if (endsRun) endRun()
            }
        }
    }

    private fun endRun() {
        currentSessionId = null
        runId++
    }

    class Factory(private val repository: SessionRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return StopwatchViewModel(repository) as T
        }
    }
}

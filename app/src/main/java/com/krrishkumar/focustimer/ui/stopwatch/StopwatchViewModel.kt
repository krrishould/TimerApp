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
    }

    fun setActivityName(name: String) {
        _uiState.update { it.copy(activityName = name.trim().ifBlank { null }) }
    }

    fun finishAndLog() {
        tickJob?.cancel()
        val elapsed = _uiState.value.elapsedMillis
        val label = _uiState.value.activityName
        if (elapsed > 0) {
            viewModelScope.launch {
                repository.logSession(SessionType.STOPWATCH, sessionStartTimeMillis, elapsed, label)
            }
        }
        accumulatedMillis = 0L
        // The activity name outlives the session it was set on.
        _uiState.update { StopwatchUiState(activityName = label) }
    }

    fun reset() {
        tickJob?.cancel()
        accumulatedMillis = 0L
        _uiState.update { StopwatchUiState(activityName = it.activityName) }
    }

    class Factory(private val repository: SessionRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return StopwatchViewModel(repository) as T
        }
    }
}

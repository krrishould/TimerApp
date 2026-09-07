package com.krrishkumar.focustimer.ui.timer

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

enum class TimerPhase { WORK, BREAK }

data class TimerUiState(
    /** false = plain countdown timer, true = Focus/Break pomodoro cycling */
    val pomodoroMode: Boolean = false,
    val phase: TimerPhase = TimerPhase.WORK,
    val timerMinutes: Int = 25,
    val workMinutes: Int = 25,
    val breakMinutes: Int = 5,
    val remainingMillis: Long = 25 * 60 * 1000L,
    val isRunning: Boolean = false
)

class TimerViewModel(private val repository: SessionRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(TimerUiState())
    val uiState: StateFlow<TimerUiState> = _uiState

    private var tickJob: Job? = null
    private var phaseStartTimeMillis: Long = 0L

    private fun TimerUiState.currentPhaseMinutes(): Int = when {
        !pomodoroMode -> timerMinutes
        phase == TimerPhase.WORK -> workMinutes
        else -> breakMinutes
    }

    fun start() {
        if (_uiState.value.isRunning) return
        phaseStartTimeMillis = System.currentTimeMillis()
        val base = SystemClock.elapsedRealtime()
        val startRemaining = _uiState.value.remainingMillis
        if (startRemaining <= 0) return
        _uiState.update { it.copy(isRunning = true) }
        tickJob = viewModelScope.launch {
            while (true) {
                val elapsed = SystemClock.elapsedRealtime() - base
                val remaining = (startRemaining - elapsed).coerceAtLeast(0)
                _uiState.update { it.copy(remainingMillis = remaining) }
                if (remaining <= 0) {
                    onPhaseFinished()
                    break
                }
                delay(200)
            }
        }
    }

    fun pause() {
        tickJob?.cancel()
        _uiState.update { it.copy(isRunning = false) }
    }

    fun reset() {
        tickJob?.cancel()
        _uiState.update {
            val fresh = it.copy(phase = TimerPhase.WORK, isRunning = false)
            fresh.copy(remainingMillis = fresh.currentPhaseMinutes() * 60 * 1000L)
        }
    }

    fun togglePomodoroMode() {
        tickJob?.cancel()
        _uiState.update {
            val next = it.copy(
                pomodoroMode = !it.pomodoroMode,
                phase = TimerPhase.WORK,
                isRunning = false
            )
            next.copy(remainingMillis = next.currentPhaseMinutes() * 60 * 1000L)
        }
    }

    fun setTimerMinutes(minutes: Int) = updateMinutes(minutes, 1, 600) { state, value ->
        state.copy(timerMinutes = value)
    }

    fun setWorkMinutes(minutes: Int) = updateMinutes(minutes, 1, 180) { state, value ->
        state.copy(workMinutes = value)
    }

    fun setBreakMinutes(minutes: Int) = updateMinutes(minutes, 1, 60) { state, value ->
        state.copy(breakMinutes = value)
    }

    /** Applies a duration change, and re-syncs the countdown when it's sitting idle. */
    private fun updateMinutes(
        minutes: Int,
        min: Int,
        max: Int,
        apply: (TimerUiState, Int) -> TimerUiState
    ) {
        val clamped = minutes.coerceIn(min, max)
        _uiState.update { current ->
            val updated = apply(current, clamped)
            if (!updated.isRunning) {
                updated.copy(remainingMillis = updated.currentPhaseMinutes() * 60 * 1000L)
            } else {
                updated
            }
        }
    }

    private fun onPhaseFinished() {
        val state = _uiState.value

        if (!state.pomodoroMode) {
            viewModelScope.launch {
                repository.logSession(SessionType.TIMER, phaseStartTimeMillis, state.timerMinutes * 60 * 1000L)
            }
            _uiState.update { it.copy(remainingMillis = it.timerMinutes * 60 * 1000L, isRunning = false) }
            return
        }

        val finishedType =
            if (state.phase == TimerPhase.WORK) SessionType.POMODORO_WORK else SessionType.POMODORO_BREAK
        val finishedMinutes =
            if (state.phase == TimerPhase.WORK) state.workMinutes else state.breakMinutes
        viewModelScope.launch {
            repository.logSession(finishedType, phaseStartTimeMillis, finishedMinutes * 60 * 1000L)
        }

        val nextPhase = if (state.phase == TimerPhase.WORK) TimerPhase.BREAK else TimerPhase.WORK
        _uiState.update {
            val next = it.copy(phase = nextPhase, isRunning = false)
            next.copy(remainingMillis = next.currentPhaseMinutes() * 60 * 1000L)
        }
    }

    class Factory(private val repository: SessionRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return TimerViewModel(repository) as T
        }
    }
}

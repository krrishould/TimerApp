package com.krrishkumar.focustimer.ui.timer

import android.media.AudioManager
import android.media.ToneGenerator
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

data class TimerUiState(
    /** false = plain countdown timer, true = Focus/Break pomodoro cycling */
    val pomodoroMode: Boolean = false,
    val phase: TimerPhase = TimerPhase.WORK,
    val timerMinutes: Int = 25,
    val workMinutes: Int = 25,
    val breakMinutes: Int = 5,
    val remainingMillis: Long = 25 * 60 * 1000L,
    val isRunning: Boolean = false,
    /** Name applied to logged sessions; stays set until the user changes it. */
    val activityName: String? = null,
    /** True once a focus period has run out and the timer is counting upwards. */
    val inOvertime: Boolean = false,
    val overtimeMillis: Long = 0L
)

class TimerViewModel(private val repository: SessionRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(TimerUiState())
    val uiState: StateFlow<TimerUiState> = _uiState

    private var tickJob: Job? = null
    private var phaseStartTimeMillis: Long = 0L

    /** Set by the screen from the stored preferences. */
    var endBehavior: PomodoroEndBehavior = PomodoroEndBehavior.OVERTIME
    var keepIncompleteCycles: Boolean = true

    private fun TimerUiState.currentPhaseMinutes(): Int = when {
        !pomodoroMode -> timerMinutes
        phase == TimerPhase.WORK -> workMinutes
        else -> breakMinutes
    }

    fun setActivityName(name: String) {
        _uiState.update { it.copy(activityName = name.trim().ifBlank { null }) }
    }

    fun start() {
        if (_uiState.value.isRunning) return
        if (_uiState.value.inOvertime) {
            runOvertime()
            return
        }
        if (_uiState.value.remainingMillis <= 0) return
        phaseStartTimeMillis = System.currentTimeMillis()
        runCountdown()
    }

    private fun runCountdown() {
        val base = SystemClock.elapsedRealtime()
        val startRemaining = _uiState.value.remainingMillis
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

    /** Counts upwards from whatever overtime has already accrued. */
    private fun runOvertime() {
        val base = SystemClock.elapsedRealtime()
        val alreadyOvertime = _uiState.value.overtimeMillis
        _uiState.update { it.copy(isRunning = true, inOvertime = true) }
        tickJob = viewModelScope.launch {
            while (true) {
                val elapsed = SystemClock.elapsedRealtime() - base
                _uiState.update { it.copy(overtimeMillis = alreadyOvertime + elapsed) }
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
        flushOvertime()
        flushIncompleteCycle()
        _uiState.update {
            val fresh = it.copy(
                phase = TimerPhase.WORK,
                isRunning = false,
                inOvertime = false,
                overtimeMillis = 0L
            )
            fresh.copy(remainingMillis = fresh.currentPhaseMinutes() * 60 * 1000L)
        }
    }

    fun togglePomodoroMode() {
        tickJob?.cancel()
        flushOvertime()
        flushIncompleteCycle()
        _uiState.update {
            val next = it.copy(
                pomodoroMode = !it.pomodoroMode,
                phase = TimerPhase.WORK,
                isRunning = false,
                inOvertime = false,
                overtimeMillis = 0L
            )
            next.copy(remainingMillis = next.currentPhaseMinutes() * 60 * 1000L)
        }
    }

    /** Ends overtime, records the whole stretch as one session, and starts the break. */
    fun claimBreak() {
        tickJob?.cancel()
        flushOvertime()
        _uiState.update {
            it.copy(
                phase = TimerPhase.BREAK,
                inOvertime = false,
                overtimeMillis = 0L,
                isRunning = false,
                remainingMillis = it.breakMinutes * 60 * 1000L
            )
        }
        phaseStartTimeMillis = System.currentTimeMillis()
        runCountdown()
    }

    /**
     * Writes the pending focus-plus-overtime stretch as a single session. Called from
     * every exit out of overtime so the work is never silently dropped.
     */
    private fun flushOvertime() {
        val state = _uiState.value
        if (!state.inOvertime) return
        val worked = state.workMinutes * 60 * 1000L + state.overtimeMillis
        val startedAt = phaseStartTimeMillis
        val label = state.activityName
        viewModelScope.launch {
            repository.logSession(SessionType.POMODORO_WORK, startedAt, worked, label)
        }
    }

    /**
     * Records a countdown that was abandoned part-way through, so the time isn't lost.
     * Skipped in overtime (that stretch is logged in full by [flushOvertime]) and for
     * breaks, and floored at a minute since anything shorter would display as "0m".
     */
    private fun flushIncompleteCycle() {
        val state = _uiState.value
        if (!keepIncompleteCycles || state.inOvertime) return
        if (state.pomodoroMode && state.phase == TimerPhase.BREAK) return

        val elapsed = state.currentPhaseMinutes() * 60 * 1000L - state.remainingMillis
        if (elapsed < 60_000L) return

        val type = if (state.pomodoroMode) SessionType.POMODORO_WORK else SessionType.TIMER
        logSession(type, elapsed, state.activityName)
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
            if (!updated.isRunning && !updated.inOvertime) {
                updated.copy(remainingMillis = updated.currentPhaseMinutes() * 60 * 1000L)
            } else {
                updated
            }
        }
    }

    private fun onPhaseFinished() {
        val state = _uiState.value

        if (!state.pomodoroMode) {
            logSession(SessionType.TIMER, state.timerMinutes * 60 * 1000L, state.activityName)
            chime()
            _uiState.update { it.copy(remainingMillis = it.timerMinutes * 60 * 1000L, isRunning = false) }
            return
        }

        if (state.phase == TimerPhase.WORK) {
            chime()
            if (endBehavior == PomodoroEndBehavior.OVERTIME) {
                // Nothing is logged yet: the focus period and the overtime that follows
                // are recorded together once the break is claimed.
                _uiState.update { it.copy(inOvertime = true, overtimeMillis = 0L) }
                runOvertime()
            } else {
                logSession(SessionType.POMODORO_WORK, state.workMinutes * 60 * 1000L, state.activityName)
                _uiState.update {
                    it.copy(phase = TimerPhase.BREAK, remainingMillis = it.breakMinutes * 60 * 1000L)
                }
                phaseStartTimeMillis = System.currentTimeMillis()
                runCountdown()
            }
            return
        }

        // Break finished: record it and hand control back rather than looping.
        logSession(SessionType.POMODORO_BREAK, state.breakMinutes * 60 * 1000L, null)
        chime()
        _uiState.update {
            it.copy(
                phase = TimerPhase.WORK,
                isRunning = false,
                remainingMillis = it.workMinutes * 60 * 1000L
            )
        }
    }

    private fun logSession(type: SessionType, durationMillis: Long, label: String?) {
        val startedAt = phaseStartTimeMillis
        viewModelScope.launch {
            repository.logSession(type, startedAt, durationMillis, label)
        }
    }

    /**
     * Short beep when a period ends. Only audible while the process is alive — there's
     * no foreground service, so a long-backgrounded timer may not sound.
     */
    private fun chime() {
        runCatching {
            val tone = ToneGenerator(AudioManager.STREAM_ALARM, 70)
            tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 500)
            viewModelScope.launch {
                delay(900)
                runCatching { tone.release() }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        tickJob?.cancel()
    }

    class Factory(private val repository: SessionRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return TimerViewModel(repository) as T
        }
    }
}

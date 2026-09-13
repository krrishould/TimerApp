package com.krrishkumar.focustimer.ui.timer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.krrishkumar.focustimer.AlltimeApp
import com.krrishkumar.focustimer.engine.TimerEngine
import com.krrishkumar.focustimer.engine.TimerModel
import com.krrishkumar.focustimer.engine.TimerPhase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

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
) {
    /** Focus time put in so far this period, before any overtime. */
    val workedMillis: Long
        get() = if (pomodoroMode && phase == TimerPhase.WORK && !inOvertime) {
            workMinutes * 60 * 1000L - remainingMillis
        } else {
            0L
        }
}

/**
 * Turns the [TimerEngine]'s saved state into a ticking display. The engine only knows
 * when things end; this works out the time left, and only ticks while something runs.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TimerViewModel(private val engine: TimerEngine) : ViewModel() {

    val uiState: StateFlow<TimerUiState> = engine.state
        .flatMapLatest { model ->
            if (!model.isRunning) {
                flowOf(render(model))
            } else {
                flow {
                    while (true) {
                        val endsAt = model.endsAt
                        // The alarm normally ends the period; this covers it arriving late
                        // while the app is open. Settling it swaps in the next state.
                        if (endsAt != null &&
                            System.currentTimeMillis() >= endsAt - TimerEngine.DUE_TOLERANCE_MS
                        ) {
                            engine.catchUp()
                        }
                        emit(render(model))
                        delay(200)
                    }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), render(engine.state.value))

    fun setActivityName(name: String) = engine.setActivityName(name)
    fun start() = engine.start()
    fun pause() = engine.pause()
    fun toggleRunning() = engine.toggleRunning()
    fun reset() = engine.reset()
    fun togglePomodoroMode() = engine.togglePomodoroMode()
    fun claimBreak() = engine.claimBreak()
    fun adjustCurrentMinutes(delta: Int) = engine.adjustCurrentMinutes(delta)
    fun setTimerMinutes(minutes: Int) = engine.setTimerMinutes(minutes)
    fun setWorkMinutes(minutes: Int) = engine.setWorkMinutes(minutes)
    fun setBreakMinutes(minutes: Int) = engine.setBreakMinutes(minutes)

    private fun render(model: TimerModel): TimerUiState {
        val now = System.currentTimeMillis()
        return TimerUiState(
            pomodoroMode = model.pomodoroMode,
            phase = model.phase,
            timerMinutes = model.timerMinutes,
            workMinutes = model.workMinutes,
            breakMinutes = model.breakMinutes,
            remainingMillis = model.remainingAt(now),
            isRunning = model.isRunning,
            activityName = model.activityName,
            inOvertime = model.inOvertime,
            overtimeMillis = model.overtimeAt(now)
        )
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { TimerViewModel((this[APPLICATION_KEY] as AlltimeApp).timerEngine) }
        }
    }
}

package com.krrishkumar.focustimer.ui.stopwatch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.krrishkumar.focustimer.AlltimeApp
import com.krrishkumar.focustimer.engine.StopwatchEngine
import com.krrishkumar.focustimer.engine.StopwatchModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

data class StopwatchUiState(
    val elapsedMillis: Long = 0L,
    val isRunning: Boolean = false,
    /** Name applied to logged sessions; stays set until the user changes it. */
    val activityName: String? = null
)

/** Ticks the [StopwatchEngine]'s saved start moment into a display, only while running. */
@OptIn(ExperimentalCoroutinesApi::class)
class StopwatchViewModel(private val engine: StopwatchEngine) : ViewModel() {

    val uiState: StateFlow<StopwatchUiState> = engine.state
        .flatMapLatest { model ->
            if (!model.isRunning) {
                flowOf(render(model))
            } else {
                flow {
                    while (true) {
                        emit(render(model))
                        delay(200)
                    }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), render(engine.state.value))

    fun start() = engine.start()
    fun pause() = engine.pause()
    fun toggleRunning() = engine.toggleRunning()
    fun reset() = engine.reset()
    fun finishAndLog() = engine.finishAndLog()
    fun setActivityName(name: String) = engine.setActivityName(name)

    private fun render(model: StopwatchModel) = StopwatchUiState(
        elapsedMillis = model.elapsedAt(System.currentTimeMillis()),
        isRunning = model.isRunning,
        activityName = model.activityName
    )

    companion object {
        val Factory = viewModelFactory {
            initializer { StopwatchViewModel((this[APPLICATION_KEY] as AlltimeApp).stopwatchEngine) }
        }
    }
}

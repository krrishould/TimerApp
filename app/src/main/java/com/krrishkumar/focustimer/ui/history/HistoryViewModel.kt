package com.krrishkumar.focustimer.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.krrishkumar.focustimer.data.SessionRepository
import com.krrishkumar.focustimer.data.SessionType
import com.krrishkumar.focustimer.data.WorkSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class DayTotal(val label: String, val totalMillis: Long)

data class HistoryUiState(
    val todayTotalMillis: Long = 0L,
    val last7Days: List<DayTotal> = emptyList(),
    val recentSessions: List<WorkSession> = emptyList(),
    val isLoading: Boolean = true
)

class HistoryViewModel(private val repository: SessionRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val calendar = Calendar.getInstance()
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val startOfToday = calendar.timeInMillis

            calendar.add(Calendar.DAY_OF_YEAR, -6)
            val sevenDaysAgo = calendar.timeInMillis

            val sessions = repository.getSessionsSince(sevenDaysAgo)
                .filter { it.type != SessionType.POMODORO_BREAK }

            val todayTotal = sessions
                .filter { it.startTimeMillis >= startOfToday }
                .sumOf { it.durationMillis }

            val dayFormat = SimpleDateFormat("EEE", Locale.getDefault())
            val dayTotals = (0..6).map { offset ->
                val dayCal = Calendar.getInstance()
                dayCal.timeInMillis = startOfToday
                dayCal.add(Calendar.DAY_OF_YEAR, -offset)
                val dayStart = dayCal.timeInMillis
                val dayEnd = dayStart + 24 * 60 * 60 * 1000L
                val total = sessions
                    .filter { it.startTimeMillis in dayStart until dayEnd }
                    .sumOf { it.durationMillis }
                DayTotal(dayFormat.format(Date(dayStart)), total)
            }.reversed()

            _uiState.value = HistoryUiState(
                todayTotalMillis = todayTotal,
                last7Days = dayTotals,
                recentSessions = sessions.take(20),
                isLoading = false
            )
        }
    }

    class Factory(private val repository: SessionRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return HistoryViewModel(repository) as T
        }
    }
}

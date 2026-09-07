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

private const val DAY_MILLIS = 24 * 60 * 60 * 1000L

data class DayTotal(
    /** Single letter shown under the bar. */
    val initial: String,
    /** Full name used as the heading when the day is opened. */
    val name: String,
    val dayStart: Long,
    val totalMillis: Long,
    val isToday: Boolean
)

data class HistoryUiState(
    val todayTotalMillis: Long = 0L,
    val week: List<DayTotal> = emptyList(),
    val recentSessions: List<WorkSession> = emptyList(),
    /** Non-null when a specific day has been opened from the chart. */
    val selectedDay: DayTotal? = null,
    val selectedDaySessions: List<WorkSession> = emptyList(),
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
            val todayStart = startOfToday()
            val weekStart = startOfWeek(todayStart)

            // Breaks aren't working time, so they're left out of the totals and lists.
            val sessions = repository.getSessionsSince(weekStart)
                .filter { it.type != SessionType.POMODORO_BREAK }

            val initialFormat = SimpleDateFormat("EEEEE", Locale.getDefault())
            val nameFormat = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault())

            val week = (0 until 7).map { offset ->
                val dayStart = weekStart + offset * DAY_MILLIS
                val dayEnd = dayStart + DAY_MILLIS
                DayTotal(
                    initial = initialFormat.format(Date(dayStart)),
                    name = nameFormat.format(Date(dayStart)),
                    dayStart = dayStart,
                    totalMillis = sessions
                        .filter { it.startTimeMillis in dayStart until dayEnd }
                        .sumOf { it.durationMillis },
                    isToday = dayStart == todayStart
                )
            }

            _uiState.value = _uiState.value.copy(
                todayTotalMillis = sessions
                    .filter { it.startTimeMillis >= todayStart }
                    .sumOf { it.durationMillis },
                week = week,
                recentSessions = sessions.take(20),
                isLoading = false
            )

            // Keep an open day in sync with freshly loaded data.
            _uiState.value.selectedDay?.let { open ->
                week.firstOrNull { it.dayStart == open.dayStart }?.let { selectDay(it) }
            }
        }
    }

    fun selectDay(day: DayTotal) {
        viewModelScope.launch {
            val sessions = repository.getSessionsBetween(day.dayStart, day.dayStart + DAY_MILLIS)
                .filter { it.type != SessionType.POMODORO_BREAK }
            _uiState.value = _uiState.value.copy(selectedDay = day, selectedDaySessions = sessions)
        }
    }

    fun clearSelectedDay() {
        _uiState.value = _uiState.value.copy(selectedDay = null, selectedDaySessions = emptyList())
    }

    fun deleteSession(session: WorkSession) {
        viewModelScope.launch {
            repository.deleteSession(session.id)
            refresh()
        }
    }

    fun setLabel(session: WorkSession, label: String) {
        viewModelScope.launch {
            repository.updateLabel(session.id, label.trim().ifBlank { null })
            refresh()
        }
    }

    private fun startOfToday(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    /** Monday of the week containing [todayStart], so the chart always reads Mon-Sun. */
    private fun startOfWeek(todayStart: Long): Long {
        val calendar = Calendar.getInstance().apply { timeInMillis = todayStart }
        val daysSinceMonday = (calendar.get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY + 7) % 7
        calendar.add(Calendar.DAY_OF_YEAR, -daysSinceMonday)
        return calendar.timeInMillis
    }

    class Factory(private val repository: SessionRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return HistoryViewModel(repository) as T
        }
    }
}

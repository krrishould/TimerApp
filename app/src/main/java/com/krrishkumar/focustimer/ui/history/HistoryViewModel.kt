package com.krrishkumar.focustimer.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.krrishkumar.focustimer.data.Category
import com.krrishkumar.focustimer.data.Segment
import com.krrishkumar.focustimer.data.SessionRepository
import com.krrishkumar.focustimer.data.SessionType
import com.krrishkumar.focustimer.data.TimelineSegment
import com.krrishkumar.focustimer.data.WorkSession
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class HistoryTab { DAYS, STATS }

enum class StatsRange(val label: String, val days: Int?) {
    WEEK("7 days", 7),
    MONTH("30 days", 30),
    ALL("All time", null)
}

data class DayTotal(
    /** Single letter shown under the bar. */
    val initial: String,
    /** Full name used as the heading when the day is open. */
    val name: String,
    val dayStart: Long,
    val totalMillis: Long,
    val isToday: Boolean,
    val isFuture: Boolean
)

/** A session opened from the list, with the stretches it actually ran. */
data class SessionDetail(val session: WorkSession, val segments: List<Segment>)

data class CategoryStat(val name: String, val color: Int?, val millis: Long, val fraction: Float)

data class Stats(
    val range: StatsRange,
    val totalMillis: Long,
    /** The same length of time just before the range; null for all time. */
    val previousTotalMillis: Long?,
    val sessionCount: Int,
    val daysInRange: Int,
    val activeDays: Int,
    val dailyAverageMillis: Long,
    val averageSessionMillis: Long,
    val longestSessionMillis: Long,
    val pausedMillis: Long,
    val focusSessions: Int,
    val currentStreak: Int,
    val bestStreak: Int,
    val bestDayStart: Long?,
    val bestDayMillis: Long,
    val byCategory: List<CategoryStat>,
    /** Running time that fell in each hour of the day, 0 to 23. */
    val byHour: List<Long>
)

data class HistoryUiState(
    val tab: HistoryTab = HistoryTab.DAYS,
    val weekLabel: String = "This week",
    val week: List<DayTotal> = emptyList(),
    val canGoForward: Boolean = false,
    val selectedDay: DayTotal? = null,
    /** Work sessions that started on the selected day, newest first. */
    val daySessions: List<WorkSession> = emptyList(),
    /** Every running stretch on the selected day, breaks included, for the timeline. */
    val dayTimeline: List<TimelineSegment> = emptyList(),
    val statsRange: StatsRange = StatsRange.WEEK,
    val stats: Stats? = null,
    val detail: SessionDetail? = null
)

class HistoryViewModel(private val repository: SessionRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState

    /** 0 is this week, -1 last week, and so on. */
    private var weekOffset = 0
    private var selectedDayStart: Long? = null
    private var daysJob: Job? = null
    private var statsJob: Job? = null

    fun refresh() {
        loadDays()
        if (_uiState.value.tab == HistoryTab.STATS) loadStats()
    }

    fun selectTab(tab: HistoryTab) {
        _uiState.update { it.copy(tab = tab) }
        if (tab == HistoryTab.STATS) loadStats()
    }

    fun previousWeek() {
        weekOffset -= 1
        selectedDayStart = null
        loadDays()
    }

    fun nextWeek() {
        if (weekOffset >= 0) return
        weekOffset += 1
        selectedDayStart = null
        loadDays()
    }

    fun selectDay(day: DayTotal) {
        if (day.isFuture) return
        selectedDayStart = day.dayStart
        loadDays()
    }

    fun setStatsRange(range: StatsRange) {
        _uiState.update { it.copy(statsRange = range) }
        loadStats()
    }

    fun openSession(session: WorkSession) {
        viewModelScope.launch {
            val segments = repository.getSegments(session.id)
            _uiState.update { it.copy(detail = SessionDetail(session, segments)) }
        }
    }

    fun closeSession() = _uiState.update { it.copy(detail = null) }

    fun setSessionCategory(session: WorkSession, category: Category?) {
        viewModelScope.launch {
            repository.setSessionCategory(session.id, category?.id)
            val updated = session.copy(
                categoryId = category?.id,
                categoryName = category?.name,
                categoryColor = category?.color
            )
            _uiState.update { state ->
                val detail = state.detail
                if (detail?.session?.id == session.id) state.copy(detail = detail.copy(session = updated)) else state
            }
            refresh()
        }
    }

    fun deleteSession(session: WorkSession) {
        viewModelScope.launch {
            repository.deleteSession(session.id)
            _uiState.update { it.copy(detail = null) }
            refresh()
        }
    }

    private fun loadDays() {
        daysJob?.cancel()
        daysJob = viewModelScope.launch {
            val today = startOfDay(System.currentTimeMillis())
            val weekStart = addDays(startOfWeek(today), weekOffset * 7)
            val bounds = (0..7).map { addDays(weekStart, it) }
            // Breaks aren't working time, so they stay out of totals and lists.
            val sessions = repository.getSessionsBetween(bounds.first(), bounds.last())
                .filter { it.type != SessionType.POMODORO_BREAK }

            val initialFormat = SimpleDateFormat("EEEEE", Locale.getDefault())
            val nameFormat = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault())
            val week = (0 until 7).map { i ->
                DayTotal(
                    initial = initialFormat.format(Date(bounds[i])),
                    name = nameFormat.format(Date(bounds[i])),
                    dayStart = bounds[i],
                    totalMillis = sessions
                        .filter { it.startTimeMillis in bounds[i] until bounds[i + 1] }
                        .sumOf { it.durationMillis },
                    isToday = bounds[i] == today,
                    isFuture = bounds[i] > today
                )
            }

            val selected = week.firstOrNull { it.dayStart == selectedDayStart }
                ?: week.firstOrNull { it.isToday }
                ?: week.last()
            val selectedEnd = addDays(selected.dayStart, 1)
            val timeline = repository.getTimeline(selected.dayStart, selectedEnd)

            _uiState.update {
                it.copy(
                    weekLabel = weekLabel(bounds.first(), bounds[6]),
                    week = week,
                    canGoForward = weekOffset < 0,
                    selectedDay = selected,
                    daySessions = sessions.filter { s -> s.startTimeMillis in selected.dayStart until selectedEnd },
                    dayTimeline = timeline
                )
            }
        }
    }

    private fun loadStats() {
        statsJob?.cancel()
        statsJob = viewModelScope.launch {
            val range = _uiState.value.statsRange
            val now = System.currentTimeMillis()
            val today = startOfDay(now)
            val all = repository.getSessionsSince(0L).filter { it.type != SessionType.POMODORO_BREAK }

            val firstDay = all.minOfOrNull { startOfDay(it.startTimeMillis) } ?: today
            val rangeStart = range.days?.let { addDays(today, -(it - 1)) } ?: firstDay
            val daysInRange = range.days ?: (daysBetween(firstDay, today) + 1)
            val inRange = all.filter { it.startTimeMillis >= rangeStart }
            val total = inRange.sumOf { it.durationMillis }

            val previousTotal = range.days?.let { days ->
                val previousStart = addDays(rangeStart, -days)
                all.filter { it.startTimeMillis in previousStart until rangeStart }.sumOf { it.durationMillis }
            }

            val perDay = inRange.groupBy { startOfDay(it.startTimeMillis) }
                .mapValues { (_, day) -> day.sumOf { it.durationMillis } }
            val bestDay = perDay.maxByOrNull { it.value }

            val byCategory = inRange.groupBy { it.categoryId }
                .map { (_, group) ->
                    val first = group.first()
                    val millis = group.sumOf { it.durationMillis }
                    CategoryStat(
                        name = first.categoryName ?: "No category",
                        color = first.categoryColor,
                        millis = millis,
                        fraction = if (total > 0) millis.toFloat() / total else 0f
                    )
                }
                .sortedByDescending { it.millis }

            val byHour = LongArray(24)
            repository.getTimeline(rangeStart, now)
                .filter { it.type != SessionType.POMODORO_BREAK }
                .forEach { spreadOverHours(max(it.start, rangeStart), it.end, byHour) }

            val activeDays = all.map { startOfDay(it.startTimeMillis) }.toSortedSet()

            _uiState.update {
                it.copy(
                    stats = Stats(
                        range = range,
                        totalMillis = total,
                        previousTotalMillis = previousTotal,
                        sessionCount = inRange.size,
                        daysInRange = daysInRange,
                        activeDays = perDay.size,
                        dailyAverageMillis = if (daysInRange > 0) total / daysInRange else 0L,
                        averageSessionMillis = if (inRange.isNotEmpty()) total / inRange.size else 0L,
                        longestSessionMillis = inRange.maxOfOrNull { s -> s.durationMillis } ?: 0L,
                        pausedMillis = inRange.sumOf { s -> s.pausedMillis },
                        focusSessions = inRange.count { s -> s.type == SessionType.POMODORO_WORK },
                        currentStreak = currentStreak(activeDays, today),
                        bestStreak = bestStreak(activeDays),
                        bestDayStart = bestDay?.key,
                        bestDayMillis = bestDay?.value ?: 0L,
                        byCategory = byCategory,
                        byHour = byHour.toList()
                    )
                )
            }
        }
    }

    /** Days in a row with any work, counting back from today, or from yesterday if today is still empty. */
    private fun currentStreak(days: Set<Long>, today: Long): Int {
        var cursor = if (today in days) today else addDays(today, -1)
        var streak = 0
        while (cursor in days) {
            streak++
            cursor = addDays(cursor, -1)
        }
        return streak
    }

    private fun bestStreak(sortedDays: Collection<Long>): Int {
        var best = 0
        var run = 0
        var previous: Long? = null
        for (day in sortedDays) {
            run = if (previous != null && addDays(previous, 1) == day) run + 1 else 1
            best = max(best, run)
            previous = day
        }
        return best
    }

    /** Adds a running stretch to the hour buckets it overlaps, split at each hour boundary. */
    private fun spreadOverHours(start: Long, end: Long, buckets: LongArray) {
        val calendar = Calendar.getInstance()
        var cursor = start
        while (cursor < end) {
            calendar.timeInMillis = cursor
            val hour = calendar.get(Calendar.HOUR_OF_DAY)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            calendar.add(Calendar.HOUR_OF_DAY, 1)
            val next = min(calendar.timeInMillis, end)
            buckets[hour] += next - cursor
            cursor = next
        }
    }

    private fun weekLabel(firstDay: Long, lastDay: Long): String = when (weekOffset) {
        0 -> "This week"
        -1 -> "Last week"
        else -> {
            val format = SimpleDateFormat("d MMM", Locale.getDefault())
            "${format.format(Date(firstDay))} – ${format.format(Date(lastDay))}"
        }
    }

    // Calendar arithmetic rather than adding 24 hours, so days stay right across clock changes.
    private fun startOfDay(millis: Long): Long = Calendar.getInstance().apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun addDays(dayStart: Long, days: Int): Long = Calendar.getInstance().apply {
        timeInMillis = dayStart
        add(Calendar.DAY_OF_YEAR, days)
    }.timeInMillis

    /** Monday of the week containing [dayStart], so the chart always reads Mon-Sun. */
    private fun startOfWeek(dayStart: Long): Long {
        val calendar = Calendar.getInstance().apply { timeInMillis = dayStart }
        val daysSinceMonday = (calendar.get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY + 7) % 7
        return addDays(dayStart, -daysSinceMonday)
    }

    private fun daysBetween(fromDay: Long, toDay: Long): Int =
        ((toDay - fromDay) / (24.0 * 60 * 60 * 1000)).roundToInt()

    class Factory(private val repository: SessionRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return HistoryViewModel(repository) as T
        }
    }
}

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
import kotlinx.coroutines.flow.drop
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

/** Calendar periods, so a week is Monday to Sunday and a month is the 1st to its last day. */
enum class StatsPeriod(val label: String) {
    DAY("Day"),
    WEEK("Week"),
    MONTH("Month"),
    ALL("All time")
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

/** Focus time on one day of a week or month. */
data class DayAmount(val dayStart: Long, val millis: Long, val isToday: Boolean, val isFuture: Boolean)

data class Stats(
    val period: StatsPeriod,
    /** "Today", "Last week", "September 2026" and so on. */
    val periodLabel: String,
    val canGoForward: Boolean,
    /** How the period before is named in the comparison, e.g. "yesterday"; null for all time. */
    val previousLabel: String?,
    val totalMillis: Long,
    val previousTotalMillis: Long?,
    val sessionCount: Int,
    /** Days of the period so far, which is what the daily average divides by. */
    val daysCounted: Int,
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
    /** When the first session in the period started, and the last one finished. */
    val firstStart: Long?,
    val lastEnd: Long?,
    val byCategory: List<CategoryStat>,
    /** Running time that fell in each hour of the day, 0 to 23. */
    val byHour: List<Long>,
    /** Every day of a week or month in order; empty for a single day or all time. */
    val byDay: List<DayAmount>
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
    val statsPeriod: StatsPeriod = StatsPeriod.DAY,
    val stats: Stats? = null,
    val detail: SessionDetail? = null
)

class HistoryViewModel(private val repository: SessionRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState

    /** 0 is this week on the Days tab, -1 last week, and so on. */
    private var weekOffset = 0
    private var selectedDayStart: Long? = null

    /** 0 is the current day, week or month on the Stats tab, -1 the one before. */
    private var statsOffset = 0

    private var daysJob: Job? = null
    private var statsJob: Job? = null

    init {
        // Reload when anything is written, including sessions arriving from another device.
        // The first value is skipped: the screen already loads when it opens.
        viewModelScope.launch {
            repository.changes.drop(1).collect { refresh() }
        }
    }

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

    fun setStatsPeriod(period: StatsPeriod) {
        statsOffset = 0
        _uiState.update { it.copy(statsPeriod = period, stats = null) }
        loadStats()
    }

    fun previousStatsPeriod() {
        if (_uiState.value.statsPeriod == StatsPeriod.ALL) return
        statsOffset -= 1
        loadStats()
    }

    fun nextStatsPeriod() {
        if (statsOffset >= 0) return
        statsOffset += 1
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
                    weekLabel = weekLabel(weekOffset, bounds.first(), bounds[6]),
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
            val period = _uiState.value.statsPeriod
            val offset = statsOffset
            val now = System.currentTimeMillis()
            val today = startOfDay(now)
            val all = repository.getSessionsSince(0L).filter { it.type != SessionType.POMODORO_BREAK }
            val firstDay = all.minOfOrNull { startOfDay(it.startTimeMillis) } ?: today

            val (start, end) = periodBounds(period, offset, today, firstDay)
            val previous = if (period == StatsPeriod.ALL) null else periodBounds(period, offset - 1, today, firstDay)

            val inPeriod = all.filter { it.startTimeMillis in start until end }
            val total = inPeriod.sumOf { it.durationMillis }
            val previousTotal = previous?.let { (from, until) ->
                all.filter { it.startTimeMillis in from until until }.sumOf { it.durationMillis }
            }

            // The current week or month only counts the days that have happened.
            val lastCountedDay = min(addDays(end, -1), today)
            val daysCounted = if (start > today) 0 else daysBetween(start, lastCountedDay) + 1

            val perDay = inPeriod.groupBy { startOfDay(it.startTimeMillis) }
                .mapValues { (_, day) -> day.sumOf { it.durationMillis } }
            val bestDay = perDay.maxByOrNull { it.value }

            val byDay = if (period == StatsPeriod.WEEK || period == StatsPeriod.MONTH) {
                generateSequence(start) { addDays(it, 1) }
                    .takeWhile { it < end }
                    .map { day -> DayAmount(day, perDay[day] ?: 0L, day == today, day > today) }
                    .toList()
            } else {
                emptyList()
            }

            val byCategory = inPeriod.groupBy { it.categoryId }
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
            repository.getTimeline(start, min(end, now))
                .filter { it.type != SessionType.POMODORO_BREAK }
                .forEach { spreadOverHours(max(it.start, start), min(it.end, end), byHour) }

            val activeDaysEver = all.map { startOfDay(it.startTimeMillis) }.toSortedSet()

            val stats = Stats(
                period = period,
                periodLabel = periodLabel(period, offset, start, end),
                canGoForward = period != StatsPeriod.ALL && offset < 0,
                previousLabel = previousLabel(period, offset),
                totalMillis = total,
                previousTotalMillis = previousTotal,
                sessionCount = inPeriod.size,
                daysCounted = daysCounted,
                activeDays = perDay.size,
                dailyAverageMillis = if (daysCounted > 0) total / daysCounted else 0L,
                averageSessionMillis = if (inPeriod.isNotEmpty()) total / inPeriod.size else 0L,
                longestSessionMillis = inPeriod.maxOfOrNull { it.durationMillis } ?: 0L,
                pausedMillis = inPeriod.sumOf { it.pausedMillis },
                focusSessions = inPeriod.count { it.type == SessionType.POMODORO_WORK },
                currentStreak = currentStreak(activeDaysEver, today),
                bestStreak = bestStreak(activeDaysEver),
                bestDayStart = bestDay?.key,
                bestDayMillis = bestDay?.value ?: 0L,
                firstStart = inPeriod.minOfOrNull { it.startTimeMillis },
                lastEnd = inPeriod.maxOfOrNull { it.endTimeMillis },
                byCategory = byCategory,
                byHour = byHour.toList(),
                byDay = byDay
            )
            _uiState.update { it.copy(stats = stats) }
        }
    }

    /** Start (inclusive) and end (exclusive) of the period [offset] steps from the current one. */
    private fun periodBounds(period: StatsPeriod, offset: Int, today: Long, firstDay: Long): Pair<Long, Long> =
        when (period) {
            StatsPeriod.DAY -> addDays(today, offset).let { it to addDays(it, 1) }
            StatsPeriod.WEEK -> addDays(startOfWeek(today), offset * 7).let { it to addDays(it, 7) }
            StatsPeriod.MONTH -> {
                val calendar = Calendar.getInstance().apply {
                    timeInMillis = today
                    set(Calendar.DAY_OF_MONTH, 1)
                    add(Calendar.MONTH, offset)
                }
                val monthStart = calendar.timeInMillis
                calendar.add(Calendar.MONTH, 1)
                monthStart to calendar.timeInMillis
            }
            StatsPeriod.ALL -> firstDay to addDays(today, 1)
        }

    private fun periodLabel(period: StatsPeriod, offset: Int, start: Long, end: Long): String = when (period) {
        StatsPeriod.DAY -> when (offset) {
            0 -> "Today"
            -1 -> "Yesterday"
            else -> SimpleDateFormat("EEEE, d MMM", Locale.getDefault()).format(Date(start))
        }
        StatsPeriod.WEEK -> weekLabel(offset, start, addDays(end, -1))
        StatsPeriod.MONTH -> SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date(start))
        StatsPeriod.ALL -> "All time"
    }

    private fun previousLabel(period: StatsPeriod, offset: Int): String? = when (period) {
        StatsPeriod.DAY -> if (offset == 0) "yesterday" else "the day before"
        StatsPeriod.WEEK -> if (offset == 0) "last week" else "the week before"
        StatsPeriod.MONTH -> if (offset == 0) "last month" else "the month before"
        StatsPeriod.ALL -> null
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

    private fun weekLabel(offset: Int, firstDay: Long, lastDay: Long): String = when (offset) {
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

    /** Monday of the week containing [dayStart], so weeks always read Mon-Sun. */
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

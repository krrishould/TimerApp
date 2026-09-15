package com.krrishkumar.focustimer.ui.history

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.krrishkumar.focustimer.ui.components.CategoryDot
import com.krrishkumar.focustimer.ui.theme.AppColors
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun StatsView(
    stats: Stats?,
    period: StatsPeriod,
    is24Hour: Boolean,
    colors: AppColors,
    onPeriodChange: (StatsPeriod) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatsPeriod.entries.forEach { option ->
                val selected = option == period
                Box(
                    modifier = Modifier
                        .background(if (selected) colors.accent else colors.surfaceRaised, RoundedCornerShape(50))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onPeriodChange(option) }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        option.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) colors.onAccent else colors.textSecondary
                    )
                }
            }
        }

        if (stats == null || stats.period != period) {
            Spacer(Modifier.height(24.dp))
            Text("Working it out…", style = MaterialTheme.typography.bodyMedium, color = colors.textMuted)
            return@Column
        }

        Spacer(Modifier.height(22.dp))
        if (period != StatsPeriod.ALL) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    stats.periodLabel,
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                ArrowButton("‹", enabled = true, colors = colors, onClick = onPrevious)
                ArrowButton("›", enabled = stats.canGoForward, colors = colors, onClick = onNext)
            }
            Spacer(Modifier.height(14.dp))
        }

        Text("FOCUS TIME", style = MaterialTheme.typography.labelLarge, color = colors.textSecondary)
        Spacer(Modifier.height(6.dp))
        Text(
            formatDuration(stats.totalMillis),
            style = MaterialTheme.typography.displayLarge.copy(fontSize = 46.sp),
            color = colors.textPrimary
        )
        comparison(stats)?.let { (text, up) ->
            Spacer(Modifier.height(4.dp))
            Text(text, style = MaterialTheme.typography.labelMedium, color = if (up) colors.accent else colors.textSecondary)
        }

        if (stats.totalMillis == 0L) {
            Spacer(Modifier.height(16.dp))
            Text(
                emptyMessage(stats),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textMuted
            )
        }

        Spacer(Modifier.height(24.dp))
        tilesFor(stats, is24Hour).chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 10.dp)) {
                pair.forEach { TileView(it, colors, Modifier.weight(1f)) }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }

        when (period) {
            StatsPeriod.WEEK -> {
                Section("BY DAY", colors)
                WeekBars(stats.byDay, colors)
            }
            StatsPeriod.MONTH -> {
                Section("CALENDAR", colors)
                MonthHeatmap(stats.byDay, colors)
            }
            else -> Unit
        }

        if (stats.byCategory.isNotEmpty()) {
            Section("BY CATEGORY", colors)
            CategoryBar(stats.byCategory, colors)
            Spacer(Modifier.height(14.dp))
            stats.byCategory.forEach { category ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp)
                ) {
                    CategoryDot(category.color, size = 10.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        category.name,
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        formatDuration(category.millis),
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.textSecondary
                    )
                    Text(
                        percentLabel(category.fraction),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.textMuted,
                        modifier = Modifier.width(48.dp).padding(start = 10.dp)
                    )
                }
            }
        }

        if (stats.byHour.any { it > 0 }) {
            Section("TIME OF DAY", colors)
            val peak = stats.byHour.indices.maxBy { stats.byHour[it] }
            Text(
                "Most focused ${hourRange(peak, is24Hour)}",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textPrimary
            )
            Spacer(Modifier.height(14.dp))
            HourChart(stats.byHour, peak, colors)
            Spacer(Modifier.height(6.dp))
            AxisLabels(
                labels = listOf("0", "6", "12", "18", "24"),
                fractions = listOf(0f, 0.25f, 0.5f, 0.75f, 1f),
                colors = colors
            )
        }
    }
}

private data class Tile(val label: String, val value: String, val note: String?)

/** A single day has no average or best day to speak of; its tiles describe the day itself. */
private fun tilesFor(stats: Stats, is24Hour: Boolean): List<Tile> {
    val sessions = Tile("Sessions", "${stats.sessionCount}", "avg ${formatDuration(stats.averageSessionMillis)}")
    val longest = Tile("Longest session", formatDuration(stats.longestSessionMillis), null)
    val paused = Tile("Time paused", formatDuration(stats.pausedMillis), "inside sessions")
    val pomodoros = Tile("Pomodoros", "${stats.focusSessions}", "focus sessions")
    val streak = Tile("Streak", days(stats.currentStreak), "best ${days(stats.bestStreak)}")

    if (stats.period == StatsPeriod.DAY) {
        return listOf(
            sessions,
            longest,
            Tile("Started", stats.firstStart?.let { formatClockTime(it, is24Hour) } ?: "–", "first session"),
            Tile("Finished", stats.lastEnd?.let { formatClockTime(it, is24Hour) } ?: "–", "last session"),
            paused,
            pomodoros,
            streak,
            Tile("Categories", "${stats.byCategory.size}", "used")
        )
    }
    val dayFormat = SimpleDateFormat("EEE, d MMM", Locale.getDefault())
    return listOf(
        Tile("Daily average", formatDuration(stats.dailyAverageMillis), "over ${days(stats.daysCounted)}"),
        Tile("Active days", "${stats.activeDays}", "of ${stats.daysCounted}"),
        Tile(
            "Best day",
            if (stats.bestDayStart != null) formatDuration(stats.bestDayMillis) else "–",
            stats.bestDayStart?.let { dayFormat.format(Date(it)) }
        ),
        streak,
        sessions,
        longest,
        paused,
        pomodoros
    )
}

@Composable
private fun Section(title: String, colors: AppColors) {
    Spacer(Modifier.height(24.dp))
    Text(title, style = MaterialTheme.typography.labelLarge, color = colors.textSecondary)
    Spacer(Modifier.height(14.dp))
}

@Composable
private fun TileView(tile: Tile, colors: AppColors, modifier: Modifier) {
    Column(
        modifier = modifier
            .background(colors.surfaceRaised, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 14.dp)
    ) {
        Text(tile.label, style = MaterialTheme.typography.labelMedium, color = colors.textMuted)
        Spacer(Modifier.height(4.dp))
        Text(tile.value, style = MaterialTheme.typography.titleLarge, color = colors.textPrimary, maxLines = 1)
        Text(
            tile.note ?: " ",
            style = MaterialTheme.typography.labelSmall,
            color = colors.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Monday to Sunday on one scale; today in full accent, days still to come left empty. */
@Composable
private fun WeekBars(days: List<DayAmount>, colors: AppColors) {
    val max = days.maxOfOrNull { it.millis }?.coerceAtLeast(60_000L) ?: 60_000L
    Canvas(Modifier.fillMaxWidth().height(110.dp)) {
        val slot = size.width / days.size.coerceAtLeast(1)
        val barWidth = slot * 0.46f
        drawLine(colors.border, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
        days.forEachIndexed { index, day ->
            if (day.millis <= 0L) return@forEachIndexed
            val height = (day.millis.toFloat() / max) * size.height
            drawRoundRect(
                color = if (day.isToday) colors.accent else colors.accent.copy(alpha = 0.55f),
                topLeft = Offset(index * slot + (slot - barWidth) / 2, size.height - height),
                size = Size(barWidth, height),
                cornerRadius = CornerRadius(5.dp.toPx())
            )
        }
    }
    Spacer(Modifier.height(8.dp))
    val initial = remember { SimpleDateFormat("EEEEE", Locale.getDefault()) }
    AxisLabels(
        labels = days.map { initial.format(Date(it.dayStart)) },
        fractions = days.indices.map { (it + 0.5f) / days.size },
        colors = colors
    )
}

/**
 * The month as a calendar, Monday first. Each day's shade grows with its focus time, so busy
 * stretches and gaps show at a glance. Today is outlined.
 */
@Composable
private fun MonthHeatmap(days: List<DayAmount>, colors: AppColors) {
    if (days.isEmpty()) return
    val max = days.maxOf { it.millis }.coerceAtLeast(60_000L)
    val leading = Calendar.getInstance().apply { timeInMillis = days.first().dayStart }
        .let { (it.get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY + 7) % 7 }
    val cells: List<DayAmount?> = List(leading) { null } + days
    val initial = remember { SimpleDateFormat("EEEEE", Locale.getDefault()) }
    val dayNumber = remember { SimpleDateFormat("d", Locale.getDefault()) }

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        // Headers taken from the first full week of cells, so they follow the locale.
        (0 until 7).forEach { column ->
            val sample = Calendar.getInstance().apply {
                timeInMillis = days.first().dayStart
                add(Calendar.DAY_OF_YEAR, column - leading)
            }.timeInMillis
            Text(
                initial.format(Date(sample)),
                style = MaterialTheme.typography.labelSmall,
                color = colors.textMuted,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
    Spacer(Modifier.height(6.dp))
    cells.chunked(7).forEach { week ->
        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (0 until 7).forEach { column ->
                val day = week.getOrNull(column)
                Box(Modifier.weight(1f).aspectRatio(1f)) {
                    if (day != null) {
                        val fraction = day.millis.toFloat() / max
                        val fill = when {
                            day.millis <= 0L -> colors.surfaceRaised
                            else -> colors.accent.copy(alpha = 0.28f + 0.72f * fraction)
                        }
                        Box(
                            modifier = Modifier
                                .matchParentSizeCompat()
                                .background(fill, RoundedCornerShape(8.dp))
                                .then(
                                    if (day.isToday) Modifier.border(1.5.dp, colors.textPrimary, RoundedCornerShape(8.dp))
                                    else Modifier
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                dayNumber.format(Date(day.dayStart)),
                                style = MaterialTheme.typography.labelSmall,
                                color = when {
                                    day.isFuture -> colors.textMuted.copy(alpha = 0.5f)
                                    fraction > 0.55f -> colors.onAccent
                                    else -> colors.textSecondary
                                }
                            )
                        }
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(4.dp))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Text("Less", style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
        listOf(0f, 0.3f, 0.6f, 1f).forEach { level ->
            Box(
                Modifier
                    .size(12.dp)
                    .background(
                        if (level == 0f) colors.surfaceRaised else colors.accent.copy(alpha = 0.28f + 0.72f * level),
                        RoundedCornerShape(3.dp)
                    )
            )
        }
        Text("More", style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
    }
}

/** Fills the parent box; a plain fillMaxSize would do the same inside a sized Box. */
private fun Modifier.matchParentSizeCompat(): Modifier = this.then(Modifier.fillMaxWidth().aspectRatio(1f))

/** One bar split into each category's share, in its colour. */
@Composable
private fun CategoryBar(categories: List<CategoryStat>, colors: AppColors) {
    Canvas(Modifier.fillMaxWidth().height(14.dp)) {
        val gap = 2.dp.toPx()
        var x = 0f
        categories.forEach { category ->
            val width = size.width * category.fraction
            if (width <= 0f) return@forEach
            drawRoundRect(
                color = category.color?.let { Color(it) } ?: colors.textMuted,
                topLeft = Offset(x, 0f),
                size = Size((width - gap).coerceAtLeast(1f), size.height),
                cornerRadius = CornerRadius(4.dp.toPx())
            )
            x += width
        }
    }
}

/** Twenty-four bars on one scale, the busiest hour in full accent. */
@Composable
private fun HourChart(byHour: List<Long>, peak: Int, colors: AppColors) {
    val max = byHour.maxOrNull()?.coerceAtLeast(1L) ?: 1L
    Canvas(Modifier.fillMaxWidth().height(96.dp)) {
        val slot = size.width / 24f
        val barWidth = slot * 0.62f
        drawLine(colors.border, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
        byHour.forEachIndexed { hour, millis ->
            if (millis <= 0L) return@forEachIndexed
            val height = (millis.toFloat() / max) * size.height
            drawRoundRect(
                color = if (hour == peak) colors.accent else colors.accent.copy(alpha = 0.45f),
                topLeft = Offset(hour * slot + (slot - barWidth) / 2, size.height - height),
                size = Size(barWidth, height),
                cornerRadius = CornerRadius(3.dp.toPx())
            )
        }
    }
}

private fun comparison(stats: Stats): Pair<String, Boolean>? {
    val previous = stats.previousTotalMillis ?: return null
    val against = stats.previousLabel ?: return null
    return when {
        stats.totalMillis == 0L && previous == 0L -> null
        previous == 0L -> "Nothing $against, so this is all new" to true
        else -> {
            // Rounding must not claim a full drop while there is still some time, or no change
            // while there is one.
            val raw = (stats.totalMillis - previous) * 100f / previous
            val change = raw.roundToInt()
                .let { if (it <= -100 && stats.totalMillis > 0L) -99 else it }
                .let { if (it == 0 && raw != 0f) (if (raw > 0f) 1 else -1) else it }
            when {
                change > 0 -> "Up $change% on $against" to true
                change < 0 -> "Down ${-change}% on $against" to false
                else -> "Same as $against" to false
            }
        }
    }
}

private fun emptyMessage(stats: Stats) = when (stats.period) {
    StatsPeriod.DAY -> "Nothing tracked on this day."
    StatsPeriod.WEEK -> "Nothing tracked this week."
    StatsPeriod.MONTH -> "Nothing tracked this month."
    StatsPeriod.ALL -> "Nothing tracked yet. Finished sessions from the Timer and Stopwatch will show up here."
}

private fun days(count: Int) = if (count == 1) "1 day" else "$count days"

private fun hourRange(hour: Int, is24Hour: Boolean): String {
    fun at(h: Int) = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, h % 24)
        set(Calendar.MINUTE, 0)
    }.timeInMillis
    return "between ${formatClockTime(at(hour), is24Hour)} and ${formatClockTime(at(hour + 1), is24Hour)}"
}

/** A share with any time at all never reads as nothing. */
private fun percentLabel(fraction: Float): String {
    val percent = (fraction * 100).roundToInt()
    return if (percent == 0 && fraction > 0f) "<1%" else "$percent%"
}

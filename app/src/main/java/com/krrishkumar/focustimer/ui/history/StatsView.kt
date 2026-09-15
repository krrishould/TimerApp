package com.krrishkumar.focustimer.ui.history

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
    range: StatsRange,
    is24Hour: Boolean,
    colors: AppColors,
    onRangeChange: (StatsRange) -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatsRange.entries.forEach { option ->
                val selected = option == range
                Box(
                    modifier = Modifier
                        .background(if (selected) colors.accent else colors.surfaceRaised, RoundedCornerShape(50))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onRangeChange(option) }
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

        if (stats == null || stats.range != range) {
            Spacer(Modifier.height(24.dp))
            Text("Working it out…", style = MaterialTheme.typography.bodyMedium, color = colors.textMuted)
            return@Column
        }

        Spacer(Modifier.height(28.dp))
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
                "Nothing tracked in this range yet. Finished sessions from the Timer and Stopwatch will show up here.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textMuted
            )
        }

        Spacer(Modifier.height(24.dp))
        val dayFormat = remember { SimpleDateFormat("EEE, d MMM", Locale.getDefault()) }
        val tiles = listOf(
            Tile("Daily average", formatDuration(stats.dailyAverageMillis), "over ${stats.daysInRange} days"),
            Tile("Streak", days(stats.currentStreak), "best ${days(stats.bestStreak)}"),
            Tile("Sessions", "${stats.sessionCount}", "avg ${formatDuration(stats.averageSessionMillis)}"),
            Tile("Longest session", formatDuration(stats.longestSessionMillis), null),
            Tile(
                "Best day",
                if (stats.bestDayStart != null) formatDuration(stats.bestDayMillis) else "–",
                stats.bestDayStart?.let { dayFormat.format(Date(it)) }
            ),
            Tile("Active days", "${stats.activeDays}", "of ${stats.daysInRange}"),
            Tile("Time paused", formatDuration(stats.pausedMillis), "inside sessions"),
            Tile("Pomodoros", "${stats.focusSessions}", "focus sessions")
        )
        tiles.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 10.dp)) {
                pair.forEach { TileView(it, colors, Modifier.weight(1f)) }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }

        if (stats.byCategory.isNotEmpty()) {
            Spacer(Modifier.height(22.dp))
            Text("BY CATEGORY", style = MaterialTheme.typography.labelLarge, color = colors.textSecondary)
            Spacer(Modifier.height(14.dp))
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
                        "${(category.fraction * 100).roundToInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.textMuted,
                        modifier = Modifier.width(48.dp).padding(start = 10.dp)
                    )
                }
            }
        }

        if (stats.byHour.any { it > 0 }) {
            Spacer(Modifier.height(26.dp))
            Text("TIME OF DAY", style = MaterialTheme.typography.labelLarge, color = colors.textSecondary)
            Spacer(Modifier.height(6.dp))
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
    val window = "previous ${stats.range.days} days"
    return when {
        stats.totalMillis == 0L && previous == 0L -> null
        previous == 0L -> "New this period · nothing in the $window" to true
        else -> {
            val change = ((stats.totalMillis - previous) * 100f / previous).roundToInt()
            when {
                change > 0 -> "Up $change% on the $window" to true
                change < 0 -> "Down ${-change}% on the $window" to false
                else -> "Same as the $window" to false
            }
        }
    }
}

private fun days(count: Int) = if (count == 1) "1 day" else "$count days"

private fun hourRange(hour: Int, is24Hour: Boolean): String {
    fun at(h: Int) = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, h % 24)
        set(Calendar.MINUTE, 0)
    }.timeInMillis
    return if (is24Hour) {
        "between ${formatClockTime(at(hour), true)} and ${formatClockTime(at(hour + 1), true)}"
    } else {
        "between ${formatClockTime(at(hour), false)} and ${formatClockTime(at(hour + 1), false)}"
    }
}

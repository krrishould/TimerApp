package com.krrishkumar.focustimer.ui.history

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.dp
import com.krrishkumar.focustimer.data.Segment
import com.krrishkumar.focustimer.data.SessionType
import com.krrishkumar.focustimer.data.TimelineSegment
import com.krrishkumar.focustimer.ui.theme.AppColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The whole day as one strip, midnight to midnight. Each running stretch is a block in its
 * category's colour, so gaps between blocks are pauses or time away. Breaks are faded.
 */
@Composable
fun DayTimeline(
    segments: List<TimelineSegment>,
    dayStart: Long,
    dayEnd: Long,
    colors: AppColors,
    now: Long = System.currentTimeMillis()
) {
    val span = (dayEnd - dayStart).toFloat()
    Column(Modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(40.dp)) {
            val radius = CornerRadius(8.dp.toPx())
            drawRoundRect(colors.surfaceRaised, cornerRadius = radius)

            // Hour grid every three hours, stronger at 6, 12 and 18.
            for (hour in 3..21 step 3) {
                val x = size.width * hour / 24f
                drawLine(
                    color = if (hour % 6 == 0) colors.border else colors.border.copy(alpha = 0.45f),
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = 1.dp.toPx()
                )
            }

            segments.forEach { segment ->
                val start = ((segment.start.coerceAtLeast(dayStart) - dayStart) / span).coerceIn(0f, 1f)
                val end = ((segment.end.coerceAtMost(dayEnd) - dayStart) / span).coerceIn(0f, 1f)
                if (end <= start) return@forEach
                val isBreak = segment.type == SessionType.POMODORO_BREAK
                val color = when {
                    isBreak -> colors.textMuted.copy(alpha = 0.45f)
                    segment.categoryColor != null -> Color(segment.categoryColor)
                    else -> colors.accent
                }
                // At least a hairline wide, so a two-minute stretch is still visible.
                val width = (end - start) * size.width
                drawRoundRect(
                    color = color,
                    topLeft = Offset(start * size.width, size.height * 0.18f),
                    size = Size(width.coerceAtLeast(2.dp.toPx()), size.height * 0.64f),
                    cornerRadius = CornerRadius(3.dp.toPx())
                )
            }

            if (now in dayStart until dayEnd) {
                val x = size.width * ((now - dayStart) / span)
                drawLine(colors.textPrimary, Offset(x, 0f), Offset(x, size.height), 1.5.dp.toPx(), StrokeCap.Round)
            }
        }
        Spacer(Modifier.height(6.dp))
        AxisLabels(
            labels = listOf("0", "3", "6", "9", "12", "15", "18", "21", "24"),
            fractions = (0..8).map { it / 8f },
            colors = colors
        )
    }
}

/**
 * One session across its own span: solid blocks where it ran, a dashed line where it was
 * paused. Zoomed to the session, so short pauses are easy to see.
 */
@Composable
fun SessionSpanBar(segments: List<Segment>, color: Color, colors: AppColors) {
    val first = segments.minOfOrNull { it.start } ?: return
    val last = segments.maxOfOrNull { it.end } ?: return
    val span = (last - first).coerceAtLeast(1L).toFloat()
    Canvas(Modifier.fillMaxWidth().height(30.dp)) {
        val mid = size.height / 2
        drawLine(
            color = colors.textMuted,
            start = Offset(0f, mid),
            end = Offset(size.width, mid),
            strokeWidth = 1.5.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))
        )
        segments.forEach { segment ->
            val start = (segment.start - first) / span * size.width
            val width = ((segment.end - segment.start) / span * size.width).coerceAtLeast(3.dp.toPx())
            drawRoundRect(
                color = color,
                topLeft = Offset(start, 0f),
                size = Size(width, size.height),
                cornerRadius = CornerRadius(6.dp.toPx())
            )
        }
    }
}

/** Places each label centred on its fraction of the width, kept inside the edges. */
@Composable
fun AxisLabels(labels: List<String>, fractions: List<Float>, colors: AppColors) {
    Layout(
        modifier = Modifier.fillMaxWidth(),
        content = {
            labels.forEach {
                Text(it, style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
            }
        }
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0)) }
        val width = constraints.maxWidth
        val height = placeables.maxOfOrNull { it.height } ?: 0
        layout(width, height) {
            placeables.forEachIndexed { index, placeable ->
                val centre = (fractions[index] * width).toInt()
                val x = (centre - placeable.width / 2).coerceIn(0, width - placeable.width)
                placeable.place(x, 0)
            }
        }
    }
}

// ------------------------------------------------------------- formatting

internal fun sessionTypeLabel(type: SessionType) = when (type) {
    SessionType.TIMER -> "Timer"
    SessionType.POMODORO_WORK -> "Pomodoro focus"
    SessionType.POMODORO_BREAK -> "Pomodoro break"
    SessionType.STOPWATCH -> "Stopwatch"
}

/** "1h 5m", "25m", or "40s" for anything under a minute. */
internal fun formatDuration(millis: Long): String {
    val totalSeconds = millis / 1000
    if (totalSeconds < 60) return "${totalSeconds}s"
    val totalMinutes = totalSeconds / 60
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}

internal fun formatClockTime(millis: Long, is24Hour: Boolean, withSeconds: Boolean = false): String {
    val pattern = when {
        is24Hour && withSeconds -> "HH:mm:ss"
        is24Hour -> "HH:mm"
        withSeconds -> "h:mm:ss a"
        else -> "h:mm a"
    }
    return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(millis))
}

package com.krrishkumar.focustimer.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.krrishkumar.focustimer.data.Category
import com.krrishkumar.focustimer.data.activeMillis
import com.krrishkumar.focustimer.ui.components.CategoryDot
import com.krrishkumar.focustimer.ui.components.CategoryPicker
import com.krrishkumar.focustimer.ui.theme.AppColors
import com.krrishkumar.focustimer.ui.theme.LocalAppColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Everything about one session: how long it ran and was paused, a bar of its span, and a
 * step-by-step account of each start, pause and resume.
 */
@Composable
fun SessionDetailDialog(
    detail: SessionDetail,
    is24Hour: Boolean,
    onChangeCategory: (Category?) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalAppColors.current
    val session = detail.session
    val segments = detail.segments
    var picking by remember { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }

    val start = segments.minOfOrNull { it.start } ?: session.startTimeMillis
    val end = segments.maxOfOrNull { it.end } ?: session.endTimeMillis
    val active = if (segments.isNotEmpty()) segments.activeMillis() else session.durationMillis
    val paused = (end - start - active).coerceAtLeast(0L)
    val barColor = session.categoryColor?.let { Color(it) } ?: colors.accent
    val dateFormat = remember { SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()) }
    // Under an hour, minutes alone would give most steps the same time; seconds tell them apart.
    val withSeconds = end - start < 60 * 60 * 1000L
    fun time(millis: Long) = formatClockTime(millis, is24Hour, withSeconds)

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .widthIn(max = 440.dp)
                .fillMaxWidth()
                .heightIn(max = 680.dp)
                .background(colors.surface, RoundedCornerShape(24.dp))
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CategoryDot(session.categoryColor, size = 12.dp)
                Text(
                    session.categoryName ?: sessionTypeLabel(session.type),
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                if (session.categoryName != null) {
                    "${sessionTypeLabel(session.type)} · ${dateFormat.format(Date(start))}"
                } else {
                    dateFormat.format(Date(start))
                },
                style = MaterialTheme.typography.labelMedium,
                color = colors.textMuted
            )

            Spacer(Modifier.height(22.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Figure("Active", formatDuration(active), colors, Modifier.weight(1f))
                Figure("Paused", if (paused > 0) formatDuration(paused) else "None", colors, Modifier.weight(1f))
                Figure("Pauses", "${(segments.size - 1).coerceAtLeast(0)}", colors, Modifier.weight(1f))
            }

            if (segments.isNotEmpty()) {
                Spacer(Modifier.height(22.dp))
                SessionSpanBar(segments, barColor, colors)
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(time(start), style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
                    Text(time(end), style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
                }

                Spacer(Modifier.height(22.dp))
                Text("WHAT HAPPENED", style = MaterialTheme.typography.labelLarge, color = colors.textSecondary)
                Spacer(Modifier.height(10.dp))
                segments.forEachIndexed { index, segment ->
                    Step(
                        time = time(segment.start),
                        text = "${if (index == 0) "Started" else "Resumed"} · ran ${formatDuration(segment.duration)}",
                        running = true,
                        dotColor = barColor,
                        colors = colors
                    )
                    val next = segments.getOrNull(index + 1)
                    if (next != null) {
                        Step(
                            time = time(segment.end),
                            text = "Paused for ${formatDuration(next.start - segment.end)}",
                            running = false,
                            dotColor = barColor,
                            colors = colors
                        )
                    }
                }
                Step(
                    time = time(end),
                    text = "Finished",
                    running = false,
                    dotColor = barColor,
                    colors = colors,
                    last = true
                )
            }

            Spacer(Modifier.height(22.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surfaceRaised, RoundedCornerShape(14.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { picking = true }
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Text("Category", style = MaterialTheme.typography.bodyLarge, color = colors.textSecondary, modifier = Modifier.weight(1f))
                CategoryDot(session.categoryColor, size = 10.dp)
                Spacer(Modifier.width(8.dp))
                Text(session.categoryName ?: "None", style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary)
            }

            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { if (confirmingDelete) onDelete() else confirmingDelete = true }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (confirmingDelete) "Tap again to delete" else "Delete session",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.danger
                )
            }
        }
    }

    if (picking) {
        CategoryPicker(
            title = "Session category",
            selectedId = session.categoryId,
            onSelect = {
                onChangeCategory(it)
                picking = false
            },
            onDismiss = { picking = false }
        )
    }
}

@Composable
private fun Figure(label: String, value: String, colors: AppColors, modifier: Modifier) {
    Column(
        modifier = modifier
            .background(colors.surfaceRaised, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 12.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = colors.textMuted)
        Spacer(Modifier.height(2.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary, maxLines = 1)
    }
}

/** One line of the account: a time, a marker (filled while running, hollow for a pause), and what happened. */
@Composable
private fun Step(
    time: String,
    text: String,
    running: Boolean,
    dotColor: Color,
    colors: AppColors,
    last: Boolean = false
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
        Text(
            time,
            style = MaterialTheme.typography.labelMedium,
            color = colors.textSecondary,
            modifier = Modifier.width(76.dp)
        )
        Box(
            Modifier
                .size(10.dp)
                .then(
                    when {
                        running -> Modifier.background(dotColor, CircleShape)
                        last -> Modifier.background(colors.textMuted, CircleShape)
                        else -> Modifier.border(1.5.dp, colors.textMuted, CircleShape)
                    }
                )
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (running) colors.textPrimary else colors.textSecondary
        )
    }
}

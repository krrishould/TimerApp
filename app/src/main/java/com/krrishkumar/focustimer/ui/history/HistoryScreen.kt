package com.krrishkumar.focustimer.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.krrishkumar.focustimer.data.SessionRepository
import com.krrishkumar.focustimer.data.SessionType
import com.krrishkumar.focustimer.data.WorkSession
import com.krrishkumar.focustimer.ui.theme.AppColors
import com.krrishkumar.focustimer.ui.theme.LocalAppColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(repository: SessionRepository, modifier: Modifier = Modifier) {
    val viewModel: HistoryViewModel = viewModel(factory = HistoryViewModel.Factory(repository))
    val state by viewModel.uiState.collectAsState()
    val colors = LocalAppColors.current

    LaunchedEffect(Unit) { viewModel.refresh() }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 28.dp),
        contentPadding = PaddingValues(top = 64.dp, bottom = 32.dp)
    ) {
        item {
            Text("TODAY", style = MaterialTheme.typography.labelLarge, color = colors.textSecondary)
            Spacer(Modifier.height(8.dp))
            Text(
                text = formatDuration(state.todayTotalMillis),
                style = MaterialTheme.typography.displayLarge.copy(fontSize = 52.sp),
                color = colors.textPrimary
            )
        }
        item {
            Spacer(Modifier.height(40.dp))
            Text("LAST 7 DAYS", style = MaterialTheme.typography.labelLarge, color = colors.textSecondary)
            Spacer(Modifier.height(20.dp))
            WeekBarChart(state.last7Days, colors)
        }
        item {
            Spacer(Modifier.height(40.dp))
            Text("RECENT SESSIONS", style = MaterialTheme.typography.labelLarge, color = colors.textSecondary)
            Spacer(Modifier.height(12.dp))
        }
        if (state.recentSessions.isEmpty()) {
            item {
                Text(
                    "No sessions yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textMuted,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            }
        }
        items(state.recentSessions) { session ->
            SessionRow(session, colors)
        }
    }
}

@Composable
private fun WeekBarChart(days: List<DayTotal>, colors: AppColors) {
    val maxMillis = (days.maxOfOrNull { it.totalMillis } ?: 0L).coerceAtLeast(60_000L)
    Row(
        modifier = Modifier.fillMaxWidth().height(110.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        days.forEach { day ->
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(0.5f),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    val fraction = (day.totalMillis.toFloat() / maxMillis.toFloat()).coerceIn(0.03f, 1f)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(fraction)
                            .background(
                                if (day.totalMillis > 0) colors.accent else colors.surfaceRaised,
                                RoundedCornerShape(6.dp)
                            )
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(day.label.take(1), style = MaterialTheme.typography.labelMedium, color = colors.textMuted)
            }
        }
    }
}

@Composable
private fun SessionRow(session: WorkSession, colors: AppColors) {
    val timeFormat = remember { SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surfaceRaised, RoundedCornerShape(16.dp))
            .padding(horizontal = 18.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(sessionLabel(session.type), style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary)
            Spacer(Modifier.height(2.dp))
            Text(timeFormat.format(Date(session.startTimeMillis)), style = MaterialTheme.typography.labelMedium, color = colors.textMuted)
        }
        Text(formatDuration(session.durationMillis), style = MaterialTheme.typography.titleMedium, color = colors.textSecondary)
    }
    Spacer(Modifier.height(10.dp))
}

private fun sessionLabel(type: SessionType) = when (type) {
    SessionType.TIMER -> "Timer"
    SessionType.POMODORO_WORK -> "Pomodoro focus"
    SessionType.POMODORO_BREAK -> "Pomodoro break"
    SessionType.STOPWATCH -> "Stopwatch"
}

private fun formatDuration(millis: Long): String {
    val totalMinutes = millis / 60000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}

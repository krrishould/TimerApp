package com.krrishkumar.focustimer.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.krrishkumar.focustimer.data.SessionRepository
import com.krrishkumar.focustimer.data.SessionType
import com.krrishkumar.focustimer.data.WorkSession
import com.krrishkumar.focustimer.ui.components.NameDialog
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
    var editing by remember { mutableStateOf<WorkSession?>(null) }

    LaunchedEffect(Unit) { viewModel.refresh() }

    val openDay = state.selectedDay
    val listedSessions = if (openDay != null) state.selectedDaySessions else state.recentSessions

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
            Text("THIS WEEK", style = MaterialTheme.typography.labelLarge, color = colors.textSecondary)
            Spacer(Modifier.height(20.dp))
            WeekBarChart(
                days = state.week,
                selectedDayStart = openDay?.dayStart,
                colors = colors,
                onDayClick = { day ->
                    if (openDay?.dayStart == day.dayStart) viewModel.clearSelectedDay()
                    else viewModel.selectDay(day)
                }
            )
        }
        item {
            Spacer(Modifier.height(36.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = openDay?.name?.uppercase() ?: "RECENT SESSIONS",
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.textSecondary
                    )
                    if (openDay != null) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = formatDuration(openDay.totalMillis),
                            style = MaterialTheme.typography.titleLarge,
                            color = colors.textPrimary
                        )
                    }
                }
                if (openDay != null) {
                    Text(
                        text = "Clear",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.accent,
                        modifier = Modifier
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { viewModel.clearSelectedDay() }
                            .padding(8.dp)
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        if (listedSessions.isEmpty()) {
            item {
                Text(
                    text = if (openDay != null) "Nothing tracked on this day" else "No sessions yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textMuted,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            }
        }
        items(listedSessions, key = { it.id }) { session ->
            SessionRow(session, colors, onClick = { editing = session })
        }
    }

    editing?.let { session ->
        NameDialog(
            title = "Name this session",
            placeholder = defaultLabel(session.type),
            initialValue = session.label ?: "",
            onSave = { label ->
                viewModel.setLabel(session, label)
                editing = null
            },
            onDismiss = { editing = null },
            onDelete = {
                viewModel.deleteSession(session)
                editing = null
            }
        )
    }
}

@Composable
private fun WeekBarChart(
    days: List<DayTotal>,
    selectedDayStart: Long?,
    colors: AppColors,
    onDayClick: (DayTotal) -> Unit
) {
    val maxMillis = (days.maxOfOrNull { it.totalMillis } ?: 0L).coerceAtLeast(60_000L)
    Row(
        modifier = Modifier.fillMaxWidth().height(124.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        days.forEach { day ->
            val selected = day.dayStart == selectedDayStart
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(horizontal = 2.dp)
                    .background(
                        if (selected) colors.surfaceRaised else androidx.compose.ui.graphics.Color.Transparent,
                        RoundedCornerShape(12.dp)
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onDayClick(day) }
                    .padding(vertical = 6.dp),
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
                Text(
                    text = day.initial,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (day.isToday) FontWeight.Bold else FontWeight.Medium,
                    color = if (day.isToday) colors.accent else colors.textMuted
                )
            }
        }
    }
}

@Composable
private fun SessionRow(session: WorkSession, colors: AppColors, onClick: () -> Unit) {
    val timeFormat = remember { SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surfaceRaised, RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 18.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = session.label ?: defaultLabel(session.type),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.textPrimary
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = timeFormat.format(Date(session.startTimeMillis)),
                style = MaterialTheme.typography.labelMedium,
                color = colors.textMuted
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = formatDuration(session.durationMillis),
            style = MaterialTheme.typography.titleMedium,
            color = colors.textSecondary
        )
    }
    Spacer(Modifier.height(10.dp))
}

private fun defaultLabel(type: SessionType) = when (type) {
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

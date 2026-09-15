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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.krrishkumar.focustimer.AlltimeApp
import com.krrishkumar.focustimer.data.SessionRepository
import com.krrishkumar.focustimer.data.SessionType
import com.krrishkumar.focustimer.data.WorkSession
import com.krrishkumar.focustimer.ui.components.CategoryDot
import com.krrishkumar.focustimer.ui.theme.AppColors
import com.krrishkumar.focustimer.ui.theme.LocalAppColors
import java.util.Calendar

@Composable
fun HistoryScreen(repository: SessionRepository, modifier: Modifier = Modifier) {
    val viewModel: HistoryViewModel = viewModel(factory = HistoryViewModel.Factory(repository))
    val state by viewModel.uiState.collectAsState()
    val colors = LocalAppColors.current
    val context = LocalContext.current
    val is24Hour = remember { (context.applicationContext as AlltimeApp).preferences.is24HourClock() }

    // Sessions are written from the timer and stopwatch while this screen is closed.
    LaunchedEffect(Unit) { viewModel.refresh() }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 24.dp),
        contentPadding = PaddingValues(top = 60.dp, bottom = 32.dp)
    ) {
        item {
            TabSwitch(state.tab, colors, onSelect = viewModel::selectTab)
            Spacer(Modifier.height(28.dp))
        }

        if (state.tab == HistoryTab.STATS) {
            item {
                StatsView(
                    stats = state.stats,
                    period = state.statsPeriod,
                    is24Hour = is24Hour,
                    colors = colors,
                    onPeriodChange = viewModel::setStatsPeriod,
                    onPrevious = viewModel::previousStatsPeriod,
                    onNext = viewModel::nextStatsPeriod
                )
            }
        } else {
            item {
                WeekHeader(
                    label = state.weekLabel,
                    canGoForward = state.canGoForward,
                    colors = colors,
                    onBack = viewModel::previousWeek,
                    onForward = viewModel::nextWeek
                )
                Spacer(Modifier.height(16.dp))
                WeekBarChart(
                    days = state.week,
                    selectedDayStart = state.selectedDay?.dayStart,
                    colors = colors,
                    onDayClick = viewModel::selectDay
                )
            }

            state.selectedDay?.let { day ->
                item {
                    val dayEnd = remember(day.dayStart) {
                        Calendar.getInstance().apply {
                            timeInMillis = day.dayStart
                            add(Calendar.DAY_OF_YEAR, 1)
                        }.timeInMillis
                    }
                    Spacer(Modifier.height(32.dp))
                    Text(
                        text = if (day.isToday) "TODAY" else day.name.uppercase(),
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.textSecondary
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = formatDuration(day.totalMillis),
                        style = MaterialTheme.typography.displayLarge.copy(fontSize = 46.sp),
                        color = colors.textPrimary
                    )
                    Spacer(Modifier.height(20.dp))
                    DayTimeline(
                        segments = state.dayTimeline,
                        dayStart = day.dayStart,
                        dayEnd = dayEnd,
                        colors = colors
                    )
                    if (state.dayTimeline.any { it.type == SessionType.POMODORO_BREAK }) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Faded blocks are breaks. Gaps are pauses or time away.",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textMuted
                        )
                    }
                    Spacer(Modifier.height(28.dp))
                    Text("SESSIONS", style = MaterialTheme.typography.labelLarge, color = colors.textSecondary)
                    Spacer(Modifier.height(12.dp))
                    if (state.daySessions.isEmpty()) {
                        Text(
                            text = if (day.isToday) "Nothing tracked yet today" else "Nothing tracked on this day",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textMuted,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }
                items(state.daySessions, key = { it.id }) { session ->
                    SessionRow(session, is24Hour, colors, onClick = { viewModel.openSession(session) })
                }
            }
        }
    }

    state.detail?.let { detail ->
        SessionDetailDialog(
            detail = detail,
            is24Hour = is24Hour,
            onChangeCategory = { viewModel.setSessionCategory(detail.session, it) },
            onDelete = { viewModel.deleteSession(detail.session) },
            onDismiss = viewModel::closeSession
        )
    }
}

@Composable
private fun TabSwitch(selected: HistoryTab, colors: AppColors, onSelect: (HistoryTab) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surfaceRaised, RoundedCornerShape(16.dp))
            .padding(4.dp)
    ) {
        listOf(HistoryTab.DAYS to "Days", HistoryTab.STATS to "Stats").forEach { (tab, label) ->
            val isSelected = tab == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(if (isSelected) colors.surface else Color.Transparent, RoundedCornerShape(12.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onSelect(tab) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isSelected) colors.textPrimary else colors.textMuted
                )
            }
        }
    }
}

@Composable
private fun WeekHeader(
    label: String,
    canGoForward: Boolean,
    colors: AppColors,
    onBack: () -> Unit,
    onForward: () -> Unit
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = colors.textSecondary,
            modifier = Modifier.weight(1f)
        )
        ArrowButton("‹", enabled = true, colors = colors, onClick = onBack)
        ArrowButton("›", enabled = canGoForward, colors = colors, onClick = onForward)
    }
}

@Composable
internal fun ArrowButton(glyph: String, enabled: Boolean, colors: AppColors, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            glyph,
            style = MaterialTheme.typography.headlineMedium,
            color = if (enabled) colors.textPrimary else colors.border
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
                        if (selected) colors.surfaceRaised else Color.Transparent,
                        RoundedCornerShape(12.dp)
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        enabled = !day.isFuture
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
                    color = when {
                        day.isToday -> colors.accent
                        day.isFuture -> colors.border
                        else -> colors.textMuted
                    }
                )
            }
        }
    }
}

@Composable
private fun SessionRow(session: WorkSession, is24Hour: Boolean, colors: AppColors, onClick: () -> Unit) {
    val paused = session.pausedMillis
    val subtitle = buildString {
        append(formatClockTime(session.startTimeMillis, is24Hour))
        append(" – ")
        append(formatClockTime(session.endTimeMillis, is24Hour))
        if (session.categoryName != null) append(" · ${sessionTypeLabel(session.type)}")
        if (paused >= 60_000L) append(" · paused ${formatDuration(paused)}")
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .background(colors.surfaceRaised, RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CategoryDot(session.categoryColor, size = 10.dp)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = session.categoryName ?: sessionTypeLabel(session.type),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = colors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = formatDuration(session.durationMillis),
            style = MaterialTheme.typography.titleMedium,
            color = colors.textSecondary
        )
    }
}

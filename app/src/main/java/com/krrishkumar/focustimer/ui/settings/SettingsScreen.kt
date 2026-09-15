package com.krrishkumar.focustimer.ui.settings

import android.app.Activity
import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.krrishkumar.focustimer.AlltimeApp
import com.krrishkumar.focustimer.engine.PomodoroEndBehavior
import kotlinx.coroutines.launch
import com.krrishkumar.focustimer.ui.components.TEXT_SIZE_STEPS
import com.krrishkumar.focustimer.ui.theme.AppColors
import com.krrishkumar.focustimer.ui.theme.LocalAppColors

/** Minutes of focus before an early break is offered; 0 means straight away. */
private val CLAIM_BREAK_OPTIONS = listOf(0, 5, 10, 15, 20)

/**
 * Everything that's set once and left alone. The things reached for often (focus mode,
 * the theme, categories) live in the menu instead.
 */
@Composable
fun SettingsScreen(
    isDark: Boolean,
    onThemeSelect: (Boolean) -> Unit,
    is24Hour: Boolean,
    on24HourSelect: (Boolean) -> Unit,
    showSeconds: Boolean,
    onShowSecondsChange: (Boolean) -> Unit,
    textSizeLevel: Int,
    onTextSizeLevelChange: (Int) -> Unit,
    endBehavior: PomodoroEndBehavior,
    onEndBehaviorChange: (PomodoroEndBehavior) -> Unit,
    claimBreakAfterMinutes: Int,
    onClaimBreakAfterChange: (Int) -> Unit,
    keepIncompleteCycles: Boolean,
    onKeepIncompleteCyclesChange: (Boolean) -> Unit,
    logStopwatchOnPause: Boolean,
    onLogStopwatchOnPauseChange: (Boolean) -> Unit,
    focusGestureEnabled: Boolean,
    onFocusGestureChange: (Boolean) -> Unit,
    onBack: () -> Unit
) {
    val colors = LocalAppColors.current
    BackHandler(onBack = onBack)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Box(
                modifier = Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onBack
                    )
                    .semantics { contentDescription = "Back" }
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Text("‹", style = MaterialTheme.typography.headlineMedium, color = colors.textPrimary)
            }
            Text("Settings", style = MaterialTheme.typography.titleLarge, color = colors.textPrimary)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Capped so the rows don't stretch edge to edge on a tablet.
            Column(Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(bottom = 40.dp)) {
                SyncSection(colors)

                Section("APPEARANCE", colors)
                Label("Theme", colors)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Option("Light", !isDark, colors, Modifier.weight(1f)) { onThemeSelect(false) }
                    Option("Dark", isDark, colors, Modifier.weight(1f)) { onThemeSelect(true) }
                }
                Spacer(Modifier.height(18.dp))
                Label("Text size", colors)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TEXT_SIZE_STEPS.indices.forEach { index ->
                        val selected = index == textSizeLevel
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(if (selected) colors.accent else colors.surfaceRaised, RoundedCornerShape(12.dp))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { onTextSizeLevelChange(index) }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "A",
                                fontSize = (11 + index * 3).sp,
                                color = if (selected) colors.onAccent else colors.textSecondary
                            )
                        }
                    }
                }
                Hint("How large the time appears on the Timer, Stopwatch and Clock.", colors)

                Section("CLOCK", colors)
                Label("Time format", colors)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Option("12-hour", !is24Hour, colors, Modifier.weight(1f)) { on24HourSelect(false) }
                    Option("24-hour", is24Hour, colors, Modifier.weight(1f)) { on24HourSelect(true) }
                }
                Spacer(Modifier.height(10.dp))
                Toggle("Show seconds", null, showSeconds, onShowSecondsChange, colors)

                Section("POMODORO", colors)
                Toggle(
                    label = "Start break automatically",
                    description = if (endBehavior == PomodoroEndBehavior.AUTO_BREAK) {
                        "When focus ends, the break timer starts on its own."
                    } else {
                        "When focus ends, the timer keeps counting up until you claim your break, and the extra time counts as work."
                    },
                    checked = endBehavior == PomodoroEndBehavior.AUTO_BREAK,
                    onCheckedChange = { on ->
                        onEndBehaviorChange(if (on) PomodoroEndBehavior.AUTO_BREAK else PomodoroEndBehavior.OVERTIME)
                    },
                    colors = colors
                )
                Spacer(Modifier.height(18.dp))
                Label("Claim a break after", colors)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CLAIM_BREAK_OPTIONS.forEach { minutes ->
                        Option(
                            if (minutes == 0) "Any" else "${minutes}m",
                            minutes == claimBreakAfterMinutes,
                            colors,
                            Modifier.weight(1f)
                        ) { onClaimBreakAfterChange(minutes) }
                    }
                }
                Hint(
                    if (claimBreakAfterMinutes == 0) "You can take your break at any point in a focus session."
                    else "You can take your break early once you've focused for $claimBreakAfterMinutes minutes.",
                    colors
                )

                Section("TRACKING", colors)
                Toggle(
                    "Keep incomplete cycles",
                    "A timer you reset part-way through is still recorded, once it has run a minute.",
                    keepIncompleteCycles, onKeepIncompleteCyclesChange, colors
                )
                Spacer(Modifier.height(14.dp))
                Toggle(
                    "Log stopwatch on pause",
                    "Pausing records the run, so walking away without finishing doesn't lose it.",
                    logStopwatchOnPause, onLogStopwatchOnPauseChange, colors
                )

                Section("FOCUS MODE", colors)
                Toggle(
                    "Double-tap to enter",
                    "Double-tap anywhere on the Timer, Stopwatch or Clock to show only the time. A single tap then pauses or resumes, and another double-tap exits.",
                    focusGestureEnabled, onFocusGestureChange, colors
                )
            }
        }
    }
}

@Composable
private fun Section(title: String, colors: AppColors) {
    Spacer(Modifier.height(30.dp))
    Text(title, style = MaterialTheme.typography.labelLarge, color = colors.accent)
    Spacer(Modifier.height(14.dp))
}

@Composable
private fun Label(text: String, colors: AppColors) {
    Text(text, style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary)
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun Hint(text: String, colors: AppColors) {
    Spacer(Modifier.height(8.dp))
    Text(text, style = MaterialTheme.typography.bodyMedium, color = colors.textMuted)
}

/** A labelled switch, with an optional line underneath explaining what it does. */
@Composable
private fun Toggle(
    label: String,
    description: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    colors: AppColors
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f).padding(end = 16.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary)
            if (description != null) {
                Spacer(Modifier.height(4.dp))
                Text(description, style = MaterialTheme.typography.bodyMedium, color = colors.textMuted)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.onAccent,
                checkedTrackColor = colors.accent,
                uncheckedThumbColor = colors.textMuted,
                uncheckedTrackColor = colors.surfaceRaised,
                uncheckedBorderColor = colors.border
            )
        )
    }
}

@Composable
private fun Option(
    label: String,
    selected: Boolean,
    colors: AppColors,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .background(if (selected) colors.accent else colors.surfaceRaised, RoundedCornerShape(14.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 13.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (selected) colors.onAccent else colors.textSecondary,
            style = MaterialTheme.typography.titleMedium
        )
    }
}

/** Sign in once on each device with the same Google account and they share their data. */
@Composable
private fun SyncSection(colors: AppColors) {
    val context = LocalContext.current
    val sync = (context.applicationContext as AlltimeApp).syncManager
    val state by sync.state.collectAsState()
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    Section("SYNC", colors)
    when {
        !state.configured -> {
            Text("Sync isn't set up in this build", style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary)
            Hint("It needs the Firebase project's google-services.json added to the app before anyone can sign in.", colors)
        }
        !state.signedIn -> {
            Text("Keep your devices in step", style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary)
            Hint("Sign in with the same Google account on your phone and tablet to share history, categories and settings. Text size stays per device.", colors)
            Spacer(Modifier.height(14.dp))
            ActionButton(
                label = if (busy) "Signing in…" else "Sign in with Google",
                filled = true,
                enabled = !busy,
                colors = colors,
                modifier = Modifier.fillMaxWidth()
            ) {
                val activity = context as? Activity ?: return@ActionButton
                busy = true
                message = null
                scope.launch {
                    sync.signIn(activity).onFailure { message = it.message }
                    busy = false
                }
            }
        }
        else -> {
            Text(state.email ?: "Signed in", style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary)
            Spacer(Modifier.height(4.dp))
            val status = when {
                state.syncing -> "Syncing…"
                state.error != null -> "Couldn't sync: ${state.error}"
                state.lastSyncedAt != null -> "Synced " + DateUtils.getRelativeTimeSpanString(
                    state.lastSyncedAt!!, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS
                ).toString().lowercase()
                else -> "Waiting to sync"
            }
            Text(
                status,
                style = MaterialTheme.typography.bodyMedium,
                color = if (state.error != null) colors.danger else colors.textMuted
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionButton("Sync now", filled = false, enabled = !state.syncing, colors = colors, modifier = Modifier.weight(1f)) {
                    sync.syncNow()
                }
                ActionButton("Sign out", filled = false, enabled = true, colors = colors, modifier = Modifier.weight(1f)) {
                    sync.signOut()
                }
            }
            Hint("Signing out keeps everything on this device; it just stops sharing.", colors)
        }
    }
    message?.let {
        Spacer(Modifier.height(8.dp))
        Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.danger)
    }
}

@Composable
private fun ActionButton(
    label: String,
    filled: Boolean,
    enabled: Boolean,
    colors: AppColors,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .background(if (filled) colors.accent else colors.surfaceRaised, RoundedCornerShape(14.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .padding(vertical = 13.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            color = when {
                !enabled -> colors.textMuted
                filled -> colors.onAccent
                else -> colors.textSecondary
            }
        )
    }
}

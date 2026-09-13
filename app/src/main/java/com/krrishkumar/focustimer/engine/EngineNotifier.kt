package com.krrishkumar.focustimer.engine

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import com.krrishkumar.focustimer.MainActivity
import com.krrishkumar.focustimer.R

enum class Alert { TIMER_DONE, FOCUS_DONE_OVERTIME, FOCUS_DONE_BREAK, BREAK_DONE }

/**
 * The ongoing notifications for a running timer or stopwatch, and the one-off alert when a
 * period ends. The ongoing ones use the system chronometer, so the time keeps counting in
 * the shade without the app having to update it every second.
 */
class EngineNotifier(private val context: Context) {
    private val manager = NotificationManagerCompat.from(context)

    init {
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_RUNNING, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName("Running timers")
                .setDescription("Shows a running timer or stopwatch, with controls.")
                .setShowBadge(false)
                .build()
        )
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ALERTS, NotificationManagerCompat.IMPORTANCE_HIGH)
                .setName("Timer alerts")
                .setDescription("When a timer, focus session or break ends.")
                // The app plays its own chime and buzz, which work even with notifications off.
                .setSound(null, null)
                .setVibrationEnabled(false)
                .build()
        )
    }

    fun showTimer(s: TimerModel) {
        if (!s.isActive) {
            manager.cancel(ID_TIMER)
            return
        }
        val now = System.currentTimeMillis()
        val phaseLabel = when {
            !s.pomodoroMode -> "Timer"
            s.inOvertime -> "Overtime"
            s.phase == TimerPhase.WORK -> "Focus"
            else -> "Break"
        }
        val builder = ongoing(TAB_TIMER).setContentTitle(s.activityName ?: phaseLabel)

        when {
            s.endsAt != null -> builder
                .setContentText(if (s.activityName != null) phaseLabel else "Running")
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setWhen(s.endsAt)
                .setShowWhen(true)
                .addAction(action("Pause", ControlReceiver.ACTION_TIMER_TOGGLE))
                .addAction(action("Reset", ControlReceiver.ACTION_TIMER_RESET))

            s.overtimeSince != null -> builder
                .setContentText("Over time. Claim your break when you're ready.")
                .setUsesChronometer(true)
                .setWhen(s.overtimeSince)
                .setShowWhen(true)
                .addAction(action("Claim break", ControlReceiver.ACTION_TIMER_CLAIM_BREAK))
                .addAction(action("Pause", ControlReceiver.ACTION_TIMER_TOGGLE))

            s.inOvertime -> builder
                .setContentText("Paused · ${formatClock(s.overtimeAt(now))} over")
                .setShowWhen(false)
                .addAction(action("Claim break", ControlReceiver.ACTION_TIMER_CLAIM_BREAK))
                .addAction(action("Resume", ControlReceiver.ACTION_TIMER_TOGGLE))

            else -> builder
                .setContentText("Paused · ${formatClock(s.remainingAt(now))} left")
                .setShowWhen(false)
                .addAction(action("Resume", ControlReceiver.ACTION_TIMER_TOGGLE))
                .addAction(action("Reset", ControlReceiver.ACTION_TIMER_RESET))
        }
        post(ID_TIMER, builder.build())
    }

    fun showStopwatch(s: StopwatchModel) {
        if (!s.isRunning && s.pausedElapsed == 0L) {
            manager.cancel(ID_STOPWATCH)
            return
        }
        val builder = ongoing(TAB_STOPWATCH).setContentTitle(s.activityName ?: "Stopwatch")
        if (s.runningSince != null) {
            builder
                .setContentText("Running")
                .setUsesChronometer(true)
                .setWhen(s.runningSince)
                .setShowWhen(true)
                .addAction(action("Pause", ControlReceiver.ACTION_STOPWATCH_TOGGLE))
        } else {
            builder
                .setContentText("Paused · ${formatClock(s.pausedElapsed)}")
                .setShowWhen(false)
                .addAction(action("Resume", ControlReceiver.ACTION_STOPWATCH_TOGGLE))
        }
        builder.addAction(action("Finish", ControlReceiver.ACTION_STOPWATCH_FINISH))
        post(ID_STOPWATCH, builder.build())
    }

    fun showAlert(alert: Alert, s: TimerModel) {
        val (title, text) = when (alert) {
            Alert.TIMER_DONE ->
                "Time's up" to "Your ${s.timerMinutes}-minute timer has finished."
            Alert.FOCUS_DONE_OVERTIME ->
                "Focus session complete" to "Take your break when you're ready. Extra time counts as work."
            Alert.FOCUS_DONE_BREAK ->
                "Focus session complete" to "Your ${s.breakMinutes}-minute break has started."
            Alert.BREAK_DONE ->
                "Break's over" to "Ready for the next focus session."
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_stat_alltime)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openApp(TAB_TIMER))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
        post(ID_ALERT, notification)
    }

    private fun ongoing(tab: Int) = NotificationCompat.Builder(context, CHANNEL_RUNNING)
        .setSmallIcon(R.drawable.ic_stat_alltime)
        .setContentIntent(openApp(tab))
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setSilent(true)
        .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

    private fun openApp(tab: Int): PendingIntent = PendingIntent.getActivity(
        context,
        tab,
        Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_TAB, tab)
            .addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            ),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun action(title: String, action: String): NotificationCompat.Action {
        val intent = PendingIntent.getBroadcast(
            context,
            action.hashCode(),
            Intent(context, ControlReceiver::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Action.Builder(null as IconCompat?, title, intent).build()
    }

    // Without the permission there's simply nothing shown: the alarm still ends the period
    // and the chime still plays.
    @SuppressLint("MissingPermission")
    private fun post(id: Int, notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        runCatching { manager.notify(id, notification) }
    }

    private companion object {
        const val CHANNEL_RUNNING = "running"
        const val CHANNEL_ALERTS = "alerts"
        const val ID_TIMER = 1
        const val ID_STOPWATCH = 2
        const val ID_ALERT = 3
        const val TAB_TIMER = 0
        const val TAB_STOPWATCH = 1
    }
}

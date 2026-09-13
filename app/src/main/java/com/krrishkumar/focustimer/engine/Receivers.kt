package com.krrishkumar.focustimer.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import com.krrishkumar.focustimer.AlltimeApp

/** How long a receiver keeps its process alive after acting, so the chime and history write finish. */
private const val LINGER_MS = 2000L

private fun BroadcastReceiver.runAndLinger(work: () -> Unit) {
    val pending = goAsync()
    try {
        work()
    } finally {
        Handler(Looper.getMainLooper()).postDelayed({ pending.finish() }, LINGER_MS)
    }
}

/** Fires when a countdown reaches zero, whether or not the app is still running. */
class TimerAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as AlltimeApp
        runAndLinger { app.timerEngine.onAlarm() }
    }
}

/** The buttons on the timer and stopwatch notifications. */
class ControlReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as AlltimeApp
        runAndLinger {
            when (intent.action) {
                ACTION_TIMER_TOGGLE -> app.timerEngine.toggleRunning()
                ACTION_TIMER_RESET -> app.timerEngine.reset()
                ACTION_TIMER_CLAIM_BREAK -> app.timerEngine.claimBreak()
                ACTION_STOPWATCH_TOGGLE -> app.stopwatchEngine.toggleRunning()
                ACTION_STOPWATCH_FINISH -> app.stopwatchEngine.finishAndLog()
            }
        }
    }

    companion object {
        const val ACTION_TIMER_TOGGLE = "com.krrishkumar.focustimer.TIMER_TOGGLE"
        const val ACTION_TIMER_RESET = "com.krrishkumar.focustimer.TIMER_RESET"
        const val ACTION_TIMER_CLAIM_BREAK = "com.krrishkumar.focustimer.TIMER_CLAIM_BREAK"
        const val ACTION_STOPWATCH_TOGGLE = "com.krrishkumar.focustimer.STOPWATCH_TOGGLE"
        const val ACTION_STOPWATCH_FINISH = "com.krrishkumar.focustimer.STOPWATCH_FINISH"
    }
}

/** Alarms and notifications don't survive a reboot or an app update, so put them back. */
class RestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as AlltimeApp
        runAndLinger {
            app.timerEngine.resync()
            app.stopwatchEngine.resync()
        }
    }
}

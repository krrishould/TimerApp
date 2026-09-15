package com.krrishkumar.focustimer

import android.app.Application
import com.krrishkumar.focustimer.data.AppPreferences
import com.krrishkumar.focustimer.data.CategoryStore
import com.krrishkumar.focustimer.data.SessionRepository
import com.krrishkumar.focustimer.engine.AlarmScheduler
import com.krrishkumar.focustimer.engine.Alerts
import com.krrishkumar.focustimer.engine.EngineNotifier
import com.krrishkumar.focustimer.engine.StopwatchEngine
import com.krrishkumar.focustimer.engine.TimerEngine
import com.krrishkumar.focustimer.sync.SyncManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Owns the pieces that must outlive any one screen: the timer and stopwatch engines are
 * reached from the UI, the notification buttons and the alarm alike, and must all be the
 * same instance.
 */
class AlltimeApp : Application() {

    /** For work that has to finish even as the UI goes away, like writing a session. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val repository by lazy { SessionRepository(this) }
    val preferences by lazy { AppPreferences(this) }
    private val notifier by lazy { EngineNotifier(this) }

    /** Set by MainActivity while it's on screen. */
    @Volatile
    var isInForeground = false

    val categoryStore by lazy {
        CategoryStore(repository, appScope).apply {
            // A deleted category can't stay selected for the next session.
            onDeleted = { id ->
                timerEngine.clearCategory(id)
                stopwatchEngine.clearCategory(id)
            }
        }
    }

    val timerEngine by lazy {
        TimerEngine(
            context = this,
            repository = repository,
            preferences = preferences,
            alarms = AlarmScheduler(this),
            notifier = notifier,
            alerts = Alerts(this),
            scope = appScope,
            isAppInForeground = { isInForeground }
        )
    }

    val stopwatchEngine by lazy {
        StopwatchEngine(this, repository, preferences, notifier, appScope)
    }

    val syncManager by lazy { SyncManager(this, repository, preferences, appScope) }

    override fun onCreate() {
        super.onCreate()
        // Started with the process rather than the screen, so a session the alarm logs while
        // the app is closed is still sent to the other device.
        syncManager.start()
    }
}

package com.krrishkumar.focustimer.engine

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/** Arms the system alarm that ends a countdown, so it fires even with the app closed. */
class AlarmScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    private var synced = false
    private var armedFor: Long? = null

    /** Arms the alarm for [endsAt], or clears it when nothing is counting down. */
    fun sync(endsAt: Long?, force: Boolean = false) {
        if (synced && !force && endsAt == armedFor) return
        synced = true
        armedFor = endsAt

        val intent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, TimerAlarmReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        if (endsAt == null) {
            alarmManager.cancel(intent)
            return
        }
        // Exact and allowed while idle, so the period ends on time with the screen off.
        // Without exact-alarm access it still fires, just possibly a little late.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endsAt, intent)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endsAt, intent)
        }
    }

    private companion object {
        const val REQUEST_CODE = 100
    }
}

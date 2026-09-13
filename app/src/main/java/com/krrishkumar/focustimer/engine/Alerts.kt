package com.krrishkumar.focustimer.engine

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * The chime and buzz when a period ends. Played directly rather than through the
 * notification, so it still sounds when notifications are turned off for the app.
 */
class Alerts(private val context: Context) {
    private val handler = Handler(Looper.getMainLooper())

    fun play() {
        runCatching {
            val tone = ToneGenerator(AudioManager.STREAM_ALARM, 80)
            tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 600)
            handler.postDelayed({ runCatching { tone.release() } }, 1200)
        }
        runCatching {
            vibrator()?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 250, 150, 250), -1))
        }
    }

    private fun vibrator(): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            context.getSystemService(Vibrator::class.java)
        }
}

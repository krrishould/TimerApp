package com.krrishkumar.focustimer.data

enum class SessionType {
    TIMER,
    POMODORO_WORK,
    POMODORO_BREAK,
    STOPWATCH
}

data class WorkSession(
    val id: Long = 0,
    val type: SessionType,
    val startTimeMillis: Long,
    val durationMillis: Long
)

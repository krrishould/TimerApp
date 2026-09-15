package com.krrishkumar.focustimer.data

enum class SessionType {
    TIMER,
    POMODORO_WORK,
    POMODORO_BREAK,
    STOPWATCH
}

/** One stretch of a session during which the clock was actually running. */
data class Segment(val start: Long, val end: Long) {
    val duration: Long get() = (end - start).coerceAtLeast(0L)
}

fun List<Segment>.activeMillis(): Long = sumOf { it.duration }

/** A user-made label for sessions, picked on the Timer and Stopwatch and used in stats. */
data class Category(
    val id: Long,
    val name: String,
    /** ARGB colour from [CategoryPalette]. */
    val color: Int,
    /** Deleted categories are only hidden, so past sessions keep their name. */
    val archived: Boolean = false
)

data class WorkSession(
    val id: Long = 0,
    val type: SessionType,
    val startTimeMillis: Long,
    /** Time the clock actually ran: the segments added up, pauses left out. */
    val durationMillis: Long,
    /** When the last segment ended. With pauses this is later than start + duration. */
    val endTimeMillis: Long = startTimeMillis + durationMillis,
    val categoryId: Long? = null,
    val categoryName: String? = null,
    val categoryColor: Int? = null
) {
    val pausedMillis: Long get() = (endTimeMillis - startTimeMillis - durationMillis).coerceAtLeast(0L)
}

/** A running segment placed on a day timeline, with what's needed to colour it. */
data class TimelineSegment(
    val sessionId: Long,
    val type: SessionType,
    val categoryColor: Int?,
    val start: Long,
    val end: Long
)

/** Colours handed to new categories in turn; chosen to read on both dark and light grounds. */
object CategoryPalette {
    val colors: List<Int> = listOf(
        0xFF8B7CFF, 0xFF2BB6A3, 0xFFF0A83C, 0xFFF06A8A,
        0xFF4EA8F0, 0xFF8CC152, 0xFFF07A3C, 0xFFB57CE6
    ).map { it.toInt() }
}

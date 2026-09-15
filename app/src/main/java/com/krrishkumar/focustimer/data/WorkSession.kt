package com.krrishkumar.focustimer.data

import java.util.UUID

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

/** An id shared by every device, unlike the local row id, which each database hands out itself. */
fun newSyncId(): String = UUID.randomUUID().toString().replace("-", "")

/** A user-made label for sessions, picked on the Timer and Stopwatch and used in stats. */
data class Category(
    val id: Long,
    val name: String,
    /** ARGB colour from [CategoryPalette]. */
    val color: Int,
    /** Deleted categories are only hidden, so past sessions keep their name. */
    val archived: Boolean = false,
    val syncId: String = ""
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
    val categoryColor: Int? = null,
    val syncId: String = ""
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

/** A category as it travels between devices. */
data class SyncCategory(
    val syncId: String,
    val name: String,
    val color: Int,
    val archived: Boolean,
    val updatedAt: Long
)

/**
 * A session as it travels between devices: its category by shared id, its segments inline,
 * and a tombstone flag so a deletion reaches the other device too.
 */
data class SyncSession(
    val syncId: String,
    val type: SessionType,
    val segments: List<Segment>,
    val categorySyncId: String?,
    val updatedAt: Long,
    val deleted: Boolean
)

/** Colours handed to new categories in turn; chosen to read on both dark and light grounds. */
object CategoryPalette {
    val colors: List<Int> = listOf(
        0xFF8B7CFF, 0xFF2BB6A3, 0xFFF0A83C, 0xFFF06A8A,
        0xFF4EA8F0, 0xFF8CC152, 0xFFF07A3C, 0xFFB57CE6
    ).map { it.toInt() }
}

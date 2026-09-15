package com.krrishkumar.focustimer.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Every database call, moved off the main thread. */
class SessionRepository(context: Context) {
    private val db = SessionDatabaseHelper(context)

    /** Records a finished session; returns its id. Empty segment lists are ignored. */
    suspend fun logSession(type: SessionType, segments: List<Segment>, categoryId: Long?): Long? =
        withContext(Dispatchers.IO) {
            if (segments.isEmpty()) null else db.insertSession(type, segments, categoryId)
        }

    suspend fun replaceSession(sessionId: Long, segments: List<Segment>, categoryId: Long?) {
        withContext(Dispatchers.IO) { db.replaceSession(sessionId, segments, categoryId) }
    }

    suspend fun setSessionCategory(sessionId: Long, categoryId: Long?) {
        withContext(Dispatchers.IO) { db.setSessionCategory(sessionId, categoryId) }
    }

    suspend fun deleteSession(sessionId: Long) {
        withContext(Dispatchers.IO) { db.deleteSession(sessionId) }
    }

    suspend fun getSessionsSince(sinceMillis: Long): List<WorkSession> =
        withContext(Dispatchers.IO) { db.getSessionsSince(sinceMillis) }

    suspend fun getSessionsBetween(startMillis: Long, endMillis: Long): List<WorkSession> =
        withContext(Dispatchers.IO) { db.getSessionsBetween(startMillis, endMillis) }

    suspend fun getSegments(sessionId: Long): List<Segment> =
        withContext(Dispatchers.IO) { db.getSegments(sessionId) }

    suspend fun getTimeline(startMillis: Long, endMillis: Long): List<TimelineSegment> =
        withContext(Dispatchers.IO) { db.getTimeline(startMillis, endMillis) }

    suspend fun getCategories(): List<Category> =
        withContext(Dispatchers.IO) { db.getCategories(includeArchived = false) }

    /**
     * Returns the category with this name, creating it if needed. A deleted one with the
     * same name comes back rather than being duplicated, reuniting it with its history.
     */
    suspend fun createCategory(name: String): Category = withContext(Dispatchers.IO) {
        val existing = db.getCategories(includeArchived = true)
            .firstOrNull { it.name.equals(name, ignoreCase = true) }
        when {
            existing == null -> {
                val palette = CategoryPalette.colors
                val color = palette[db.countCategories() % palette.size]
                Category(db.insertCategory(name, color), name, color)
            }
            existing.archived -> {
                db.setCategoryArchived(existing.id, archived = false)
                existing.copy(archived = false)
            }
            else -> existing
        }
    }

    suspend fun archiveCategory(categoryId: Long) {
        withContext(Dispatchers.IO) { db.setCategoryArchived(categoryId, archived = true) }
    }
}

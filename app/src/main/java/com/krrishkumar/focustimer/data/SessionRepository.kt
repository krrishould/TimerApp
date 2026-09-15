package com.krrishkumar.focustimer.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

/** Something changed on this device that other devices should hear about. */
sealed interface SyncChange {
    data class Session(val syncId: String) : SyncChange
    data class Category(val syncId: String) : SyncChange
}

/** Every database call, moved off the main thread. */
class SessionRepository(context: Context) {
    private val db = SessionDatabaseHelper(context)

    private val _changes = MutableStateFlow(0L)

    /** Moves after every write, local or from another device, so screens know to reload. */
    val changes: StateFlow<Long> = _changes

    /** Set by sync; told about each local change so it can be uploaded. */
    @Volatile
    var onLocalChange: (SyncChange) -> Unit = {}

    /**
     * Records a finished session; returns its id. Empty segment lists are ignored. Passing a
     * [syncId] that already exists rewrites that session instead of adding another.
     */
    suspend fun logSession(
        type: SessionType,
        segments: List<Segment>,
        categoryId: Long?,
        syncId: String? = null
    ): Long? = withContext(Dispatchers.IO) {
        if (segments.isEmpty()) return@withContext null
        val id = db.insertSession(type, segments, categoryId, syncId ?: newSyncId())
        sessionChanged(id)
        id
    }

    suspend fun replaceSession(sessionId: Long, segments: List<Segment>, categoryId: Long?) {
        withContext(Dispatchers.IO) {
            db.replaceSession(sessionId, segments, categoryId)
            sessionChanged(sessionId)
        }
    }

    suspend fun setSessionCategory(sessionId: Long, categoryId: Long?) {
        withContext(Dispatchers.IO) {
            db.setSessionCategory(sessionId, categoryId)
            sessionChanged(sessionId)
        }
    }

    suspend fun deleteSession(sessionId: Long) {
        withContext(Dispatchers.IO) {
            db.deleteSession(sessionId)
            sessionChanged(sessionId)
        }
    }

    suspend fun getSessionsSince(sinceMillis: Long): List<WorkSession> =
        withContext(Dispatchers.IO) { db.getSessionsSince(sinceMillis) }

    suspend fun getSessionsBetween(startMillis: Long, endMillis: Long): List<WorkSession> =
        withContext(Dispatchers.IO) { db.getSessionsBetween(startMillis, endMillis) }

    suspend fun getSegments(sessionId: Long): List<Segment> =
        withContext(Dispatchers.IO) { db.getSegments(sessionId) }

    suspend fun getTimeline(startMillis: Long, endMillis: Long): List<TimelineSegment> =
        withContext(Dispatchers.IO) { db.getTimeline(startMillis, endMillis) }

    suspend fun getCategories(includeArchived: Boolean = false): List<Category> =
        withContext(Dispatchers.IO) { db.getCategories(includeArchived) }

    /**
     * Returns the category with this name, creating it if needed. A deleted one with the
     * same name comes back rather than being duplicated, reuniting it with its history.
     */
    suspend fun createCategory(name: String): Category = withContext(Dispatchers.IO) {
        val existing = db.getCategories(includeArchived = true)
            .firstOrNull { it.name.equals(name, ignoreCase = true) }
        val category = when {
            existing == null -> {
                val palette = CategoryPalette.colors
                val color = palette[db.countCategories() % palette.size]
                val id = db.insertCategory(name, color)
                Category(id, name, color, syncId = db.categorySyncId(id) ?: "")
            }
            existing.archived -> {
                db.setCategoryArchived(existing.id, archived = false)
                existing.copy(archived = false)
            }
            else -> return@withContext existing
        }
        categoryChanged(category.id)
        category
    }

    suspend fun archiveCategory(categoryId: Long) {
        withContext(Dispatchers.IO) {
            db.setCategoryArchived(categoryId, archived = true)
            categoryChanged(categoryId)
        }
    }

    // ------------------------------------------------------------ sync side

    suspend fun getSyncSessions(): List<SyncSession> = withContext(Dispatchers.IO) { db.getSyncSessions() }

    suspend fun getSyncSession(syncId: String): SyncSession? = withContext(Dispatchers.IO) { db.getSyncSession(syncId) }

    suspend fun getSyncCategories(): List<SyncCategory> = withContext(Dispatchers.IO) { db.getSyncCategories() }

    suspend fun getSyncCategory(syncId: String): SyncCategory? = withContext(Dispatchers.IO) { db.getSyncCategory(syncId) }

    /** Applies categories from another device; returns how many changed anything here. */
    suspend fun applyRemoteCategories(categories: List<SyncCategory>): Int = withContext(Dispatchers.IO) {
        categories.count { db.applyRemoteCategory(it) }.also { if (it > 0) bump() }
    }

    /** Applies sessions from another device; returns how many changed anything here. */
    suspend fun applyRemoteSessions(sessions: List<SyncSession>): Int = withContext(Dispatchers.IO) {
        sessions.count { db.applyRemoteSession(it) }.also { if (it > 0) bump() }
    }

    suspend fun adoptCategorySyncId(localSyncId: String, remoteSyncId: String) {
        withContext(Dispatchers.IO) {
            db.adoptCategorySyncId(localSyncId, remoteSyncId)
            bump()
        }
    }

    private fun sessionChanged(sessionId: Long) {
        db.sessionSyncId(sessionId)?.let { onLocalChange(SyncChange.Session(it)) }
        bump()
    }

    private fun categoryChanged(categoryId: Long) {
        db.categorySyncId(categoryId)?.let { onLocalChange(SyncChange.Category(it)) }
        bump()
    }

    private fun bump() = _changes.update { it + 1 }
}

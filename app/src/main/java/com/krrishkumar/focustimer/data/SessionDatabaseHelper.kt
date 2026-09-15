package com.krrishkumar.focustimer.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class SessionDatabaseHelper(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        // Build the original table and run every migration over it, so a fresh install ends
        // up with exactly the schema an upgraded one has.
        db.execSQL(
            "CREATE TABLE $T_SESSIONS ($C_ID INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "$C_TYPE TEXT NOT NULL, $C_START INTEGER NOT NULL, $C_DURATION INTEGER NOT NULL)"
        )
        onUpgrade(db, 1, DATABASE_VERSION)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Every step migrates in place: existing sessions are the user's own recorded
        // history and must survive the upgrade.
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE $T_SESSIONS ADD COLUMN $C_LABEL TEXT")
        }
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE $T_SESSIONS ADD COLUMN $C_CATEGORY INTEGER")
            db.execSQL("ALTER TABLE $T_SESSIONS ADD COLUMN $C_END INTEGER")
            db.execSQL("UPDATE $T_SESSIONS SET $C_END = $C_START + $C_DURATION")
            db.execSQL(
                "CREATE TABLE $T_CATEGORIES ($C_ID INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "$C_NAME TEXT NOT NULL, $C_COLOR INTEGER NOT NULL, $C_ARCHIVED INTEGER NOT NULL DEFAULT 0)"
            )
            db.execSQL(
                "CREATE TABLE $T_SEGMENTS ($C_ID INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "$C_SESSION INTEGER NOT NULL, $C_START INTEGER NOT NULL, $C_END INTEGER NOT NULL)"
            )
            db.execSQL("CREATE INDEX idx_segments_session ON $T_SEGMENTS ($C_SESSION)")
            db.execSQL("CREATE INDEX idx_segments_start ON $T_SEGMENTS ($C_START)")

            // Older sessions were never broken into segments: each becomes one unbroken run.
            db.execSQL(
                "INSERT INTO $T_SEGMENTS ($C_SESSION, $C_START, $C_END) " +
                    "SELECT $C_ID, $C_START, $C_START + $C_DURATION FROM $T_SESSIONS"
            )

            // Free-text activity names become categories, so old history counts in stats.
            val names = mutableListOf<String>()
            db.rawQuery(
                "SELECT DISTINCT TRIM($C_LABEL) FROM $T_SESSIONS " +
                    "WHERE $C_LABEL IS NOT NULL AND TRIM($C_LABEL) != ''",
                null
            ).use { while (it.moveToNext()) names += it.getString(0) }
            names.forEachIndexed { index, name ->
                val id = db.insert(T_CATEGORIES, null, ContentValues().apply {
                    put(C_NAME, name)
                    put(C_COLOR, CategoryPalette.colors[index % CategoryPalette.colors.size])
                    put(C_ARCHIVED, 0)
                })
                db.execSQL(
                    "UPDATE $T_SESSIONS SET $C_CATEGORY = ? WHERE TRIM($C_LABEL) = ?",
                    arrayOf<Any>(id, name)
                )
            }
        }
        if (oldVersion < 4) {
            // Sync: an id every device agrees on, when each row last changed, and deletions
            // kept as tombstones so they can reach the other device.
            db.execSQL("ALTER TABLE $T_SESSIONS ADD COLUMN $C_SYNC_ID TEXT")
            db.execSQL("ALTER TABLE $T_SESSIONS ADD COLUMN $C_CATEGORY_SYNC TEXT")
            db.execSQL("ALTER TABLE $T_SESSIONS ADD COLUMN $C_UPDATED INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE $T_SESSIONS ADD COLUMN $C_DELETED INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE $T_CATEGORIES ADD COLUMN $C_SYNC_ID TEXT")
            db.execSQL("ALTER TABLE $T_CATEGORIES ADD COLUMN $C_UPDATED INTEGER NOT NULL DEFAULT 0")

            val now = System.currentTimeMillis()
            db.execSQL("UPDATE $T_CATEGORIES SET $C_SYNC_ID = lower(hex(randomblob(16))), $C_UPDATED = $now")
            db.execSQL(
                "UPDATE $T_SESSIONS SET $C_SYNC_ID = lower(hex(randomblob(16))), " +
                    "$C_UPDATED = COALESCE($C_END, $C_START + $C_DURATION), " +
                    "$C_CATEGORY_SYNC = (SELECT c.$C_SYNC_ID FROM $T_CATEGORIES c WHERE c.$C_ID = $T_SESSIONS.$C_CATEGORY)"
            )
            db.execSQL("CREATE UNIQUE INDEX idx_sessions_sync ON $T_SESSIONS ($C_SYNC_ID)")
            db.execSQL("CREATE UNIQUE INDEX idx_categories_sync ON $T_CATEGORIES ($C_SYNC_ID)")
        }
    }

    // ---------------------------------------------------------------- sessions

    /**
     * Writes a session and its segments together, so neither exists without the other. A
     * [syncId] that's already here rewrites that session rather than adding a second one.
     */
    fun insertSession(
        type: SessionType,
        segments: List<Segment>,
        categoryId: Long?,
        syncId: String = newSyncId()
    ): Long {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val values = sessionValues(db, segments, categoryId).apply {
                put(C_TYPE, type.name)
                put(C_DELETED, 0)
            }
            val existing = localIdForSync(db, T_SESSIONS, syncId)
            val id = if (existing != null) {
                db.update(T_SESSIONS, values, "$C_ID = ?", arrayOf(existing.toString()))
                db.delete(T_SEGMENTS, "$C_SESSION = ?", arrayOf(existing.toString()))
                existing
            } else {
                db.insert(T_SESSIONS, null, values.apply { put(C_SYNC_ID, syncId) })
            }
            insertSegments(db, id, segments)
            db.setTransactionSuccessful()
            return id
        } finally {
            db.endTransaction()
        }
    }

    /** Rewrites a session still in progress, such as a stopwatch run saved on each pause. */
    fun replaceSession(sessionId: Long, segments: List<Segment>, categoryId: Long?) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.update(T_SESSIONS, sessionValues(db, segments, categoryId), "$C_ID = ?", arrayOf(sessionId.toString()))
            db.delete(T_SEGMENTS, "$C_SESSION = ?", arrayOf(sessionId.toString()))
            insertSegments(db, sessionId, segments)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun setSessionCategory(sessionId: Long, categoryId: Long?) {
        val db = writableDatabase
        val values = ContentValues().apply {
            putCategory(db, categoryId)
            put(C_UPDATED, System.currentTimeMillis())
        }
        db.update(T_SESSIONS, values, "$C_ID = ?", arrayOf(sessionId.toString()))
    }

    /** Hides the session and keeps a tombstone, so the deletion can reach other devices. */
    fun deleteSession(sessionId: Long) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete(T_SEGMENTS, "$C_SESSION = ?", arrayOf(sessionId.toString()))
            db.update(
                T_SESSIONS,
                ContentValues().apply {
                    put(C_DELETED, 1)
                    put(C_UPDATED, System.currentTimeMillis())
                },
                "$C_ID = ?",
                arrayOf(sessionId.toString())
            )
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun sessionSyncId(sessionId: Long): String? = readableDatabase.rawQuery(
        "SELECT $C_SYNC_ID FROM $T_SESSIONS WHERE $C_ID = ?", arrayOf(sessionId.toString())
    ).use { if (it.moveToFirst() && !it.isNull(0)) it.getString(0) else null }

    fun getSessionsBetween(startMillis: Long, endMillis: Long): List<WorkSession> = querySessions(
        "s.$C_START >= ? AND s.$C_START < ?",
        arrayOf(startMillis.toString(), endMillis.toString())
    )

    fun getSessionsSince(sinceMillis: Long): List<WorkSession> =
        querySessions("s.$C_START >= ?", arrayOf(sinceMillis.toString()))

    fun getSegments(sessionId: Long): List<Segment> {
        val segments = mutableListOf<Segment>()
        readableDatabase.query(
            T_SEGMENTS, arrayOf(C_START, C_END), "$C_SESSION = ?", arrayOf(sessionId.toString()),
            null, null, "$C_START ASC"
        ).use { while (it.moveToNext()) segments += Segment(it.getLong(0), it.getLong(1)) }
        return segments
    }

    /** Every running segment that overlaps the window, for drawing a day. */
    fun getTimeline(startMillis: Long, endMillis: Long): List<TimelineSegment> {
        val result = mutableListOf<TimelineSegment>()
        readableDatabase.rawQuery(
            """
            SELECT g.$C_SESSION, s.$C_TYPE, c.$C_COLOR, g.$C_START, g.$C_END
            FROM $T_SEGMENTS g
            JOIN $T_SESSIONS s ON s.$C_ID = g.$C_SESSION
            LEFT JOIN $T_CATEGORIES c ON c.$C_ID = s.$C_CATEGORY
            WHERE s.$C_DELETED = 0 AND g.$C_END > ? AND g.$C_START < ?
            ORDER BY g.$C_START ASC
            """.trimIndent(),
            arrayOf(startMillis.toString(), endMillis.toString())
        ).use {
            while (it.moveToNext()) {
                result += TimelineSegment(
                    sessionId = it.getLong(0),
                    type = SessionType.valueOf(it.getString(1)),
                    categoryColor = if (it.isNull(2)) null else it.getInt(2),
                    start = it.getLong(3),
                    end = it.getLong(4)
                )
            }
        }
        return result
    }

    // -------------------------------------------------------------- categories

    fun getCategories(includeArchived: Boolean): List<Category> {
        val categories = mutableListOf<Category>()
        readableDatabase.query(
            T_CATEGORIES, arrayOf(C_ID, C_NAME, C_COLOR, C_ARCHIVED, C_SYNC_ID),
            if (includeArchived) null else "$C_ARCHIVED = 0", null, null, null, "$C_NAME COLLATE NOCASE ASC"
        ).use {
            while (it.moveToNext()) {
                categories += Category(
                    id = it.getLong(0),
                    name = it.getString(1),
                    color = it.getInt(2),
                    archived = it.getInt(3) != 0,
                    syncId = it.getString(4) ?: ""
                )
            }
        }
        return categories
    }

    fun insertCategory(name: String, color: Int): Long = writableDatabase.insert(
        T_CATEGORIES, null,
        ContentValues().apply {
            put(C_NAME, name)
            put(C_COLOR, color)
            put(C_ARCHIVED, 0)
            put(C_SYNC_ID, newSyncId())
            put(C_UPDATED, System.currentTimeMillis())
        }
    )

    fun setCategoryArchived(categoryId: Long, archived: Boolean) {
        val values = ContentValues().apply {
            put(C_ARCHIVED, if (archived) 1 else 0)
            put(C_UPDATED, System.currentTimeMillis())
        }
        writableDatabase.update(T_CATEGORIES, values, "$C_ID = ?", arrayOf(categoryId.toString()))
    }

    fun countCategories(): Int =
        readableDatabase.rawQuery("SELECT COUNT(*) FROM $T_CATEGORIES", null)
            .use { if (it.moveToFirst()) it.getInt(0) else 0 }

    fun categorySyncId(categoryId: Long): String? = readableDatabase.rawQuery(
        "SELECT $C_SYNC_ID FROM $T_CATEGORIES WHERE $C_ID = ?", arrayOf(categoryId.toString())
    ).use { if (it.moveToFirst() && !it.isNull(0)) it.getString(0) else null }

    // -------------------------------------------------------------------- sync

    /** Every session including tombstones, as it would be sent to another device. */
    fun getSyncSessions(): List<SyncSession> = querySyncSessions(null, null)

    fun getSyncSession(syncId: String): SyncSession? =
        querySyncSessions("$C_SYNC_ID = ?", arrayOf(syncId)).firstOrNull()

    fun getSyncCategories(): List<SyncCategory> = querySyncCategories(null, null)

    fun getSyncCategory(syncId: String): SyncCategory? =
        querySyncCategories("$C_SYNC_ID = ?", arrayOf(syncId)).firstOrNull()

    /**
     * Applies a session from another device. Whichever copy changed last wins, so an older
     * remote copy is ignored. Returns true if anything here changed.
     */
    fun applyRemoteSession(remote: SyncSession): Boolean {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val local = stampForSync(db, T_SESSIONS, remote.syncId)
            if (local != null && local.second >= remote.updatedAt) return false

            val values = ContentValues().apply {
                put(C_TYPE, remote.type.name)
                put(C_START, remote.segments.minOfOrNull { it.start } ?: 0L)
                put(C_END, remote.segments.maxOfOrNull { it.end } ?: 0L)
                put(C_DURATION, remote.segments.activeMillis())
                val categoryId = remote.categorySyncId?.let { localIdForSync(db, T_CATEGORIES, it) }
                if (categoryId != null) put(C_CATEGORY, categoryId) else putNull(C_CATEGORY)
                if (remote.categorySyncId != null) put(C_CATEGORY_SYNC, remote.categorySyncId) else putNull(C_CATEGORY_SYNC)
                put(C_UPDATED, remote.updatedAt)
                put(C_DELETED, if (remote.deleted) 1 else 0)
            }
            val id = if (local != null) {
                db.update(T_SESSIONS, values, "$C_ID = ?", arrayOf(local.first.toString()))
                local.first
            } else {
                db.insert(T_SESSIONS, null, values.apply { put(C_SYNC_ID, remote.syncId) })
            }
            db.delete(T_SEGMENTS, "$C_SESSION = ?", arrayOf(id.toString()))
            if (!remote.deleted) insertSegments(db, id, remote.segments)
            db.setTransactionSuccessful()
            return true
        } finally {
            db.endTransaction()
        }
    }

    /** Applies a category from another device, newest change winning. */
    fun applyRemoteCategory(remote: SyncCategory): Boolean {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val local = stampForSync(db, T_CATEGORIES, remote.syncId)
            if (local != null && local.second >= remote.updatedAt) return false

            val values = ContentValues().apply {
                put(C_NAME, remote.name)
                put(C_COLOR, remote.color)
                put(C_ARCHIVED, if (remote.archived) 1 else 0)
                put(C_UPDATED, remote.updatedAt)
            }
            val id = if (local != null) {
                db.update(T_CATEGORIES, values, "$C_ID = ?", arrayOf(local.first.toString()))
                local.first
            } else {
                db.insert(T_CATEGORIES, null, values.apply { put(C_SYNC_ID, remote.syncId) })
            }
            // Sessions that arrived before their category can now point at it.
            db.execSQL(
                "UPDATE $T_SESSIONS SET $C_CATEGORY = ? WHERE $C_CATEGORY_SYNC = ?",
                arrayOf<Any>(id, remote.syncId)
            )
            db.setTransactionSuccessful()
            return true
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Makes a local category share another device's id, for when both devices made a
     * category with the same name before they ever synced. Its sessions follow it.
     */
    fun adoptCategorySyncId(localSyncId: String, remoteSyncId: String) {
        val db = writableDatabase
        if (localIdForSync(db, T_CATEGORIES, remoteSyncId) != null) return
        db.beginTransaction()
        try {
            db.execSQL(
                "UPDATE $T_CATEGORIES SET $C_SYNC_ID = ? WHERE $C_SYNC_ID = ?",
                arrayOf<Any>(remoteSyncId, localSyncId)
            )
            db.execSQL(
                "UPDATE $T_SESSIONS SET $C_CATEGORY_SYNC = ? WHERE $C_CATEGORY_SYNC = ?",
                arrayOf<Any>(remoteSyncId, localSyncId)
            )
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    // ----------------------------------------------------------------- helpers

    private fun sessionValues(db: SQLiteDatabase, segments: List<Segment>, categoryId: Long?) = ContentValues().apply {
        put(C_START, segments.minOfOrNull { it.start } ?: 0L)
        put(C_END, segments.maxOfOrNull { it.end } ?: 0L)
        put(C_DURATION, segments.activeMillis())
        putCategory(db, categoryId)
        put(C_UPDATED, System.currentTimeMillis())
    }

    /** Sets both the local category id and the shared one it's known by elsewhere. */
    private fun ContentValues.putCategory(db: SQLiteDatabase, categoryId: Long?) {
        if (categoryId == null) {
            putNull(C_CATEGORY)
            putNull(C_CATEGORY_SYNC)
            return
        }
        put(C_CATEGORY, categoryId)
        val syncId = db.rawQuery(
            "SELECT $C_SYNC_ID FROM $T_CATEGORIES WHERE $C_ID = ?", arrayOf(categoryId.toString())
        ).use { if (it.moveToFirst() && !it.isNull(0)) it.getString(0) else null }
        if (syncId != null) put(C_CATEGORY_SYNC, syncId) else putNull(C_CATEGORY_SYNC)
    }

    private fun insertSegments(db: SQLiteDatabase, sessionId: Long, segments: List<Segment>) {
        segments.forEach { segment ->
            db.insert(T_SEGMENTS, null, ContentValues().apply {
                put(C_SESSION, sessionId)
                put(C_START, segment.start)
                put(C_END, segment.end)
            })
        }
    }

    private fun localIdForSync(db: SQLiteDatabase, table: String, syncId: String): Long? = db.rawQuery(
        "SELECT $C_ID FROM $table WHERE $C_SYNC_ID = ?", arrayOf(syncId)
    ).use { if (it.moveToFirst()) it.getLong(0) else null }

    /** Local row id and last-changed time for a shared id, or null if it isn't here. */
    private fun stampForSync(db: SQLiteDatabase, table: String, syncId: String): Pair<Long, Long>? = db.rawQuery(
        "SELECT $C_ID, $C_UPDATED FROM $table WHERE $C_SYNC_ID = ?", arrayOf(syncId)
    ).use { if (it.moveToFirst()) it.getLong(0) to it.getLong(1) else null }

    private fun querySessions(selection: String, args: Array<String>): List<WorkSession> {
        val sessions = mutableListOf<WorkSession>()
        readableDatabase.rawQuery(
            """
            SELECT s.$C_ID, s.$C_TYPE, s.$C_START, s.$C_DURATION, s.$C_END,
                   s.$C_CATEGORY, c.$C_NAME, c.$C_COLOR, s.$C_SYNC_ID
            FROM $T_SESSIONS s
            LEFT JOIN $T_CATEGORIES c ON c.$C_ID = s.$C_CATEGORY
            WHERE s.$C_DELETED = 0 AND $selection
            ORDER BY s.$C_START DESC
            """.trimIndent(),
            args
        ).use { cursor -> while (cursor.moveToNext()) sessions += cursor.toSession() }
        return sessions
    }

    private fun Cursor.toSession(): WorkSession {
        val start = getLong(2)
        val duration = getLong(3)
        return WorkSession(
            id = getLong(0),
            type = SessionType.valueOf(getString(1)),
            startTimeMillis = start,
            durationMillis = duration,
            endTimeMillis = if (isNull(4)) start + duration else getLong(4),
            categoryId = if (isNull(5)) null else getLong(5),
            categoryName = if (isNull(6)) null else getString(6),
            categoryColor = if (isNull(7)) null else getInt(7),
            syncId = getString(8) ?: ""
        )
    }

    private fun querySyncSessions(selection: String?, args: Array<String>?): List<SyncSession> {
        val rows = mutableListOf<Pair<Long, SyncSession>>()
        readableDatabase.query(
            T_SESSIONS, arrayOf(C_ID, C_SYNC_ID, C_TYPE, C_CATEGORY_SYNC, C_UPDATED, C_DELETED),
            selection, args, null, null, null
        ).use {
            while (it.moveToNext()) {
                rows += it.getLong(0) to SyncSession(
                    syncId = it.getString(1),
                    type = SessionType.valueOf(it.getString(2)),
                    segments = emptyList(),
                    categorySyncId = if (it.isNull(3)) null else it.getString(3),
                    updatedAt = it.getLong(4),
                    deleted = it.getInt(5) != 0
                )
            }
        }
        return rows.map { (id, session) -> if (session.deleted) session else session.copy(segments = getSegments(id)) }
    }

    private fun querySyncCategories(selection: String?, args: Array<String>?): List<SyncCategory> {
        val categories = mutableListOf<SyncCategory>()
        readableDatabase.query(
            T_CATEGORIES, arrayOf(C_SYNC_ID, C_NAME, C_COLOR, C_ARCHIVED, C_UPDATED),
            selection, args, null, null, null
        ).use {
            while (it.moveToNext()) {
                categories += SyncCategory(
                    syncId = it.getString(0),
                    name = it.getString(1),
                    color = it.getInt(2),
                    archived = it.getInt(3) != 0,
                    updatedAt = it.getLong(4)
                )
            }
        }
        return categories
    }

    companion object {
        private const val DATABASE_NAME = "focus_timer.db"
        private const val DATABASE_VERSION = 4

        private const val T_SESSIONS = "sessions"
        private const val T_CATEGORIES = "categories"
        private const val T_SEGMENTS = "segments"

        private const val C_ID = "id"
        private const val C_TYPE = "type"
        private const val C_START = "start_time"
        private const val C_END = "end_time"
        private const val C_DURATION = "duration"
        /** Free-text name from before categories; kept only so the upgrade can read it. */
        private const val C_LABEL = "label"
        private const val C_CATEGORY = "category_id"
        private const val C_CATEGORY_SYNC = "category_sync_id"
        private const val C_SESSION = "session_id"
        private const val C_NAME = "name"
        private const val C_COLOR = "color"
        private const val C_ARCHIVED = "archived"
        private const val C_SYNC_ID = "sync_id"
        private const val C_UPDATED = "updated_at"
        private const val C_DELETED = "deleted"
    }
}

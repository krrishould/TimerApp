package com.krrishkumar.focustimer.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class SessionDatabaseHelper(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $T_SESSIONS (
                $C_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $C_TYPE TEXT NOT NULL,
                $C_START INTEGER NOT NULL,
                $C_DURATION INTEGER NOT NULL,
                $C_LABEL TEXT,
                $C_CATEGORY INTEGER,
                $C_END INTEGER
            )
            """.trimIndent()
        )
        createV3Tables(db)
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
            createV3Tables(db)

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
    }

    private fun createV3Tables(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $T_CATEGORIES (
                $C_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $C_NAME TEXT NOT NULL,
                $C_COLOR INTEGER NOT NULL,
                $C_ARCHIVED INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE $T_SEGMENTS (
                $C_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $C_SESSION INTEGER NOT NULL,
                $C_START INTEGER NOT NULL,
                $C_END INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_segments_session ON $T_SEGMENTS ($C_SESSION)")
        db.execSQL("CREATE INDEX idx_segments_start ON $T_SEGMENTS ($C_START)")
    }

    // ---------------------------------------------------------------- sessions

    /** Writes a session and its segments together, so neither exists without the other. */
    fun insertSession(type: SessionType, segments: List<Segment>, categoryId: Long?): Long {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val id = db.insert(T_SESSIONS, null, sessionValues(segments, categoryId).apply {
                put(C_TYPE, type.name)
            })
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
            db.update(T_SESSIONS, sessionValues(segments, categoryId), "$C_ID = ?", arrayOf(sessionId.toString()))
            db.delete(T_SEGMENTS, "$C_SESSION = ?", arrayOf(sessionId.toString()))
            insertSegments(db, sessionId, segments)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun setSessionCategory(sessionId: Long, categoryId: Long?) {
        val values = ContentValues().apply {
            if (categoryId != null) put(C_CATEGORY, categoryId) else putNull(C_CATEGORY)
        }
        writableDatabase.update(T_SESSIONS, values, "$C_ID = ?", arrayOf(sessionId.toString()))
    }

    fun deleteSession(sessionId: Long) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete(T_SEGMENTS, "$C_SESSION = ?", arrayOf(sessionId.toString()))
            db.delete(T_SESSIONS, "$C_ID = ?", arrayOf(sessionId.toString()))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

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
            WHERE g.$C_END > ? AND g.$C_START < ?
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
            T_CATEGORIES, arrayOf(C_ID, C_NAME, C_COLOR, C_ARCHIVED),
            if (includeArchived) null else "$C_ARCHIVED = 0", null, null, null, "$C_NAME COLLATE NOCASE ASC"
        ).use {
            while (it.moveToNext()) {
                categories += Category(it.getLong(0), it.getString(1), it.getInt(2), it.getInt(3) != 0)
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
        }
    )

    fun setCategoryArchived(categoryId: Long, archived: Boolean) {
        val values = ContentValues().apply { put(C_ARCHIVED, if (archived) 1 else 0) }
        writableDatabase.update(T_CATEGORIES, values, "$C_ID = ?", arrayOf(categoryId.toString()))
    }

    fun countCategories(): Int =
        readableDatabase.rawQuery("SELECT COUNT(*) FROM $T_CATEGORIES", null)
            .use { if (it.moveToFirst()) it.getInt(0) else 0 }

    // ----------------------------------------------------------------- helpers

    private fun sessionValues(segments: List<Segment>, categoryId: Long?) = ContentValues().apply {
        put(C_START, segments.minOfOrNull { it.start } ?: 0L)
        put(C_END, segments.maxOfOrNull { it.end } ?: 0L)
        put(C_DURATION, segments.activeMillis())
        if (categoryId != null) put(C_CATEGORY, categoryId) else putNull(C_CATEGORY)
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

    private fun querySessions(selection: String, args: Array<String>): List<WorkSession> {
        val sessions = mutableListOf<WorkSession>()
        readableDatabase.rawQuery(
            """
            SELECT s.$C_ID, s.$C_TYPE, s.$C_START, s.$C_DURATION, s.$C_END,
                   s.$C_CATEGORY, c.$C_NAME, c.$C_COLOR
            FROM $T_SESSIONS s
            LEFT JOIN $T_CATEGORIES c ON c.$C_ID = s.$C_CATEGORY
            WHERE $selection
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
            categoryColor = if (isNull(7)) null else getInt(7)
        )
    }

    companion object {
        private const val DATABASE_NAME = "focus_timer.db"
        private const val DATABASE_VERSION = 3

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
        private const val C_SESSION = "session_id"
        private const val C_NAME = "name"
        private const val C_COLOR = "color"
        private const val C_ARCHIVED = "archived"
    }
}

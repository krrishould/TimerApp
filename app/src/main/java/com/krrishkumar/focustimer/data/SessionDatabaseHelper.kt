package com.krrishkumar.focustimer.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class SessionDatabaseHelper(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_SESSIONS (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_TYPE TEXT NOT NULL,
                $COL_START_TIME INTEGER NOT NULL,
                $COL_DURATION INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_SESSIONS")
        onCreate(db)
    }

    fun insertSession(session: WorkSession): Long {
        val values = ContentValues().apply {
            put(COL_TYPE, session.type.name)
            put(COL_START_TIME, session.startTimeMillis)
            put(COL_DURATION, session.durationMillis)
        }
        return writableDatabase.insert(TABLE_SESSIONS, null, values)
    }

    fun getSessionsSince(sinceMillis: Long): List<WorkSession> {
        val sessions = mutableListOf<WorkSession>()
        val cursor = readableDatabase.query(
            TABLE_SESSIONS,
            null,
            "$COL_START_TIME >= ?",
            arrayOf(sinceMillis.toString()),
            null,
            null,
            "$COL_START_TIME DESC"
        )
        cursor.use {
            val idIndex = it.getColumnIndexOrThrow(COL_ID)
            val typeIndex = it.getColumnIndexOrThrow(COL_TYPE)
            val startIndex = it.getColumnIndexOrThrow(COL_START_TIME)
            val durationIndex = it.getColumnIndexOrThrow(COL_DURATION)
            while (it.moveToNext()) {
                sessions += WorkSession(
                    id = it.getLong(idIndex),
                    type = SessionType.valueOf(it.getString(typeIndex)),
                    startTimeMillis = it.getLong(startIndex),
                    durationMillis = it.getLong(durationIndex)
                )
            }
        }
        return sessions
    }

    companion object {
        private const val DATABASE_NAME = "focus_timer.db"
        private const val DATABASE_VERSION = 1
        private const val TABLE_SESSIONS = "sessions"
        private const val COL_ID = "id"
        private const val COL_TYPE = "type"
        private const val COL_START_TIME = "start_time"
        private const val COL_DURATION = "duration"
    }
}

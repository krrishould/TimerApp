package com.krrishkumar.focustimer.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SessionRepository(context: Context) {
    private val dbHelper = SessionDatabaseHelper(context)

    suspend fun logSession(type: SessionType, startTimeMillis: Long, durationMillis: Long) {
        withContext(Dispatchers.IO) {
            dbHelper.insertSession(WorkSession(type = type, startTimeMillis = startTimeMillis, durationMillis = durationMillis))
        }
    }

    suspend fun getSessionsSince(sinceMillis: Long): List<WorkSession> {
        return withContext(Dispatchers.IO) {
            dbHelper.getSessionsSince(sinceMillis)
        }
    }
}

package com.krrishkumar.focustimer.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SessionRepository(context: Context) {
    private val dbHelper = SessionDatabaseHelper(context)

    suspend fun logSession(
        type: SessionType,
        startTimeMillis: Long,
        durationMillis: Long,
        label: String? = null
    ) {
        withContext(Dispatchers.IO) {
            dbHelper.insertSession(
                WorkSession(
                    type = type,
                    startTimeMillis = startTimeMillis,
                    durationMillis = durationMillis,
                    label = label
                )
            )
        }
    }

    suspend fun getSessionsSince(sinceMillis: Long): List<WorkSession> =
        withContext(Dispatchers.IO) { dbHelper.getSessionsSince(sinceMillis) }

    suspend fun getSessionsBetween(startMillis: Long, endMillis: Long): List<WorkSession> =
        withContext(Dispatchers.IO) { dbHelper.getSessionsBetween(startMillis, endMillis) }

    /** Same as [logSession] but hands back the new row id, so it can be updated later. */
    suspend fun logSessionReturningId(
        type: SessionType,
        startTimeMillis: Long,
        durationMillis: Long,
        label: String? = null
    ): Long = withContext(Dispatchers.IO) {
        dbHelper.insertSession(
            WorkSession(
                type = type,
                startTimeMillis = startTimeMillis,
                durationMillis = durationMillis,
                label = label
            )
        )
    }

    suspend fun updateLabel(sessionId: Long, label: String?) {
        withContext(Dispatchers.IO) { dbHelper.updateLabel(sessionId, label) }
    }

    suspend fun updateDuration(sessionId: Long, durationMillis: Long) {
        withContext(Dispatchers.IO) { dbHelper.updateDuration(sessionId, durationMillis) }
    }

    suspend fun deleteSession(sessionId: Long) {
        withContext(Dispatchers.IO) { dbHelper.deleteSession(sessionId) }
    }
}

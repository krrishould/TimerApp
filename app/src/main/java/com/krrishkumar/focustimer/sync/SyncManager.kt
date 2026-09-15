package com.krrishkumar.focustimer.sync

import android.app.Activity
import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.krrishkumar.focustimer.data.AppPreferences
import com.krrishkumar.focustimer.data.Segment
import com.krrishkumar.focustimer.data.SessionRepository
import com.krrishkumar.focustimer.data.SessionType
import com.krrishkumar.focustimer.data.SyncCategory
import com.krrishkumar.focustimer.data.SyncChange
import com.krrishkumar.focustimer.data.SyncSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class SyncState(
    /** False when this build has no Firebase project set up, so sync can't work at all. */
    val configured: Boolean = true,
    val signedIn: Boolean = false,
    val email: String? = null,
    val syncing: Boolean = false,
    val lastSyncedAt: Long? = null,
    val error: String? = null
)

/**
 * Keeps this device's history, categories and settings in step with the signed-in account in
 * Cloud Firestore. The local database stays the source the app reads from; sync copies changes
 * up as they happen and copies other devices' changes down as they arrive. When both sides have
 * changed the same thing, the most recent change wins.
 *
 * Firestore queues writes while offline and sends them later, so nothing here waits on the
 * network before the app carries on.
 */
class SyncManager(
    private val context: Context,
    private val repository: SessionRepository,
    private val preferences: AppPreferences,
    private val scope: CoroutineScope
) {
    private val configured: Boolean =
        FirebaseApp.getApps(context).isNotEmpty() || FirebaseApp.initializeApp(context) != null

    private val _state = MutableStateFlow(SyncState(configured = configured))
    val state: StateFlow<SyncState> = _state

    private val auth: FirebaseAuth? by lazy { if (configured) FirebaseAuth.getInstance() else null }
    private val firestore: FirebaseFirestore? by lazy { if (configured) FirebaseFirestore.getInstance() else null }

    private val listeners = mutableListOf<ListenerRegistration>()
    private var activeUid: String? = null
    private var reconcileJob: Job? = null

    fun start() {
        val auth = auth ?: return
        repository.onLocalChange = { change -> push(change) }
        preferences.onSyncedSettingChanged = { pushSettings() }
        // Fires straight away with whoever is already signed in, then on every change.
        auth.addAuthStateListener { onUser(it.currentUser) }
    }

    /** Shows Google's account picker, then signs in to Firebase with the chosen account. */
    suspend fun signIn(activity: Activity): Result<Unit> {
        val auth = auth ?: return Result.failure(IllegalStateException("Sync isn't set up in this build."))
        val clientIdRes = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        if (clientIdRes == 0) {
            return Result.failure(IllegalStateException("Google sign-in isn't enabled for this Firebase project."))
        }
        val option = GetGoogleIdOption.Builder()
            .setServerClientId(context.getString(clientIdRes))
            .setFilterByAuthorizedAccounts(false)
            .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()

        return try {
            val credential = CredentialManager.create(activity).getCredential(activity, request).credential
            if (credential !is CustomCredential ||
                credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                return Result.failure(IllegalStateException("That account can't be used to sign in."))
            }
            val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
            auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await()
            Result.success(Unit)
        } catch (e: GetCredentialCancellationException) {
            Result.failure(IllegalStateException("Sign-in cancelled."))
        } catch (e: NoCredentialException) {
            Result.failure(IllegalStateException("No Google account on this device. Add one in system settings."))
        } catch (e: Exception) {
            Result.failure(IllegalStateException(e.localizedMessage ?: "Couldn't sign in."))
        }
    }

    /** Stops syncing. Everything already on this device stays on it. */
    fun signOut() {
        val auth = auth ?: return
        auth.signOut()
        scope.launch {
            runCatching { CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest()) }
        }
    }

    fun syncNow() {
        val uid = activeUid ?: return
        reconcile(uid)
    }

    private fun onUser(user: FirebaseUser?) {
        if (user?.uid == activeUid) return
        stopListening()
        activeUid = user?.uid
        if (user == null) {
            _state.value = SyncState(configured = true)
            return
        }
        _state.update { it.copy(signedIn = true, email = user.email, error = null) }
        reconcile(user.uid)
    }

    /**
     * A full pass: fetch everything, keep the newest copy of each item on both sides, then
     * listen for changes. Runs on sign-in, on app start, and from "Sync now".
     */
    private fun reconcile(uid: String) {
        reconcileJob?.cancel()
        reconcileJob = scope.launch {
            _state.update { it.copy(syncing = true, error = null) }
            try {
                val db = firestore ?: return@launch

                // Categories first, so sessions arriving next can point at them.
                val remoteCategories = categories(db, uid).get().await().documents.mapNotNull { it.toSyncCategory() }
                val remoteCategoryIds = remoteCategories.map { it.syncId }.toSet()
                // A category made separately on each device before syncing becomes one.
                repository.getSyncCategories()
                    .filter { it.syncId !in remoteCategoryIds }
                    .forEach { local ->
                        remoteCategories.firstOrNull { it.name.equals(local.name, ignoreCase = true) }
                            ?.let { repository.adoptCategorySyncId(local.syncId, it.syncId) }
                    }
                repository.applyRemoteCategories(remoteCategories)
                val remoteCategoryStamps = remoteCategories.associate { it.syncId to it.updatedAt }
                repository.getSyncCategories()
                    .filter { it.updatedAt > (remoteCategoryStamps[it.syncId] ?: -1L) }
                    .chunked(BATCH_LIMIT)
                    .forEach { chunk ->
                        val batch = db.batch()
                        chunk.forEach { batch.set(categories(db, uid).document(it.syncId), it.toMap()) }
                        batch.commit()
                    }

                val remoteSessions = sessions(db, uid).get().await().documents.mapNotNull { it.toSyncSession() }
                repository.applyRemoteSessions(remoteSessions)
                val remoteSessionStamps = remoteSessions.associate { it.syncId to it.updatedAt }
                repository.getSyncSessions()
                    .filter { it.updatedAt > (remoteSessionStamps[it.syncId] ?: -1L) }
                    .chunked(BATCH_LIMIT)
                    .forEach { chunk ->
                        val batch = db.batch()
                        chunk.forEach { batch.set(sessions(db, uid).document(it.syncId), it.toMap()) }
                        batch.commit()
                    }

                val remoteSettings = settings(db, uid).get().await()
                val remoteSettingsAt = remoteSettings.getLong("updatedAt") ?: -1L
                if (remoteSettingsAt > preferences.settingsUpdatedAt()) {
                    @Suppress("UNCHECKED_CAST")
                    preferences.applyRemoteSettings(remoteSettings.get("values") as? Map<String, Any?> ?: emptyMap(), remoteSettingsAt)
                } else if (preferences.settingsUpdatedAt() > remoteSettingsAt) {
                    pushSettings()
                }

                listen(db, uid)
                _state.update { it.copy(syncing = false, lastSyncedAt = System.currentTimeMillis()) }
            } catch (e: Exception) {
                _state.update { it.copy(syncing = false, error = e.localizedMessage ?: "Sync failed.") }
            }
        }
    }

    /** Applies other devices' changes as they arrive. Echoes of this device's own writes are harmless. */
    private fun listen(db: FirebaseFirestore, uid: String) {
        stopListening()
        listeners += categories(db, uid).addSnapshotListener { snapshot, _ ->
            val changed = snapshot?.documentChanges?.mapNotNull { it.document.toSyncCategory() } ?: return@addSnapshotListener
            if (changed.isNotEmpty()) scope.launch { repository.applyRemoteCategories(changed); markSynced() }
        }
        listeners += sessions(db, uid).addSnapshotListener { snapshot, _ ->
            val changed = snapshot?.documentChanges?.mapNotNull { it.document.toSyncSession() } ?: return@addSnapshotListener
            if (changed.isNotEmpty()) scope.launch { repository.applyRemoteSessions(changed); markSynced() }
        }
        listeners += settings(db, uid).addSnapshotListener { snapshot, _ ->
            val updatedAt = snapshot?.getLong("updatedAt") ?: return@addSnapshotListener
            @Suppress("UNCHECKED_CAST")
            val values = snapshot.get("values") as? Map<String, Any?> ?: return@addSnapshotListener
            if (preferences.applyRemoteSettings(values, updatedAt)) markSynced()
        }
    }

    private fun stopListening() {
        listeners.forEach { it.remove() }
        listeners.clear()
    }

    /** Uploads one local change. Firestore holds it until there's a connection. */
    private fun push(change: SyncChange) {
        val uid = activeUid ?: return
        val db = firestore ?: return
        scope.launch {
            val task = when (change) {
                is SyncChange.Session -> repository.getSyncSession(change.syncId)
                    ?.let { sessions(db, uid).document(it.syncId).set(it.toMap()) }
                is SyncChange.Category -> repository.getSyncCategory(change.syncId)
                    ?.let { categories(db, uid).document(it.syncId).set(it.toMap()) }
            }
            task?.addOnSuccessListener { markSynced() }
        }
    }

    private fun pushSettings() {
        val uid = activeUid ?: return
        val db = firestore ?: return
        settings(db, uid)
            .set(mapOf("values" to preferences.syncedSettings(), "updatedAt" to preferences.settingsUpdatedAt()))
            .addOnSuccessListener { markSynced() }
    }

    private fun markSynced() = _state.update { it.copy(lastSyncedAt = System.currentTimeMillis(), error = null) }

    // ------------------------------------------------------- firestore layout

    private fun user(db: FirebaseFirestore, uid: String) = db.collection("users").document(uid)
    private fun categories(db: FirebaseFirestore, uid: String): CollectionReference = user(db, uid).collection("categories")
    private fun sessions(db: FirebaseFirestore, uid: String): CollectionReference = user(db, uid).collection("sessions")
    private fun settings(db: FirebaseFirestore, uid: String) = user(db, uid).collection("meta").document("settings")

    private fun SyncCategory.toMap() = mapOf(
        "name" to name,
        "color" to color.toLong(),
        "archived" to archived,
        "updatedAt" to updatedAt
    )

    private fun SyncSession.toMap() = mapOf(
        "type" to type.name,
        // Firestore can't hold a list of lists, so each segment is a small map.
        "segments" to segments.map { mapOf("s" to it.start, "e" to it.end) },
        "category" to categorySyncId,
        "updatedAt" to updatedAt,
        "deleted" to deleted
    )

    private fun DocumentSnapshot.toSyncCategory(): SyncCategory? {
        val name = getString("name") ?: return null
        return SyncCategory(
            syncId = id,
            name = name,
            color = (getLong("color") ?: 0L).toInt(),
            archived = getBoolean("archived") ?: false,
            updatedAt = getLong("updatedAt") ?: 0L
        )
    }

    private fun DocumentSnapshot.toSyncSession(): SyncSession? {
        val type = getString("type")?.let { name -> SessionType.entries.firstOrNull { it.name == name } } ?: return null
        @Suppress("UNCHECKED_CAST")
        val segments = (get("segments") as? List<Map<String, Any?>>).orEmpty().mapNotNull { raw ->
            val start = (raw["s"] as? Number)?.toLong()
            val end = (raw["e"] as? Number)?.toLong()
            if (start != null && end != null && end > start) Segment(start, end) else null
        }
        return SyncSession(
            syncId = id,
            type = type,
            segments = segments,
            categorySyncId = getString("category"),
            updatedAt = getLong("updatedAt") ?: 0L,
            deleted = getBoolean("deleted") ?: false
        )
    }

    private companion object {
        /** Firestore accepts at most 500 writes in one batch. */
        const val BATCH_LIMIT = 400
    }
}

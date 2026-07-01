package com.exchangepro.moviles.data.repository

import com.exchangepro.moviles.data.firebase.FirebaseCollections
import com.exchangepro.moviles.domain.model.NotificationItem
import com.google.android.gms.tasks.Task
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class FirebaseNotificationRepository(
    private val authProvider: () -> FirebaseAuth = { FirebaseAuth.getInstance() },
    private val dbProvider: () -> FirebaseFirestore = { FirebaseFirestore.getInstance() }
) {
    private fun currentUserId(): String =
        authProvider().currentUser?.uid ?: error("No hay una sesion activa.")

    fun observeMine(): Flow<List<NotificationItem>> = callbackFlow {
        val registration = dbProvider().collection(FirebaseCollections.NOTIFICATIONS)
            .whereEqualTo("userId", currentUserId())
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val items = snapshot?.documents
                    .orEmpty()
                    .sortedByDescending {
                        (it.get("createdAt") as? Timestamp)?.toDate()?.time ?: 0L
                    }
                    .map {
                        NotificationItem(
                            id = it.id,
                            title = it.getString("title").orEmpty(),
                            message = it.getString("message").orEmpty(),
                            read = it.getBoolean("read") ?: false
                        )
                    }
                    .orEmpty()
                trySend(items)
            }
        awaitClose { registration.remove() }
    }

    suspend fun getMine(): List<NotificationItem> {
        val snapshot = dbProvider().collection(FirebaseCollections.NOTIFICATIONS)
            .whereEqualTo("userId", currentUserId())
            .get()
            .awaitNotification()
        return snapshot.documents
            .sortedByDescending { (it.get("createdAt") as? Timestamp)?.toDate()?.time ?: 0L }
            .map {
                NotificationItem(
                    id = it.id,
                    title = it.getString("title").orEmpty(),
                    message = it.getString("message").orEmpty(),
                    read = it.getBoolean("read") ?: false
                )
            }
    }

    suspend fun markRead(notificationId: String) {
        dbProvider().collection(FirebaseCollections.NOTIFICATIONS)
            .document(notificationId)
            .update("read", true, "readAt", FieldValue.serverTimestamp())
            .awaitNotification()
    }
}

private suspend fun <T> Task<T>.awaitNotification(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { continuation.resume(it) }
    addOnFailureListener { continuation.resumeWithException(it) }
    addOnCanceledListener { continuation.cancel() }
}

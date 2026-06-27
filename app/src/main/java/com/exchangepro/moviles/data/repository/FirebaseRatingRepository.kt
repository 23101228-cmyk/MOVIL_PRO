package com.exchangepro.moviles.data.repository

import com.exchangepro.moviles.data.firebase.FirebaseCollections
import com.exchangepro.moviles.domain.model.TransactionStatus
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class FirebaseRatingRepository(
    private val authProvider: () -> FirebaseAuth = { FirebaseAuth.getInstance() },
    private val dbProvider: () -> FirebaseFirestore = { FirebaseFirestore.getInstance() }
) {
    private fun currentUserId(): String =
        authProvider().currentUser?.uid ?: error("No hay una sesion activa.")

    suspend fun hasRated(transactionId: String): Boolean =
        dbProvider().collection(FirebaseCollections.RATINGS)
            .document("${transactionId}_${currentUserId()}")
            .get()
            .awaitRating()
            .exists()

    suspend fun submit(transactionId: String, score: Int, comment: String) {
        require(score in 1..5) { "Selecciona una puntuacion entre 1 y 5." }
        val db = dbProvider()
        val uid = currentUserId()
        val transactionRef = db.collection(FirebaseCollections.TRANSACTIONS).document(transactionId)
        val ratingRef = db.collection(FirebaseCollections.RATINGS).document("${transactionId}_$uid")

        db.runTransaction { transaction ->
            val operation = transaction.get(transactionRef)
            val existing = transaction.get(ratingRef)
            require(operation.exists()) { "La transaccion ya no existe." }
            require(operation.getString("status") == TransactionStatus.COMPLETADO.name) {
                "Solo puedes calificar una transaccion completada."
            }
            require(!existing.exists()) { "Ya calificaste esta transaccion." }
            val buyerId = operation.getString("buyerId").orEmpty()
            val sellerId = operation.getString("sellerId").orEmpty()
            require(uid == buyerId || uid == sellerId) { "No perteneces a esta transaccion." }
            val ratedUserId = if (uid == buyerId) sellerId else buyerId
            transaction.set(
                ratingRef,
                mapOf(
                    "transactionId" to transactionId,
                    "raterId" to uid,
                    "ratedUserId" to ratedUserId,
                    "score" to score,
                    "comment" to comment.trim(),
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            null
        }.awaitRating()
    }
}

private suspend fun <T> Task<T>.awaitRating(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { continuation.resume(it) }
    addOnFailureListener { continuation.resumeWithException(it) }
    addOnCanceledListener { continuation.cancel() }
}

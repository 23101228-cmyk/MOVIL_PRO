package com.exchangepro.moviles.data.repository

import com.exchangepro.moviles.data.firebase.FirebaseCollections
import com.exchangepro.moviles.data.image.CompressedImage
import com.exchangepro.moviles.domain.model.TransactionStatus
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

enum class AttachmentType {
    VOUCHER,
    DISPUTE_EVIDENCE,
    PROFILE_PHOTO
}

class FirebaseAttachmentRepository(
    private val authProvider: () -> FirebaseAuth = { FirebaseAuth.getInstance() },
    private val dbProvider: () -> FirebaseFirestore = { FirebaseFirestore.getInstance() }
) {
    private fun currentUserId(): String =
        authProvider().currentUser?.uid ?: error("No hay una sesion activa.")

    /**
     * Guarda el comprobante como Blob separado, enlaza su id a la transaccion,
     * cambia el estado a PAGADO y genera una notificacion de revision.
     */
    suspend fun uploadVoucher(transactionId: String, image: CompressedImage): String {
        val db = dbProvider()
        val uid = currentUserId()
        val transactionRef = db.collection(FirebaseCollections.TRANSACTIONS).document(transactionId)
        val attachmentRef = db.collection(FirebaseCollections.ATTACHMENTS).document()
        val notificationRef = db.collection(FirebaseCollections.NOTIFICATIONS).document()

        db.runTransaction { transaction ->
            val record = transaction.get(transactionRef)
            require(record.exists()) { "La transaccion ya no existe." }
            require(record.getString("fundsRecipientId") == uid) {
                "No puedes adjuntar el comprobante de esta transaccion."
            }
            require(record.getString("status") == TransactionStatus.PENDIENTE_PAGO.name) {
                "La transaccion ya no admite un comprobante."
            }

            transaction.set(
                attachmentRef,
                attachmentData(
                    uid = uid,
                    type = AttachmentType.VOUCHER,
                    relatedId = transactionId,
                    image = image
                )
            )
            transaction.update(
                transactionRef,
                mapOf(
                    "voucherAttachmentId" to attachmentRef.id,
                    "status" to TransactionStatus.PAGADO.name,
                    "paidAt" to FieldValue.serverTimestamp()
                )
            )
            transaction.set(
                notificationRef,
                mapOf(
                    "userId" to record.getString("fundsOwnerId").orEmpty(),
                    "title" to "Comprobante recibido",
                    "message" to "Revisa el comprobante de ${record.getString("code").orEmpty()}.",
                    "read" to false,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            null
        }.awaitAttachment()

        return attachmentRef.id
    }

    suspend fun uploadProfilePhoto(image: CompressedImage): String {
        val db = dbProvider()
        val uid = currentUserId()
        val userRef = db.collection(FirebaseCollections.USERS).document(uid)
        val attachmentRef = db.collection(FirebaseCollections.ATTACHMENTS).document()

        db.runTransaction { transaction ->
            val user = transaction.get(userRef)
            require(user.exists()) { "No se encontro el perfil." }
            transaction.set(
                attachmentRef,
                attachmentData(
                    uid = uid,
                    type = AttachmentType.PROFILE_PHOTO,
                    relatedId = uid,
                    image = image
                )
            )
            transaction.update(userRef, "photoAttachmentId", attachmentRef.id)
            null
        }.awaitAttachment()

        return attachmentRef.id
    }

    /**
     * Adjunta evidencia a una disputa sin incrustar los bytes en el documento principal.
     */
    suspend fun uploadDisputeEvidence(disputeId: String, image: CompressedImage): String {
        val db = dbProvider()
        val uid = currentUserId()
        val disputeRef = db.collection(FirebaseCollections.DISPUTES).document(disputeId)
        val attachmentRef = db.collection(FirebaseCollections.ATTACHMENTS).document()

        db.runTransaction { transaction ->
            val dispute = transaction.get(disputeRef)
            require(dispute.exists()) { "La disputa ya no existe." }
            require(
                dispute.getString("reporterId") == uid ||
                    dispute.getString("buyerId") == uid ||
                    dispute.getString("sellerId") == uid
            ) { "No puedes adjuntar evidencia a esta disputa." }

            transaction.set(
                attachmentRef,
                attachmentData(
                    uid = uid,
                    type = AttachmentType.DISPUTE_EVIDENCE,
                    relatedId = disputeId,
                    image = image
                )
            )
            transaction.update(
                disputeRef,
                "evidenceAttachmentIds",
                FieldValue.arrayUnion(attachmentRef.id)
            )
            null
        }.awaitAttachment()

        return attachmentRef.id
    }

    suspend fun getImage(attachmentId: String): ByteArray {
        require(attachmentId.isNotBlank()) { "El identificador de imagen esta vacio." }
        val document = dbProvider()
            .collection(FirebaseCollections.ATTACHMENTS)
            .document(attachmentId)
            .get()
            .awaitAttachment()
        require(document.exists()) { "La imagen ya no existe." }
        return document.getBlob("imageData")?.toBytes()
            ?: throw IllegalStateException("El adjunto no contiene una imagen.")
    }

    private fun attachmentData(
        uid: String,
        type: AttachmentType,
        relatedId: String,
        image: CompressedImage
    ): Map<String, Any> = mapOf(
        "ownerId" to uid,
        "type" to type.name,
        "relatedId" to relatedId,
        "contentType" to image.contentType,
        "imageData" to Blob.fromBytes(image.bytes),
        "size" to image.bytes.size,
        "width" to image.width,
        "height" to image.height,
        "createdAt" to FieldValue.serverTimestamp()
    )
}

private suspend fun <T> Task<T>.awaitAttachment(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { result -> continuation.resume(result) }
    addOnFailureListener { error -> continuation.resumeWithException(error) }
    addOnCanceledListener { continuation.cancel() }
}

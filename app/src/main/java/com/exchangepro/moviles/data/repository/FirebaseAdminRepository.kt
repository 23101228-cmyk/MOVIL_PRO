package com.exchangepro.moviles.data.repository

import com.exchangepro.moviles.data.firebase.FirebaseCollections
import com.exchangepro.moviles.domain.model.TransactionStatus
import com.google.android.gms.tasks.Task
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class AdminDashboardData(
    val users: Int = 0,
    val activeOffers: Int = 0,
    val completedTransactions: Int = 0,
    val pendingDisputes: Int = 0,
    val resolvedDisputes: Int = 0,
    val pendingFeedback: Int = 0,
    val externalPaymentTransactions: Int = 0,
    val internalWalletTransactions: Int = 0,
    val transactionsByStatus: Map<String, Int> = emptyMap(),
    val volumeByCurrency: Map<String, Double> = emptyMap()
)

data class AdminDisputeRecord(
    val id: String,
    val transactionId: String,
    val transactionCode: String,
    val reason: String,
    val description: String,
    val reporterId: String,
    val buyerId: String,
    val sellerId: String,
    val amount: Double,
    val currency: String,
    val status: String,
    val evidenceCount: Int,
    val resolution: String
)

data class AdminFeedbackRecord(
    val id: String,
    val userId: String,
    val type: String,
    val title: String,
    val description: String,
    val status: String,
    val adminResponse: String
)

data class AdminReportRow(
    val id: String,
    val owner: String,
    val amount: String,
    val status: String
)

class FirebaseAdminRepository(
    private val authProvider: () -> FirebaseAuth = { FirebaseAuth.getInstance() },
    private val dbProvider: () -> FirebaseFirestore = { FirebaseFirestore.getInstance() }
) {
    private fun currentUserId(): String =
        authProvider().currentUser?.uid ?: error("No hay una sesion activa.")

    private suspend fun requireAdmin() {
        val profile = dbProvider().collection(FirebaseCollections.USERS)
            .document(currentUserId()).get().awaitAdmin()
        require(profile.getString("role") == "ADMIN") { "Esta cuenta no tiene permisos de administrador." }
    }

    /**
     * Reune conteos globales para el dashboard despues de comprobar el rol ADMIN.
     */
    suspend fun dashboard(): AdminDashboardData {
        requireAdmin()
        val db = dbProvider()
        val users = db.collection(FirebaseCollections.USERS).get().awaitAdmin()
        val offers = db.collection(FirebaseCollections.OFFERS).get().awaitAdmin()
        val transactions = db.collection(FirebaseCollections.TRANSACTIONS).get().awaitAdmin()
        val disputes = db.collection(FirebaseCollections.DISPUTES).get().awaitAdmin()
        val feedback = db.collection(FirebaseCollections.FEEDBACK).get().awaitAdmin()
        val transactionDocs = transactions.documents
        val transactionsByStatus = transactionDocs
            .groupingBy { it.getString("status").orEmpty().ifBlank { "SIN_ESTADO" } }
            .eachCount()
        val volumeByCurrency = transactionDocs
            .groupBy { it.getString("heldCurrency") ?: it.getString("fromCurrency") ?: "N/A" }
            .mapValues { entry ->
                entry.value.sumOf { it.getDouble("heldAmount") ?: it.getDouble("operationAmount") ?: 0.0 }
            }
        return AdminDashboardData(
            users = users.size(),
            activeOffers = offers.count { it.getString("status") == "ACTIVA" },
            completedTransactions = transactionDocs.count { it.getString("status") == TransactionStatus.COMPLETADO.name },
            pendingDisputes = disputes.count { it.getString("status") == "PENDIENTE" },
            resolvedDisputes = disputes.count { it.getString("status") == "RESUELTA" },
            pendingFeedback = feedback.count { it.getString("status") == "PENDIENTE" },
            externalPaymentTransactions = transactionDocs.count {
                val method = it.getString("paymentMethod").orEmpty()
                method.isNotBlank() && method != "Wallet Interna"
            },
            internalWalletTransactions = transactionDocs.count { it.getString("paymentMethod") == "Wallet Interna" },
            transactionsByStatus = transactionsByStatus,
            volumeByCurrency = volumeByCurrency
        )
    }

    suspend fun disputes(): List<AdminDisputeRecord> {
        requireAdmin()
        val snapshot = dbProvider().collection(FirebaseCollections.DISPUTES).get().awaitAdmin()
        return snapshot.documents.sortedByDescending {
            (it.get("createdAt") as? Timestamp)?.toDate()?.time ?: 0L
        }.map { dispute ->
            val transaction = dispute.getString("transactionId")?.let {
                dbProvider().collection(FirebaseCollections.TRANSACTIONS).document(it).get().awaitAdmin()
            }
            AdminDisputeRecord(
                id = dispute.id,
                transactionId = dispute.getString("transactionId").orEmpty(),
                transactionCode = dispute.getString("transactionCode").orEmpty(),
                reason = dispute.getString("reason").orEmpty(),
                description = dispute.getString("description").orEmpty(),
                reporterId = dispute.getString("reporterId").orEmpty(),
                buyerId = dispute.getString("buyerId").orEmpty(),
                sellerId = dispute.getString("sellerId").orEmpty(),
                amount = transaction?.getDouble("heldAmount") ?: 0.0,
                currency = transaction?.getString("heldCurrency").orEmpty(),
                status = dispute.getString("status").orEmpty(),
                evidenceCount = (dispute.get("evidenceAttachmentIds") as? List<*>)?.size ?: 0,
                resolution = dispute.getString("resolution").orEmpty()
            )
        }
    }

    /**
     * Emite el fallo administrativo y mueve el saldo retenido hacia la contraparte
     * o de vuelta al propietario. Estado, movimiento y avisos se confirman juntos.
     */
    suspend fun resolveDispute(disputeId: String, releaseToRecipient: Boolean, note: String) {
        require(note.isNotBlank()) { "Escribe una observacion." }
        requireAdmin()
        val db = dbProvider()
        val adminId = currentUserId()
        val disputeRef = db.collection(FirebaseCollections.DISPUTES).document(disputeId)
        val movementRef = db.collection(FirebaseCollections.WALLET_MOVEMENTS).document()
        val buyerNotification = db.collection(FirebaseCollections.NOTIFICATIONS).document()
        val sellerNotification = db.collection(FirebaseCollections.NOTIFICATIONS).document()

        db.runTransaction { tx ->
            val dispute = tx.get(disputeRef)
            require(dispute.exists() && dispute.getString("status") == "PENDIENTE") {
                "La disputa ya fue resuelta."
            }
            val transactionRef = db.collection(FirebaseCollections.TRANSACTIONS)
                .document(dispute.getString("transactionId").orEmpty())
            val operation = tx.get(transactionRef)
            require(operation.getString("status") == TransactionStatus.EN_DISPUTA.name) {
                "La transaccion no esta en disputa."
            }
            val ownerId = operation.getString("fundsOwnerId").orEmpty()
            val recipientId = operation.getString("fundsRecipientId").orEmpty()
            val currency = operation.getString("heldCurrency").orEmpty()
            val amount = operation.getDouble("heldAmount") ?: 0.0
            val ownerBalanceRef = db.collection(FirebaseCollections.WALLETS).document(ownerId)
                .collection(FirebaseCollections.BALANCES).document(currency)
            val ownerBalance = tx.get(ownerBalanceRef)
            val retained = ownerBalance.getDouble("retained") ?: 0.0
            require(retained + 0.0001 >= amount) { "El saldo retenido es insuficiente." }
            tx.set(
                ownerBalanceRef,
                mapOf(
                    "currency" to currency,
                    "available" to (ownerBalance.getDouble("available") ?: 0.0) +
                        if (releaseToRecipient) 0.0 else amount,
                    "retained" to (retained - amount).coerceAtLeast(0.0),
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            )
            val beneficiaryId = if (releaseToRecipient) recipientId else ownerId
            if (releaseToRecipient) {
                val recipientBalanceRef = db.collection(FirebaseCollections.WALLETS).document(recipientId)
                    .collection(FirebaseCollections.BALANCES).document(currency)
                val recipientBalance = tx.get(recipientBalanceRef)
                tx.set(
                    recipientBalanceRef,
                    mapOf(
                        "currency" to currency,
                        "available" to (recipientBalance.getDouble("available") ?: 0.0) + amount,
                        "retained" to (recipientBalance.getDouble("retained") ?: 0.0),
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                )
            }
            tx.update(
                transactionRef,
                mapOf(
                    "status" to if (releaseToRecipient) TransactionStatus.COMPLETADO.name
                    else TransactionStatus.CANCELADO.name,
                    "resolvedAt" to FieldValue.serverTimestamp()
                )
            )
            tx.update(
                disputeRef,
                mapOf(
                    "status" to "RESUELTA",
                    "resolution" to note.trim(),
                    "decision" to if (releaseToRecipient) "LIBERAR_A_CONTRAPARTE" else "DEVOLVER_AL_PROPIETARIO",
                    "resolvedBy" to adminId,
                    "resolvedAt" to FieldValue.serverTimestamp()
                )
            )
            tx.set(
                movementRef,
                mapOf(
                    "userId" to beneficiaryId,
                    "currency" to currency,
                    "amount" to amount,
                    "operationType" to "RESOLUCION_DISPUTA",
                    "result" to "EXITOSO",
                    "referenceType" to "DISPUTE",
                    "referenceId" to disputeId,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            val notification = mapOf(
                "title" to "Disputa resuelta",
                "message" to "La disputa de ${operation.getString("code").orEmpty()} fue resuelta.",
                "read" to false,
                "createdAt" to FieldValue.serverTimestamp()
            )
            tx.set(buyerNotification, notification + ("userId" to operation.getString("buyerId").orEmpty()))
            tx.set(sellerNotification, notification + ("userId" to operation.getString("sellerId").orEmpty()))
            null
        }.awaitAdmin()
    }

    suspend fun feedback(): List<AdminFeedbackRecord> {
        requireAdmin()
        return dbProvider().collection(FirebaseCollections.FEEDBACK).get().awaitAdmin().documents
            .sortedByDescending { (it.get("createdAt") as? Timestamp)?.toDate()?.time ?: 0L }
            .map {
                AdminFeedbackRecord(
                    id = it.id,
                    userId = it.getString("userId").orEmpty(),
                    type = it.getString("type").orEmpty(),
                    title = it.getString("title").orEmpty(),
                    description = it.getString("description").orEmpty(),
                    status = it.getString("status").orEmpty(),
                    adminResponse = it.getString("adminResponse").orEmpty()
                )
            }
    }

    /**
     * Marca feedback como revisado y entrega la respuesta mediante una notificacion.
     */
    suspend fun respondFeedback(id: String, response: String) {
        require(response.isNotBlank()) { "Escribe una respuesta." }
        requireAdmin()
        val db = dbProvider()
        val ref = db.collection(FirebaseCollections.FEEDBACK).document(id)
        val feedback = ref.get().awaitAdmin()
        require(feedback.exists()) { "El feedback ya no existe." }
        val notification = db.collection(FirebaseCollections.NOTIFICATIONS).document()
        db.runBatch { batch ->
            batch.update(
                ref,
                mapOf(
                    "status" to "REVISADO",
                    "adminResponse" to response.trim(),
                    "respondedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.set(
                notification,
                mapOf(
                    "userId" to feedback.getString("userId").orEmpty(),
                    "title" to "Respuesta a tu feedback",
                    "message" to response.trim(),
                    "read" to false,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
        }.awaitAdmin()
    }

    /**
     * Construye filas de reporte desde la coleccion seleccionada y aplica filtros
     * academicos de moneda y estado en el cliente.
     */
    suspend fun report(type: String, currency: String, status: String): List<AdminReportRow> {
        requireAdmin()
        val collection = when (type) {
            "Usuarios" -> FirebaseCollections.USERS
            "Ofertas" -> FirebaseCollections.OFFERS
            "Recargas" -> FirebaseCollections.TOP_UPS
            "Disputas" -> FirebaseCollections.DISPUTES
            else -> FirebaseCollections.TRANSACTIONS
        }
        return dbProvider().collection(collection).get().awaitAdmin().documents.mapNotNull { doc ->
            val rowStatus = doc.getString("status") ?: doc.getString("state") ?: doc.getString("role").orEmpty()
            val rowCurrency = doc.getString("heldCurrency") ?: doc.getString("currency")
                ?: doc.getString("fromCurrency").orEmpty()
            if (currency != "Todas" && rowCurrency != currency) return@mapNotNull null
            if (status != "Todos" && rowStatus != status) return@mapNotNull null
            AdminReportRow(
                id = doc.id.take(8),
                owner = doc.getString("userName") ?: doc.getString("fullName")
                    ?: doc.getString("userId")?.take(8) ?: "-",
                amount = (doc.getDouble("heldAmount") ?: doc.getDouble("amount")
                    ?: doc.getDouble("operationAmount"))?.let { "%.2f %s".format(it, rowCurrency) } ?: "-",
                status = rowStatus
            )
        }
    }
}

private suspend fun <T> Task<T>.awaitAdmin(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { continuation.resume(it) }
    addOnFailureListener { continuation.resumeWithException(it) }
    addOnCanceledListener { continuation.cancel() }
}

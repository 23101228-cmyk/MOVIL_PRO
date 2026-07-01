package com.exchangepro.moviles.data.repository

import com.exchangepro.moviles.data.firebase.FirebaseCollections
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class UserPaymentData(
    val yape: String = "",
    val plin: String = "",
    val bankName: String = "",
    val accountNumber: String = "",
    val cci: String = ""
) {
    fun availableDestinations(): List<PaymentDestination> = buildList {
        if (yape.isNotBlank()) add(PaymentDestination("YAPE", "Yape", yape))
        if (plin.isNotBlank()) add(PaymentDestination("PLIN", "Plin", plin))
        if (bankName.isNotBlank() && accountNumber.isNotBlank()) {
            add(
                PaymentDestination(
                    key = "BANK",
                    label = bankName,
                    detail = if (cci.isBlank()) accountNumber else "$accountNumber / CCI $cci"
                )
            )
        }
    }
}

data class PaymentDestination(
    val key: String,
    val label: String,
    val detail: String
)

class FirebasePaymentDataRepository(
    private val authProvider: () -> FirebaseAuth = { FirebaseAuth.getInstance() },
    private val dbProvider: () -> FirebaseFirestore = { FirebaseFirestore.getInstance() }
) {
    private fun currentUserId(): String =
        authProvider().currentUser?.uid ?: error("No hay una sesion activa.")

    suspend fun get(): UserPaymentData {
        val document = dbProvider()
            .collection(FirebaseCollections.PAYMENT_DATA)
            .document(currentUserId())
            .get()
            .awaitPayment()

        if (!document.exists()) return UserPaymentData()
        return UserPaymentData(
            yape = document.getString("yape").orEmpty(),
            plin = document.getString("plin").orEmpty(),
            bankName = document.getString("bankName").orEmpty(),
            accountNumber = document.getString("accountNumber").orEmpty(),
            cci = document.getString("cci").orEmpty()
        )
    }

    suspend fun save(data: UserPaymentData) {
        require(data.availableDestinations().isNotEmpty()) {
            "Debes proporcionar al menos un metodo de pago."
        }
        val uid = currentUserId()
        val db = dbProvider()
        db
            .collection(FirebaseCollections.PAYMENT_DATA)
            .document(uid)
            .set(
                mapOf(
                    "userId" to uid,
                    "yape" to data.yape,
                    "plin" to data.plin,
                    "bankName" to data.bankName,
                    "accountNumber" to data.accountNumber,
                    "cci" to data.cci,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            .awaitPayment()
        syncActiveOffers(db, uid, data)
    }

    suspend fun delete() {
        val db = dbProvider()
        val uid = currentUserId()
        db
            .collection(FirebaseCollections.PAYMENT_DATA)
            .document(uid)
            .delete()
            .awaitPayment()
        syncActiveOffers(db, uid, UserPaymentData())
    }

    /**
     * Mantiene las ofertas ya publicadas alineadas con los destinos configurados
     * por su propietario, sin obligarlo a cancelarlas y crearlas nuevamente.
     */
    private suspend fun syncActiveOffers(
        db: FirebaseFirestore,
        uid: String,
        data: UserPaymentData
    ) {
        val details = paymentMethodDetails(data)
        val activeOffers = db.collection(FirebaseCollections.OFFERS)
            .whereEqualTo("userId", uid)
            .get()
            .awaitPayment()
            .documents
            .filter { it.getString("status") == "ACTIVA" }

        if (activeOffers.isEmpty()) return

        val batch = db.batch()
        activeOffers.forEach { offer ->
            batch.update(
                offer.reference,
                mapOf(
                    "paymentMethods" to details.keys.toList(),
                    "paymentMethodDetails" to details,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
        }
        batch.commit().awaitPayment()
    }
}

private fun paymentMethodDetails(data: UserPaymentData): Map<String, String> = buildMap {
    put("Wallet Interna", "Saldo interno ExchangePro")
    if (data.yape.isNotBlank()) put("Yape", data.yape)
    if (data.plin.isNotBlank()) put("Plin", data.plin)
    if (data.bankName.isNotBlank() && data.accountNumber.isNotBlank()) {
        put(
            "Transferencia Bancaria",
            buildString {
                append(data.bankName)
                append(" - Cuenta ")
                append(data.accountNumber)
                if (data.cci.isNotBlank()) {
                    append(" / CCI ")
                    append(data.cci)
                }
            }
        )
    }
}

private suspend fun <T> Task<T>.awaitPayment(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { result -> continuation.resume(result) }
    addOnFailureListener { error -> continuation.resumeWithException(error) }
    addOnCanceledListener { continuation.cancel() }
}

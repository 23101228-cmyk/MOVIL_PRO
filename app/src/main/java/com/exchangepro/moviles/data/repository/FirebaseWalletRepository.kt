package com.exchangepro.moviles.data.repository

import com.exchangepro.moviles.data.firebase.FirebaseCollections
import com.exchangepro.moviles.domain.model.CurrencyCode
import com.exchangepro.moviles.domain.model.TopUpRequest
import com.exchangepro.moviles.domain.model.Wallet
import com.exchangepro.moviles.domain.model.WalletBalance
import com.exchangepro.moviles.domain.model.WalletMovement
import com.exchangepro.moviles.domain.model.WithdrawalRequest
import com.google.android.gms.tasks.Task
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class FirebaseWalletRepository(
    private val authProvider: () -> FirebaseAuth = { FirebaseAuth.getInstance() },
    private val dbProvider: () -> FirebaseFirestore = { FirebaseFirestore.getInstance() }
) {
    private fun userId(): String =
        authProvider().currentUser?.uid ?: error("No hay una sesion activa.")

    suspend fun getWallet(): Wallet {
        val db = dbProvider()
        val uid = userId()
        val balancesSnapshot = db.collection(FirebaseCollections.WALLETS)
            .document(uid)
            .collection(FirebaseCollections.BALANCES)
            .get()
            .await()

        val balances = balancesSnapshot.documents.mapNotNull { it.toWalletBalance() }

        return Wallet(userId = uid, balances = balances.sortedBy { it.currency.ordinal })
    }

    suspend fun getMovements(limit: Long = 20): List<WalletMovement> {
        val db = dbProvider()
        val snapshot = db.collection(FirebaseCollections.WALLET_MOVEMENTS)
            .whereEqualTo("userId", userId())
            .get()
            .await()

        return snapshot.documents
            .mapNotNull { it.toWalletMovement() }
            .sortedByDescending { it.createdAtMillis }
            .take(limit.toInt())
    }

    /**
     * Simula una recarga academica de forma atomica: incrementa el balance y crea
     * tanto el registro topUp como su movimiento de auditoria.
     */
    suspend fun topUp(request: TopUpRequest) {
        val db = dbProvider()
        val uid = userId()
        val walletRef = db.collection(FirebaseCollections.WALLETS).document(uid)
        val balanceRef = walletRef.collection(FirebaseCollections.BALANCES).document(request.currency.name)
        val topUpRef = db.collection(FirebaseCollections.TOP_UPS).document()
        val movementRef = db.collection(FirebaseCollections.WALLET_MOVEMENTS).document()

        db.runTransaction { transaction ->
            val balanceSnapshot = transaction.get(balanceRef)
            val current = balanceSnapshot.getDouble("available") ?: 0.0
            val retained = balanceSnapshot.getDouble("retained") ?: 0.0

            transaction.set(
                walletRef,
                mapOf(
                    "userId" to uid,
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            )
            transaction.set(
                balanceRef,
                mapOf(
                    "currency" to request.currency.name,
                    "available" to current + request.amount,
                    "retained" to retained,
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            )
            transaction.set(
                topUpRef,
                mapOf(
                    "userId" to uid,
                    "currency" to request.currency.name,
                    "amount" to request.amount,
                    "paymentMethod" to request.paymentMethod,
                    "referenceNumber" to request.referenceNumber,
                    "status" to "COMPLETADA",
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            transaction.set(
                movementRef,
                mapOf(
                    "userId" to uid,
                    "currency" to request.currency.name,
                    "amount" to request.amount,
                    "operationType" to "RECARGA",
                    "result" to "EXITOSO",
                    "referenceType" to request.paymentMethod,
                    "referenceId" to topUpRef.id,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            null
        }.await()
    }

    /**
     * Descuenta saldo disponible y registra retiro/movimiento en una sola transaccion.
     * El destino debe existir previamente en paymentData/{uid}.
     */
    suspend fun withdraw(request: WithdrawalRequest) {
        require(request.amount > 0.0) { "El monto debe ser mayor a 0." }
        val db = dbProvider()
        val uid = userId()
        val walletRef = db.collection(FirebaseCollections.WALLETS).document(uid)
        val balanceRef = walletRef.collection(FirebaseCollections.BALANCES).document(request.currency.name)
        val paymentRef = db.collection(FirebaseCollections.PAYMENT_DATA).document(uid)
        val withdrawalRef = db.collection(FirebaseCollections.WITHDRAWALS).document()
        val movementRef = db.collection(FirebaseCollections.WALLET_MOVEMENTS).document()

        db.runTransaction { transaction ->
            val balance = transaction.get(balanceRef)
            val payment = transaction.get(paymentRef)
            require(payment.exists()) { "Primero registra tus datos de pago." }

            val destination = when (request.paymentMethodKey) {
                "YAPE" -> payment.getString("yape").orEmpty()
                "PLIN" -> payment.getString("plin").orEmpty()
                "BANK" -> payment.getString("accountNumber").orEmpty()
                else -> ""
            }
            require(destination.isNotBlank()) { "El metodo de retiro seleccionado no esta configurado." }

            val available = balance.getDouble("available") ?: 0.0
            val retained = balance.getDouble("retained") ?: 0.0
            require(available >= request.amount) {
                "Saldo insuficiente. Disponible: %.2f %s.".format(available, request.currency.name)
            }

            transaction.set(
                walletRef,
                mapOf(
                    "userId" to uid,
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            )
            transaction.set(
                balanceRef,
                mapOf(
                    "currency" to request.currency.name,
                    "available" to available - request.amount,
                    "retained" to retained,
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            )
            transaction.set(
                withdrawalRef,
                mapOf(
                    "userId" to uid,
                    "currency" to request.currency.name,
                    "amount" to request.amount,
                    "paymentMethod" to request.paymentMethodKey,
                    "destination" to destination,
                    "bankName" to payment.getString("bankName"),
                    "status" to "COMPLETADO",
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            transaction.set(
                movementRef,
                mapOf(
                    "userId" to uid,
                    "currency" to request.currency.name,
                    "amount" to -request.amount,
                    "operationType" to "RETIRO",
                    "result" to "EXITOSO",
                    "referenceType" to request.paymentMethodKey,
                    "referenceId" to withdrawalRef.id,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            null
        }.await()
    }

    private fun DocumentSnapshot.toWalletBalance(): WalletBalance? {
        val currency = runCatching { CurrencyCode.valueOf(id) }.getOrNull()
            ?: runCatching { CurrencyCode.valueOf(getString("currency").orEmpty()) }.getOrNull()
            ?: return null

        return WalletBalance(
            currency = currency,
            available = getDouble("available") ?: 0.0,
            retained = getDouble("retained") ?: 0.0
        )
    }

    private fun DocumentSnapshot.toWalletMovement(): WalletMovement? {
        val currency = runCatching { CurrencyCode.valueOf(getString("currency").orEmpty()) }.getOrNull()
            ?: return null
        val timestamp = getTimestamp("createdAt") ?: get("createdAt") as? Timestamp

        return WalletMovement(
            id = id,
            currency = currency,
            amount = getDouble("amount") ?: 0.0,
            operationType = getString("operationType").orEmpty(),
            result = getString("result").orEmpty(),
            referenceType = getString("referenceType"),
            referenceId = getString("referenceId"),
            createdAtMillis = timestamp?.toDate()?.time
        )
    }

}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { result -> continuation.resume(result) }
    addOnFailureListener { error -> continuation.resumeWithException(error) }
    addOnCanceledListener { continuation.cancel() }
}

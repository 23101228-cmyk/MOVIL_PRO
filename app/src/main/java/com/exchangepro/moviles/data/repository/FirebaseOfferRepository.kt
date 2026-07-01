package com.exchangepro.moviles.data.repository

import com.exchangepro.moviles.data.firebase.FirebaseCollections
import com.exchangepro.moviles.domain.model.CreateOfferRequest
import com.exchangepro.moviles.domain.model.CurrencyCode
import com.exchangepro.moviles.domain.model.Offer
import com.exchangepro.moviles.domain.model.OfferStatus
import com.exchangepro.moviles.domain.model.OperationType
import com.google.android.gms.tasks.Task
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class FirebaseOfferRepository(
    private val authProvider: () -> FirebaseAuth = { FirebaseAuth.getInstance() },
    private val dbProvider: () -> FirebaseFirestore = { FirebaseFirestore.getInstance() }
) {
    fun currentUserId(): String =
        authProvider().currentUser?.uid ?: error("No hay una sesion activa.")

    fun observeActiveOffers(): Flow<List<Offer>> = callbackFlow {
        val uid = currentUserId()
        val registration = dbProvider()
            .collection(FirebaseCollections.OFFERS)
            .whereEqualTo("status", OfferStatus.ACTIVA.name)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(
                    snapshot?.documents
                        .orEmpty()
                        .sortedByDescending { it.createdAtMillis() }
                        .mapNotNull { it.toOffer() }
                        .filter { it.userId != uid }
                )
            }
        awaitClose { registration.remove() }
    }

    fun observeMyActiveOffers(): Flow<List<Offer>> = callbackFlow {
        val uid = currentUserId()
        val registration = dbProvider()
            .collection(FirebaseCollections.OFFERS)
            .whereEqualTo("userId", uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(
                    snapshot?.documents
                        .orEmpty()
                        .sortedByDescending { it.createdAtMillis() }
                        .mapNotNull { it.toOffer() }
                        .filter { it.status == OfferStatus.ACTIVA }
                )
            }
        awaitClose { registration.remove() }
    }

    suspend fun getActiveOffers(): List<Offer> {
        val uid = currentUserId()
        val snapshot = dbProvider()
            .collection(FirebaseCollections.OFFERS)
            .whereEqualTo("status", OfferStatus.ACTIVA.name)
            .get()
            .awaitOffer()

        return snapshot.documents
            .sortedByDescending { it.createdAtMillis() }
            .mapNotNull { it.toOffer() }
            .filter { it.userId != uid }
    }

    suspend fun getMyActiveOffers(): List<Offer> {
        val snapshot = dbProvider()
            .collection(FirebaseCollections.OFFERS)
            .whereEqualTo("userId", currentUserId())
            .get()
            .awaitOffer()

        return snapshot.documents
            .sortedByDescending { it.createdAtMillis() }
            .mapNotNull { it.toOffer() }
            .filter { it.status == OfferStatus.ACTIVA }
    }

    /**
     * Retiene el saldo que respalda la oferta y crea el documento offers/{id}
     * atomicamente, evitando publicar ofertas sin fondos.
     */
    suspend fun createOffer(request: CreateOfferRequest) {
        require(request.offeredAmount > 0.0) { "El monto ofertado debe ser mayor a 0." }
        require(request.minimumAmount > 0.0) { "El monto minimo debe ser mayor a 0." }
        require(request.minimumAmount <= request.offeredAmount) { "El monto minimo no puede ser mayor que el monto ofertado." }
        require(request.exchangeRate > 0.0) { "La tasa de cambio debe ser mayor a 0." }
        val db = dbProvider()
        val uid = currentUserId()
        val holdCurrency = if (request.operationType == OperationType.COMPRA) {
            request.toCurrency
        } else {
            request.fromCurrency
        }
        val requiredAmount = if (request.operationType == OperationType.COMPRA) {
            request.offeredAmount * request.exchangeRate
        } else {
            request.offeredAmount
        }

        val walletRef = db.collection(FirebaseCollections.WALLETS).document(uid)
        val balanceRef = walletRef.collection(FirebaseCollections.BALANCES).document(holdCurrency.name)
        val userRef = db.collection(FirebaseCollections.USERS).document(uid)
        val paymentDataRef = db.collection(FirebaseCollections.PAYMENT_DATA).document(uid)
        val offerRef = db.collection(FirebaseCollections.OFFERS).document()

        db.runTransaction { transaction ->
            val balance = transaction.get(balanceRef)
            val user = transaction.get(userRef)
            val paymentData = transaction.get(paymentDataRef)
            val available = balance.getDouble("available") ?: 0.0
            val retained = balance.getDouble("retained") ?: 0.0
            val userName = user.getString("fullName").orEmpty()
            val yape = paymentData.getString("yape").orEmpty()
            val plin = paymentData.getString("plin").orEmpty()
            val bankName = paymentData.getString("bankName").orEmpty()
            val accountNumber = paymentData.getString("accountNumber").orEmpty()
            val cci = paymentData.getString("cci").orEmpty()
            val paymentMethodDetails = buildMap {
                put("Wallet Interna", "Saldo interno ExchangePro")
                if (yape.isNotBlank()) put("Yape", yape)
                if (plin.isNotBlank()) put("Plin", plin)
                if (bankName.isNotBlank() && accountNumber.isNotBlank()) {
                    put(
                        "Transferencia Bancaria",
                        buildString {
                            append(bankName)
                            append(" - Cuenta ")
                            append(accountNumber)
                            if (cci.isNotBlank()) {
                                append(" / CCI ")
                                append(cci)
                            }
                        }
                    )
                }
            }

            require(available >= requiredAmount) {
                "Fondos insuficientes en ${holdCurrency.name}. Necesitas %.2f y tienes %.2f.".format(requiredAmount, available)
            }
            require(userName.isNotBlank()) { "El perfil no tiene un nombre valido." }

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
                    "currency" to holdCurrency.name,
                    "available" to available - requiredAmount,
                    "retained" to retained + requiredAmount,
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            )
            transaction.set(
                offerRef,
                mapOf(
                    "userId" to uid,
                    "userName" to userName,
                    "operationType" to request.operationType.name,
                    "fromCurrency" to request.fromCurrency.name,
                    "toCurrency" to request.toCurrency.name,
                    "exchangeRate" to request.exchangeRate,
                    "offeredAmount" to request.offeredAmount,
                    "minimumAmount" to request.minimumAmount,
                    "paymentMethods" to paymentMethodDetails.keys.toList(),
                    "paymentMethodDetails" to paymentMethodDetails,
                    "status" to "ACTIVA",
                    "heldCurrency" to holdCurrency.name,
                    "heldAmount" to requiredAmount,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            null
        }.awaitOffer()
    }

    /**
     * Cancela una oferta propia y devuelve su retencion al saldo disponible.
     */
    suspend fun cancelOffer(offerId: String) {
        val db = dbProvider()
        val uid = currentUserId()
        val offerRef = db.collection(FirebaseCollections.OFFERS).document(offerId)
        val walletRef = db.collection(FirebaseCollections.WALLETS).document(uid)

        db.runTransaction { transaction ->
            val offer = transaction.get(offerRef)
            require(offer.exists()) { "La oferta ya no existe." }
            require(offer.getString("userId") == uid) { "No puedes cancelar una oferta ajena." }
            require(offer.getString("status") == OfferStatus.ACTIVA.name) { "La oferta ya no esta activa." }

            val heldCurrency = offer.getString("heldCurrency")
                ?: throw IllegalStateException("La oferta no tiene moneda retenida.")
            val heldAmount = offer.getDouble("heldAmount") ?: 0.0
            val balanceRef = walletRef.collection(FirebaseCollections.BALANCES).document(heldCurrency)
            val balance = transaction.get(balanceRef)
            val available = balance.getDouble("available") ?: 0.0
            val retained = balance.getDouble("retained") ?: 0.0

            transaction.set(
                balanceRef,
                mapOf(
                    "currency" to heldCurrency,
                    "available" to available + heldAmount,
                    "retained" to (retained - heldAmount).coerceAtLeast(0.0),
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            )
            transaction.update(
                offerRef,
                mapOf(
                    "status" to OfferStatus.CANCELADA.name,
                    "cancelledAt" to FieldValue.serverTimestamp()
                )
            )
            null
        }.awaitOffer()
    }

    private fun DocumentSnapshot.toOffer(): Offer? {
        val operationType = enumValue<OperationType>("operationType") ?: return null
        val fromCurrency = enumValue<CurrencyCode>("fromCurrency") ?: return null
        val toCurrency = enumValue<CurrencyCode>("toCurrency") ?: return null
        val status = enumValue<OfferStatus>("status") ?: return null

        return Offer(
            id = id,
            userId = getString("userId").orEmpty(),
            userName = getString("userName").orEmpty(),
            operationType = operationType,
            fromCurrency = fromCurrency,
            toCurrency = toCurrency,
            exchangeRate = getDouble("exchangeRate") ?: return null,
            offeredAmount = getDouble("offeredAmount") ?: return null,
            minimumAmount = getDouble("minimumAmount") ?: return null,
            paymentMethods = (get("paymentMethods") as? List<*>)
                ?.mapNotNull { it as? String }
                .orEmpty(),
            paymentMethodDetails = (get("paymentMethodDetails") as? Map<*, *>)
                ?.mapNotNull { (key, value) ->
                    val method = key as? String
                    val detail = value as? String
                    if (method != null && detail != null) method to detail else null
                }
                ?.toMap()
                .orEmpty(),
            status = status
        )
    }

    private inline fun <reified T : Enum<T>> DocumentSnapshot.enumValue(field: String): T? =
        getString(field)?.let { value -> enumValues<T>().firstOrNull { it.name == value } }

    private fun DocumentSnapshot.createdAtMillis(): Long =
        (get("createdAt") as? Timestamp)?.toDate()?.time ?: 0L
}

private suspend fun <T> Task<T>.awaitOffer(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { result -> continuation.resume(result) }
    addOnFailureListener { error -> continuation.resumeWithException(error) }
    addOnCanceledListener { continuation.cancel() }
}

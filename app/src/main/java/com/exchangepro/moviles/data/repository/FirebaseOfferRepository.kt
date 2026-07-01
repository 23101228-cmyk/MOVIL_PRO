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
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class FirebaseOfferRepository(
    private val authProvider: () -> FirebaseAuth = { FirebaseAuth.getInstance() },
    private val dbProvider: () -> FirebaseFirestore = { FirebaseFirestore.getInstance() }
) {
    fun currentUserId(): String =
        authProvider().currentUser?.uid ?: error("No hay una sesion activa.")

    suspend fun getActiveOffers(): List<Offer> {
        val snapshot = dbProvider()
            .collection(FirebaseCollections.OFFERS)
            .whereEqualTo("status", OfferStatus.ACTIVA.name)
            .get()
            .awaitOffer()

        return snapshot.documents
            .sortedByDescending { it.createdAtMillis() }
            .mapNotNull { it.toOffer() }
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
        val offerRef = db.collection(FirebaseCollections.OFFERS).document()

        db.runTransaction { transaction ->
            val balance = transaction.get(balanceRef)
            val user = transaction.get(userRef)
            val available = balance.getDouble("available") ?: 0.0
            val retained = balance.getDouble("retained") ?: 0.0
            val userName = user.getString("fullName").orEmpty()

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
                    "paymentMethods" to listOf("WALLET_INTERNA"),
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

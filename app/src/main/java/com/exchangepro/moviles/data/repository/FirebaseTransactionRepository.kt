package com.exchangepro.moviles.data.repository

import com.exchangepro.moviles.data.firebase.FirebaseCollections
import com.exchangepro.moviles.domain.model.CurrencyCode
import com.exchangepro.moviles.domain.model.OfferStatus
import com.exchangepro.moviles.domain.model.OperationType
import com.exchangepro.moviles.domain.model.Transaction
import com.exchangepro.moviles.domain.model.TransactionStatus
import com.google.android.gms.tasks.Task
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Calendar
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class FirebaseTransactionRepository(
    private val authProvider: () -> FirebaseAuth = { FirebaseAuth.getInstance() },
    private val dbProvider: () -> FirebaseFirestore = { FirebaseFirestore.getInstance() }
) {
    fun currentUserId(): String =
        authProvider().currentUser?.uid ?: error("No hay una sesion activa.")

    /**
     * Convierte una parte de una oferta activa en transaccion P2P: valida montos,
     * asigna comprador/vendedor, reduce la oferta y crea la operacion atomicamente.
     */
    suspend fun createFromOffer(
        offerId: String,
        amount: Double,
        paymentMethod: String
    ): Transaction {
        val db = dbProvider()
        val takerId = currentUserId()
        val offerRef = db.collection(FirebaseCollections.OFFERS).document(offerId)
        val takerRef = db.collection(FirebaseCollections.USERS).document(takerId)
        val transactionRef = db.collection(FirebaseCollections.TRANSACTIONS).document()
        val ownerNotificationRef = db.collection(FirebaseCollections.NOTIFICATIONS).document()
        val takerNotificationRef = db.collection(FirebaseCollections.NOTIFICATIONS).document()
        val internalMovementRefs = List(4) {
            db.collection(FirebaseCollections.WALLET_MOVEMENTS).document()
        }
        var created: Transaction? = null

        db.runTransaction { firestoreTransaction ->
            val offer = firestoreTransaction.get(offerRef)
            val taker = firestoreTransaction.get(takerRef)

            require(offer.exists()) { "La oferta ya no existe." }
            require(offer.getString("status") == OfferStatus.ACTIVA.name) { "La oferta ya no esta activa." }

            val ownerId = offer.getString("userId").orEmpty()
            require(ownerId.isNotBlank() && ownerId != takerId) { "No puedes tomar tu propia oferta." }

            val ownerName = offer.getString("userName").orEmpty()
            val takerName = taker.getString("fullName").orEmpty()
            require(takerName.isNotBlank()) { "Tu perfil no tiene un nombre valido." }

            val operationType = offer.enumValue<OperationType>("operationType")
                ?: throw IllegalStateException("La oferta tiene un tipo invalido.")
            val fromCurrency = offer.enumValue<CurrencyCode>("fromCurrency")
                ?: throw IllegalStateException("La oferta tiene una moneda invalida.")
            val toCurrency = offer.enumValue<CurrencyCode>("toCurrency")
                ?: throw IllegalStateException("La oferta tiene una moneda destino invalida.")
            val exchangeRate = offer.getDouble("exchangeRate") ?: 0.0
            val availableAmount = offer.getDouble("offeredAmount") ?: 0.0
            val minimumAmount = offer.getDouble("minimumAmount") ?: 0.0
            val offerHeldAmount = offer.getDouble("heldAmount") ?: 0.0
            val offeredPaymentMethods = (offer.get("paymentMethods") as? List<*>)
                ?.mapNotNull { it as? String }
                .orEmpty()
            val paymentMethodDetails = (offer.get("paymentMethodDetails") as? Map<*, *>)
                .orEmpty()
            val normalizedPaymentMethod = paymentMethodLabel(paymentMethod)
            require(
                normalizedPaymentMethod == "Wallet Interna" ||
                    offeredPaymentMethods.any { paymentMethodLabel(it) == normalizedPaymentMethod }
            ) {
                "El metodo de pago seleccionado ya no esta disponible."
            }
            val paymentDetail = paymentMethodDetails.entries
                .firstOrNull { paymentMethodLabel(it.key.toString()) == normalizedPaymentMethod }
                ?.value
                ?.toString()
                .orEmpty()
            val heldCurrency = offer.enumValue<CurrencyCode>("heldCurrency")
                ?: throw IllegalStateException("La oferta no tiene una moneda retenida valida.")

            require(amount >= minimumAmount && amount <= availableAmount) {
                "El monto debe estar entre %.2f y %.2f.".format(minimumAmount, availableAmount)
            }
            require(exchangeRate > 0.0) { "La tasa de la oferta no es valida." }

            val transactionHeldAmount = if (operationType == OperationType.COMPRA) {
                amount * exchangeRate
            } else {
                amount
            }
            val isInternalWallet = normalizedPaymentMethod == "Wallet Interna"
            if (!isInternalWallet) {
                require(paymentDetail.isNotBlank()) {
                    "La oferta no contiene los datos del metodo de pago seleccionado."
                }
            }
            val counterCurrency = if (operationType == OperationType.COMPRA) {
                fromCurrency
            } else {
                toCurrency
            }
            val counterAmount = if (operationType == OperationType.COMPRA) {
                amount
            } else {
                amount * exchangeRate
            }
            require(offerHeldAmount + 0.0001 >= transactionHeldAmount) {
                "La oferta no tiene fondos retenidos suficientes."
            }

            val takerPaymentBalanceRef = db.collection(FirebaseCollections.WALLETS)
                .document(takerId)
                .collection(FirebaseCollections.BALANCES)
                .document(counterCurrency.name)
            val takerPaymentBalance = if (isInternalWallet) {
                firestoreTransaction.get(takerPaymentBalanceRef)
            } else {
                null
            }
            val takerAvailable = takerPaymentBalance?.getDouble("available") ?: 0.0
            if (isInternalWallet) {
                require(takerAvailable + 0.0001 >= counterAmount) {
                    "Saldo insuficiente en ${counterCurrency.name}. Necesitas %.2f y tienes %.2f."
                        .format(counterAmount, takerAvailable)
                }
            }

            val buyerId: String
            val buyerName: String
            val sellerId: String
            val sellerName: String
            if (operationType == OperationType.COMPRA) {
                buyerId = ownerId
                buyerName = ownerName
                sellerId = takerId
                sellerName = takerName
            } else {
                buyerId = takerId
                buyerName = takerName
                sellerId = ownerId
                sellerName = ownerName
            }

            val remainingAmount = (availableAmount - amount).coerceAtLeast(0.0)
            val remainingHeld = (offerHeldAmount - transactionHeldAmount).coerceAtLeast(0.0)
            val code = "EX-${Calendar.getInstance().get(Calendar.YEAR)}-${transactionRef.id.take(6).uppercase()}"
            val fundsRecipientId = if (ownerId == buyerId) sellerId else buyerId

            created = Transaction(
                id = transactionRef.id,
                code = code,
                offerId = offerId,
                buyerId = buyerId,
                buyerName = buyerName,
                sellerId = sellerId,
                sellerName = sellerName,
                paymentMethod = normalizedPaymentMethod,
                paymentDetail = paymentDetail,
                operationAmount = amount,
                totalToPay = amount * exchangeRate,
                currency = fromCurrency,
                status = if (isInternalWallet) {
                    TransactionStatus.COMPLETADO
                } else {
                    TransactionStatus.PENDIENTE_PAGO
                },
                toCurrency = toCurrency,
                fundsOwnerId = ownerId,
                fundsRecipientId = fundsRecipientId,
                heldCurrency = heldCurrency,
                heldAmount = transactionHeldAmount
            )

            firestoreTransaction.update(
                offerRef,
                mapOf(
                    "offeredAmount" to 0.0,
                    "heldAmount" to 0.0,
                    "status" to OfferStatus.COMPLETADA.name,
                    "takenAmount" to amount,
                    "unusedAmount" to remainingAmount,
                    "completedAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            firestoreTransaction.set(
                transactionRef,
                mapOf(
                    "code" to code,
                    "offerId" to offerId,
                    "offerOperationType" to operationType.name,
                    "buyerId" to buyerId,
                    "buyerName" to buyerName,
                    "sellerId" to sellerId,
                    "sellerName" to sellerName,
                    "paymentMethod" to normalizedPaymentMethod,
                    "paymentDetail" to paymentDetail,
                    "operationAmount" to amount,
                    "totalToPay" to amount * exchangeRate,
                    "fromCurrency" to fromCurrency.name,
                    "toCurrency" to toCurrency.name,
                    "status" to if (isInternalWallet) {
                        TransactionStatus.COMPLETADO.name
                    } else {
                        TransactionStatus.PENDIENTE_PAGO.name
                    },
                    "voucherUrl" to null,
                    "fundsOwnerId" to ownerId,
                    "fundsRecipientId" to fundsRecipientId,
                    "heldCurrency" to heldCurrency.name,
                    "heldAmount" to transactionHeldAmount,
                    "offerMinimumAmount" to minimumAmount,
                    "completedAt" to if (isInternalWallet) FieldValue.serverTimestamp() else null,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )

            if (isInternalWallet) {
                val ownerHeldBalanceRef = db.collection(FirebaseCollections.WALLETS)
                    .document(ownerId)
                    .collection(FirebaseCollections.BALANCES)
                    .document(heldCurrency.name)
                val ownerReceiveBalanceRef = db.collection(FirebaseCollections.WALLETS)
                    .document(ownerId)
                    .collection(FirebaseCollections.BALANCES)
                    .document(counterCurrency.name)
                val takerReceiveBalanceRef = db.collection(FirebaseCollections.WALLETS)
                    .document(takerId)
                    .collection(FirebaseCollections.BALANCES)
                    .document(heldCurrency.name)

                firestoreTransaction.set(
                    ownerHeldBalanceRef,
                    mapOf(
                        "currency" to heldCurrency.name,
                        "available" to FieldValue.increment(remainingHeld),
                        "retained" to FieldValue.increment(-offerHeldAmount),
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                )
                firestoreTransaction.set(
                    takerPaymentBalanceRef,
                    mapOf(
                        "currency" to counterCurrency.name,
                        "available" to (takerAvailable - counterAmount).coerceAtLeast(0.0),
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                )
                firestoreTransaction.set(
                    ownerReceiveBalanceRef,
                    mapOf(
                        "currency" to counterCurrency.name,
                        "available" to FieldValue.increment(counterAmount),
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                )
                firestoreTransaction.set(
                    takerReceiveBalanceRef,
                    mapOf(
                        "currency" to heldCurrency.name,
                        "available" to FieldValue.increment(transactionHeldAmount),
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                )

                firestoreTransaction.set(
                    internalMovementRefs[0],
                    walletMovementData(ownerId, heldCurrency, -transactionHeldAmount, transactionRef.id)
                )
                firestoreTransaction.set(
                    internalMovementRefs[1],
                    walletMovementData(takerId, heldCurrency, transactionHeldAmount, transactionRef.id)
                )
                firestoreTransaction.set(
                    internalMovementRefs[2],
                    walletMovementData(takerId, counterCurrency, -counterAmount, transactionRef.id)
                )
                firestoreTransaction.set(
                    internalMovementRefs[3],
                    walletMovementData(ownerId, counterCurrency, counterAmount, transactionRef.id)
                )
            } else if (remainingHeld > 0.0001) {
                val ownerHeldBalanceRef = db.collection(FirebaseCollections.WALLETS)
                    .document(ownerId)
                    .collection(FirebaseCollections.BALANCES)
                    .document(heldCurrency.name)
                firestoreTransaction.set(
                    ownerHeldBalanceRef,
                    mapOf(
                        "currency" to heldCurrency.name,
                        "available" to FieldValue.increment(remainingHeld),
                        "retained" to FieldValue.increment(-remainingHeld),
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                )
            }
            firestoreTransaction.set(
                ownerNotificationRef,
                notificationData(
                    userId = ownerId,
                    title = if (isInternalWallet) "Intercambio completado" else "Nueva transaccion",
                    message = if (isInternalWallet) {
                        "La operacion $code se completo con Wallet Interna."
                    } else {
                        "La operacion $code fue iniciada. Espera que la contraparte registre el pago."
                    }
                )
            )
            firestoreTransaction.set(
                takerNotificationRef,
                notificationData(
                    userId = takerId,
                    title = if (isInternalWallet) "Intercambio completado" else "Operacion iniciada",
                    message = if (isInternalWallet) {
                        "La operacion $code se completo con Wallet Interna."
                    } else {
                        "La operacion $code fue iniciada. Realiza el pago y adjunta el comprobante."
                    }
                )
            )
            null
        }.awaitTransaction()

        return created ?: error("No se pudo crear la transaccion.")
    }

    suspend fun getMyTransactions(): List<Transaction> {
        val db = dbProvider()
        val uid = currentUserId()
        val buyerDocuments = db.collection(FirebaseCollections.TRANSACTIONS)
            .whereEqualTo("buyerId", uid)
            .get()
            .awaitTransaction()
            .documents
        val sellerDocuments = db.collection(FirebaseCollections.TRANSACTIONS)
            .whereEqualTo("sellerId", uid)
            .get()
            .awaitTransaction()
            .documents

        return (buyerDocuments + sellerDocuments)
            .distinctBy { it.id }
            .sortedByDescending { it.createdAtMillis() }
            .mapNotNull { it.toTransaction() }
    }

    suspend fun markPaymentSent(transactionId: String) {
        updateStatus(
            transactionId = transactionId,
            expected = TransactionStatus.PENDIENTE_PAGO,
            next = TransactionStatus.PAGADO,
            allowedUser = { it.getString("fundsRecipientId") },
            timestampField = "paidAt"
        )
    }

    /**
     * Congela una operacion elegible en EN_DISPUTA y avisa a la contraparte.
     */
    suspend fun openDispute(transactionId: String) {
        val db = dbProvider()
        val uid = currentUserId()
        val ref = db.collection(FirebaseCollections.TRANSACTIONS).document(transactionId)
        val notificationRef = db.collection(FirebaseCollections.NOTIFICATIONS).document()
        db.runTransaction { transaction ->
            val snapshot = transaction.get(ref)
            require(snapshot.exists()) { "La transaccion ya no existe." }
            require(uid == snapshot.getString("buyerId") || uid == snapshot.getString("sellerId")) {
                "No perteneces a esta transaccion."
            }
            val status = snapshot.enumValue<TransactionStatus>("status")
            require(status == TransactionStatus.PENDIENTE_PAGO || status == TransactionStatus.PAGADO) {
                "La transaccion ya no admite disputas."
            }
            transaction.update(
                ref,
                mapOf(
                    "status" to TransactionStatus.EN_DISPUTA.name,
                    "disputedAt" to FieldValue.serverTimestamp()
                )
            )
            val counterpartId = if (uid == snapshot.getString("buyerId")) {
                snapshot.getString("sellerId").orEmpty()
            } else {
                snapshot.getString("buyerId").orEmpty()
            }
            transaction.set(
                notificationRef,
                notificationData(
                    userId = counterpartId,
                    title = "Transaccion en disputa",
                    message = "La operacion ${snapshot.getString("code").orEmpty()} fue puesta en disputa."
                )
            )
            null
        }.awaitTransaction()
    }

    /**
     * Libera el escrow: resta retenido al propietario, acredita al receptor, crea
     * movimientos y marca COMPLETADO dentro de una unica transaccion Firestore.
     */
    suspend fun complete(transactionId: String) {
        val db = dbProvider()
        val uid = currentUserId()
        val transactionRef = db.collection(FirebaseCollections.TRANSACTIONS).document(transactionId)
        val ownerMovementRef = db.collection(FirebaseCollections.WALLET_MOVEMENTS).document()
        val recipientMovementRef = db.collection(FirebaseCollections.WALLET_MOVEMENTS).document()
        val recipientNotificationRef = db.collection(FirebaseCollections.NOTIFICATIONS).document()
        val ownerNotificationRef = db.collection(FirebaseCollections.NOTIFICATIONS).document()

        db.runTransaction { transaction ->
            val record = transaction.get(transactionRef)
            require(record.exists()) { "La transaccion ya no existe." }
            require(record.getString("fundsOwnerId") == uid) { "Solo quien retuvo los fondos puede liberarlos." }
            require(record.getString("status") == TransactionStatus.PAGADO.name) {
                "La transaccion debe estar pagada antes de liberar fondos."
            }

            val ownerId = record.getString("fundsOwnerId").orEmpty()
            val recipientId = record.getString("fundsRecipientId").orEmpty()
            val heldCurrency = record.getString("heldCurrency").orEmpty()
            val heldAmount = record.getDouble("heldAmount") ?: 0.0
            require(ownerId.isNotBlank() && recipientId.isNotBlank() && heldCurrency.isNotBlank() && heldAmount > 0.0) {
                "La retencion de la transaccion es invalida."
            }

            val ownerBalanceRef = db.collection(FirebaseCollections.WALLETS)
                .document(ownerId)
                .collection(FirebaseCollections.BALANCES)
                .document(heldCurrency)
            val recipientBalanceRef = db.collection(FirebaseCollections.WALLETS)
                .document(recipientId)
                .collection(FirebaseCollections.BALANCES)
                .document(heldCurrency)
            val ownerBalance = transaction.get(ownerBalanceRef)
            val ownerRetained = ownerBalance.getDouble("retained") ?: 0.0
            require(ownerRetained + 0.0001 >= heldAmount) { "El saldo retenido es insuficiente." }

            transaction.set(
                ownerBalanceRef,
                mapOf(
                    "currency" to heldCurrency,
                    "available" to (ownerBalance.getDouble("available") ?: 0.0),
                    "retained" to (ownerRetained - heldAmount).coerceAtLeast(0.0),
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            )
            transaction.set(
                recipientBalanceRef,
                mapOf(
                    "currency" to heldCurrency,
                    "available" to FieldValue.increment(heldAmount),
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            )
            transaction.update(
                transactionRef,
                mapOf(
                    "status" to TransactionStatus.COMPLETADO.name,
                    "completedAt" to FieldValue.serverTimestamp()
                )
            )
            transaction.set(
                ownerMovementRef,
                mapOf(
                    "userId" to ownerId,
                    "currency" to heldCurrency,
                    "amount" to -heldAmount,
                    "operationType" to "INTERCAMBIO_P2P",
                    "result" to "EXITOSO",
                    "referenceType" to "TRANSACTION",
                    "referenceId" to transactionId,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            transaction.set(
                recipientMovementRef,
                mapOf(
                    "userId" to recipientId,
                    "currency" to heldCurrency,
                    "amount" to heldAmount,
                    "operationType" to "INTERCAMBIO_P2P",
                    "result" to "EXITOSO",
                    "referenceType" to "TRANSACTION",
                    "referenceId" to transactionId,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            transaction.set(
                recipientNotificationRef,
                notificationData(
                    userId = recipientId,
                    title = "Operacion completada",
                    message = "Recibiste los fondos de la operacion ${record.getString("code").orEmpty()}."
                )
            )
            transaction.set(
                ownerNotificationRef,
                notificationData(
                    userId = ownerId,
                    title = "Operacion completada",
                    message = "Liberaste los fondos de la operacion ${record.getString("code").orEmpty()}."
                )
            )
            null
        }.awaitTransaction()
    }

    /**
     * Cancela antes del pago, devuelve toda retencion pendiente y mantiene cerrada
     * la oferta original para que no vuelva a aparecer en el mercado.
     */
    suspend fun cancel(transactionId: String) {
        val db = dbProvider()
        val uid = currentUserId()
        val transactionRef = db.collection(FirebaseCollections.TRANSACTIONS).document(transactionId)
        val cancellationNotificationRef = db.collection(FirebaseCollections.NOTIFICATIONS).document()
        val requesterNotificationRef = db.collection(FirebaseCollections.NOTIFICATIONS).document()

        db.runTransaction { transaction ->
            val record = transaction.get(transactionRef)
            require(record.exists()) { "La transaccion ya no existe." }
            require(uid == record.getString("buyerId") || uid == record.getString("sellerId")) {
                "No perteneces a esta transaccion."
            }
            require(record.getString("status") == TransactionStatus.PENDIENTE_PAGO.name) {
                "Solo se puede cancelar antes de confirmar el pago."
            }

            val offerRef = db.collection(FirebaseCollections.OFFERS)
                .document(record.getString("offerId").orEmpty())
            val offer = transaction.get(offerRef)
            require(offer.exists()) { "No se encontro la oferta original." }

            val heldCurrency = record.getString("heldCurrency").orEmpty()
            val heldAmount = record.getDouble("heldAmount") ?: 0.0
            val unusedHeldAmount = offer.getDouble("heldAmount") ?: 0.0
            val refundAmount = heldAmount + unusedHeldAmount
            val ownerId = record.getString("fundsOwnerId").orEmpty()
            val ownerBalanceRef = db.collection(FirebaseCollections.WALLETS)
                .document(ownerId)
                .collection(FirebaseCollections.BALANCES)
                .document(heldCurrency)
            val ownerBalance = transaction.get(ownerBalanceRef)
            val available = ownerBalance.getDouble("available") ?: 0.0
            val retained = ownerBalance.getDouble("retained") ?: 0.0
            require(retained + 0.0001 >= refundAmount) {
                "El saldo retenido es insuficiente para cancelar la transaccion."
            }

            transaction.set(
                ownerBalanceRef,
                mapOf(
                    "currency" to heldCurrency,
                    "available" to available + refundAmount,
                    "retained" to (retained - refundAmount).coerceAtLeast(0.0),
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            )
            transaction.update(
                offerRef,
                mapOf(
                    "offeredAmount" to 0.0,
                    "heldAmount" to 0.0,
                    "status" to OfferStatus.COMPLETADA.name,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )

            val movementRef = db.collection(FirebaseCollections.WALLET_MOVEMENTS).document()
            transaction.set(
                movementRef,
                mapOf(
                    "userId" to ownerId,
                    "currency" to heldCurrency,
                    "amount" to refundAmount,
                    "operationType" to "DEVOLUCION_CANCELACION",
                    "result" to "EXITOSO",
                    "referenceType" to "TRANSACTION",
                    "referenceId" to transactionId,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )

            transaction.update(
                transactionRef,
                mapOf(
                    "status" to TransactionStatus.CANCELADO.name,
                    "cancelledAt" to FieldValue.serverTimestamp()
                )
            )
            val counterpartId = if (uid == record.getString("buyerId")) {
                record.getString("sellerId").orEmpty()
            } else {
                record.getString("buyerId").orEmpty()
            }
            transaction.set(
                cancellationNotificationRef,
                notificationData(
                    userId = counterpartId,
                    title = "Transaccion cancelada",
                    message = "La operacion ${record.getString("code").orEmpty()} fue cancelada."
                )
            )
            transaction.set(
                requesterNotificationRef,
                notificationData(
                    userId = uid,
                    title = "Transaccion cancelada",
                    message = "Cancelaste la operacion ${record.getString("code").orEmpty()}."
                )
            )
            null
        }.awaitTransaction()
    }

    private suspend fun updateStatus(
        transactionId: String,
        expected: TransactionStatus,
        next: TransactionStatus,
        allowedUser: (DocumentSnapshot) -> String?,
        timestampField: String
    ) {
        val db = dbProvider()
        val uid = currentUserId()
        val ref = db.collection(FirebaseCollections.TRANSACTIONS).document(transactionId)
        db.runTransaction { transaction ->
            val snapshot = transaction.get(ref)
            require(snapshot.exists()) { "La transaccion ya no existe." }
            require(allowedUser(snapshot) == uid) { "No puedes realizar esta accion." }
            require(snapshot.getString("status") == expected.name) { "La transaccion cambio de estado." }
            transaction.update(
                ref,
                mapOf(
                    "status" to next.name,
                    timestampField to FieldValue.serverTimestamp()
                )
            )
            null
        }.awaitTransaction()
    }

    private fun DocumentSnapshot.toTransaction(): Transaction? {
        val fromCurrency = enumValue<CurrencyCode>("fromCurrency") ?: return null
        return Transaction(
            id = id,
            code = getString("code").orEmpty(),
            offerId = getString("offerId").orEmpty(),
            buyerId = getString("buyerId").orEmpty(),
            buyerName = getString("buyerName").orEmpty(),
            sellerId = getString("sellerId").orEmpty(),
            sellerName = getString("sellerName").orEmpty(),
            paymentMethod = getString("paymentMethod").orEmpty(),
            paymentDetail = getString("paymentDetail").orEmpty(),
            operationAmount = getDouble("operationAmount") ?: return null,
            totalToPay = getDouble("totalToPay") ?: return null,
            currency = fromCurrency,
            status = enumValue<TransactionStatus>("status") ?: return null,
            voucherUrl = getString("voucherUrl"),
            toCurrency = enumValue<CurrencyCode>("toCurrency") ?: CurrencyCode.PEN,
            fundsOwnerId = getString("fundsOwnerId").orEmpty(),
            fundsRecipientId = getString("fundsRecipientId").orEmpty(),
            heldCurrency = enumValue<CurrencyCode>("heldCurrency") ?: fromCurrency,
            heldAmount = getDouble("heldAmount") ?: 0.0,
            voucherAttachmentId = getString("voucherAttachmentId")
        )
    }

    private inline fun <reified T : Enum<T>> DocumentSnapshot.enumValue(field: String): T? =
        getString(field)?.let { value -> enumValues<T>().firstOrNull { it.name == value } }

    private fun DocumentSnapshot.createdAtMillis(): Long =
        (get("createdAt") as? Timestamp)?.toDate()?.time ?: 0L
}

private fun paymentMethodLabel(value: String): String = when (
    value.trim().uppercase().replace(' ', '_')
) {
    "YAPE" -> "Yape"
    "PLIN" -> "Plin"
    "BANK", "TRANSFERENCIA_BANCARIA" -> "Transferencia Bancaria"
    "WALLET_INTERNA" -> "Wallet Interna"
    else -> value.trim()
}

private fun notificationData(userId: String, title: String, message: String): Map<String, Any> =
    mapOf(
        "userId" to userId,
        "title" to title,
        "message" to message,
        "read" to false,
        "createdAt" to FieldValue.serverTimestamp()
    )

private fun walletMovementData(
    userId: String,
    currency: CurrencyCode,
    amount: Double,
    transactionId: String
): Map<String, Any> = mapOf(
    "userId" to userId,
    "currency" to currency.name,
    "amount" to amount,
    "operationType" to "INTERCAMBIO_P2P",
    "result" to "EXITOSO",
    "referenceType" to "TRANSACTION",
    "referenceId" to transactionId,
    "createdAt" to FieldValue.serverTimestamp()
)

private suspend fun <T> Task<T>.awaitTransaction(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { result -> continuation.resume(result) }
    addOnFailureListener { error -> continuation.resumeWithException(error) }
    addOnCanceledListener { continuation.cancel() }
}

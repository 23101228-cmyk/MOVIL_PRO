package com.exchangepro.moviles.domain.model

enum class UserRole { USER, ADMIN }
enum class OperationType { COMPRA, VENTA }
enum class OfferStatus { ACTIVA, PAUSADA, COMPLETADA, CANCELADA }
enum class TransactionStatus { PENDIENTE_PAGO, PAGADO, COMPLETADO, CANCELADO, EN_DISPUTA }
enum class CurrencyCode { PEN, USD, EUR, JPY, GBP }

data class AppUser(
    val id: String,
    val role: UserRole,
    val fullName: String,
    val email: String,
    val phone: String,
    val documentNumber: String,
    val reputation: Double,
    val totalRatings: Int,
    val photoUrl: String? = null,
    val photoAttachmentId: String? = null
)

data class WalletBalance(
    val currency: CurrencyCode,
    val available: Double,
    val retained: Double
)

data class Wallet(
    val userId: String,
    val balances: List<WalletBalance>
)

data class DepositAccount(
    val key: String,
    val method: String,
    val detail: String,
    val extra: String? = null
)

data class WalletMovement(
    val id: String,
    val currency: CurrencyCode,
    val amount: Double,
    val operationType: String,
    val result: String,
    val referenceType: String? = null,
    val referenceId: String? = null,
    val createdAtMillis: Long? = null
)

data class TopUpRequest(
    val currency: CurrencyCode,
    val amount: Double,
    val paymentMethod: String,
    val referenceNumber: String
)

data class ExchangeRate(
    val code: CurrencyCode,
    val mid: Double,
    val buy: Double,
    val sell: Double,
    val direction: String
)

data class CreateOfferRequest(
    val operationType: OperationType,
    val fromCurrency: CurrencyCode,
    val toCurrency: CurrencyCode,
    val exchangeRate: Double,
    val offeredAmount: Double,
    val minimumAmount: Double
)

data class Offer(
    val id: String,
    val userId: String,
    val userName: String,
    val operationType: OperationType,
    val fromCurrency: CurrencyCode,
    val toCurrency: CurrencyCode,
    val exchangeRate: Double,
    val offeredAmount: Double,
    val minimumAmount: Double,
    val paymentMethods: List<String>,
    val paymentMethodDetails: Map<String, String> = emptyMap(),
    val status: OfferStatus
)

data class Transaction(
    val id: String,
    val code: String,
    val offerId: String,
    val buyerId: String,
    val buyerName: String,
    val sellerId: String,
    val sellerName: String,
    val paymentMethod: String,
    val paymentDetail: String = "",
    val operationAmount: Double,
    val totalToPay: Double,
    val currency: CurrencyCode,
    val status: TransactionStatus,
    val voucherUrl: String? = null,
    val toCurrency: CurrencyCode = CurrencyCode.PEN,
    val fundsOwnerId: String = "",
    val fundsRecipientId: String = "",
    val heldCurrency: CurrencyCode = currency,
    val heldAmount: Double = operationAmount,
    val voucherAttachmentId: String? = null
)

data class WithdrawalRequest(
    val currency: CurrencyCode,
    val amount: Double,
    val paymentMethodKey: String
)

data class PaymentData(
    val id: String,
    val userId: String,
    val methodName: String,
    val bankName: String?,
    val accountNumber: String?,
    val alias: String
)

data class Dispute(
    val id: String,
    val transactionCode: String,
    val reason: String,
    val status: String,
    val description: String = "",
    val transactionId: String = "",
    val evidenceAttachmentIds: List<String> = emptyList()
)

data class NotificationItem(
    val id: String,
    val title: String,
    val message: String,
    val read: Boolean
)

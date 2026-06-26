# Firestore schema propuesto

Este modelo sale de la base SQL inicial `P2PWalletDB`, pero adaptado a Firebase/Firestore.

## Colecciones principales

```text
users/{userId}
wallets/{userId}
wallets/{userId}/balances/{currencyCode}
offers/{offerId}
transactions/{transactionId}
paymentData/{paymentDataId}
disputes/{disputeId}
ratings/{ratingId}
notifications/{notificationId}
walletMovements/{movementId}
topUps/{topUpId}
```

## users

Equivale a `Usuarios`.

```json
{
  "role": "USER",
  "fullName": "Gustavo Ramirez",
  "email": "gustavo@exchangepro.pe",
  "phone": "999888777",
  "documentNumber": "74859612",
  "reputation": 4.8,
  "totalRatings": 32,
  "photoUrl": null,
  "createdAt": "serverTimestamp"
}
```

## wallets y balances

Equivale a `Wallets` y `WalletSaldos`.

```text
wallets/{userId}/balances/PEN
wallets/{userId}/balances/USD
```

```json
{
  "available": 4250.50,
  "retained": 350.00,
  "updatedAt": "serverTimestamp"
}
```

## offers

Equivale a `Ofertas` y `OfertaMetodoPago`.

```json
{
  "userId": "user_demo_001",
  "userName": "Gustavo Ramirez",
  "operationType": "VENTA",
  "fromCurrency": "USD",
  "toCurrency": "PEN",
  "exchangeRate": 3.72,
  "offeredAmount": 1200.00,
  "minimumAmount": 100.00,
  "paymentMethods": ["Yape", "BCP"],
  "status": "ACTIVA",
  "createdAt": "serverTimestamp"
}
```

## transactions

Equivale a `Transacciones` y `ComprobantesPago`.

```json
{
  "code": "EX-2026-0001",
  "offerId": "offer_001",
  "buyerId": "user_demo_001",
  "buyerName": "Gustavo Ramirez",
  "sellerId": "user_ana",
  "sellerName": "Ana Torres",
  "paymentMethod": "Yape",
  "operationAmount": 250.00,
  "totalToPay": 930.00,
  "currency": "USD",
  "status": "PENDIENTE_PAGO",
  "voucherUrl": null,
  "createdAt": "serverTimestamp"
}
```

## Storage

Los archivos no van en Firestore. Firestore solo guarda URL y metadatos.

```text
profilePhotos/{userId}/profile.jpg
vouchers/{transactionId}/{fileName}
disputeEvidence/{disputeId}/{fileName}
```

## topUps

Registra cada solicitud de recarga realizada desde Wallet.

```json
{
  "userId": "user_demo_001",
  "currency": "PEN",
  "amount": 250.00,
  "paymentMethod": "YAPE",
  "referenceNumber": "123456",
  "status": "COMPLETADA",
  "createdAt": "serverTimestamp"
}
```

## walletMovements

Mantiene el historial de entradas y salidas de cada usuario.

```json
{
  "userId": "user_demo_001",
  "currency": "PEN",
  "amount": 250.00,
  "operationType": "RECARGA",
  "result": "EXITOSO",
  "referenceType": "YAPE",
  "referenceId": "topUp_001",
  "createdAt": "serverTimestamp"
}
```

## Nota sobre saldos

Cuando se conecte Firebase real, los cambios de saldo deben hacerse con transacciones de Firestore o Cloud Functions. No conviene modificar saldos desde varias pantallas sin una operacion atomica.

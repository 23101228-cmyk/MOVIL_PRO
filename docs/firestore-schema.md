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
withdrawals/{withdrawalId}
attachments/{attachmentId}
feedback/{feedbackId}
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

## Adjuntos de imagen para el prototipo academico

El proyecto usa `attachments/{attachmentId}` porque Cloud Storage requiere
facturacion en proyectos nuevos. Esta solucion es solo para la demostracion
universitaria.

Las imagenes se reducen automaticamente a JPEG de 500 KB como maximo y se
guardan en un documento independiente para no cargar sus bytes al consultar
usuarios, transacciones o disputas.

```json
{
  "ownerId": "firebaseAuthUid",
  "type": "VOUCHER | DISPUTE_EVIDENCE | PROFILE_PHOTO",
  "relatedId": "transactionId | disputeId | userId",
  "contentType": "image/jpeg",
  "imageData": "Firestore Blob",
  "size": 421350,
  "width": 1280,
  "height": 720,
  "createdAt": "serverTimestamp"
}
```

No se admiten PDF ni archivos grandes. En produccion estos adjuntos deben
migrarse a un servicio especializado de almacenamiento de archivos.

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

## paymentData

Cada usuario mantiene un unico documento con sus destinos de cobro.

```text
paymentData/{userId}
```

```json
{
  "userId": "firebaseAuthUid",
  "yape": "999888777",
  "plin": "",
  "bankName": "BCP",
  "accountNumber": "19112345678012",
  "cci": "00219112345678012000",
  "updatedAt": "serverTimestamp"
}
```

## withdrawals

Los retiros del prototipo se completan de forma atomica: descuentan el saldo,
crean un retiro y agregan un movimiento negativo.

```json
{
  "userId": "firebaseAuthUid",
  "currency": "PEN",
  "amount": 100.00,
  "paymentMethod": "YAPE",
  "destination": "999888777",
  "status": "COMPLETADO",
  "createdAt": "serverTimestamp"
}
```

## Nota sobre saldos

La aplicacion usa transacciones atomicas de Firestore para evitar cambios parciales.
Sin embargo, sigue siendo un prototipo academico: un cliente modificado podria
intentar operaciones no previstas. En produccion, las recargas, retiros,
retenciones, liberaciones y resoluciones de disputas deben ejecutarse en un
backend confiable o Cloud Functions.

## Reglas de seguridad

El archivo `firestore.rules` exige Firebase Authentication y aplica acceso por
propietario, participantes de una transaccion y rol `ADMIN`. Tambien limita los
adjuntos a JPEG de 500 KB y bloquea eliminaciones directas de registros
financieros.

Para publicarlas desde la raiz del proyecto:

```powershell
firebase login
firebase use <ID_DEL_PROYECTO>
firebase deploy --only firestore:rules
```

Antes de desplegar, el usuario administrador debe tener `"role": "ADMIN"` en
`users/{uid}`. Los usuarios registrados por la aplicacion siempre nacen con
`"role": "USER"`.

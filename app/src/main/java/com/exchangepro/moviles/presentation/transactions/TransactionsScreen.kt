package com.exchangepro.moviles.presentation.transactions

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.exchangepro.moviles.data.image.ImageCompressor
import com.exchangepro.moviles.data.repository.FirebaseAttachmentRepository
import com.exchangepro.moviles.data.repository.FirebaseRatingRepository
import com.exchangepro.moviles.data.repository.FirebaseTransactionRepository
import com.exchangepro.moviles.domain.model.Transaction
import com.exchangepro.moviles.domain.model.TransactionStatus
import com.exchangepro.moviles.ui.components.ExchangeCard
import com.exchangepro.moviles.ui.components.PrimaryAction
import com.exchangepro.moviles.ui.components.SecondaryAction
import com.exchangepro.moviles.ui.components.StatusPill
import com.exchangepro.moviles.ui.theme.ExchangeElevated
import com.exchangepro.moviles.ui.theme.ExchangeMuted
import com.exchangepro.moviles.ui.theme.ExchangeNegative
import com.exchangepro.moviles.ui.theme.ExchangePositive
import com.exchangepro.moviles.ui.theme.ExchangePrimary
import com.exchangepro.moviles.ui.theme.ExchangePrimaryLight
import com.exchangepro.moviles.ui.theme.ExchangeSurface
import androidx.navigation.NavController
import com.exchangepro.moviles.presentation.navigation.Route
import kotlinx.coroutines.launch

@Composable
fun TransactionsScreen(navController: NavController) {
    val repository = remember { FirebaseTransactionRepository() }
    val attachmentRepository = remember { FirebaseAttachmentRepository() }
    val ratingRepository = remember { FirebaseRatingRepository() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentUserId = remember { repository.currentUserId() }
    var transactions by remember { mutableStateOf(emptyList<Transaction>()) }
    var selected by remember { mutableStateOf<Transaction?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var actionFailed by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var uploadingVoucher by remember { mutableStateOf(false) }
    var voucherPreview by remember { mutableStateOf<ByteArray?>(null) }
    var loadingPreview by remember { mutableStateOf(false) }
    var ratingTarget by remember { mutableStateOf<Transaction?>(null) }
//Obtiene las transacciones del usuario con:
    suspend fun reloadTransactions() {
        transactions = repository.getMyTransactions()
    }

    fun performAction(successMessage: String, action: suspend () -> Unit) {
        scope.launch {
            try {
                action()
                reloadTransactions()
                actionFailed = false
                message = successMessage
                selected = null
            } catch (error: Exception) {
                actionFailed = true
                message = error.message ?: "No se pudo actualizar la transaccion."
            }
        }
    }

    val voucherPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val transaction = selected
        if (uri != null && transaction != null && !uploadingVoucher) {
            scope.launch {
                uploadingVoucher = true
                try {
                    val compressed = ImageCompressor.compress(context, uri)
                    attachmentRepository.uploadVoucher(transaction.id, compressed)
                    reloadTransactions()
                    actionFailed = false
                    message = "Comprobante comprimido y registrado correctamente."
                    selected = null
                } catch (error: Exception) {
                    actionFailed = true
                    message = error.message ?: "No se pudo guardar el comprobante."
                } finally {
                    uploadingVoucher = false
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        try {
            reloadTransactions()
        } catch (error: Exception) {
            actionFailed = true
            message = error.message ?: "No se pudieron cargar las transacciones."
        } finally {
            loading = false
        }
    }
    //Lista de operaciones
    LazyColumn(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Mis operaciones", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Revisa pagos, comprobantes y liberacion de fondos.", color = ExchangeMuted)
        }
        message?.let {
            item { Text(it, color = if (actionFailed) ExchangeNegative else ExchangePositive, style = MaterialTheme.typography.bodySmall) }
        }
        if (loading) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator(color = ExchangePrimary)
                }
            }
        } else if (transactions.isEmpty()) {
            item {
                ExchangeCard {
                    Text("Aun no tienes transacciones registradas.", color = ExchangeMuted)
                }
            }
        } else {
            items(transactions, key = { it.id }) { trx ->
                TransactionCard(
                    trx = trx,
                    onClick = { selected = trx }
                )
            }
        }
    }

    selected?.let { trx ->
        TransactionDetailDialog(
            trx = trx,
            currentUserId = currentUserId,
            uploadingVoucher = uploadingVoucher,
            onDismiss = { selected = null },
            onUploadVoucher = {
                if (!uploadingVoucher) {
                    voucherPicker.launch("image/*")
                }
            },
            onViewVoucher = {
                val attachmentId = trx.voucherAttachmentId
                if (attachmentId != null && !loadingPreview) {
                    scope.launch {
                        loadingPreview = true
                        try {
                            voucherPreview = attachmentRepository.getImage(attachmentId)
                        } catch (error: Exception) {
                            actionFailed = true
                            message = error.message ?: "No se pudo abrir el comprobante."
                        } finally {
                            loadingPreview = false
                        }
                    }
                }
            },
            onRelease = {
                performAction("Fondos liberados. Transaccion completada.") {
                    repository.complete(trx.id)
                }
            },
            onCancel = {
                performAction("Transaccion cancelada. El monto regreso a la oferta.") {
                    repository.cancel(trx.id)
                }
            },
            onDispute = {
                selected = null
                navController.navigate(Route.Disputes.value)
            },
            onRate = {
                scope.launch {
                    try {
                        if (ratingRepository.hasRated(trx.id)) {
                            actionFailed = true
                            message = "Ya calificaste esta transaccion."
                            selected = null
                        } else {
                            selected = null
                            ratingTarget = trx
                        }
                    } catch (error: Exception) {
                        actionFailed = true
                        message = error.message ?: "No se pudo verificar la calificacion."
                    }
                }
            }
        )
    }

    ratingTarget?.let { trx ->
        RatingDialog(
            transactionCode = trx.code,
            onDismiss = { ratingTarget = null },
            onSubmit = { score, comment ->
                scope.launch {
                    try {
                        ratingRepository.submit(trx.id, score, comment)
                        actionFailed = false
                        message = "Calificacion registrada. Gracias por tu opinion."
                        ratingTarget = null
                    } catch (error: Exception) {
                        actionFailed = true
                        message = error.message ?: "No se pudo guardar la calificacion."
                    }
                }
            }
        )
    }

    voucherPreview?.let { bytes ->
        val bitmap = remember(bytes) {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }
        AlertDialog(
            onDismissRequest = { voucherPreview = null },
            title = { Text("Comprobante") },
            text = {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = "Comprobante de pago",
                        modifier = Modifier.fillMaxWidth().height(420.dp),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Text("No se pudo decodificar la imagen.")
                }
            },
            confirmButton = {
                TextButton(onClick = { voucherPreview = null }) { Text("Cerrar") }
            },
            containerColor = ExchangeSurface
        )
    }
}

@Composable
//Representa una transaccion dentro de la lista
private fun TransactionCard(trx: Transaction, onClick: () -> Unit) {
    ExchangeCard(modifier = Modifier.clickable(onClick = onClick)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(trx.code, fontWeight = FontWeight.Bold)
                Text("${trx.buyerName} / ${trx.sellerName}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            StatusPill(statusLabel(trx.status))
        }
        Spacer(Modifier.height(10.dp))
        Text("Monto: %.2f ${trx.currency}".format(trx.operationAmount))
        Text("Total a pagar: %.2f ${trx.toCurrency}".format(trx.totalToPay), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Toca para ver detalle", color = ExchangeMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun TransactionDetailDialog(
    trx: Transaction,
    currentUserId: String,
    uploadingVoucher: Boolean,
    onDismiss: () -> Unit,
    onUploadVoucher: () -> Unit,
    onViewVoucher: () -> Unit,
    onRelease: () -> Unit,
    onCancel: () -> Unit,
    onDispute: () -> Unit,
    onRate: () -> Unit
) {
    //identificacion del usuario
    val isBuyer = trx.buyerId == currentUserId
    val isSeller = trx.sellerId == currentUserId
    val canConfirmPayment = trx.fundsRecipientId == currentUserId
    val canReleaseFunds = trx.fundsOwnerId == currentUserId

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(ExchangePrimary.copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.SwapHoriz, contentDescription = null, tint = ExchangePrimaryLight)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(trx.code, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                            Text("Transaccion #${trx.id}", color = ExchangeMuted)
                        }
                        StatusPill(statusLabel(trx.status))
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Cerrar")
                        }
                    }
                }

                item {
                    Timeline(trx.status)
                }

                item {
                    ExchangeCard {
                        Text("Informacion General", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(10.dp))
                        DetailRow("Monto operacion", "%.2f ${trx.currency}".format(trx.operationAmount))
                        DetailRow("Total a pagar", "%.2f ${trx.toCurrency}".format(trx.totalToPay))
                        DetailRow("Metodo", trx.paymentMethod)
                        if (trx.paymentDetail.isNotBlank()) {
                            DetailRow("Destino de pago", trx.paymentDetail)
                        }
                        DetailRow("Oferta", "#${trx.offerId}")
                    }
                }

                item {
                    ExchangeCard {
                        Text("Participantes", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(10.dp))
                        DetailRow("Comprador", if (isBuyer) "Tu" else trx.buyerName)
                        DetailRow("Vendedor", if (isSeller) "Tu" else trx.sellerName)
                    }
                }

                if (canConfirmPayment && trx.status == TransactionStatus.PENDIENTE_PAGO) {
                    item {
                        ExchangeCard {
                            Text("Pasos para completar el pago", fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(10.dp))
                            StepRow("1", "Realiza el pago", "Transfiere el monto exacto al vendedor usando ${trx.paymentMethod}.")
                            StepRow("2", "Confirma el envio", "Marca el pago como enviado para que la contraparte pueda liberar los fondos.")
                        }
                    }
                }

                if (trx.voucherAttachmentId != null || trx.status == TransactionStatus.PAGADO) {
                    item {
                        ExchangeCard {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = ExchangePrimaryLight)
                                Spacer(Modifier.width(8.dp))
                                Text("Comprobante de Pago", fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.height(10.dp))
                            Text("Comprobante de imagen registrado para revision.", color = ExchangeMuted)
                            if (trx.voucherAttachmentId != null) {
                                Spacer(Modifier.height(10.dp))
                                SecondaryAction("Ver comprobante", onViewVoucher, Modifier.fillMaxWidth())
                            }
                        }
                    }
                }

                if (canReleaseFunds && trx.status == TransactionStatus.PAGADO) {
                    item {
                        ExchangeCard {
                            Text("Pasos para liberar fondos", fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(10.dp))
                            StepRow("1", "Verifica el pago", "Revisa monto, cuenta y referencia.")
                            StepRow("2", "Libera fondos", "Confirma que recibiste la contraprestacion.")
                        }
                    }
                }

                item {
                    ExchangeCard {
                        Text("Acciones disponibles", color = ExchangeMuted, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(12.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (canConfirmPayment && trx.status == TransactionStatus.PENDIENTE_PAGO) {
                                PrimaryAction(
                                    if (uploadingVoucher) "Comprimiendo imagen..." else "Seleccionar comprobante",
                                    onUploadVoucher,
                                    Modifier.fillMaxWidth()
                                )
                            }
                            if (canReleaseFunds && trx.status == TransactionStatus.PAGADO) {
                                Button(
                                    onClick = onRelease,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = ExchangePositive),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Liberar Fondos")
                                }
                            }
                            if (trx.status == TransactionStatus.PENDIENTE_PAGO || trx.status == TransactionStatus.PAGADO) {
                                SecondaryAction("Abrir disputa", onDispute, Modifier.fillMaxWidth())
                            }
                            if (trx.status == TransactionStatus.PENDIENTE_PAGO) {
                                Button(
                                    onClick = onCancel,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = ExchangeNegative),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.Cancel, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Cancelar")
                                }
                            }
                            if (trx.status == TransactionStatus.COMPLETADO) {
                                PrimaryAction("Calificar usuario", onRate, Modifier.fillMaxWidth())
                            }
                            if (trx.status == TransactionStatus.EN_DISPUTA) {
                                Text("La transaccion esta en disputa.", color = ExchangeMuted)
                            }
                        }
                    }
                }
            }
        },
        containerColor = ExchangeSurface,
        shape = RoundedCornerShape(18.dp)
    )
}

@Composable
private fun RatingDialog(
    transactionCode: String,
    onDismiss: () -> Unit,
    onSubmit: (Int, String) -> Unit
) {
    var score by remember { mutableStateOf(5) }
    var comment by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Calificar $transactionCode") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Selecciona una puntuacion")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    (1..5).forEach { value ->
                        Button(
                            onClick = { score = value },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (score == value) ExchangePrimary else ExchangeElevated
                            ),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                            modifier = Modifier.size(42.dp)
                        ) {
                            Text(value.toString())
                        }
                    }
                }
                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it.take(300) },
                    label = { Text("Comentario opcional") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(score, comment) }) { Text("Enviar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
        containerColor = ExchangeSurface
    )
}

@Composable
private fun Timeline(status: TransactionStatus) {
    val steps = listOf(
        "Iniciada" to true,
        "Pagada" to (status == TransactionStatus.PAGADO || status == TransactionStatus.COMPLETADO),
        "Completada" to (status == TransactionStatus.COMPLETADO)
    )

    ExchangeCard {
        Text("Progreso", fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            steps.forEach { (label, done) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(if (done) ExchangePositive else ExchangeElevated),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = if (done) Color.White else ExchangeMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(label, color = if (done) ExchangePositive else ExchangeMuted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (status == TransactionStatus.CANCELADO || status == TransactionStatus.EN_DISPUTA) {
            Spacer(Modifier.height(10.dp))
            Text(statusLabel(status), color = if (status == TransactionStatus.CANCELADO) ExchangeNegative else ExchangePrimaryLight)
        }
    }
}

@Composable
private fun StepRow(number: String, title: String, description: String) {
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(vertical = 6.dp)) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(ExchangePrimary),
            contentAlignment = Alignment.Center
        ) {
            Text(number, fontWeight = FontWeight.Bold, color = Color.White)
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(description, color = ExchangeMuted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(ExchangeElevated.copy(alpha = 0.72f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = ExchangeMuted)
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}

private fun statusLabel(status: TransactionStatus): String = when (status) {
    TransactionStatus.PENDIENTE_PAGO -> "PENDIENTE"
    TransactionStatus.PAGADO -> "PAGADO"
    TransactionStatus.COMPLETADO -> "COMPLETADO"
    TransactionStatus.CANCELADO -> "CANCELADO"
    TransactionStatus.EN_DISPUTA -> "EN DISPUTA"
}

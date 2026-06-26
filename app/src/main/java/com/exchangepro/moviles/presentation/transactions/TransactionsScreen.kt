package com.exchangepro.moviles.presentation.transactions

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
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.exchangepro.moviles.data.repository.MockExchangeRepository
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

@Composable
fun TransactionsScreen() {
    val transactions = remember { mutableStateListOf<Transaction>().apply { addAll(MockExchangeRepository.transactions) } }
    var selected by remember { mutableStateOf<Transaction?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    LazyColumn(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Mis operaciones", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Revisa pagos, comprobantes y liberacion de fondos.", color = ExchangeMuted)
        }
        message?.let {
            item { Text(it, color = ExchangePositive, style = MaterialTheme.typography.bodySmall) }
        }
        items(transactions, key = { it.id }) { trx ->
            TransactionCard(
                trx = trx,
                onClick = { selected = trx }
            )
        }
    }

    selected?.let { trx ->
        TransactionDetailDialog(
            trx = trx,
            onDismiss = { selected = null },
            onUploadVoucher = {
                transactions.replaceTransaction(trx.copy(status = TransactionStatus.PAGADO, voucherUrl = "mock://voucher/${trx.id}.png"))
                message = "Comprobante registrado. El vendedor debe liberar los fondos."
                selected = null
            },
            onRelease = {
                transactions.replaceTransaction(trx.copy(status = TransactionStatus.COMPLETADO))
                message = "Fondos liberados. Transaccion completada."
                selected = null
            },
            onCancel = {
                transactions.replaceTransaction(trx.copy(status = TransactionStatus.CANCELADO))
                message = "Transaccion cancelada. La oferta volveria al mercado."
                selected = null
            },
            onDispute = {
                transactions.replaceTransaction(trx.copy(status = TransactionStatus.EN_DISPUTA))
                message = "Disputa abierta para ${trx.code}."
                selected = null
            }
        )
    }
}

@Composable
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
        Text("Total a pagar: S/ %.2f".format(trx.totalToPay), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Toca para ver detalle", color = ExchangeMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun TransactionDetailDialog(
    trx: Transaction,
    onDismiss: () -> Unit,
    onUploadVoucher: () -> Unit,
    onRelease: () -> Unit,
    onCancel: () -> Unit,
    onDispute: () -> Unit
) {
    val currentUserId = MockExchangeRepository.currentUser.id
    val isBuyer = trx.buyerId == currentUserId
    val isSeller = trx.sellerId == currentUserId

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
                        DetailRow("Total a pagar", "S/ %.2f".format(trx.totalToPay))
                        DetailRow("Metodo", trx.paymentMethod)
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

                if (isBuyer && trx.status == TransactionStatus.PENDIENTE_PAGO) {
                    item {
                        ExchangeCard {
                            Text("Pasos para completar el pago", fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(10.dp))
                            StepRow("1", "Realiza el pago", "Transfiere el monto exacto al vendedor usando ${trx.paymentMethod}.")
                            StepRow("2", "Sube el comprobante", "Adjunta la captura o PDF para que el vendedor confirme.")
                        }
                    }
                }

                if (trx.voucherUrl != null || trx.status == TransactionStatus.PAGADO) {
                    item {
                        ExchangeCard {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = ExchangePrimaryLight)
                                Spacer(Modifier.width(8.dp))
                                Text("Comprobante de Pago", fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.height(10.dp))
                            Text("Comprobante registrado para revision.", color = ExchangeMuted)
                        }
                    }
                }

                if (isSeller && trx.status == TransactionStatus.PAGADO) {
                    item {
                        ExchangeCard {
                            Text("Pasos para liberar fondos", fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(10.dp))
                            StepRow("1", "Verifica comprobante", "Revisa monto, cuenta y referencia.")
                            StepRow("2", "Libera fondos", "Confirma que recibiste el pago.")
                        }
                    }
                }

                item {
                    ExchangeCard {
                        Text("Acciones disponibles", color = ExchangeMuted, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(12.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (isBuyer && trx.status == TransactionStatus.PENDIENTE_PAGO) {
                                PrimaryAction("Subir comprobante", onUploadVoucher, Modifier.fillMaxWidth())
                            }
                            if (isSeller && trx.status == TransactionStatus.PAGADO) {
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
                                Text("Transaccion completada. Luego agregaremos calificacion.", color = ExchangeMuted)
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

private fun MutableList<Transaction>.replaceTransaction(next: Transaction) {
    val index = indexOfFirst { it.id == next.id }
    if (index >= 0) this[index] = next
}

private fun statusLabel(status: TransactionStatus): String = when (status) {
    TransactionStatus.PENDIENTE_PAGO -> "PENDIENTE"
    TransactionStatus.PAGADO -> "PAGADO"
    TransactionStatus.COMPLETADO -> "COMPLETADO"
    TransactionStatus.CANCELADO -> "CANCELADO"
    TransactionStatus.EN_DISPUTA -> "EN DISPUTA"
}

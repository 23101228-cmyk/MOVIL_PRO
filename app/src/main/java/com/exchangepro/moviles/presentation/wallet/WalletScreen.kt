package com.exchangepro.moviles.presentation.wallet

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Output
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.exchangepro.moviles.data.repository.ExchangeRateRepository
import com.exchangepro.moviles.data.repository.FirebasePaymentDataRepository
import com.exchangepro.moviles.data.repository.FirebaseWalletRepository
import com.exchangepro.moviles.data.repository.PaymentDestination
import com.exchangepro.moviles.domain.model.CurrencyCode
import com.exchangepro.moviles.domain.model.DepositAccount
import com.exchangepro.moviles.domain.model.ExchangeRate
import com.exchangepro.moviles.domain.model.TopUpRequest
import com.exchangepro.moviles.domain.model.Wallet
import com.exchangepro.moviles.domain.model.WalletBalance
import com.exchangepro.moviles.domain.model.WalletMovement
import com.exchangepro.moviles.domain.model.WithdrawalRequest
import com.exchangepro.moviles.ui.components.ExchangeCard
import com.exchangepro.moviles.ui.components.PrimaryAction
import com.exchangepro.moviles.ui.components.SecondaryAction
import com.exchangepro.moviles.ui.theme.ExchangeAccent
import com.exchangepro.moviles.ui.theme.ExchangeElevated
import com.exchangepro.moviles.ui.theme.ExchangeMuted
import com.exchangepro.moviles.ui.theme.ExchangeNegative
import com.exchangepro.moviles.ui.theme.ExchangePositive
import com.exchangepro.moviles.ui.theme.ExchangePrimary
import com.exchangepro.moviles.ui.theme.ExchangePrimaryLight
import com.exchangepro.moviles.ui.theme.ExchangeSurface
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

@Composable
fun WalletScreen() {
    val walletRepository = remember { FirebaseWalletRepository() }
    val paymentRepository = remember { FirebasePaymentDataRepository() }
    val exchangeRateRepository = remember { ExchangeRateRepository() }
    val scope = rememberCoroutineScope()

    var wallet by remember { mutableStateOf(Wallet(userId = "", balances = emptyList())) }
    var movements by remember { mutableStateOf(emptyList<WalletMovement>()) }
    var paymentDestinations by remember { mutableStateOf(emptyList<PaymentDestination>()) }
    var rates by remember { mutableStateOf(emptyList<ExchangeRate>()) }
    var loading by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf<String?>(null) }
    var showTopUp by remember { mutableStateOf(false) }
    var showWithdraw by remember { mutableStateOf(false) }

    fun refresh() {
        scope.launch {
            loading = true
            try {
                wallet = walletRepository.getWallet()
                movements = runCatching { walletRepository.getMovements() }.getOrDefault(emptyList())
                paymentDestinations = runCatching {
                    paymentRepository.get().availableDestinations()
                }.getOrDefault(emptyList())
                message = null
            } catch (error: Exception) {
                wallet = Wallet(userId = "", balances = emptyList())
                movements = emptyList()
                message = "No se pudo cargar la wallet: ${error.message.orEmpty()}"
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        refresh()
        rates = exchangeRateRepository.getRates()
    }

    LazyColumn(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            WalletHero(
                wallet = wallet,
                rates = rates,
                onTopUp = { showTopUp = true },
                onWithdraw = { showWithdraw = true }
            )
        }

        item {
            ExchangeRateBar(rates = rates)
        }

        message?.let { text ->
            item {
                Text(text, color = ExchangeMuted, style = MaterialTheme.typography.bodySmall)
            }
        }

        if (loading) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(color = ExchangePrimary)
                }
            }
        } else {
            items(wallet.balances, key = { it.currency.name }) { balance ->
                BalanceCard(balance)
            }

            item {
                Text("Movimientos", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
            }

            if (movements.isEmpty()) {
                item {
                    ExchangeCard {
                        Text("No hay movimientos registrados", color = ExchangeMuted)
                    }
                }
            } else {
                items(movements, key = { it.id }) { movement ->
                    MovementRow(movement)
                }
            }
        }
    }

    if (showTopUp) {
        TopUpDialog(
            wallet = wallet,
            onDismiss = { showTopUp = false },
            onConfirm = { request ->
                scope.launch {
                    try {
                        walletRepository.topUp(request)
                        showTopUp = false
                        message = "Saldo recargado correctamente."
                        refresh()
                    } catch (error: Exception) {
                        message = "No se pudo guardar en Firebase: ${error.message.orEmpty()}"
                    }
                }
            }
        )
    }

    if (showWithdraw) {
        WithdrawDialog(
            wallet = wallet,
            destinations = paymentDestinations,
            onDismiss = { showWithdraw = false },
            onConfirm = { request ->
                scope.launch {
                    try {
                        walletRepository.withdraw(request)
                        showWithdraw = false
                        message = "Retiro completado correctamente."
                        refresh()
                    } catch (error: Exception) {
                        message = error.message ?: "No se pudo completar el retiro."
                    }
                }
            }
        )
    }
}

@Composable
private fun WalletHero(
    wallet: Wallet,
    rates: List<ExchangeRate>,
    onTopUp: () -> Unit,
    onWithdraw: () -> Unit
) {
    val canEstimate = wallet.balances.all { balance ->
        balance.currency == CurrencyCode.PEN || rates.any { it.code == balance.currency }
    }
    val totalInPen = wallet.balances.sumOf { balance ->
        val ownedAmount = balance.available + balance.retained
        if (balance.currency == CurrencyCode.PEN) {
            ownedAmount
        } else {
            ownedAmount * (rates.firstOrNull { it.code == balance.currency }?.buy ?: 0.0)
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier
                .background(Brush.horizontalGradient(listOf(ExchangePrimary, ExchangePrimaryLight)))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = Color.White)
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("Wallet ...${wallet.userId.takeLast(4)}", color = Color.White.copy(alpha = 0.78f), style = MaterialTheme.typography.labelMedium)
                    Text("Saldo total estimado en PEN", color = Color.White.copy(alpha = 0.86f), style = MaterialTheme.typography.bodySmall)
                    Text(
                        if (canEstimate) formatMoney(totalInPen, CurrencyCode.PEN) else "Calculando...",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.headlineSmall
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onTopUp,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = ExchangePrimary),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Recargar", fontWeight = FontWeight.SemiBold)
                }
                SecondaryAction("Retirar", onWithdraw, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ExchangeRateBar(rates: List<ExchangeRate>) {
    var selected by remember(rates) { mutableStateOf(rates.firstOrNull()?.code ?: CurrencyCode.USD) }
    val active = rates.firstOrNull { it.code == selected } ?: rates.firstOrNull()

    ExchangeCard {
        Text("Tipo de cambio", fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        if (active == null) {
            Text("Cargando...", color = ExchangeMuted)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rates.forEach { rate ->
                    RateChip(rate.code.name, selected == rate.code) { selected = rate.code }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${active.code}/PEN", fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Text(directionSymbol(active.direction), color = directionColor(active.direction), fontWeight = FontWeight.Bold)
            }
            Text("C S/${"%.3f".format(active.buy)}   |   V S/${"%.3f".format(active.sell)}", color = ExchangeMuted)
        }
    }
}

@Composable
private fun RateChip(text: String, active: Boolean, onClick: () -> Unit) {
    Text(
        text = text,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (active) ExchangePrimary else ExchangeElevated)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        color = if (active) Color.White else ExchangeMuted,
        fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.labelMedium
    )
}

@Composable
private fun BalanceCard(balance: WalletBalance) {
    ExchangeCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(currencyColor(balance.currency).copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(currencySymbol(balance.currency), color = currencyColor(balance.currency), fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(10.dp))
                Text(currencyName(balance.currency), fontWeight = FontWeight.SemiBold)
            }
            Text(balance.currency.name, color = ExchangeMuted, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(14.dp))
        Text("Disponible", color = ExchangeMuted, style = MaterialTheme.typography.labelMedium)
        Text(formatMoney(balance.available, balance.currency), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineSmall)
        Divider(Modifier.padding(vertical = 12.dp), color = ExchangePrimary.copy(alpha = 0.18f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Retenido", color = ExchangeMuted)
            Text(formatMoney(balance.retained, balance.currency), color = ExchangeMuted, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun MovementRow(movement: WalletMovement) {
    ExchangeCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(typeLabel(movement.operationType), fontWeight = FontWeight.Bold)
                Text("${movement.currency.name} • ${movement.result}", color = ExchangeMuted, style = MaterialTheme.typography.bodySmall)
                movement.createdAtMillis?.let {
                    Text(formatDate(it), color = ExchangeMuted, style = MaterialTheme.typography.bodySmall)
                }
            }
            val outgoing = movement.amount < 0.0
            Text(
                "${if (outgoing) "-" else "+"}${formatMoney(abs(movement.amount), movement.currency)}",
                color = if (outgoing) ExchangeNegative else ExchangePositive,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun TopUpDialog(
    wallet: Wallet,
    onDismiss: () -> Unit,
    onConfirm: (TopUpRequest) -> Unit
) {
    val clipboard = LocalClipboardManager.current
    var currency by remember { mutableStateOf<CurrencyCode?>(null) }
    var amount by remember { mutableStateOf("") }
    var selectedAccount by remember { mutableStateOf<DepositAccount?>(null) }
    var reference by remember { mutableStateOf("") }
    var currencyMenuOpen by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val accounts = remember { exchangeProAccounts() }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                            .background(Brush.horizontalGradient(listOf(ExchangePrimary, ExchangePrimaryLight)))
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.AccountBalance, contentDescription = null, tint = Color.White)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Recargar Saldo", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                            Text("Elige un metodo y deposita en la cuenta de ExchangePro", color = Color.White.copy(alpha = 0.76f), style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color.White)
                        }
                    }
                }

                item {
                    SummaryRow("Wallet", "...${wallet.userId.takeLast(4)}")
                }

                item {
                    Column {
                        Text("Moneda", fontWeight = FontWeight.SemiBold)
                        Box {
                            OutlinedTextField(
                                value = currency?.let { "${it.name} - ${currencyName(it)}" }.orEmpty(),
                                onValueChange = {},
                                modifier = Modifier.fillMaxWidth(),
                                readOnly = true,
                                trailingIcon = { Icon(Icons.Default.KeyboardArrowDown, contentDescription = null) },
                                placeholder = { Text("Seleccionar moneda") }
                            )
                            Box(
                                Modifier
                                    .matchParentSize()
                                    .clickable { currencyMenuOpen = true }
                            )
                            DropdownMenu(expanded = currencyMenuOpen, onDismissRequest = { currencyMenuOpen = false }) {
                                CurrencyCode.values().forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text("${option.name} - ${currencyName(option)}") },
                                        onClick = {
                                            currency = option
                                            currencyMenuOpen = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    Column {
                        Text("Monto a recargar", fontWeight = FontWeight.SemiBold)
                        OutlinedTextField(
                            value = amount,
                            onValueChange = { amount = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            leadingIcon = { Text(currencySymbol(currency ?: CurrencyCode.PEN), fontWeight = FontWeight.Bold) },
                            placeholder = { Text("0.00") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                        )
                    }
                }

                item {
                    Text("Deposita a una de estas cuentas", fontWeight = FontWeight.SemiBold)
                }

                items(accounts, key = { it.key }) { account ->
                    AccountOption(
                        account = account,
                        selected = selectedAccount?.key == account.key,
                        onSelect = { selectedAccount = account },
                        onCopy = { clipboard.setText(AnnotatedString(account.detail)) }
                    )
                }

                item {
                    Column {
                        Text("Nro de operacion / referencia", fontWeight = FontWeight.SemiBold)
                        OutlinedTextField(
                            value = reference,
                            onValueChange = { reference = it.filter(Char::isDigit).take(15) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            placeholder = { Text("Ej: 123456") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }
                }

                error?.let { text ->
                    item { Text(text, color = ExchangeNegative, style = MaterialTheme.typography.bodySmall) }
                }

                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onDismiss) { Text("Cancelar") }
                        Spacer(Modifier.width(8.dp))
                        PrimaryAction("Confirmar Recarga", {
                            val parsedAmount = amount.toDoubleOrNull()
                            when {
                                currency == null -> error = "Selecciona una moneda."
                                parsedAmount == null || parsedAmount <= 0.0 -> error = "El monto debe ser mayor a 0."
                                selectedAccount == null -> error = "Selecciona una cuenta de deposito."
                                reference.length < 6 -> error = "La referencia debe tener entre 6 y 15 digitos."
                                else -> onConfirm(
                                    TopUpRequest(
                                        currency = currency!!,
                                        amount = parsedAmount,
                                        paymentMethod = selectedAccount!!.key,
                                        referenceNumber = reference
                                    )
                                )
                            }
                        })
                    }
                }
            }
        },
        containerColor = ExchangeSurface,
        shape = RoundedCornerShape(18.dp)
    )
}

@Composable
private fun WithdrawDialog(
    wallet: Wallet,
    destinations: List<PaymentDestination>,
    onDismiss: () -> Unit,
    onConfirm: (WithdrawalRequest) -> Unit
) {
    var currency by remember { mutableStateOf<CurrencyCode?>(null) }
    var amount by remember { mutableStateOf("") }
    var selectedDestination by remember { mutableStateOf<PaymentDestination?>(null) }
    var currencyMenuOpen by remember { mutableStateOf(false) }
    var destinationMenuOpen by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val selectedBalance = wallet.balances.firstOrNull { it.currency == currency }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Output, contentDescription = null, tint = ExchangePrimaryLight)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Retirar Saldo", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                        Text("Envia fondos a un metodo registrado", color = ExchangeMuted)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar")
                    }
                }

                if (destinations.isEmpty()) {
                    ExchangeCard {
                        Text("Primero registra Yape, Plin o una cuenta bancaria en Datos de pago.", color = ExchangeNegative)
                    }
                }

                Column {
                    Text("Moneda", fontWeight = FontWeight.SemiBold)
                    Box {
                        OutlinedTextField(
                            value = currency?.let { "${it.name} - ${currencyName(it)}" }.orEmpty(),
                            onValueChange = {},
                            modifier = Modifier.fillMaxWidth(),
                            readOnly = true,
                            placeholder = { Text("Seleccionar moneda") },
                            trailingIcon = { Icon(Icons.Default.KeyboardArrowDown, contentDescription = null) }
                        )
                        Box(Modifier.matchParentSize().clickable { currencyMenuOpen = true })
                        DropdownMenu(expanded = currencyMenuOpen, onDismissRequest = { currencyMenuOpen = false }) {
                            wallet.balances.forEach { balance ->
                                DropdownMenuItem(
                                    text = { Text("${balance.currency.name} - Disponible: ${formatMoney(balance.available, balance.currency)}") },
                                    onClick = {
                                        currency = balance.currency
                                        currencyMenuOpen = false
                                    }
                                )
                            }
                        }
                    }
                    selectedBalance?.let {
                        Text("Disponible: ${formatMoney(it.available, it.currency)}", color = ExchangeMuted, style = MaterialTheme.typography.bodySmall)
                    }
                }

                Column {
                    Text("Monto", fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { amount = it.filter { char -> char.isDigit() || char == '.' } },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        placeholder = { Text("0.00") }
                    )
                }

                Column {
                    Text("Destino", fontWeight = FontWeight.SemiBold)
                    Box {
                        OutlinedTextField(
                            value = selectedDestination?.let { "${it.label} - ${it.detail}" }.orEmpty(),
                            onValueChange = {},
                            modifier = Modifier.fillMaxWidth(),
                            readOnly = true,
                            placeholder = { Text("Seleccionar metodo") },
                            trailingIcon = { Icon(Icons.Default.KeyboardArrowDown, contentDescription = null) }
                        )
                        Box(
                            Modifier
                                .matchParentSize()
                                .clickable(enabled = destinations.isNotEmpty()) { destinationMenuOpen = true }
                        )
                        DropdownMenu(expanded = destinationMenuOpen, onDismissRequest = { destinationMenuOpen = false }) {
                            destinations.forEach { destination ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(destination.label)
                                            Text(destination.detail, color = ExchangeMuted, style = MaterialTheme.typography.bodySmall)
                                        }
                                    },
                                    onClick = {
                                        selectedDestination = destination
                                        destinationMenuOpen = false
                                    }
                                )
                            }
                        }
                    }
                }

                error?.let { Text(it, color = ExchangeNegative, style = MaterialTheme.typography.bodySmall) }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancelar") }
                    Spacer(Modifier.width(8.dp))
                    PrimaryAction("Confirmar Retiro", {
                        val parsedAmount = amount.toDoubleOrNull()
                        when {
                            currency == null -> error = "Selecciona una moneda."
                            parsedAmount == null || parsedAmount <= 0.0 -> error = "El monto debe ser mayor a 0."
                            selectedBalance == null || parsedAmount > selectedBalance.available -> error = "Saldo disponible insuficiente."
                            selectedDestination == null -> error = "Selecciona un destino."
                            else -> onConfirm(
                                WithdrawalRequest(
                                    currency = currency!!,
                                    amount = parsedAmount,
                                    paymentMethodKey = selectedDestination!!.key
                                )
                            )
                        }
                    })
                }
            }
        },
        containerColor = ExchangeSurface,
        shape = RoundedCornerShape(18.dp)
    )
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.24f))
            .padding(14.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = ExchangeMuted)
        Text(value, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun AccountOption(
    account: DepositAccount,
    selected: Boolean,
    onSelect: () -> Unit,
    onCopy: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onSelect),
        colors = CardDefaults.cardColors(containerColor = if (selected) ExchangePrimary.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.24f)),
        border = BorderStroke(1.dp, if (selected) ExchangePrimary else ExchangePrimary.copy(alpha = 0.35f)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = selected,
                onClick = onSelect,
                colors = RadioButtonDefaults.colors(selectedColor = ExchangePrimaryLight, unselectedColor = ExchangeMuted)
            )
            Column(Modifier.weight(1f)) {
                Text(account.method, fontWeight = FontWeight.Bold)
                Text(account.detail, fontWeight = FontWeight.SemiBold)
                account.extra?.let { Text(it, color = ExchangeMuted, style = MaterialTheme.typography.bodySmall) }
            }
            IconButton(onClick = onCopy) {
                Icon(Icons.Default.ContentCopy, contentDescription = "Copiar", tint = ExchangeMuted)
            }
        }
    }
}

private fun exchangeProAccounts() = listOf(
    DepositAccount("YAPE", "Yape", "999 888 777", "ExchangePro"),
    DepositAccount("PLIN", "Plin", "999 888 777", "ExchangePro"),
    DepositAccount("BCP", "BCP - Cuenta Corriente", "193-1234567-0-00", "CCI: 002-193-1234567000-00"),
    DepositAccount("INTERBANK", "Interbank - Cuenta Corriente", "898-1234567890", "CCI: 003-898-1234567890-00")
)

private fun currencyName(currency: CurrencyCode): String = when (currency) {
    CurrencyCode.PEN -> "Soles"
    CurrencyCode.USD -> "Dolares"
    CurrencyCode.EUR -> "Euros"
    CurrencyCode.JPY -> "Yenes"
    CurrencyCode.GBP -> "Libras"
}

private fun currencySymbol(currency: CurrencyCode): String = when (currency) {
    CurrencyCode.PEN -> "S/"
    CurrencyCode.USD -> "$"
    CurrencyCode.EUR -> "€"
    CurrencyCode.JPY -> "¥"
    CurrencyCode.GBP -> "£"
}

private fun currencyColor(currency: CurrencyCode): Color = when (currency) {
    CurrencyCode.PEN -> ExchangePositive
    CurrencyCode.USD -> ExchangeAccent
    CurrencyCode.EUR -> ExchangePrimaryLight
    CurrencyCode.JPY -> Color(0xFFEAB308)
    CurrencyCode.GBP -> ExchangeNegative
}

private fun formatMoney(value: Double, currency: CurrencyCode?): String {
    val symbol = currency?.let { currencySymbol(it) } ?: "S/"
    return "$symbol ${"%.2f".format(value)}"
}

private fun directionSymbol(direction: String): String = when (direction) {
    "sube" -> "▲"
    "baja" -> "▼"
    else -> "-"
}

private fun directionColor(direction: String): Color = when (direction) {
    "sube" -> ExchangePositive
    "baja" -> ExchangeNegative
    else -> ExchangeMuted
}

private fun typeLabel(type: String): String = when (type) {
    "RECARGA" -> "Recarga"
    "RETIRO" -> "Retiro"
    "COMPRA_VENTA" -> "Compra/Venta"
    "PAGO_WALLET" -> "Pago Wallet"
    else -> type.ifBlank { "Movimiento" }
}

private fun formatDate(millis: Long): String =
    SimpleDateFormat("dd MMM yyyy HH:mm", Locale("es", "PE")).format(Date(millis))

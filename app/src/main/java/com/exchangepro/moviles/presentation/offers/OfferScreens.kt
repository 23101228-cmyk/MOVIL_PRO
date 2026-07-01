package com.exchangepro.moviles.presentation.offers

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.exchangepro.moviles.data.repository.ExchangeRateRepository
import com.exchangepro.moviles.data.repository.FirebaseOfferRepository
import com.exchangepro.moviles.data.repository.FirebaseTransactionRepository
import com.exchangepro.moviles.data.repository.FirebaseWalletRepository
import com.exchangepro.moviles.domain.model.CreateOfferRequest
import com.exchangepro.moviles.domain.model.CurrencyCode
import com.exchangepro.moviles.domain.model.ExchangeRate
import com.exchangepro.moviles.domain.model.Offer
import com.exchangepro.moviles.domain.model.OperationType
import com.exchangepro.moviles.domain.model.TransactionStatus
import com.exchangepro.moviles.domain.model.Wallet
import com.exchangepro.moviles.presentation.navigation.Route
import com.exchangepro.moviles.ui.components.ExchangeCard
import com.exchangepro.moviles.ui.components.OperationSuccessDialog
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
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlin.math.round

@Composable
fun OffersScreen(navController: NavController) {
    val offerRepository = remember { FirebaseOfferRepository() }
    val transactionRepository = remember { FirebaseTransactionRepository() }
    val scope = rememberCoroutineScope()
    val currentUserId = remember { offerRepository.currentUserId() }
    var offers by remember { mutableStateOf(emptyList<Offer>()) }
    var selectedOffer by remember { mutableStateOf<Offer?>(null) }
    var takingOffer by remember { mutableStateOf<Offer?>(null) }
    var actionMessage by remember { mutableStateOf<String?>(null) }
    var actionFailed by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var showSuccessDialog by remember { mutableStateOf(false) }
    var successData by remember { mutableStateOf(Pair("", "")) }

    suspend fun reloadOffers() {
        offers = offerRepository.getActiveOffers()
    }

    LaunchedEffect(Unit) {
        offerRepository.observeActiveOffers()
            .catch { error ->
                actionFailed = true
                actionMessage = error.message ?: "No se pudieron cargar las ofertas."
                loading = false
            }
            .collect { updatedOffers ->
                offers = updatedOffers
                selectedOffer = selectedOffer?.let { selected ->
                    updatedOffers.firstOrNull { it.id == selected.id }
                }
                takingOffer = takingOffer?.let { selected ->
                    updatedOffers.firstOrNull { it.id == selected.id }
                }
                loading = false
            }
    }

    LazyColumn(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            OffersTabs(
                selected = OfferTab.MARKET,
                onNew = { navController.navigate(Route.CreateOffer.value) },
                onMarket = {},
                onMyOffers = { navController.navigate(Route.MyOffers.value) }
            )
        }
        actionMessage?.let {
            item { Text(it, color = if (actionFailed) ExchangeNegative else ExchangePositive, style = MaterialTheme.typography.bodySmall) }
        }
        if (loading) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator(color = ExchangePrimary)
                }
            }
        } else if (offers.isEmpty()) {
            item {
                ExchangeCard {
                    Text("No hay ofertas activas en este momento.", color = ExchangeMuted)
                }
            }
        } else {
            items(offers) { offer ->
                OfferCard(offer, onClick = { selectedOffer = offer })
            }
        }
    }

    selectedOffer?.let { offer ->
        OfferDetailDialog(
            offer = offer,
            isMine = offer.userId == currentUserId,
            onDismiss = { selectedOffer = null },
            onAction = {
                if (offer.userId == currentUserId) {
                    scope.launch {
                        try {
                            offerRepository.cancelOffer(offer.id)
                            reloadOffers()
                            actionFailed = false
                            successData = "Oferta Cancelada" to "La oferta de ${formatMoney(offer.offeredAmount, offer.fromCurrency)} ha sido retirada del mercado y el saldo retenido ha sido liberado."
                            showSuccessDialog = true
                        } catch (error: Exception) {
                            actionFailed = true
                            actionMessage = error.message ?: "No se pudo cancelar la oferta."
                        }
                    }
                } else {
                    takingOffer = offer
                }
                selectedOffer = null
            }
        )
    }

    takingOffer?.let { offer ->
        TakeOfferDialog(
            offer = offer,
            onDismiss = { takingOffer = null },
            onCreate = { amount, paymentMethod, callback ->
                scope.launch {
                    callback(
                        runCatching {
                            transactionRepository.createFromOffer(
                                offerId = offer.id,
                                amount = amount,
                                paymentMethod = paymentMethod
                            )
                        }
                    )
                }
            },
            onDone = { code, amount, currency ->
                actionFailed = false
                takingOffer = null
                scope.launch { reloadOffers() }
                successData = "Transacción Iniciada" to "La transacción $code por ${formatMoney(amount, currency)} ha sido iniciada. Puedes seguir el proceso desde la sección de transacciones."
                showSuccessDialog = true
            }
        )
    }

    if (showSuccessDialog) {
        OperationSuccessDialog(
            title = successData.first,
            message = successData.second,
            onDismiss = { 
                showSuccessDialog = false
                navController.navigate(Route.Transactions.value)
            }
        )
    }
}

@Composable
fun MyOffersScreen(navController: NavController) {
    val offerRepository = remember { FirebaseOfferRepository() }
    val scope = rememberCoroutineScope()
    var myOffers by remember { mutableStateOf(emptyList<Offer>()) }
    var selectedOffer by remember { mutableStateOf<Offer?>(null) }
    var actionMessage by remember { mutableStateOf<String?>(null) }
    var actionFailed by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var showSuccessDialog by remember { mutableStateOf(false) }
    var successData by remember { mutableStateOf(Pair("", "")) }

    suspend fun reloadOffers() {
        myOffers = offerRepository.getMyActiveOffers()
    }

    LaunchedEffect(Unit) {
        offerRepository.observeMyActiveOffers()
            .catch { error ->
                actionFailed = true
                actionMessage = error.message ?: "No se pudieron cargar tus ofertas."
                loading = false
            }
            .collect { updatedOffers ->
                myOffers = updatedOffers
                selectedOffer = selectedOffer?.let { selected ->
                    updatedOffers.firstOrNull { it.id == selected.id }
                }
                loading = false
            }
    }

    LazyColumn(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            OffersTabs(
                selected = OfferTab.MY_OFFERS,
                onNew = { navController.navigate(Route.CreateOffer.value) },
                onMarket = { navController.navigate(Route.Offers.value) },
                onMyOffers = {}
            )
        }
        item {
            Text("Mis ofertas", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Revisa tus publicaciones o crea una nueva oferta.", color = ExchangeMuted)
        }
        actionMessage?.let {
            item { Text(it, color = if (actionFailed) ExchangeNegative else ExchangePositive, style = MaterialTheme.typography.bodySmall) }
        }
        if (loading) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator(color = ExchangePrimary)
                }
            }
        } else if (myOffers.isEmpty()) {
            item {
                ExchangeCard {
                    Text("Aun no tienes ofertas publicadas.", color = ExchangeMuted)
                    Spacer(Modifier.height(12.dp))
                    PrimaryAction("Crear oferta", { navController.navigate(Route.CreateOffer.value) }, Modifier.fillMaxWidth())
                }
            }
        } else {
            items(myOffers) { offer ->
                OfferCard(
                    offer = offer.copy(userName = "Mi oferta"),
                    onClick = { selectedOffer = offer.copy(userName = "Mi oferta") }
                )
            }
        }
    }

    selectedOffer?.let { offer ->
        OfferDetailDialog(
            offer = offer,
            isMine = true,
            onDismiss = { selectedOffer = null },
            onAction = {
                scope.launch {
                    try {
                        offerRepository.cancelOffer(offer.id)
                        reloadOffers()
                        actionFailed = false
                        successData = "Oferta Cancelada" to "La oferta de ${formatMoney(offer.offeredAmount, offer.fromCurrency)} ha sido retirada del mercado y el saldo retenido ha sido liberado."
                        showSuccessDialog = true
                    } catch (error: Exception) {
                        actionFailed = true
                        actionMessage = error.message ?: "No se pudo cancelar la oferta."
                    }
                }
                selectedOffer = null
            }
        )
    }

    if (showSuccessDialog) {
        OperationSuccessDialog(
            title = successData.first,
            message = successData.second,
            onDismiss = { showSuccessDialog = false }
        )
    }
}

@Composable
fun CreateOfferScreen(navController: NavController) {
    val walletRepository = remember { FirebaseWalletRepository() }
    val offerRepository = remember { FirebaseOfferRepository() }
    val rateRepository = remember { ExchangeRateRepository() }
    val scope = rememberCoroutineScope()

    var wallet by remember { mutableStateOf(Wallet(userId = "", balances = emptyList())) }
    var rates by remember { mutableStateOf(emptyList<ExchangeRate>()) }
    var operationType by remember { mutableStateOf<OperationType?>(null) }
    var fromCurrency by remember { mutableStateOf<CurrencyCode?>(null) }
    var toCurrency by remember { mutableStateOf<CurrencyCode?>(null) }
    var selectedRate by remember { mutableStateOf<Double?>(null) }
    var offeredAmount by remember { mutableStateOf("") }
    var minimumAmount by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var submitted by remember { mutableStateOf(false) }
    var showSuccessDialog by remember { mutableStateOf(false) }
    var successData by remember { mutableStateOf(Pair("", "")) }

    val rateOptions = remember(fromCurrency, toCurrency, rates) {
        buildRateOptions(fromCurrency, toCurrency, rates)
    }

    LaunchedEffect(Unit) {
        wallet = runCatching { walletRepository.getWallet() }
            .onFailure { message = it.message ?: "No se pudo cargar la wallet." }
            .getOrDefault(Wallet(userId = "", balances = emptyList()))
        rates = rateRepository.getRates()
    }

    LaunchedEffect(fromCurrency, toCurrency) {
        selectedRate = rateOptions.firstOrNull()
    }

    LazyColumn(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("Crear Oferta", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Publica una nueva oferta en el mercado P2P", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        item {
            WalletSummary(wallet)
        }

        item {
            ExchangeCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AddCircleOutline, contentDescription = null, tint = ExchangePrimaryLight)
                    Spacer(Modifier.width(8.dp))
                    Text("Detalles de la Oferta", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                }

                Spacer(Modifier.height(18.dp))

                OfferDropdown(
                    label = "Tipo de Operacion",
                    value = operationType?.name.orEmpty(),
                    options = OperationType.values().map { it.name },
                    showRequired = submitted && operationType == null,
                    onSelect = { operationType = OperationType.valueOf(it) }
                )

                OfferDropdown(
                    label = if (operationType == OperationType.COMPRA) "Moneda a Comprar" else "Moneda a Vender",
                    value = fromCurrency?.let { "${it.name} (${currencyName(it)})" }.orEmpty(),
                    options = CurrencyCode.values()
                        .filter { it != toCurrency }
                        .map { "${it.name} (${currencyName(it)})" },
                    showRequired = submitted && fromCurrency == null,
                    onSelect = { selected ->
                        fromCurrency = currencyFromOption(selected)
                        if (fromCurrency == toCurrency) toCurrency = null
                    }
                )

                OfferDropdown(
                    label = if (operationType == OperationType.COMPRA) "Moneda a Pagar" else "Moneda a Recibir",
                    value = toCurrency?.let { "${it.name} (${currencyName(it)})" }.orEmpty(),
                    options = CurrencyCode.values()
                        .filter { it != fromCurrency }
                        .map { "${it.name} (${currencyName(it)})" },
                    showRequired = submitted && toCurrency == null,
                    onSelect = { selected ->
                        toCurrency = currencyFromOption(selected)
                        if (fromCurrency == toCurrency) fromCurrency = null
                    }
                )

                OfferDropdown(
                    label = "Tasa de Cambio",
                    value = selectedRate?.let { "%.3f".format(it) }.orEmpty(),
                    options = rateOptions.map { "%.3f".format(it) },
                    showRequired = submitted && selectedRate == null,
                    enabled = fromCurrency != null && toCurrency != null,
                    onSelect = { selectedRate = it.toDoubleOrNull() }
                )

                selectedRate?.let { rate ->
                    if (fromCurrency != null && toCurrency != null) {
                        Text(
                            "1 ${fromCurrency!!.name} ≈ ${"%.3f".format(rate)} ${toCurrency!!.name}",
                            color = ExchangeMuted,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                }

                AmountField(
                    label = "Monto a Ofertar${fromCurrency?.let { " (${it.name})" }.orEmpty()}",
                    value = offeredAmount,
                    onValueChange = { offeredAmount = it },
                    showRequired = submitted && (offeredAmount.toDoubleOrNull() == null || offeredAmount.toDoubleOrNull()!! <= 0.0)
                )

                val previewAmount = offeredAmount.toDoubleOrNull()
                if (previewAmount != null && selectedRate != null && toCurrency != null) {
                    Text(
                        "Recibiras ≈ ${"%.3f".format(previewAmount * selectedRate!!)} ${toCurrency!!.name}",
                        color = ExchangePositive,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(10.dp))
                }

                AmountField(
                    label = "Monto Minimo por operacion${fromCurrency?.let { " (${it.name})" }.orEmpty()}",
                    value = minimumAmount,
                    onValueChange = { minimumAmount = it },
                    hint = "Monto minimo que aceptaras por transaccion",
                    showRequired = submitted && (minimumAmount.toDoubleOrNull() == null || minimumAmount.toDoubleOrNull()!! <= 0.0)
                )

                message?.let {
                    Text(it, color = if (it.contains("publicada", true)) ExchangePositive else ExchangeNegative)
                    Spacer(Modifier.height(8.dp))
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(
                        onClick = {
                            navController.navigate(Route.MyOffers.value) {
                                popUpTo(Route.Offers.value) { inclusive = false }
                            }
                        }
                    ) {
                        Text("Cancelar")
                    }
                    Spacer(Modifier.width(10.dp))
                    Button(
                        onClick = {
                            submitted = true
                            val offered = offeredAmount.toDoubleOrNull()
                            val minimum = minimumAmount.toDoubleOrNull()
                            when {
                                operationType == null -> message = "Selecciona el tipo de operacion."
                                fromCurrency == null -> message = "Selecciona la moneda principal."
                                toCurrency == null -> message = "Selecciona la moneda destino."
                                fromCurrency == toCurrency -> message = "Las monedas deben ser diferentes."
                                selectedRate == null -> message = "Selecciona una tasa de cambio."
                                offered == null || offered <= 0.0 -> message = "El monto a ofertar debe ser mayor a 0."
                                minimum == null || minimum <= 0.0 -> message = "El monto minimo debe ser mayor a 0."
                                minimum > offered -> message = "El monto minimo no puede superar el monto ofertado."
                                else -> {
                                    saving = true
                                    scope.launch {
                                        try {
                                            offerRepository.createOffer(
                                                CreateOfferRequest(
                                                    operationType = operationType!!,
                                                    fromCurrency = fromCurrency!!,
                                                    toCurrency = toCurrency!!,
                                                    exchangeRate = selectedRate!!,
                                                    offeredAmount = offered,
                                                    minimumAmount = minimum
                                                )
                                            )
                                            wallet = walletRepository.getWallet()
                                            successData = "Oferta Publicada" to "Tu oferta de ${formatMoney(offered, fromCurrency)} ha sido publicada exitosamente en el mercado P2P."
                                            showSuccessDialog = true
                                        } catch (error: Exception) {
                                            message = error.message ?: "No se pudo publicar la oferta."
                                        } finally {
                                            saving = false
                                        }
                                    }
                                }
                            }
                        },
                        enabled = !saving,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(if (saving) "Publicando..." else "Publicar Oferta", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }

    if (showSuccessDialog) {
        OperationSuccessDialog(
            title = successData.first,
            message = successData.second,
            onDismiss = {
                showSuccessDialog = false
                navController.navigate(Route.MyOffers.value) {
                    popUpTo(Route.Offers.value) { inclusive = false }
                }
            }
        )
    }
}

@Composable
private fun WalletSummary(wallet: Wallet) {
    ExchangeCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(ExchangePrimary),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = Color.White)
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text("Tu saldo disponible", fontWeight = FontWeight.SemiBold)
                if (wallet.balances.isEmpty()) {
                    Text("Sin saldo. Recarga primero", color = ExchangeMuted)
                } else {
                    Text(
                        wallet.balances.joinToString("   ") { "${it.currency.name}: ${currencySymbol(it.currency)} ${"%.2f".format(it.available)}" },
                        color = ExchangeMuted
                    )
                }
            }
        }
    }
}

@Composable
private fun OfferDropdown(
    label: String,
    value: String,
    options: List<String>,
    showRequired: Boolean,
    enabled: Boolean = true,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column {
        Text(label, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
        Box {
            OutlinedTextField(
                value = value,
                onValueChange = {},
                modifier = Modifier.fillMaxWidth(),
                readOnly = true,
                enabled = enabled,
                placeholder = { Text("Seleccionar") },
                trailingIcon = {
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier.clickable(enabled = enabled) { expanded = true }
                    )
                },
                isError = showRequired
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clickable(enabled = enabled) { expanded = true }
            )
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            onSelect(option)
                            expanded = false
                        }
                    )
                }
            }
        }
        if (showRequired) {
            Text("Requerido", color = ExchangeNegative, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 12.dp, top = 3.dp))
        }
        Spacer(Modifier.height(14.dp))
    }
}

@Composable
private fun AmountField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    hint: String? = null,
    showRequired: Boolean = false,
    errorMessage: String = "Requerido"
) {
    Column {
        Text(label, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
        OutlinedTextField(
            value = value,
            onValueChange = { text -> onValueChange(text.filter { it.isDigit() || it == '.' }) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("0.00") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            isError = showRequired
        )
        if (showRequired) {
            Text(errorMessage, color = ExchangeNegative, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 12.dp, top = 3.dp))
        }
        hint?.let {
            Text(it, color = ExchangeMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 2.dp, top = 4.dp))
        }
        Spacer(Modifier.height(14.dp))
    }
}

private enum class OfferTab { MARKET, MY_OFFERS }

@Composable
private fun OffersTabs(
    selected: OfferTab,
    onNew: () -> Unit,
    onMarket: () -> Unit,
    onMyOffers: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryAction("Nueva", onNew, Modifier.weight(1f))
            if (selected == OfferTab.MARKET) {
                SecondaryAction("Mis ofertas", onMyOffers, Modifier.weight(1f))
            } else {
                SecondaryAction("Mercado", onMarket, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun OfferCard(offer: Offer, onClick: () -> Unit) {
    ExchangeCard(modifier = Modifier.clickable(onClick = onClick)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(offer.userName, fontWeight = FontWeight.Bold)
                Text("${offer.fromCurrency} por ${offer.toCurrency}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            StatusPill(offer.operationType.name)
        }
        Spacer(Modifier.height(10.dp))
        Text("Tasa: %.3f".format(offer.exchangeRate), style = MaterialTheme.typography.titleMedium)
        Text("Disponible: %.2f".format(offer.offeredAmount), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Toca para ver detalle", color = ExchangeMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun OfferDetailDialog(
    offer: Offer,
    isMine: Boolean,
    onDismiss: () -> Unit,
    onAction: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
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
                        Text("Detalle de Oferta", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                        Text(offer.userName, color = ExchangeMuted)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar")
                    }
                }

                DetailRow("Operacion", offer.operationType.name)
                DetailRow("Entrega", offer.fromCurrency.name)
                DetailRow("Recibe", offer.toCurrency.name)
                DetailRow("Tasa", "%.3f".format(offer.exchangeRate))
                DetailRow("Disponible", "${currencySymbol(offer.fromCurrency)} ${"%.2f".format(offer.offeredAmount)}")
                DetailRow("Monto minimo", "${currencySymbol(offer.fromCurrency)} ${"%.2f".format(offer.minimumAmount)}")
                DetailRow("Metodos de pago", offer.paymentMethods.joinToString(", ").ifBlank { "Por definir" })

                val estimated = offer.offeredAmount * offer.exchangeRate
                ExchangeCard {
                    Text("Conversion estimada", color = ExchangeMuted, style = MaterialTheme.typography.bodySmall)
                    Text(
                        "${"%.2f".format(offer.offeredAmount)} ${offer.fromCurrency} -> ${"%.2f".format(estimated)} ${offer.toCurrency}",
                        fontWeight = FontWeight.Bold
                    )
                }

                if (isMine) {
                    Text(
                        "Esta oferta es tuya. No puedes comprar o vender contra tu propia publicacion.",
                        color = ExchangeMuted,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onDismiss) { Text("Cerrar") }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = onAction,
                            colors = ButtonDefaults.buttonColors(containerColor = ExchangeNegative),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Cancelar oferta")
                        }
                    }
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onDismiss) { Text("Cancelar") }
                        Spacer(Modifier.width(8.dp))
                        PrimaryAction(actionLabel(offer), onAction)
                    }
                }
            }
        },
        containerColor = ExchangeSurface,
        shape = RoundedCornerShape(18.dp)
    )
}

private data class PaymentMethodOption(val id: Int, val label: String, val detail: String)

@Composable
private fun TakeOfferDialog(
    offer: Offer,
    onDismiss: () -> Unit,
    onCreate: (Double, String, (Result<com.exchangepro.moviles.domain.model.Transaction>) -> Unit) -> Unit,
    onDone: (String, Double, CurrencyCode) -> Unit
) {
    var amount by remember { mutableStateOf("") }
    var selectedMethod by remember { mutableStateOf<PaymentMethodOption?>(null) }
    var methodMenuOpen by remember { mutableStateOf(false) }
    var submitted by remember { mutableStateOf(false) }
    var instructionsVisible by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var createError by remember { mutableStateOf<String?>(null) }
    var transactionCode by remember { mutableStateOf<String?>(null) }
    var completedInternally by remember { mutableStateOf(false) }
    val methods = remember(offer) { paymentMethodsForOffer(offer) }
    val amountValue = amount.toDoubleOrNull()
    val converted = (amountValue ?: 0.0) * offer.exchangeRate
    val amountError = when {
        amountValue == null || amountValue <= 0.0 -> "Ingresa un monto."
        amountValue < offer.minimumAmount || amountValue > offer.offeredAmount ->
            "El monto debe estar entre %.2f y %.2f %s."
                .format(offer.minimumAmount, offer.offeredAmount, offer.fromCurrency)
        else -> null
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
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
                        Text(
                            if (completedInternally) {
                                "Intercambio Completado"
                            } else if (instructionsVisible) {
                                "Transaccion Iniciada"
                            } else {
                                "Confirmar Operacion"
                            },
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text("${offer.fromCurrency} por ${offer.toCurrency}", color = ExchangeMuted)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar")
                    }
                }

                if (!instructionsVisible) {
                    DetailRow("Usuario", offer.userName)
                    DetailRow("Tipo", offer.operationType.name)
                    DetailRow("Rango", "${"%.2f".format(offer.minimumAmount)} - ${"%.2f".format(offer.offeredAmount)} ${offer.fromCurrency}")
                    DetailRow("Tasa", "%.3f".format(offer.exchangeRate))

                    AmountField(
                        label = if (offer.operationType == OperationType.COMPRA) {
                            "Monto a vender (${offer.fromCurrency})"
                        } else {
                            "Monto a recibir (${offer.fromCurrency})"
                        },
                        value = amount,
                        onValueChange = { amount = it },
                        showRequired = submitted && amountError != null,
                        errorMessage = amountError.orEmpty()
                    )

                    if (amountValue != null && amountValue > 0.0) {
                        Text(
                            "Equivale a ${"%.2f".format(converted)} ${offer.toCurrency}",
                            color = ExchangePositive,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Column {
                        Text("Metodo de pago", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
                        Box {
                            OutlinedTextField(
                                value = selectedMethod?.label.orEmpty(),
                                onValueChange = {},
                                modifier = Modifier.fillMaxWidth(),
                                readOnly = true,
                                placeholder = { Text("Seleccionar") },
                                trailingIcon = {
                                    Icon(
                                        Icons.Default.KeyboardArrowDown,
                                        contentDescription = null,
                                        modifier = Modifier.clickable { methodMenuOpen = true }
                                    )
                                },
                                isError = submitted && selectedMethod == null
                            )
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .clickable { methodMenuOpen = true }
                            )
                            DropdownMenu(expanded = methodMenuOpen, onDismissRequest = { methodMenuOpen = false }) {
                                methods.forEach { method ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(method.label)
                                                Text(method.detail, color = ExchangeMuted, style = MaterialTheme.typography.bodySmall)
                                            }
                                        },
                                        onClick = {
                                            selectedMethod = method
                                            methodMenuOpen = false
                                        }
                                    )
                                }
                            }
                        }
                        if (submitted && selectedMethod == null) {
                            Text("Selecciona un metodo", color = ExchangeNegative, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 12.dp, top = 3.dp))
                        }
                    }

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onDismiss) { Text("Cancelar") }
                        Spacer(Modifier.width(8.dp))
                        PrimaryAction("Confirmar Operacion", {
                            submitted = true
                            if (!saving && isValidOfferAmount(amountValue, offer) && selectedMethod != null) {
                                saving = true
                                createError = null
                                onCreate(amountValue!!, selectedMethod!!.label) { result ->
                                    saving = false
                                    result.fold(
                                        onSuccess = { transaction ->
                                            transactionCode = transaction.code
                                            completedInternally =
                                                transaction.status == TransactionStatus.COMPLETADO
                                            instructionsVisible = true
                                        },
                                        onFailure = { error ->
                                            createError = error.message ?: "No se pudo iniciar la transaccion."
                                        }
                                    )
                                }
                            }
                        })
                    }
                    createError?.let {
                        Text(it, color = ExchangeNegative, style = MaterialTheme.typography.bodySmall)
                    }
                } else {
                    ExchangeCard {
                        Text("Codigo", color = ExchangeMuted, style = MaterialTheme.typography.bodySmall)
                        Text(transactionCode.orEmpty(), fontWeight = FontWeight.Bold)
                    }
                    DetailRow("Metodo seleccionado", selectedMethod?.label.orEmpty())
                    DetailRow("Intercambio", "${"%.2f".format(amountValue ?: 0.0)} ${offer.fromCurrency} por ${"%.2f".format(converted)} ${offer.toCurrency}")
                    DetailRow("Tasa de cambio", "%.3f".format(offer.exchangeRate))

                    if (completedInternally) {
                        ExchangeCard {
                            Text("Fondos intercambiados", fontWeight = FontWeight.Bold, color = ExchangePositive)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Wallet Interna valido ambos saldos y transfirio las dos monedas de forma atomica.",
                                color = ExchangeMuted
                            )
                        }
                    } else {
                        DetailRow("Monto exacto a depositar", "${"%.2f".format(amountValue ?: 0.0)} ${offer.fromCurrency}")
                        ExchangeCard {
                            Text("Datos de la contraparte", fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            Text(selectedMethod?.detail.orEmpty(), color = ExchangeMuted)
                            Text("Titular: ${offer.userName}", color = ExchangeMuted)
                        }
                        Text(
                            "La operacion ya esta registrada. Continua desde Mis Transacciones para confirmar el pago y seguir su estado.",
                            color = ExchangeMuted,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onDismiss) { Text("Cerrar") }
                        Spacer(Modifier.width(8.dp))
                        PrimaryAction("Ir a Transacciones", { onDone(transactionCode.orEmpty(), amountValue ?: 0.0, offer.fromCurrency) })
                    }
                }
            }
        },
        containerColor = ExchangeSurface,
        shape = RoundedCornerShape(18.dp)
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(ExchangeElevated.copy(alpha = 0.74f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = ExchangeMuted)
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}

private fun actionLabel(offer: Offer): String =
    if (offer.operationType == OperationType.VENTA) "Comprar" else "Vender"

private fun isValidOfferAmount(amount: Double?, offer: Offer): Boolean =
    amount != null &&
        amount >= offer.minimumAmount &&
        amount <= offer.offeredAmount

private fun paymentMethodsForOffer(offer: Offer): List<PaymentMethodOption> {
    val rawLabels = offer.paymentMethods + "Wallet Interna"
    val labels = rawLabels
        .map(::paymentMethodLabel)
        .distinctBy { it.lowercase() }

    return labels.mapNotNull { method ->
        val detail = offer.paymentMethodDetails.entries
            .firstOrNull { paymentMethodLabel(it.key) == method }
            ?.value
            .orEmpty()
            .ifBlank {
                if (method == "Wallet Interna") "Saldo interno ExchangePro" else return@mapNotNull null
            }
        PaymentMethodOption(
            id = method.hashCode(),
            label = method,
            detail = detail
        )
    }
}

private fun paymentMethodLabel(value: String): String = when (
    value.trim().uppercase().replace(' ', '_')
) {
    "YAPE" -> "Yape"
    "PLIN" -> "Plin"
    "TRANSFERENCIA_BANCARIA" -> "Transferencia Bancaria"
    "WALLET_INTERNA" -> "Wallet Interna"
    else -> value.trim()
}

private fun buildRateOptions(
    fromCurrency: CurrencyCode?,
    toCurrency: CurrencyCode?,
    rates: List<ExchangeRate>
): List<Double> {
    if (fromCurrency == null || toCurrency == null || fromCurrency == toCurrency) return emptyList()

    val mid = when {
        toCurrency == CurrencyCode.PEN -> rates.firstOrNull { it.code == fromCurrency }?.mid
        fromCurrency == CurrencyCode.PEN -> rates.firstOrNull { it.code == toCurrency }?.mid?.let { 1 / it }
        else -> {
            val fromMid = rates.firstOrNull { it.code == fromCurrency }?.mid
            val toMid = rates.firstOrNull { it.code == toCurrency }?.mid
            if (fromMid != null && toMid != null) fromMid / toMid else null
        }
    } ?: return emptyList()

    val step = when {
        mid < 1 -> 0.001
        mid < 10 -> 0.02
        else -> 0.5
    }
    val start = mid - step * 3
    return List(7) { index -> round3(start + index * step) }.filter { it > 0.0 }
}

private fun currencyFromOption(option: String): CurrencyCode =
    CurrencyCode.valueOf(option.substringBefore(" "))

private fun round3(value: Double): Double = round(value * 1000) / 1000

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

private fun formatMoney(value: Double, currency: CurrencyCode?): String {
    val symbol = currency?.let { currencySymbol(it) } ?: "S/"
    return "$symbol ${"%.2f".format(value)}"
}

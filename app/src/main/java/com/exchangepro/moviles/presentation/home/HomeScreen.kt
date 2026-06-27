package com.exchangepro.moviles.presentation.home

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
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.exchangepro.moviles.data.repository.FirebaseOfferRepository
import com.exchangepro.moviles.data.repository.FirebaseTransactionRepository
import com.exchangepro.moviles.data.repository.FirebaseUserRepository
import com.exchangepro.moviles.data.repository.FirebaseWalletRepository
import com.exchangepro.moviles.domain.model.AppUser
import com.exchangepro.moviles.domain.model.Offer
import com.exchangepro.moviles.domain.model.Transaction
import com.exchangepro.moviles.domain.model.Wallet
import com.exchangepro.moviles.presentation.navigation.Route
import com.exchangepro.moviles.ui.components.ExchangeCard
import com.exchangepro.moviles.ui.components.PrimaryAction
import com.exchangepro.moviles.ui.components.SectionHeader
import com.exchangepro.moviles.ui.components.StatusPill
import com.exchangepro.moviles.ui.theme.ExchangeAccent
import com.exchangepro.moviles.ui.theme.ExchangeMuted
import com.exchangepro.moviles.ui.theme.ExchangePositive
import com.exchangepro.moviles.ui.theme.ExchangePrimary
import com.exchangepro.moviles.ui.theme.ExchangePrimaryLight

@Composable
fun HomeScreen(navController: NavController) {
    val userRepository = remember { FirebaseUserRepository() }
    val walletRepository = remember { FirebaseWalletRepository() }
    val offerRepository = remember { FirebaseOfferRepository() }
    val transactionRepository = remember { FirebaseTransactionRepository() }
    var user by remember { mutableStateOf<AppUser?>(null) }
    var wallet by remember { mutableStateOf(Wallet(userId = "", balances = emptyList())) }
    var offers by remember { mutableStateOf(emptyList<Offer>()) }
    var transactions by remember { mutableStateOf(emptyList<Transaction>()) }
    var loadError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            user = userRepository.getCurrentUser()
            wallet = walletRepository.getWallet()
            offers = offerRepository.getActiveOffers()
            transactions = transactionRepository.getMyTransactions()
        } catch (error: Exception) {
            loadError = error.message ?: "No se pudieron cargar los datos."
        }
    }

    val totalPen = wallet.balances.sumOf { if (it.currency.name == "PEN") it.available else it.available * 3.72 }
    val activeOffers = offers.size

    LazyColumn(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                "Hola, ${user?.fullName?.split(" ")?.firstOrNull().orEmpty().ifBlank { "Usuario" }}",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text("Gestiona tus intercambios P2P desde un solo lugar", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        loadError?.let { error ->
            item {
                Text("No se pudo actualizar el resumen: $error", color = ExchangeMuted, style = MaterialTheme.typography.bodySmall)
            }
        }

        item {
            MarketRhythmCard(activeOffers = activeOffers)
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DashboardStat(
                        label = "Saldo Wallet",
                        value = "S/ %.2f".format(totalPen),
                        detail = "${wallet.balances.size} monedas",
                        iconTint = ExchangePrimary,
                        icon = Icons.Default.AccountBalanceWallet,
                        modifier = Modifier.weight(1f),
                        onClick = { navController.navigate(Route.Wallet.value) }
                    )
                    DashboardStat(
                        label = "Ofertas Activas",
                        value = activeOffers.toString(),
                        detail = "En el mercado",
                        iconTint = ExchangeAccent,
                        icon = Icons.Default.Storefront,
                        modifier = Modifier.weight(1f),
                        onClick = { navController.navigate(Route.Offers.value) }
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DashboardStat(
                        label = "Mis Transacciones",
                        value = transactions.size.toString(),
                        detail = "Total de operaciones",
                        iconTint = ExchangePrimaryLight,
                        icon = Icons.Default.SwapHoriz,
                        modifier = Modifier.weight(1f),
                        onClick = { navController.navigate(Route.Transactions.value) }
                    )
                    DashboardStat(
                        label = "Mi Rol",
                        value = "Usuario",
                        detail = "Cuenta activa",
                        iconTint = ExchangePositive,
                        icon = Icons.Default.VerifiedUser,
                        modifier = Modifier.weight(1f),
                        onClick = { navController.navigate(Route.Profile.value) }
                    )
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryAction("Crear oferta", { navController.navigate(Route.CreateOffer.value) }, Modifier.weight(1f))
                PrimaryAction("Recargar wallet", { navController.navigate(Route.Wallet.value) }, Modifier.weight(1f))
            }
        }

        item {
            SectionHeader("Ultimas Ofertas", "Ver todas") {
                navController.navigate(Route.Offers.value)
            }
        }
        items(offers.take(4)) { offer ->
            ExchangeCard(modifier = Modifier.clickable { navController.navigate(Route.Offers.value) }) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(ExchangePrimary),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(offer.userName.take(1), color = Color.White, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(offer.userName, fontWeight = FontWeight.SemiBold)
                            Text("${offer.fromCurrency} -> ${offer.toCurrency}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    StatusPill(offer.operationType.name)
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Tasa %.3f".format(offer.exchangeRate), fontWeight = FontWeight.SemiBold)
                    Text("%.2f ${offer.fromCurrency}".format(offer.offeredAmount), color = ExchangeMuted)
                }
            }
        }

        item {
            SectionHeader("Actividad Reciente", "Ver todo") {
                navController.navigate(Route.Transactions.value)
            }
        }
        items(transactions.take(4)) { trx ->
            ExchangeCard(modifier = Modifier.clickable { navController.navigate(Route.Transactions.value) }) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text(trx.code, fontWeight = FontWeight.SemiBold)
                        Text("${trx.operationAmount} ${trx.currency}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    StatusPill(trx.status.name)
                }
            }
        }
    }
}

@Composable
private fun MarketRhythmCard(activeOffers: Int) {
    ExchangeCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(ExchangePrimary.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Storefront, contentDescription = null, tint = ExchangePrimaryLight)
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Mercado P2P activo", fontWeight = FontWeight.Bold)
                Text("$activeOffers ofertas disponibles para operar", color = ExchangeMuted, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun DashboardStat(
    label: String,
    value: String,
    detail: String,
    iconTint: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier,
    onClick: () -> Unit
) {
    ExchangeCard(modifier = modifier.clickable(onClick = onClick)) {
        Icon(icon, contentDescription = null, tint = iconTint)
        Spacer(Modifier.height(8.dp))
        Text(label, color = ExchangeMuted, style = MaterialTheme.typography.bodySmall)
        Text(value, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
        Text(detail, color = ExchangeMuted, style = MaterialTheme.typography.bodySmall)
    }
}

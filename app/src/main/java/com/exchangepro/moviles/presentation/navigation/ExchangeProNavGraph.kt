package com.exchangepro.moviles.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.exchangepro.moviles.presentation.admin.AdminDashboardScreen
import com.exchangepro.moviles.presentation.auth.LoginScreen
import com.exchangepro.moviles.presentation.auth.RegisterScreen
import com.exchangepro.moviles.presentation.disputes.DisputesScreen
import com.exchangepro.moviles.presentation.home.HomeScreen
import com.exchangepro.moviles.presentation.notifications.NotificationsScreen
import com.exchangepro.moviles.presentation.offers.CreateOfferScreen
import com.exchangepro.moviles.presentation.offers.MyOffersScreen
import com.exchangepro.moviles.presentation.offers.OffersScreen
import com.exchangepro.moviles.presentation.payment.PaymentDataScreen
import com.exchangepro.moviles.presentation.placeholder.PendingScreen
import com.exchangepro.moviles.presentation.profile.ProfileScreen
import com.exchangepro.moviles.presentation.transactions.TransactionsScreen
import com.exchangepro.moviles.presentation.wallet.WalletScreen

@Composable
fun ExchangeProNavGraph() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Route.Login.value) {
        composable(Route.Login.value) { LoginScreen(navController) }
        composable(Route.Register.value) { RegisterScreen(navController) }
        composable(Route.Home.value) {
            ExchangeScaffold(navController, "Inicio") { HomeScreen(navController) }
        }
        composable(Route.Offers.value) {
            ExchangeScaffold(navController, "Ofertas") { OffersScreen(navController) }
        }
        composable(Route.CreateOffer.value) {
            ExchangeScaffold(navController, "Crear oferta") { CreateOfferScreen(navController) }
        }
        composable(Route.MyOffers.value) {
            ExchangeScaffold(navController, "Mis ofertas") { MyOffersScreen(navController) }
        }
        composable(Route.Wallet.value) {
            ExchangeScaffold(navController, "Wallet") { WalletScreen() }
        }
        composable(Route.Transactions.value) {
            ExchangeScaffold(navController, "Transacciones") { TransactionsScreen() }
        }
        composable(Route.PaymentData.value) {
            ExchangeScaffold(navController, "Datos de pago") { PaymentDataScreen() }
        }
        composable(Route.Disputes.value) {
            ExchangeScaffold(navController, "Disputas", content = { DisputesScreen() })
        }
        composable(Route.Profile.value) {
            ExchangeScaffold(navController, "Perfil") { ProfileScreen() }
        }
        composable(Route.Notifications.value) {
            ExchangeScaffold(navController, "Notificaciones") { NotificationsScreen() }
        }
        composable(Route.AdminDashboard.value) {
            ExchangeScaffold(navController, "Dashboard", isAdmin = true) {
                AdminDashboardScreen(navController)
            }
        }
        composable(Route.AdminDisputes.value) {
            ExchangeScaffold(navController, "Disputas", isAdmin = true) { PendingScreen("Disputas admin", "Integrante 5") }
        }
        composable(Route.AdminFeedback.value) {
            ExchangeScaffold(navController, "Feedback", isAdmin = true) { PendingScreen("Feedback admin", "Integrante 5") }
        }
        composable(Route.AdminReports.value) {
            ExchangeScaffold(navController, "Reportes", isAdmin = true) { PendingScreen("Reportes admin", "Integrante 5") }
        }
        composable(Route.AdminNotifications.value) {
            ExchangeScaffold(navController, "Notificaciones", isAdmin = true) { PendingScreen("Notificaciones admin", "Integrante 5") }
        }
    }
}

package com.exchangepro.moviles.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.exchangepro.moviles.presentation.auth.LoginScreen
import com.exchangepro.moviles.presentation.auth.RegisterScreen
import com.exchangepro.moviles.presentation.placeholder.PendingScreen
import com.exchangepro.moviles.presentation.wallet.WalletScreen

@Composable
fun ExchangeProNavGraph() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Route.Login.value) {
        composable(Route.Login.value) { LoginScreen(navController) }
        composable(Route.Register.value) { RegisterScreen(navController) }
        composable(Route.Home.value) {
            ExchangeScaffold(navController, "Inicio") { PendingScreen("Inicio", "Integrante 4") }
        }
        composable(Route.Offers.value) {
            ExchangeScaffold(navController, "Ofertas") { PendingScreen("Ofertas", "Integrante 3") }
        }
        composable(Route.CreateOffer.value) {
            ExchangeScaffold(navController, "Crear oferta") { PendingScreen("Crear oferta", "Integrante 3") }
        }
        composable(Route.MyOffers.value) {
            ExchangeScaffold(navController, "Mis ofertas") { PendingScreen("Mis ofertas", "Integrante 3") }
        }
        composable(Route.Wallet.value) {
            ExchangeScaffold(navController, "Wallet") { WalletScreen() }
        }
        composable(Route.Transactions.value) {
            ExchangeScaffold(navController, "Transacciones") { PendingScreen("Transacciones", "Integrante 4") }
        }
        composable(Route.PaymentData.value) {
            ExchangeScaffold(navController, "Datos de pago") { PendingScreen("Datos de pago", "Integrante 3") }
        }
        composable(Route.Disputes.value) {
            ExchangeScaffold(navController, "Disputas", content = { PendingScreen("Disputas", "Integrante 4") })
        }
        composable(Route.Profile.value) {
            ExchangeScaffold(navController, "Perfil") { PendingScreen("Perfil", "Integrante 4") }
        }
        composable(Route.Notifications.value) {
            ExchangeScaffold(navController, "Notificaciones") { PendingScreen("Notificaciones", "Integrante 4") }
        }
        composable(Route.AdminDashboard.value) {
            ExchangeScaffold(navController, "Dashboard", isAdmin = true) { PendingScreen("Dashboard admin", "Integrante 5") }
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

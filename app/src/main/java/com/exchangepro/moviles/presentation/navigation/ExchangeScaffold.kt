package com.exchangepro.moviles.presentation.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.exchangepro.moviles.data.repository.FirebaseAuthRepository
import com.exchangepro.moviles.ui.theme.ExchangeBg
import com.exchangepro.moviles.ui.theme.ExchangeSurface
import kotlinx.coroutines.launch

private data class DrawerItem(val label: String, val route: Route, val icon: androidx.compose.ui.graphics.vector.ImageVector)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExchangeScaffold(
    navController: NavController,
    title: String,
    isAdmin: Boolean = false,
    content: @Composable () -> Unit
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val authRepository = remember { FirebaseAuthRepository() }
    val items = if (isAdmin) {
        listOf(
            DrawerItem("Dashboard", Route.AdminDashboard, Icons.Default.Dashboard),
            DrawerItem("Disputas", Route.AdminDisputes, Icons.Default.Gavel),
            DrawerItem("Feedback", Route.AdminFeedback, Icons.Default.Feedback),
            DrawerItem("Reportes", Route.AdminReports, Icons.Default.BarChart),
            DrawerItem("Notificaciones", Route.AdminNotifications, Icons.Default.Notifications)
        )
    } else {
        listOf(
            DrawerItem("Inicio", Route.Home, Icons.Default.Home),
            DrawerItem("Ofertas", Route.Offers, Icons.Default.Storefront),
            DrawerItem("Wallet", Route.Wallet, Icons.Default.AccountBalanceWallet),
            DrawerItem("Transacciones", Route.Transactions, Icons.Default.SwapHoriz),
            DrawerItem("Datos de pago", Route.PaymentData, Icons.Default.CreditCard),
            DrawerItem("Disputas", Route.Disputes, Icons.Default.ReportProblem),
            DrawerItem("Feedback", Route.Feedback, Icons.Default.Feedback),
            DrawerItem("Notificaciones", Route.Notifications, Icons.Default.Campaign),
            DrawerItem("Perfil", Route.Profile, Icons.Default.Person)
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = ExchangeSurface) {
                Spacer(Modifier.height(18.dp))
                Text(
                    if (isAdmin) "Admin Panel" else "ExchangePro",
                    modifier = Modifier.padding(16.dp),
                    fontWeight = FontWeight.Bold
                )
                items.forEach { item ->
                    NavigationDrawerItem(
                        label = { Text(item.label) },
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        selected = false,
                        onClick = {
                            scope.launch { drawerState.close() }
                            navController.navigate(item.route.value)
                        }
                    )
                }
                NavigationDrawerItem(
                    label = { Text("Cerrar sesion") },
                    icon = { Icon(Icons.Default.Logout, contentDescription = "Cerrar sesion") },
                    selected = false,
                    onClick = {
                        authRepository.signOut()
                        navController.navigate(Route.Login.value) {
                            popUpTo(0)
                        }
                    }
                )
            }
        }
    ) {
        Scaffold(
            containerColor = ExchangeBg,
            topBar = {
                TopAppBar(
                    title = { Text(if (isAdmin) "🛡 $title" else title) },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = ExchangeBg)
                )
            }
        ) { padding ->
            Box(modifier = Modifier.padding(padding)) {
                content()
            }
        }
    }
}

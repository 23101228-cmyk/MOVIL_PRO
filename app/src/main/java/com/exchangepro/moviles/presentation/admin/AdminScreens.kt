package com.exchangepro.moviles.presentation.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.exchangepro.moviles.presentation.navigation.Route
import com.exchangepro.moviles.ui.components.ExchangeCard
import com.exchangepro.moviles.ui.components.PrimaryAction
import com.exchangepro.moviles.ui.components.SecondaryAction
import com.exchangepro.moviles.ui.theme.ExchangeAccent
import com.exchangepro.moviles.ui.theme.ExchangeMuted
import com.exchangepro.moviles.ui.theme.ExchangeNegative
import com.exchangepro.moviles.ui.theme.ExchangePositive
import com.exchangepro.moviles.ui.theme.ExchangePrimary
import com.exchangepro.moviles.ui.theme.ExchangeWarning

private data class AdminStat(
    val label: String,
    val value: String,
    val detail: String,
    val icon: ImageVector,
    val color: Color
)

@Composable
fun AdminDashboardScreen(navController: NavController) {
    val stats = listOf(
        AdminStat("Usuarios Registrados", "128", "+12 esta semana", Icons.Default.Groups, ExchangePrimary),
        AdminStat("Ofertas Activas", "34", "PEN, USD y EUR", Icons.Default.Sell, ExchangeAccent),
        AdminStat("Transacciones Completadas", "286", "S/ 94,250 movidos", Icons.Default.SwapHoriz, ExchangePositive),
        AdminStat("Disputas Pendientes", "3", "Requieren revision", Icons.Default.Gavel, ExchangeNegative)
    )

    AdminPage(
        title = "Dashboard",
        subtitle = "Panel de control administrativo"
    ) {
        item {
            ExchangeCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Refresh, contentDescription = null, tint = ExchangeAccent)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "ESTADO DE LA PLATAFORMA: OPERATIVO  -  USUARIOS ACTIVOS: 128  -  OFERTAS: 34  -  DISPUTAS: 3",
                        color = ExchangeMuted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        items(stats) { stat ->
            AdminStatCard(stat)
        }
        item {
            ExchangeCard {
                Text("Acciones rapidas", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PrimaryAction(
                        "Revisar disputas",
                        onClick = { navController.navigate(Route.AdminDisputes.value) },
                        modifier = Modifier.weight(1f)
                    )
                    SecondaryAction(
                        "Reportes",
                        onClick = { navController.navigate(Route.AdminReports.value) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        item {
            ExchangeCard {
                Text("Actividad Reciente", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                ActivityRow("Nuevo usuario registrado", "hace 2 min", ExchangePrimary)
                ActivityRow("Transaccion #1284 completada", "hace 8 min", ExchangePositive)
                ActivityRow("Disputa #42 resuelta", "hace 15 min", ExchangePositive)
                ActivityRow("Feedback recibido de usuario", "hace 35 min", ExchangeWarning)
            }
        }
    }
}

@Composable
private fun AdminPage(
    title: String,
    subtitle: String,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column {
                Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(subtitle, color = ExchangeMuted)
            }
        }
        content()
        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun AdminStatCard(stat: AdminStat) {
    ExchangeCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    stat.value,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = stat.color
                )
                Text(stat.label, fontWeight = FontWeight.SemiBold)
                Text(stat.detail, color = ExchangeMuted, style = MaterialTheme.typography.bodySmall)
            }
            IconBadge(stat.icon, stat.color)
        }
    }
}

@Composable
private fun ActivityRow(text: String, time: String, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(10.dp))
        Text(text, modifier = Modifier.weight(1f))
        Text(time, color = ExchangeMuted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun IconBadge(icon: ImageVector, color: Color) {
    Box(
        modifier = Modifier
            .size(46.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = color)
    }
}

package com.exchangepro.moviles.presentation.admin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.PendingActions
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.exchangepro.moviles.presentation.navigation.Route
import com.exchangepro.moviles.ui.components.ExchangeCard
import com.exchangepro.moviles.ui.components.PrimaryAction
import com.exchangepro.moviles.ui.components.SecondaryAction
import com.exchangepro.moviles.ui.components.StatusPill
import com.exchangepro.moviles.ui.theme.ExchangeAccent
import com.exchangepro.moviles.ui.theme.ExchangeElevated
import com.exchangepro.moviles.ui.theme.ExchangeMuted
import com.exchangepro.moviles.ui.theme.ExchangeNegative
import com.exchangepro.moviles.ui.theme.ExchangePositive
import com.exchangepro.moviles.ui.theme.ExchangePrimary
import com.exchangepro.moviles.ui.theme.ExchangeSurface
import com.exchangepro.moviles.ui.theme.ExchangeWarning

private data class AdminStat(
    val label: String,
    val value: String,
    val detail: String,
    val icon: ImageVector,
    val color: Color
)

private data class AdminDispute(
    val id: String,
    val transactionId: String,
    val reason: String,
    val reporter: String,
    val buyer: String,
    val seller: String,
    val amount: String,
    val state: String,
    val evidence: String,
    val description: String,
    val resolution: String = ""
)

private data class AdminFeedback(
    val id: String,
    val type: String,
    val title: String,
    val user: String,
    val email: String,
    val state: String,
    val message: String,
    val response: String = ""
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

@Composable
fun AdminDisputesScreen() {
    val disputes = remember { mutableStateListOf(*demoDisputes().toTypedArray()) }
    var filter by remember { mutableStateOf("Pendientes") }
    var selected by remember { mutableStateOf<AdminDispute?>(null) }
    val visible = disputes.filter { filter == "Todos" || it.state == filter }

    selected?.let { dispute ->
        ResolveDisputeDialog(
            dispute = dispute,
            onDismiss = { selected = null },
            onResolve = { decision, note ->
                val index = disputes.indexOfFirst { it.id == dispute.id }
                if (index >= 0) {
                    disputes[index] = dispute.copy(
                        state = "Resueltas",
                        resolution = "$decision: $note"
                    )
                }
                selected = null
            }
        )
    }

    AdminPage(
        title = "Gestion de Disputas",
        subtitle = "Revisa y resuelve disputas pendientes"
    ) {
        item {
            AdminFilterRow(
                options = listOf("Todos", "Pendientes", "Resueltas"),
                selected = filter,
                onSelected = { filter = it }
            )
        }
        items(visible) { dispute ->
            AdminDisputeCard(dispute, onResolve = { selected = dispute })
        }
    }
}

@Composable
fun AdminFeedbackScreen() {
    val feedback = remember { mutableStateListOf(*demoFeedback().toTypedArray()) }
    var typeFilter by remember { mutableStateOf("Todos") }
    var selected by remember { mutableStateOf<AdminFeedback?>(null) }
    val visible = feedback.filter { typeFilter == "Todos" || it.type == typeFilter }

    selected?.let { item ->
        RespondFeedbackDialog(
            feedback = item,
            onDismiss = { selected = null },
            onSend = { response ->
                val index = feedback.indexOfFirst { it.id == item.id }
                if (index >= 0) {
                    feedback[index] = item.copy(state = "REVISADO", response = response)
                }
                selected = null
            }
        )
    }

    AdminPage(
        title = "Buzon de Feedback",
        subtitle = "Revisa sugerencias y reportes de error"
    ) {
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                MiniStat(
                    "Total",
                    feedback.size.toString(),
                    Icons.Default.Feedback,
                    ExchangePrimary,
                    Modifier.weight(1f)
                )
                MiniStat(
                    "Pendientes",
                    feedback.count { it.state != "REVISADO" }.toString(),
                    Icons.Default.PendingActions,
                    ExchangeWarning,
                    Modifier.weight(1f)
                )
            }
        }
        item {
            AdminFilterRow(
                options = listOf("Todos", "RECOMENDACION", "BUG_REPORT"),
                selected = typeFilter,
                onSelected = { typeFilter = it }
            )
        }
        items(visible) { item ->
            AdminFeedbackCard(item, onRespond = { selected = item })
        }
    }
}

@Composable
private fun MiniStat(
    label: String,
    value: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = ExchangeSurface),
        border = BorderStroke(1.dp, color.copy(alpha = 0.35f)),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Icon(icon, contentDescription = null, tint = color)
            Spacer(Modifier.height(8.dp))
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(label, color = ExchangeMuted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdminFilterRow(
    options: List<String>,
    selected: String,
    onSelected: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.chunked(2).forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                row.forEach { option ->
                    FilterChip(
                        selected = selected == option,
                        onClick = { onSelected(option) },
                        label = { Text(option, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (row.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun AdminDisputeCard(dispute: AdminDispute, onResolve: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    ExchangeCard(modifier = Modifier.clickable { expanded = !expanded }) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.Top) {
                InitialCircle("#${dispute.id}", ExchangePrimary)
                Spacer(Modifier.width(12.dp))
                Column {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StatusPill(dispute.state)
                        Text(
                            "Tx ${dispute.transactionId}",
                            color = ExchangeMuted,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Text(dispute.reason, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text("Reportado por ${dispute.reporter}", color = ExchangeMuted)
                }
            }
            if (dispute.state == "Pendientes") {
                SecondaryAction("Resolver", onClick = onResolve)
            }
        }

        if (expanded) {
            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = ExchangeElevated)
            Spacer(Modifier.height(12.dp))
            DetailLine("Comprador", dispute.buyer)
            DetailLine("Vendedor", dispute.seller)
            DetailLine("Monto", dispute.amount)
            DetailLine("Evidencia", dispute.evidence)
            Text(dispute.description, color = ExchangeMuted, modifier = Modifier.padding(top = 8.dp))
            if (dispute.resolution.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(dispute.resolution, color = ExchangePositive, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun AdminFeedbackCard(item: AdminFeedback, onRespond: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val color = if (item.type == "BUG_REPORT") ExchangeNegative else ExchangePrimary

    ExchangeCard(modifier = Modifier.clickable { expanded = !expanded }) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.Top) {
                InitialCircle(item.user.take(1).uppercase(), color)
                Spacer(Modifier.width(12.dp))
                Column {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StatusPill(if (item.type == "BUG_REPORT") "Bug" else "Sugerencia")
                        StatusPill(item.state)
                    }
                    Text(item.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${item.user} - ${item.email}",
                        color = ExchangeMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (item.state != "REVISADO") {
                SecondaryAction("Responder", onClick = onRespond)
            }
        }

        if (expanded) {
            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = ExchangeElevated)
            Spacer(Modifier.height(12.dp))
            Text(item.message, color = ExchangeMuted)
            if (item.response.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text("Respuesta admin", fontWeight = FontWeight.Bold, color = ExchangePositive)
                Text(item.response, color = ExchangeMuted)
            }
        }
    }
}

@Composable
private fun ResolveDisputeDialog(
    dispute: AdminDispute,
    onDismiss: () -> Unit,
    onResolve: (String, String) -> Unit
) {
    var decision by remember { mutableStateOf("Liberar fondos al comprador") }
    var note by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Resolver Disputa ${dispute.id}") },
        text = {
            Column {
                Text(dispute.reason, fontWeight = FontWeight.Bold)
                Text("Transaccion ${dispute.transactionId}", color = ExchangeMuted)
                Spacer(Modifier.height(12.dp))
                AdminFilterRow(
                    options = listOf("Liberar fondos al comprador", "Devolver al vendedor"),
                    selected = decision,
                    onSelected = { decision = it }
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(500) },
                    label = { Text("Observacion") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 110.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { if (note.isNotBlank()) onResolve(decision, note) }) {
                Text("Emitir fallo")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
private fun RespondFeedbackDialog(
    feedback: AdminFeedback,
    onDismiss: () -> Unit,
    onSend: (String) -> Unit
) {
    var response by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Responder Comentario") },
        text = {
            Column {
                Text(feedback.title, fontWeight = FontWeight.Bold)
                Text(feedback.message, color = ExchangeMuted)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = response,
                    onValueChange = { response = it.take(500) },
                    label = { Text("Tu respuesta") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { if (response.isNotBlank()) onSend(response) }) {
                Text("Enviar respuesta")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = ExchangeMuted)
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun InitialCircle(text: String, color: Color) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(color),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Color.White, fontWeight = FontWeight.Bold)
    }
}

private fun demoDisputes() = listOf(
    AdminDispute(
        id = "42",
        transactionId = "#1284",
        reason = "Pago no confirmado",
        reporter = "Ana Torres",
        buyer = "Ana Torres",
        seller = "Luis Chang",
        amount = "S/ 1,200.00",
        state = "Pendientes",
        evidence = "voucher-yape-1284.png",
        description = "El comprador indica que realizo el pago por Yape, pero el vendedor aun no libera los fondos."
    ),
    AdminDispute(
        id = "43",
        transactionId = "#1285",
        reason = "Datos bancarios incorrectos",
        reporter = "Gustavo Ramirez",
        buyer = "Luis Chang",
        seller = "Gustavo Ramirez",
        amount = "$ 350.00",
        state = "Pendientes",
        evidence = "captura-transferencia.png",
        description = "La transferencia fue enviada a una cuenta distinta a la registrada en datos de pago."
    ),
    AdminDispute(
        id = "41",
        transactionId = "#1278",
        reason = "Comprobante validado",
        reporter = "Maria Silva",
        buyer = "Maria Silva",
        seller = "Ana Torres",
        amount = "S/ 540.00",
        state = "Resueltas",
        evidence = "voucher-bcp.png",
        description = "El comprobante fue validado y se libero el saldo.",
        resolution = "Liberar fondos al comprador: evidencia correcta."
    )
)

private fun demoFeedback() = listOf(
    AdminFeedback(
        id = "F-01",
        type = "RECOMENDACION",
        title = "Agregar filtro por banco",
        user = "Ana Torres",
        email = "ana@demo.com",
        state = "PENDIENTE",
        message = "Seria util filtrar ofertas por metodo de pago o banco para encontrar operaciones mas rapido."
    ),
    AdminFeedback(
        id = "F-02",
        type = "BUG_REPORT",
        title = "No abre comprobante",
        user = "Luis Chang",
        email = "luis@demo.com",
        state = "PENDIENTE",
        message = "Al intentar ver el comprobante desde transacciones, la vista se queda cargando."
    ),
    AdminFeedback(
        id = "F-03",
        type = "RECOMENDACION",
        title = "Notificar tasa favorable",
        user = "Maria Silva",
        email = "maria@demo.com",
        state = "REVISADO",
        message = "Me gustaria recibir alertas cuando la tasa baje de cierto valor.",
        response = "La sugerencia queda registrada para el modulo de notificaciones."
    )
)

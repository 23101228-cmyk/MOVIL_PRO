package com.exchangepro.moviles.presentation.admin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import com.exchangepro.moviles.data.ai.GeminiAdminResult
import com.exchangepro.moviles.data.ai.GeminiAdminService
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
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.exchangepro.moviles.data.repository.AdminDashboardData
import com.exchangepro.moviles.data.repository.AdminDisputeRecord
import com.exchangepro.moviles.data.repository.AdminFeedbackRecord
import com.exchangepro.moviles.data.repository.AdminReportRow
import com.exchangepro.moviles.data.repository.FirebaseAdminRepository
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
import com.exchangepro.moviles.ui.theme.ExchangePrimaryLight
import com.exchangepro.moviles.ui.theme.ExchangeWarning
import kotlinx.coroutines.launch
import kotlin.math.max

private data class AdminStat(
    val label: String,
    val value: String,
    val icon: ImageVector,
    val color: Color
)

@Composable
fun AdminDashboardScreen(navController: NavController) {
    val repository = remember { FirebaseAdminRepository() }
    val aiService = remember { GeminiAdminService() }

    var data by remember { mutableStateOf(AdminDashboardData()) }
    var loading by remember { mutableStateOf(true) }
    var aiLoading by remember { mutableStateOf(false) }
    var aiResult by remember { mutableStateOf<GeminiAdminResult?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        runCatching { repository.dashboard() }
            .onSuccess { data = it }
            .onFailure { error = it.message ?: "No se pudo cargar el dashboard." }
        loading = false
    }

    LaunchedEffect(loading, data) {
        if (!loading && error == null) {
            aiLoading = true
            val fallback = buildAdminAiAnalysis(data).toDisplayText()
            aiResult = aiService.analyzeDashboard(data, fallback)
            aiLoading = false
        }
    }

    val stats = listOf(
        AdminStat("Usuarios registrados", data.users.toString(), Icons.Default.Groups, ExchangePrimary),
        AdminStat("Ofertas activas", data.activeOffers.toString(), Icons.Default.Sell, ExchangeAccent),
        AdminStat("Transacciones completadas", data.completedTransactions.toString(), Icons.Default.SwapHoriz, ExchangePositive),
        AdminStat("Disputas pendientes", data.pendingDisputes.toString(), Icons.Default.Gavel, ExchangeNegative)
    )

    AdminPage("Dashboard", "Panel de control administrativo") {
        if (loading) item { ExchangeCard { Text("Cargando datos reales...", color = ExchangeMuted) } }
        error?.let { item { ErrorCard(it) } }

        items(stats.chunked(2)) { rowStats ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                rowStats.forEach { stat -> AdminStatCard(stat, Modifier.weight(1f)) }
                if (rowStats.size == 1) Spacer(Modifier.weight(1f))
            }
        }

        item {
            AdminBarChart(
                title = "Transacciones por estado",
                values = orderedStatusValues(data.transactionsByStatus)
            )
        }

        item {
            AdminDonutChart(
                title = "Disputas",
                pending = data.pendingDisputes,
                resolved = data.resolvedDisputes
            )
        }

        item {
            AdminVolumeChart(data.volumeByCurrency)
        }

        item {
            AdminAiSummaryCard(data, aiResult, aiLoading)
        }

        item {
            ExchangeCard {
                Text("Acciones rapidas", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PrimaryAction("Revisar disputas", { navController.navigate(Route.AdminDisputes.value) }, Modifier.weight(1f))
                    SecondaryAction("Reportes", { navController.navigate(Route.AdminReports.value) }, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun AdminDisputesScreen() {
    val repository = remember { FirebaseAdminRepository() }
    val aiService = remember { GeminiAdminService() }
    val scope = rememberCoroutineScope()

    var disputes by remember { mutableStateOf(emptyList<AdminDisputeRecord>()) }
    var filter by remember { mutableStateOf("Pendientes") }
    var selected by remember { mutableStateOf<AdminDisputeRecord?>(null) }
    var analyzedDisputeId by remember { mutableStateOf<String?>(null) }
    var analyzingDisputeId by remember { mutableStateOf<String?>(null) }
    var disputeAiResults by remember { mutableStateOf<Map<String, GeminiAdminResult>>(emptyMap()) }
    var message by remember { mutableStateOf<String?>(null) }

    suspend fun reload() {
        disputes = repository.disputes()
    }

    LaunchedEffect(Unit) {
        runCatching { reload() }
            .onFailure { message = it.message ?: "No se pudieron cargar las disputas." }
    }

    selected?.let { dispute ->
        ResolveDisputeDialog(
            dispute = dispute,
            onDismiss = { selected = null },
            onResolve = { release, note ->
                scope.launch {
                    runCatching {
                        repository.resolveDispute(dispute.id, release, note)
                        reload()
                    }
                        .onSuccess {
                            message = "Disputa resuelta y fondos actualizados."
                            selected = null
                        }
                        .onFailure {
                            message = it.message ?: "No se pudo resolver la disputa."
                        }
                }
            }
        )
    }

    val visible = disputes.filter {
        filter == "Todos" ||
                (filter == "Pendientes" && it.status == "PENDIENTE") ||
                (filter == "Resueltas" && it.status == "RESUELTA")
    }

    AdminPage("Gestion de Disputas", "Revisa evidencias y resuelve fondos retenidos") {
        message?.let {
            item { ExchangeCard { Text(it, color = ExchangeMuted) } }
        }

        item {
            AdminFilterRow(listOf("Todos", "Pendientes", "Resueltas"), filter) { filter = it }
        }

        if (visible.isEmpty()) {
            item { EmptyAdminState(Icons.Default.Gavel, "No hay disputas en este filtro") }
        }

        items(visible, key = { it.id }) { dispute ->
            ExchangeCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        StatusPill(if (dispute.status == "PENDIENTE") "Pendiente" else "Resuelta")
                        Text(dispute.reason, fontWeight = FontWeight.Bold)
                        Text(dispute.transactionCode, color = ExchangeMuted)
                    }

                    if (dispute.status == "PENDIENTE") {
                        SecondaryAction("Resolver", { selected = dispute })
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text(dispute.description, color = ExchangeMuted)
                Text("Monto retenido: %.2f %s".format(dispute.amount, dispute.currency))
                Text("Evidencias: ${dispute.evidenceCount}", color = ExchangeMuted)

                if (dispute.status == "PENDIENTE") {
                    Spacer(Modifier.height(10.dp))

                    PrimaryAction(
                        if (analyzedDisputeId == dispute.id) {
                            "Ocultar analisis IA"
                        } else {
                            "Analizar con IA"
                        },
                        {
                            if (analyzedDisputeId == dispute.id) {
                                analyzedDisputeId = null
                            } else {
                                analyzedDisputeId = dispute.id

                                if (disputeAiResults[dispute.id] == null) {
                                    analyzingDisputeId = dispute.id

                                    scope.launch {
                                        val fallback = buildDisputeAiAnalysis(dispute).toDisplayText()
                                        val result = aiService.analyzeDispute(dispute, fallback)

                                        disputeAiResults = disputeAiResults + (dispute.id to result)
                                        analyzingDisputeId = null
                                    }
                                }
                            }
                        },
                        Modifier.fillMaxWidth()
                    )
                }

                if (analyzedDisputeId == dispute.id) {
                    Spacer(Modifier.height(10.dp))
                    DisputeAiInsightCard(
                        aiResult = disputeAiResults[dispute.id],
                        loading = analyzingDisputeId == dispute.id
                    )
                }

                if (dispute.resolution.isNotBlank()) {
                    Text("Fallo: ${dispute.resolution}", color = ExchangePositive)
                }
            }
        }
    }
}

@Composable
fun AdminFeedbackScreen() {
    val repository = remember { FirebaseAdminRepository() }
    val scope = rememberCoroutineScope()
    var feedback by remember { mutableStateOf(emptyList<AdminFeedbackRecord>()) }
    var filter by remember { mutableStateOf("Todos") }
    var selected by remember { mutableStateOf<AdminFeedbackRecord?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    suspend fun reload() { feedback = repository.feedback() }
    LaunchedEffect(Unit) {
        runCatching { reload() }.onFailure { message = it.message ?: "No se pudo cargar el feedback." }
    }
    selected?.let { item ->
        ResponseDialog(item, { selected = null }) { response ->
            scope.launch {
                runCatching { repository.respondFeedback(item.id, response); reload() }
                    .onSuccess { message = "Respuesta enviada al usuario."; selected = null }
                    .onFailure { message = it.message ?: "No se pudo responder." }
            }
        }
    }
    val visible = feedback.filter { filter == "Todos" || it.type == filter }
    AdminPage("Buzon de Feedback", "Sugerencias y reportes reales de usuarios") {
        message?.let { item { ExchangeCard { Text(it, color = ExchangeMuted) } } }
        item { AdminFilterRow(listOf("Todos", "RECOMENDACION", "REPORTE_ERROR"), filter) { filter = it } }
        if (visible.isEmpty()) item { EmptyAdminState(Icons.Default.Feedback, "No hay feedback en este filtro") }
        items(visible, key = { it.id }) { item ->
            ExchangeCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text(item.title, fontWeight = FontWeight.Bold)
                        Text(item.type.replace("_", " "), color = ExchangeMuted)
                    }
                    StatusPill(item.status)
                }
                Spacer(Modifier.height(6.dp))
                Text(item.description)
                if (item.adminResponse.isNotBlank()) Text("Respuesta: ${item.adminResponse}", color = ExchangePositive)
                else {
                    Spacer(Modifier.height(8.dp))
                    SecondaryAction("Responder", { selected = item })
                }
            }
        }
    }
}

@Composable
fun AdminReportsScreen() {
    val repository = remember { FirebaseAdminRepository() }
    val scope = rememberCoroutineScope()
    var type by remember { mutableStateOf("Transacciones") }
    var currency by remember { mutableStateOf("Todas") }
    var status by remember { mutableStateOf("Todos") }
    var rows by remember { mutableStateOf<List<AdminReportRow>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    AdminPage("Reportes", "Consulta información actual de Firestore") {
        item {
            ExchangeCard {
                Text("Tipo de reporte", fontWeight = FontWeight.Bold)
                AdminFilterRow(listOf("Usuarios", "Ofertas", "Transacciones", "Recargas", "Disputas"), type) { type = it }
                Text("Moneda", fontWeight = FontWeight.Bold)
                AdminFilterRow(listOf("Todas", "PEN", "USD", "EUR"), currency) { currency = it }
                Text("Estado", fontWeight = FontWeight.Bold)
                AdminFilterRow(listOf("Todos", "ACTIVA", "PENDIENTE", "COMPLETADO"), status) { status = it }
                PrimaryAction(
                    "Generar reporte",
                    {
                        scope.launch {
                            runCatching { repository.report(type, currency, status) }
                                .onSuccess { rows = it; error = null }
                                .onFailure { error = it.message ?: "No se pudo generar el reporte." }
                        }
                    },
                    Modifier.fillMaxWidth()
                )
            }
        }
        error?.let { item { ErrorCard(it) } }
        rows?.let { result ->
            item {
                ExchangeCard {
                    Text("Resultados (${result.size})", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    ReportRow("ID", "Usuario", "Monto", "Estado", true)
                    result.forEach { ReportRow(it.id, it.owner, it.amount, it.status) }
                }
            }
        } ?: item { EmptyAdminState(Icons.Default.BarChart, "Selecciona filtros y genera un reporte") }
    }
}

@Composable
fun AdminNotificationsScreen() {
    val repository = remember { FirebaseAdminRepository() }
    var disputes by remember { mutableStateOf(emptyList<AdminDisputeRecord>()) }
    var feedback by remember { mutableStateOf(emptyList<AdminFeedbackRecord>()) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        runCatching {
            disputes = repository.disputes().filter { it.status == "PENDIENTE" }
            feedback = repository.feedback().filter { it.status == "PENDIENTE" }
        }.onFailure { error = it.message ?: "No se pudieron cargar las alertas." }
    }
    AdminPage("Notificaciones", "Alertas administrativas del sistema") {
        error?.let { item { ErrorCard(it) } }
        items(disputes, key = { "d-${it.id}" }) {
            AlertCard("Nueva disputa pendiente", "${it.transactionCode}: ${it.reason}", ExchangeNegative)
        }
        items(feedback, key = { "f-${it.id}" }) {
            AlertCard("Feedback pendiente", it.title, ExchangeWarning)
        }
        if (disputes.isEmpty() && feedback.isEmpty() && error == null) {
            item { EmptyAdminState(Icons.Default.Notifications, "No hay alertas administrativas pendientes") }
        }
    }
}

@Composable
private fun ResolveDisputeDialog(
    dispute: AdminDisputeRecord,
    onDismiss: () -> Unit,
    onResolve: (Boolean, String) -> Unit
) {
    var release by remember { mutableStateOf(true) }
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Resolver ${dispute.transactionCode}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(dispute.reason)
                FilterChip(release, { release = true }, { Text("Liberar a la contraparte") })
                FilterChip(!release, { release = false }, { Text("Devolver al propietario") })
                OutlinedTextField(note, { note = it.take(500) }, label = { Text("Fundamento del fallo") })
            }
        },
        confirmButton = { TextButton({ if (note.isNotBlank()) onResolve(release, note) }) { Text("Resolver") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun ResponseDialog(item: AdminFeedbackRecord, onDismiss: () -> Unit, onSend: (String) -> Unit) {
    var response by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Responder feedback") },
        text = {
            Column {
                Text(item.title, fontWeight = FontWeight.Bold)
                Text(item.description, color = ExchangeMuted)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(response, { response = it.take(500) }, label = { Text("Respuesta") })
            }
        },
        confirmButton = { TextButton({ if (response.isNotBlank()) onSend(response) }) { Text("Enviar") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun AdminPage(
    title: String,
    subtitle: String,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit
) {
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text(subtitle, color = ExchangeMuted) }
        content()
        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun AdminStatCard(stat: AdminStat, modifier: Modifier = Modifier) {
    ExchangeCard(modifier) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Column { Text(stat.value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = stat.color); Text(stat.label) }
            IconBadge(stat.icon, stat.color)
        }
    }
}

@Composable
private fun AdminBarChart(title: String, values: List<Pair<String, Int>>) {
    ExchangeCard {
        Text(title, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        val colors = listOf(ExchangeWarning, ExchangeAccent, ExchangePositive, ExchangeNegative, ExchangePrimaryLight)
        val maxValue = max(1, values.maxOfOrNull { it.second } ?: 1)
        Canvas(Modifier.fillMaxWidth().height(150.dp)) {
            val barWidth = size.width / (values.size * 2f + 1f)
            val bottom = size.height - 24f
            values.forEachIndexed { index, item ->
                val left = barWidth + index * barWidth * 2f
                val height = (item.second / maxValue.toFloat()) * (size.height - 44f)
                drawRoundRect(
                    color = colors[index % colors.size],
                    topLeft = Offset(left, bottom - height),
                    size = Size(barWidth, height),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f)
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            values.forEach { (label, count) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    Text(count.toString(), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                    Text(label.take(7), color = ExchangeMuted, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun AdminDonutChart(title: String, pending: Int, resolved: Int) {
    val total = pending + resolved
    ExchangeCard {
        Text(title, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(124.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(112.dp)) {
                    val stroke = Stroke(width = 22f)
                    drawArc(ExchangeElevated, -90f, 360f, false, style = stroke)
                    if (total > 0) {
                        val pendingSweep = 360f * pending / total.toFloat()
                        drawArc(ExchangeNegative, -90f, pendingSweep, false, style = stroke)
                        drawArc(ExchangePositive, -90f + pendingSweep, 360f - pendingSweep, false, style = stroke)
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(total.toString(), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineSmall)
                    Text("Total", color = ExchangeMuted, style = MaterialTheme.typography.labelSmall)
                }
            }
            Spacer(Modifier.width(18.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LegendRow("Pendientes", pending, ExchangeNegative)
                LegendRow("Resueltas", resolved, ExchangePositive)
            }
        }
    }
}

@Composable
private fun AdminVolumeChart(volumeByCurrency: Map<String, Double>) {
    val values = volumeByCurrency
        .filterKeys { it.isNotBlank() && it != "N/A" }
        .entries
        .sortedByDescending { it.value }
        .take(4)
        .map { it.key to it.value }
    ExchangeCard {
        Text("Volumen por moneda", fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        if (values.isEmpty()) {
            Text("Aun no hay volumen registrado.", color = ExchangeMuted)
        } else {
            val maxValue = values.maxOf { it.second }.coerceAtLeast(1.0)
            values.forEach { (currency, amount) ->
                Text("$currency  ${"%.2f".format(amount)}", fontWeight = FontWeight.SemiBold)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(ExchangeElevated)
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth((amount / maxValue).toFloat().coerceIn(0.04f, 1f))
                            .height(10.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(ExchangeAccent)
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun AdminAiSummaryCard(
    data: AdminDashboardData,
    aiResult: GeminiAdminResult?,
    loading: Boolean
) {
    val fallback = remember(data) { buildAdminAiAnalysis(data).toDisplayText() }

    ExchangeCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Default.Psychology, ExchangePrimaryLight)
            Spacer(Modifier.width(10.dp))
            Column {
                Text("Analisis IA administrativo", fontWeight = FontWeight.Bold)
                Text(
                    if (aiResult?.generatedByGemini == true) {
                        "Generado con Gemini desde metricas reales"
                    } else {
                        "Resumen local si Gemini no responde"
                    },
                    color = ExchangeMuted,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        if (loading) {
            Text("Consultando Gemini...", color = ExchangeMuted)
        } else {
            AiTextResult(aiResult?.text ?: fallback)

            aiResult?.errorMessage?.let {
                Spacer(Modifier.height(10.dp))
                Text(
                    "Gemini no respondio: $it",
                    color = ExchangeWarning,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun DisputeAiInsightCard(
    aiResult: GeminiAdminResult?,
    loading: Boolean
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = ExchangeElevated),
        border = BorderStroke(1.dp, ExchangePrimary.copy(alpha = 0.55f)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Insights, contentDescription = null, tint = ExchangePrimaryLight)
                Spacer(Modifier.width(8.dp))
                Text("Resumen IA de disputa", fontWeight = FontWeight.Bold, color = ExchangePrimaryLight)
            }

            if (loading) {
                Text(
                    "Consultando Gemini...",
                    color = ExchangeMuted,
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                AiTextResult(aiResult?.text.orEmpty())

                aiResult?.errorMessage?.let {
                    Text(
                        "Gemini no respondio: $it",
                        color = ExchangeWarning,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}


@Composable
private fun AiTextResult(text: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        text.lines()
            .filter { it.isNotBlank() }
            .forEach { line ->
                Text(
                    line.trim(),
                    color = ExchangeMuted,
                    style = MaterialTheme.typography.bodySmall
                )
            }
    }
}

@Composable
private fun LegendRow(label: String, value: Int, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(8.dp))
        Text("$label: $value", color = ExchangeMuted)
    }
}

@Composable
private fun AdminFilterRow(options: List<String>, selected: String, onSelected: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        options.chunked(2).forEach { optionsRow ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                optionsRow.forEach {
                    FilterChip(selected == it, { onSelected(it) }, { Text(it, maxLines = 1) }, modifier = Modifier.weight(1f))
                }
                if (optionsRow.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun IconBadge(icon: ImageVector, color: Color) {
    Box(Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = .16f)), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = color)
    }
}

@Composable
private fun AlertCard(title: String, body: String, color: Color) {
    ExchangeCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Default.Notifications, color); Spacer(Modifier.width(10.dp))
            Column { Text(title, fontWeight = FontWeight.Bold); Text(body, color = ExchangeMuted) }
        }
    }
}

@Composable
private fun ErrorCard(message: String) {
    ExchangeCard { Text(message, color = ExchangeNegative) }
}

@Composable
private fun EmptyAdminState(icon: ImageVector, text: String) {
    ExchangeCard {
        Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = ExchangeMuted, modifier = Modifier.size(44.dp)); Text(text, color = ExchangeMuted)
        }
    }
}

@Composable
private fun ReportRow(a: String, b: String, c: String, d: String, header: Boolean = false) {
    val weight = if (header) FontWeight.Bold else FontWeight.Normal
    Row(
        Modifier.fillMaxWidth().background(if (header) ExchangeElevated else Color.Transparent).padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(a, Modifier.weight(.8f), fontWeight = weight)
        Text(b, Modifier.weight(1.2f), fontWeight = weight, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(c, Modifier.weight(1f), fontWeight = weight)
        Text(d, Modifier.weight(1f), fontWeight = weight, maxLines = 1)
    }
}

private data class AdminAiAnalysis(
    val summary: String,
    val alerts: List<String>,
    val recommendations: List<String>
)

private data class DisputeAiAnalysis(
    val risk: String,
    val summary: String,
    val recommendation: String
)

private fun orderedStatusValues(values: Map<String, Int>): List<Pair<String, Int>> {
    val order = listOf("PENDIENTE_PAGO", "PAGADO", "COMPLETADO", "EN_DISPUTA", "CANCELADO")
    return order.map { status -> statusLabelShort(status) to (values[status] ?: 0) }
        .filter { it.second > 0 }
        .ifEmpty { listOf("Sin datos" to 0) }
}

private fun statusLabelShort(status: String): String = when (status) {
    "PENDIENTE_PAGO" -> "Pendiente"
    "PAGADO" -> "Pagado"
    "COMPLETADO" -> "Completado"
    "EN_DISPUTA" -> "Disputa"
    "CANCELADO" -> "Cancelado"
    else -> status
}

private fun buildAdminAiAnalysis(data: AdminDashboardData): AdminAiAnalysis {
    val totalTransactions = data.transactionsByStatus.values.sum()
    val disputeLoad = data.pendingDisputes + data.resolvedDisputes
    val externalRatio = if (totalTransactions == 0) 0.0 else data.externalPaymentTransactions.toDouble() / totalTransactions
    val pendingDisputeRatio = if (disputeLoad == 0) 0.0 else data.pendingDisputes.toDouble() / disputeLoad
    val mainCurrency = data.volumeByCurrency.maxByOrNull { it.value }?.key ?: "sin moneda dominante"

    val summary = when {
        totalTransactions == 0 -> "Aun no hay transacciones suficientes para detectar tendencias operativas."
        data.pendingDisputes > 0 -> "La operacion esta activa y requiere atencion por disputas pendientes."
        else -> "La operacion general se mantiene estable, sin disputas pendientes relevantes."
    }

    val alerts = buildList {
        if (data.pendingDisputes > 0) add("${data.pendingDisputes} disputas pendientes requieren revision.")
        if (data.pendingFeedback > 0) add("${data.pendingFeedback} feedbacks pendientes esperan respuesta.")
        if (externalRatio >= 0.5) add("Alta dependencia de pagos externos; revisar comprobantes con cuidado.")
        if (mainCurrency != "sin moneda dominante") add("$mainCurrency concentra el mayor volumen operativo.")
        if (isEmpty()) add("No se detectan alertas criticas con los datos actuales.")
    }

    val recommendations = buildList {
        if (data.pendingDisputes > 0) add("Priorizar disputas con estado pagado y monto retenido alto.")
        if (externalRatio >= 0.5) add("Promover Wallet Interna para reducir friccion por comprobantes externos.")
        if (pendingDisputeRatio >= 0.4) add("Revisar patrones de usuarios que aparecen en varias disputas.")
        if (data.pendingFeedback > 0) add("Responder feedback pendiente para cerrar el ciclo de soporte.")
        if (isEmpty()) add("Mantener monitoreo semanal de transacciones, disputas y feedback.")
    }

    return AdminAiAnalysis(summary, alerts, recommendations)
}

private fun buildDisputeAiAnalysis(dispute: AdminDisputeRecord): DisputeAiAnalysis {
    val risk = when {
        dispute.amount >= 1000.0 || dispute.evidenceCount == 0 -> "Alto"
        dispute.amount >= 300.0 -> "Medio"
        else -> "Bajo"
    }
    val summary = "La disputa ${dispute.transactionCode} reporta '${dispute.reason}' con %.2f %s retenidos y %d evidencias."
        .format(dispute.amount, dispute.currency.ifBlank { "N/A" }, dispute.evidenceCount)
    val recommendation = when {
        dispute.evidenceCount == 0 -> "Solicitar evidencia antes de liberar o devolver fondos."
        risk == "Alto" -> "Validar comprobantes y revisar historial de ambos usuarios antes del fallo."
        else -> "Contrastar descripcion, evidencia y estado de la transaccion antes de resolver."
    }
    return DisputeAiAnalysis(risk, summary, recommendation)
}
private fun DisputeAiAnalysis.toDisplayText(): String =
    """
        Riesgo: $risk.
        Lectura del caso: $summary
        Sugerencia: $recommendation
    """.trimIndent()
private fun AdminAiAnalysis.toDisplayText(): String =
    buildString {
        appendLine("Resumen: $summary")
        appendLine("Alertas:")
        alerts.forEach { appendLine("- $it") }
        appendLine("Recomendaciones:")
        recommendations.forEach { appendLine("- $it") }
    }.trim()

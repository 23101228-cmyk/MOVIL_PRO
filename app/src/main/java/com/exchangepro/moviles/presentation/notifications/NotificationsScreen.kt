package com.exchangepro.moviles.presentation.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.exchangepro.moviles.data.repository.FirebaseNotificationRepository
import com.exchangepro.moviles.domain.model.NotificationItem
import com.exchangepro.moviles.ui.components.ExchangeCard
import com.exchangepro.moviles.ui.components.StatusPill
import com.exchangepro.moviles.ui.theme.ExchangeElevated
import com.exchangepro.moviles.ui.theme.ExchangeMuted
import com.exchangepro.moviles.ui.theme.ExchangePrimary
import com.exchangepro.moviles.ui.theme.ExchangePrimaryLight
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

@Composable
fun NotificationsScreen() {
    val repository = remember { FirebaseNotificationRepository() }
    val scope = rememberCoroutineScope()
    var notifications by remember { mutableStateOf(emptyList<NotificationItem>()) }
    var loading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        repository.observeMine()
            .catch { error ->
                errorMessage = error.message ?: "No se pudieron cargar las notificaciones."
                loading = false
            }
            .collect {
                notifications = it
                loading = false
                errorMessage = null
            }
    }

    LazyColumn(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Notificaciones", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Tus avisos recientes", color = ExchangeMuted)
        }

        errorMessage?.let {
            item { ExchangeCard { Text(it, color = MaterialTheme.colorScheme.error) } }
        }

        if (loading) {
            item { ExchangeCard { Text("Cargando notificaciones...", color = ExchangeMuted) } }
        } else if (notifications.isEmpty()) {
            item {
                ExchangeCard {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.NotificationsOff, contentDescription = null, tint = ExchangeMuted, modifier = Modifier.size(48.dp))
                        Text("No hay notificaciones nuevas.", color = ExchangeMuted)
                    }
                }
            }
        } else {
            items(notifications, key = { it.id }) { item ->
                NotificationRow(
                    item = item,
                    onClick = {
                        if (!item.read) {
                            scope.launch {
                                runCatching { repository.markRead(item.id) }
                                    .onSuccess {
                                        notifications = notifications.map {
                                            if (it.id == item.id) it.copy(read = true) else it
                                        }
                                    }
                                    .onFailure {
                                        errorMessage = it.message ?: "No se pudo marcar como leida."
                                    }
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun NotificationRow(item: NotificationItem, onClick: () -> Unit) {
    ExchangeCard(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (!item.read) ExchangePrimary.copy(alpha = 0.08f) else ExchangeElevated.copy(alpha = 0.20f))
            .clickable(onClick = onClick)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(if (!item.read) ExchangePrimary else ExchangeElevated),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Notifications, contentDescription = null, tint = if (!item.read) androidx.compose.ui.graphics.Color.White else ExchangeMuted)
            }
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.title, fontWeight = FontWeight.Bold)
                Text(item.message, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!item.read) {
                StatusPill("Nuevo")
            } else {
                Text("Leida", color = ExchangeMuted, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

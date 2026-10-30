package com.khanz1.serlok.ui.screens.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.khanz1.serlok.R
import com.khanz1.serlok.data.AppSettings
import com.khanz1.serlok.service.ConnectionState
import com.khanz1.serlok.ui.components.RoomRuntimePermissions
import com.khanz1.serlok.ui.components.SerlokMark
import com.khanz1.serlok.ui.components.openApplicationSettings
import com.khanz1.serlok.ui.screens.room.RoomViewModel
import com.khanz1.serlok.ui.theme.SerlokSpacing

private sealed interface PendingRoomAction {
    data class Create(val shakeThreshold: Float, val locationIntervalMs: Long) : PendingRoomAction
    data class Join(val code: String) : PendingRoomAction
    data object Resume : PendingRoomAction
}

@Composable
fun HomeScreen(viewModel: RoomViewModel, onEnterRoom: () -> Unit) {
    val context = LocalContext.current
    val room by viewModel.roomState.collectAsState()
    val connection by viewModel.connectionState.collectAsState()
    val error by viewModel.error.collectAsState()
    var joinSheet by remember { mutableStateOf(false) }
    var createSheet by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<PendingRoomAction?>(null) }
    var waitingForRoom by remember { mutableStateOf(false) }
    var denied by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        if (result.values.all { it }) {
            when (val action = pending) {
                is PendingRoomAction.Create -> {
                    waitingForRoom = true
                    viewModel.createRoom(action.shakeThreshold, action.locationIntervalMs)
                }
                is PendingRoomAction.Join -> { waitingForRoom = true; viewModel.joinRoom(action.code) }
                PendingRoomAction.Resume -> onEnterRoom()
                null -> Unit
            }
            pending = null
        } else denied = true
    }

    fun runWithPermissions(action: PendingRoomAction) {
        if (RoomRuntimePermissions.hasRoomRuntimePermissions(context)) {
            when (action) {
                is PendingRoomAction.Create -> {
                    waitingForRoom = true
                    viewModel.createRoom(action.shakeThreshold, action.locationIntervalMs)
                }
                is PendingRoomAction.Join -> { waitingForRoom = true; viewModel.joinRoom(action.code) }
                PendingRoomAction.Resume -> onEnterRoom()
            }
        } else {
            pending = action
            launcher.launch(RoomRuntimePermissions.requiredPermissions())
        }
    }

    LaunchedEffect(room?.code) {
        if (waitingForRoom && room != null) {
            waitingForRoom = false
            onEnterRoom()
        }
    }

    if (denied) {
        AlertDialog(
            onDismissRequest = { denied = false },
            title = { Text(stringResource(R.string.permission_denied_title)) },
            text = { Text(stringResource(R.string.permission_denied_message)) },
            confirmButton = {
                TextButton(onClick = { denied = false; context.openApplicationSettings() }) {
                    Text(stringResource(R.string.permission_denied_open_settings))
                }
            },
            dismissButton = {
                TextButton(onClick = { denied = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SerlokMark(Modifier.size(34.dp), contentDescription = stringResource(R.string.app_name))
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(start = 10.dp).weight(1f),
            )
            ConnectionPill(connection)
        }
        Spacer(Modifier.height(SerlokSpacing.xxl))
        Text(
            stringResource(R.string.home_greeting, AppSettings.userName.ifBlank { "Serlok" }),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            stringResource(R.string.home_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
        Spacer(Modifier.height(SerlokSpacing.xl))

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            shape = MaterialTheme.shapes.medium,
        ) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Lock, null, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.padding(start = 12.dp)) {
                    Text(
                        stringResource(R.string.home_privacy_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        stringResource(R.string.home_privacy_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.82f),
                    )
                }
            }
        }

        if (connection == ConnectionState.OFFLINE) {
            Spacer(Modifier.height(12.dp))
            StateCard(Icons.Rounded.WifiOff, stringResource(R.string.room_offline_body), viewModel::reconnect)
        }
        if (error != null) {
            Spacer(Modifier.height(12.dp))
            StateCard(Icons.Rounded.WifiOff, error.orEmpty())
        }

        Spacer(Modifier.height(SerlokSpacing.xxl))
        Text(
            stringResource(R.string.home_rooms),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(SerlokSpacing.sm))
        ActionCard(
            icon = Icons.Rounded.Add,
            title = stringResource(R.string.home_create),
            body = stringResource(R.string.home_create_body),
            primary = true,
            onClick = {
                if (room == null) createSheet = true
                else runWithPermissions(PendingRoomAction.Resume)
            },
        )
        Spacer(Modifier.height(12.dp))
        ActionCard(
            icon = Icons.AutoMirrored.Rounded.Login,
            title = stringResource(R.string.home_join),
            body = stringResource(R.string.home_join_body),
            primary = false,
            onClick = { joinSheet = true },
        )

        room?.let { current ->
            Spacer(Modifier.height(SerlokSpacing.xl))
            Card(
                onClick = { runWithPermissions(PendingRoomAction.Resume) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = MaterialTheme.shapes.medium,
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(48.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Rounded.Groups, null, tint = MaterialTheme.colorScheme.primary) }
                    Column(Modifier.padding(start = 14.dp).weight(1f)) {
                        Text(
                            stringResource(R.string.home_active_room),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            current.code,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            stringResource(if (current.isSharing) R.string.session_active else R.string.session_ready),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (current.isSharing) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(stringResource(R.string.home_resume), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    if (joinSheet) {
        JoinRoomSheet(
            onConfirm = { code -> joinSheet = false; runWithPermissions(PendingRoomAction.Join(code)) },
            onDismiss = { joinSheet = false },
        )
    }
    if (createSheet) {
        CreateRoomSheet(
            onConfirm = { shake, interval ->
                createSheet = false
                runWithPermissions(PendingRoomAction.Create(shake, interval))
            },
            onDismiss = { createSheet = false },
        )
    }
}

@Composable
private fun ConnectionPill(state: ConnectionState) {
    val label = when (state) {
        ConnectionState.ONLINE -> R.string.state_online
        ConnectionState.CONNECTING -> R.string.state_connecting
        ConnectionState.OFFLINE -> R.string.state_offline
    }
    val color = if (state == ConnectionState.ONLINE) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier.clip(CircleShape).background(color.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(color))
        Text(stringResource(label), style = MaterialTheme.typography.labelSmall, color = color, modifier = Modifier.padding(start = 6.dp))
    }
}

@Composable
private fun StateCard(icon: ImageVector, text: String, onRetry: (() -> Unit)? = null) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.error)
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(start = 10.dp).weight(1f),
            )
            if (onRetry != null) {
                TextButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
            }
        }
    }
}

@Composable
private fun ActionCard(
    icon: ImageVector,
    title: String,
    body: String,
    primary: Boolean,
    onClick: () -> Unit,
) {
    val colors = if (primary) {
        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)
    } else {
        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    }
    Card(onClick = onClick, colors = colors, shape = MaterialTheme.shapes.medium) {
        Row(
            Modifier.fillMaxWidth().padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                icon,
                null,
                tint = if (primary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(30.dp),
            )
            Column(Modifier.padding(start = 16.dp).weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (primary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    body,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (primary) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f)
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

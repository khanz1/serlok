package com.khanz1.serlok.ui.screens.room

import android.content.ClipData
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import com.khanz1.serlok.R
import com.khanz1.serlok.data.AppSettings
import com.khanz1.serlok.data.model.LocationData
import com.khanz1.serlok.data.model.RoomMember
import com.khanz1.serlok.data.model.RoomState
import com.khanz1.serlok.service.ConnectionState
import com.khanz1.serlok.service.RoomAlert
import com.khanz1.serlok.service.RoomEventBus
import com.khanz1.serlok.ui.components.DeterministicAvatar
import com.khanz1.serlok.ui.components.LocationPermissionCard
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun RoomScreen(onLeaveRoom: () -> Unit, onBack: () -> Unit = onLeaveRoom) {
    val activity = LocalActivity.current as? ComponentActivity ?: return
    val viewModel: RoomViewModel = viewModel(viewModelStoreOwner = activity)
    val room by viewModel.roomState.collectAsState()
    val locations by viewModel.memberLocations.collectAsState()
    val timestamps by viewModel.locationTimestamps.collectAsState()
    val incidents by viewModel.incidents.collectAsState()
    val selectedMemberId by viewModel.selectedMemberId.collectAsState()
    val selectedIncidentId by viewModel.selectedIncidentId.collectAsState()
    val focus by viewModel.focusedDeviceId.collectAsState()
    val alert by viewModel.activeAlert.collectAsState()
    val alertBannerVisible by viewModel.alertBannerVisible.collectAsState()
    val connection by viewModel.connectionState.collectAsState()
    val error by viewModel.error.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var shakeFeedbackSequence by remember { mutableStateOf(0L) }
    var showLeaveConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(error) {
        error?.let { snackbar.showSnackbar(it); viewModel.clearError() }
    }
    LaunchedEffect(Unit) {
        RoomEventBus.notifications.collect { snackbar.showSnackbar(it) }
    }
    LaunchedEffect(viewModel) {
        viewModel.shakeFeedback.collect { shakeFeedbackSequence += 1 }
    }
    LaunchedEffect(room) { if (room == null) onLeaveRoom() }

    if (showLeaveConfirm) {
        AlertDialog(
            onDismissRequest = { showLeaveConfirm = false },
            title = { Text(stringResource(R.string.room_leave_confirm_title)) },
            text = { Text(stringResource(R.string.room_leave_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLeaveConfirm = false
                        viewModel.leaveRoom()
                    },
                ) {
                    Text(stringResource(R.string.room_leave), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveConfirm = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    Box(Modifier.fillMaxSize()) {
        room?.let { state ->
            RoomContent(
                state = state,
                selfId = viewModel.deviceId,
                locations = locations,
                timestamps = timestamps,
                incidents = incidents,
                selectedMemberId = selectedMemberId,
                selectedIncidentId = selectedIncidentId,
                focusedUserId = focus,
                activeAlert = alert,
                alertBannerVisible = alertBannerVisible,
                connection = connection,
                onFocus = viewModel::focusOnDevice,
                onSelectMember = viewModel::selectMember,
                onSelectIncident = viewModel::selectIncident,
                onStart = viewModel::startSharing,
                onStop = viewModel::stopSharing,
                onBack = onBack,
                onLeaveRequest = {
                    if (state.isSharing) showLeaveConfirm = true
                    else viewModel.leaveRoom()
                },
                onDismissAlert = viewModel::dismissActiveAlertBanner,
                onSettings = viewModel::updateRoomSettings,
                onReconnect = viewModel::reconnect,
                onLocationGranted = viewModel::refreshLocationUpdatesIfInRoom,
            )
        }
        ShakeEdgeFeedback(
            trigger = shakeFeedbackSequence,
            modifier = Modifier.matchParentSize().zIndex(10f),
        )
        SnackbarHost(
            snackbar,
            Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp).zIndex(11f),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RoomContent(
    state: RoomState,
    selfId: String,
    locations: Map<String, LocationData>,
    timestamps: Map<String, Long>,
    incidents: List<RoomAlert> = emptyList(),
    selectedMemberId: String? = null,
    selectedIncidentId: String? = null,
    focusedUserId: String?,
    activeAlert: RoomAlert?,
    alertBannerVisible: Boolean,
    connection: ConnectionState,
    onFocus: (String?) -> Unit,
    onSelectMember: (String?) -> Unit = {},
    onSelectIncident: (RoomAlert) -> Unit = {},
    onStart: () -> Unit,
    onStop: () -> Unit,
    onBack: () -> Unit,
    onLeaveRequest: () -> Unit,
    onDismissAlert: () -> Unit,
    onSettings: (Float, Long) -> Unit,
    onReconnect: () -> Unit,
    onLocationGranted: () -> Unit,
) {
    val isHost = state.masterId == selfId
    val context = LocalContext.current
    val sheetState = rememberStandardBottomSheetState(
        initialValue = SheetValue.PartiallyExpanded,
        skipHiddenState = true,
    )
    val scaffoldState = rememberBottomSheetScaffoldState(sheetState)
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    val selectedMember = state.members.find { it.userId == selectedMemberId }
    val memberIncidents = selectedMember?.let { member ->
        incidents.filter { it.userId == member.userId }
    }.orEmpty()
    val selectedIncident = incidents.find { it.incidentId == selectedIncidentId }

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetPeekHeight = 300.dp,
        sheetContainerColor = MaterialTheme.colorScheme.surface,
        sheetShape = MaterialTheme.shapes.extraLarge,
        sheetDragHandle = {
            Box(
                Modifier.padding(vertical = 12.dp).size(40.dp, 4.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.outline),
            )
        },
        sheetContent = {
            if (selectedMember != null) {
                MemberSosSheet(
                    member = selectedMember,
                    isSelf = selectedMember.userId == selfId,
                    incidents = memberIncidents,
                    selectedIncidentId = selectedIncidentId,
                    onBack = { onSelectMember(null) },
                    onIncident = onSelectIncident,
                )
            } else {
                RoomMemberSheet(
                    state = state,
                    selfId = selfId,
                    locations = locations,
                    timestamps = timestamps,
                    activeAlert = activeAlert,
                    isHost = isHost,
                    onMember = { userId -> onSelectMember(userId) },
                    onSettings = onSettings,
                )
            }
        },
    ) { _ ->
        Box(Modifier.fillMaxSize()) {
            RoomMap(
                members = state.members,
                locations = locations,
                selfId = selfId,
                focusedUserId = focusedUserId,
                alertingUserId = activeAlert?.userId,
                incidentPins = memberIncidents,
                selectedIncidentId = selectedIncidentId,
                incidentCamera = selectedIncident?.takeIf {
                    it.latitude != null && it.longitude != null
                },
                onMarkerClick = { userId -> onSelectMember(userId) },
                onIncidentClick = { incidentId ->
                    incidents.find { it.incidentId == incidentId }?.let(onSelectIncident)
                },
                modifier = Modifier.fillMaxSize(),
            )

            Column(
                Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.room_back))
                        }
                        Box(
                            Modifier.size(10.dp).clip(CircleShape).background(
                                if (state.isSharing) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline,
                            ),
                        )
                        Row(
                            Modifier.padding(start = 10.dp).weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                state.code,
                                style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                            )
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        clipboard.setClipEntry(
                                            ClipEntry(
                                                ClipData.newPlainText(
                                                    "Serlok Room",
                                                    state.code,
                                                ),
                                            ),
                                        )
                                    }
                                },
                                modifier = Modifier.size(40.dp),
                            ) {
                                Icon(Icons.Rounded.ContentCopy, stringResource(R.string.room_copy_code), Modifier.size(18.dp))
                            }
                            IconButton(
                                onClick = {
                                    val share = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(
                                            Intent.EXTRA_TEXT,
                                            context.getString(R.string.room_share_text, state.code),
                                        )
                                    }
                                    context.startActivity(Intent.createChooser(share, null))
                                },
                                modifier = Modifier.size(40.dp),
                            ) {
                                Icon(Icons.Rounded.Share, stringResource(R.string.room_share), Modifier.size(18.dp))
                            }
                        }
                        if (isHost) {
                            IconButton(
                                onClick = if (state.isSharing) onStop else onStart,
                            ) {
                                Icon(
                                    imageVector = if (state.isSharing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    contentDescription = stringResource(
                                        if (state.isSharing) R.string.room_stop else R.string.room_start,
                                    ),
                                    tint = if (state.isSharing) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                        IconButton(onClick = onLeaveRequest) {
                            Icon(
                                Icons.AutoMirrored.Rounded.Logout,
                                stringResource(R.string.room_leave),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }

                if (connection == ConnectionState.OFFLINE) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(R.string.room_offline_body),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = onReconnect) { Text(stringResource(R.string.action_retry)) }
                        }
                    }
                }

                activeAlert?.takeIf { alertBannerVisible }?.let { incident ->
                    IncidentBanner(
                        alert = incident,
                        onDismiss = onDismissAlert,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }

                LocationPermissionCard(
                    modifier = Modifier.padding(top = 8.dp),
                    onGranted = onLocationGranted,
                )
            }

            IconButton(
                onClick = { onFocus(selfId) },
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 200.dp)
                    .size(52.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface),
            ) {
                Icon(
                    Icons.Rounded.MyLocation,
                    contentDescription = stringResource(R.string.room_my_location),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun IncidentBanner(
    alert: RoomAlert,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = stringResource(R.string.alert_title_in_app, alert.name)
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.StartToEnd || value == SwipeToDismissBoxValue.EndToStart) {
                onDismiss()
                true
            } else {
                false
            }
        },
    )
    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier.fillMaxWidth(),
        backgroundContent = {},
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
            ),
            shape = MaterialTheme.shapes.medium,
        ) {
            Column(Modifier.fillMaxWidth().padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.Warning,
                        null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Column(Modifier.padding(start = 10.dp).weight(1f)) {
                        Text(title, style = MaterialTheme.typography.titleSmall)
                        Text(
                            stringResource(R.string.alert_source_shake, alert.magnitudeG),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RoomMemberSheet(
    state: RoomState,
    selfId: String,
    locations: Map<String, LocationData>,
    timestamps: Map<String, Long>,
    activeAlert: RoomAlert?,
    isHost: Boolean,
    onMember: (String) -> Unit,
    onSettings: (Float, Long) -> Unit,
) {
    var draftShake by remember(state.shakeThreshold) { mutableStateOf(state.shakeThreshold) }
    var draftIntervalIndex by remember(state.locationIntervalMs) {
        mutableStateOf(
            AppSettings.LOCATION_INTERVALS.indexOf(state.locationIntervalMs).coerceAtLeast(0),
        )
    }
    Column(Modifier.fillMaxWidth().heightIn(min = 420.dp, max = 700.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Groups, null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.padding(start = 10.dp).weight(1f)) {
                Text(stringResource(R.string.room_members), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.room_member_count, state.members.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (isHost) {
                Text(
                    stringResource(R.string.room_host),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        ShakeStatusCard(
            isSharing = state.isSharing,
            threshold = state.shakeThreshold,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        )

        if (isHost && !state.isSharing) {
            Text(
                stringResource(R.string.room_settings_title),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 20.dp).padding(top = 12.dp),
            )
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.settings_shake_level),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                Text("${draftShake} g", style = MaterialTheme.typography.labelMedium)
            }
            Slider(
                value = draftShake,
                onValueChange = { raw ->
                    draftShake = listOf(2.5f, 3.5f, 4.5f).minBy { kotlin.math.abs(it - raw) }
                },
                onValueChangeFinished = {
                    onSettings(draftShake, AppSettings.LOCATION_INTERVALS[draftIntervalIndex])
                },
                valueRange = 2.5f..4.5f,
                steps = 1,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.settings_location_interval),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    stringResource(
                        R.string.settings_location_interval_value,
                        when (val ms = AppSettings.LOCATION_INTERVALS[draftIntervalIndex]) {
                            in 0 until 1_000 -> "${ms} ms"
                            in 1_000 until 60_000 -> "${ms / 1_000} s"
                            else -> "${ms / 60_000} min"
                        },
                    ),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Slider(
                value = draftIntervalIndex.toFloat(),
                onValueChange = { draftIntervalIndex = it.roundToInt() },
                onValueChangeFinished = {
                    onSettings(draftShake, AppSettings.LOCATION_INTERVALS[draftIntervalIndex])
                },
                valueRange = 0f..AppSettings.LOCATION_INTERVALS.lastIndex.toFloat(),
                steps = AppSettings.LOCATION_INTERVALS.size - 2,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        } else if (isHost && state.isSharing) {
            Text(
                stringResource(R.string.room_settings_locked),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }

        HorizontalDivider(Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        LazyColumn(Modifier.weight(1f)) {
            items(state.members, key = { it.userId }) { member ->
                val memberAlert = activeAlert?.takeIf { it.userId == member.userId }
                MemberRow(
                    member = member,
                    location = locations[member.userId],
                    timestamp = timestamps[member.userId],
                    isSelf = member.userId == selfId,
                    isAlerting = memberAlert != null,
                    onClick = { onMember(member.userId) },
                )
            }
        }
    }
}

@Composable
internal fun MemberSosSheet(
    member: RoomMember,
    isSelf: Boolean,
    incidents: List<RoomAlert>,
    selectedIncidentId: String?,
    onBack: () -> Unit,
    onIncident: (RoomAlert) -> Unit,
) {
    Column(Modifier.fillMaxWidth().heightIn(min = 420.dp, max = 700.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    stringResource(R.string.member_sos_back),
                )
            }
            DeterministicAvatar(member.userId, member.name)
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        member.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (isSelf) {
                        Text(
                            stringResource(R.string.room_you),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
                Text(
                    stringResource(R.string.member_sos_alerts),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            stringResource(R.string.member_sos_count, incidents.size),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        HorizontalDivider(Modifier.padding(horizontal = 20.dp))
        if (incidents.isEmpty()) {
            Box(
                Modifier.fillMaxWidth().weight(1f).padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.member_sos_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(Modifier.weight(1f).padding(top = 4.dp)) {
                items(incidents, key = { it.incidentId }) { incident ->
                    val selected = incident.incidentId == selectedIncidentId
                    Card(
                        onClick = { onIncident(incident) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (selected) {
                                MaterialTheme.colorScheme.errorContainer
                            } else {
                                ComposeColor.Transparent
                            },
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 2.dp)
                            .testTag("sos_row_${incident.incidentId}"),
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier.size(40.dp).clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Rounded.Warning,
                                    null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                                Text(
                                    stringResource(R.string.alert_source_shake, incident.magnitudeG),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    if (incident.latitude == null || incident.longitude == null) {
                                        stringResource(R.string.member_sos_location_unavailable)
                                    } else {
                                        formatIncidentWhen(incident.triggeredAt)
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Icon(
                                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MemberRow(
    member: RoomMember,
    location: LocationData?,
    timestamp: Long?,
    isSelf: Boolean,
    isAlerting: Boolean,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = ComposeColor.Transparent),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box {
                DeterministicAvatar(member.userId, member.name)
                if (isAlerting) {
                    Box(
                        Modifier.matchParentSize().clip(CircleShape)
                            .border(2.dp, MaterialTheme.colorScheme.error, CircleShape),
                    )
                }
            }
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        member.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (isSelf) {
                        Text(
                            stringResource(R.string.room_you),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                    if (member.isMaster) {
                        Text(
                            stringResource(R.string.room_host),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
                Text(
                    if (location == null) stringResource(R.string.room_location_waiting)
                    else stringResource(R.string.room_location_accuracy, location.accuracy),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
internal fun ShakeStatusCard(isSharing: Boolean, threshold: Float, modifier: Modifier = Modifier) {
    val container = if (isSharing) MaterialTheme.colorScheme.primaryContainer
    else MaterialTheme.colorScheme.surfaceVariant
    val accent = if (isSharing) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.onSurfaceVariant
    Card(
        modifier = modifier.testTag("shake_status"),
        colors = CardDefaults.cardColors(containerColor = container),
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(44.dp).clip(CircleShape).background(accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Sensors, null, tint = accent)
            }
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text(
                    stringResource(if (isSharing) R.string.shake_status_active else R.string.shake_status_armed),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    stringResource(
                        if (isSharing) R.string.shake_status_active_body else R.string.shake_status_armed_body,
                        threshold,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
            Box(Modifier.size(10.dp).clip(CircleShape).background(accent))
        }
    }
}

@Composable
internal fun ShakeEdgeFeedback(trigger: Long, modifier: Modifier = Modifier) {
    val alpha = remember { Animatable(0f) }
    val haptic = LocalHapticFeedback.current
    val edgeColor = MaterialTheme.colorScheme.error

    LaunchedEffect(trigger) {
        if (trigger == 0L) return@LaunchedEffect
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        repeat(2) {
            alpha.animateTo(0.82f, tween(70, easing = LinearEasing))
            alpha.animateTo(0f, tween(70, easing = LinearEasing))
        }
    }

    ComposeCanvas(
        modifier = modifier.testTag("shake_edge_feedback"),
    ) {
        if (alpha.value <= 0f) return@ComposeCanvas
        val thickness = 16.dp.toPx()
        val color = edgeColor.copy(alpha = alpha.value)
        drawRect(color, size = size.copy(height = thickness))
        drawRect(
            color,
            topLeft = Offset(0f, size.height - thickness),
            size = size.copy(height = thickness),
        )
        drawRect(color, size = size.copy(width = thickness))
        drawRect(
            color,
            topLeft = Offset(size.width - thickness, 0f),
            size = size.copy(width = thickness),
        )
    }
}

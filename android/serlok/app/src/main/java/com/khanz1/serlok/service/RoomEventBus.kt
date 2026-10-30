package com.khanz1.serlok.service

import com.khanz1.serlok.data.model.LocationData
import com.khanz1.serlok.data.model.RoomState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ConnectionState { CONNECTING, ONLINE, OFFLINE }

data class RoomAlert(
    val incidentId: String,
    val userId: String,
    val name: String,
    val magnitudeG: Float,
    val latitude: Double?,
    val longitude: Double?,
    val triggeredAt: String,
)

object RoomEventBus {
    private val _connectionState = MutableStateFlow(ConnectionState.OFFLINE)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _roomState = MutableStateFlow<RoomState?>(null)
    val roomState: StateFlow<RoomState?> = _roomState.asStateFlow()

    private val _roomShakeLevel = MutableStateFlow(3.5f)
    val roomShakeLevel: StateFlow<Float> = _roomShakeLevel.asStateFlow()

    private val _roomLocationIntervalMs = MutableStateFlow(10_000L)
    val roomLocationIntervalMs: StateFlow<Long> = _roomLocationIntervalMs.asStateFlow()

    private val _roomError = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val roomError: SharedFlow<String> = _roomError.asSharedFlow()

    private val _roomAlert = MutableSharedFlow<RoomAlert>(extraBufferCapacity = 16)
    val roomAlert: SharedFlow<RoomAlert> = _roomAlert.asSharedFlow()

    private val _notifications = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val notifications: SharedFlow<String> = _notifications.asSharedFlow()

    private val _focusUserId = MutableSharedFlow<String>(replay = 1, extraBufferCapacity = 4)
    val focusUserId: SharedFlow<String> = _focusUserId.asSharedFlow()

    private val _memberLocations = MutableStateFlow<Map<String, LocationData>>(emptyMap())
    val memberLocations: StateFlow<Map<String, LocationData>> = _memberLocations.asStateFlow()

    private val _locationTimestamps = MutableStateFlow<Map<String, Long>>(emptyMap())
    val locationTimestamps: StateFlow<Map<String, Long>> = _locationTimestamps.asStateFlow()

    private val _incidents = MutableStateFlow<List<RoomAlert>>(emptyList())
    val incidents: StateFlow<List<RoomAlert>> = _incidents.asStateFlow()

    fun emitRoomState(state: RoomState?) { _roomState.value = state }
    fun emitConnectionState(state: ConnectionState) { _connectionState.value = state }
    fun emitRoomShakeLevel(magnitude: Float) { _roomShakeLevel.value = magnitude }
    fun emitRoomLocationIntervalMs(intervalMs: Long) { _roomLocationIntervalMs.value = intervalMs }
    fun emitError(message: String) { _roomError.tryEmit(message) }
    fun emitNotification(message: String) { _notifications.tryEmit(message) }
    fun emitFocus(userId: String) { _focusUserId.tryEmit(userId) }

    fun emitAlert(alert: RoomAlert) {
        _roomAlert.tryEmit(alert)
        appendIncident(alert)
    }

    fun replaceIncidents(incidents: List<RoomAlert>) {
        _incidents.value = incidents
    }

    fun appendIncident(alert: RoomAlert) {
        if (_incidents.value.any { it.incidentId == alert.incidentId }) return
        _incidents.value = (listOf(alert) + _incidents.value).take(50)
    }

    fun emitMemberLocation(deviceId: String, location: LocationData) {
        _memberLocations.value = _memberLocations.value + (deviceId to location)
        _locationTimestamps.value = _locationTimestamps.value + (deviceId to System.currentTimeMillis())
    }

    fun clear() {
        _roomState.value = null
        _memberLocations.value = emptyMap()
        _locationTimestamps.value = emptyMap()
        _incidents.value = emptyList()
        _roomShakeLevel.value = 3.5f
        _roomLocationIntervalMs.value = 10_000L
    }

    fun isHost(userId: String): Boolean = _roomState.value?.masterId == userId
}

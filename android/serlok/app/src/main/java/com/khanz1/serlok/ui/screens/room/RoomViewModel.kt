package com.khanz1.serlok.ui.screens.room

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.khanz1.serlok.data.AppSettings
import com.khanz1.serlok.data.model.LocationData
import com.khanz1.serlok.data.model.RoomState
import com.khanz1.serlok.data.remote.SerlokWebSocketClient
import com.khanz1.serlok.data.repository.LocationRepository
import com.khanz1.serlok.service.ConnectionState
import com.khanz1.serlok.service.RoomAlert
import com.khanz1.serlok.service.RoomEventBus
import com.khanz1.serlok.service.SosEventBus
import com.khanz1.serlok.service.SosMonitorService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RoomViewModel(app: Application) : AndroidViewModel(app) {
    private val wsClient = SerlokWebSocketClient()
    private val locationRepo = LocationRepository(app)

    val roomState: StateFlow<RoomState?> = RoomEventBus.roomState
    val memberLocations: StateFlow<Map<String, LocationData>> = RoomEventBus.memberLocations
    val locationTimestamps: StateFlow<Map<String, Long>> = RoomEventBus.locationTimestamps
    val incidents: StateFlow<List<RoomAlert>> = RoomEventBus.incidents
    val connectionState: StateFlow<ConnectionState> = RoomEventBus.connectionState
    val deviceId: String = AppSettings.userId

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    private val _focusedDeviceId = MutableStateFlow<String?>(null)
    val focusedDeviceId: StateFlow<String?> = _focusedDeviceId.asStateFlow()
    private val _activeAlert = MutableStateFlow<RoomAlert?>(null)
    val activeAlert: StateFlow<RoomAlert?> = _activeAlert.asStateFlow()
    private val _alertBannerVisible = MutableStateFlow(false)
    val alertBannerVisible: StateFlow<Boolean> = _alertBannerVisible.asStateFlow()
    private val _shakeFeedback = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val shakeFeedback: SharedFlow<Unit> = _shakeFeedback.asSharedFlow()
    private val _alertedDeviceId = MutableStateFlow<String?>(null)
    val alertedDeviceId: StateFlow<String?> = _alertedDeviceId.asStateFlow()
    private val _permanentAlertDevices = MutableStateFlow<Set<String>>(emptySet())
    val permanentAlertDevices: StateFlow<Set<String>> = _permanentAlertDevices.asStateFlow()
    private val _selectedMemberId = MutableStateFlow<String?>(null)
    val selectedMemberId: StateFlow<String?> = _selectedMemberId.asStateFlow()
    private val _selectedIncidentId = MutableStateFlow<String?>(null)
    val selectedIncidentId: StateFlow<String?> = _selectedIncidentId.asStateFlow()

    fun memberAlertStatus(userId: String): StateFlow<MemberAlertStatus> = combine(
        _alertedDeviceId,
        _permanentAlertDevices,
    ) { alerting, persistent ->
        when {
            alerting == userId -> MemberAlertStatus.Pulsing(System.currentTimeMillis())
            userId in persistent -> MemberAlertStatus.Persistent
            else -> MemberAlertStatus.None
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MemberAlertStatus.None)

    private var shakeForwardJob: Job? = null
    private var locationJob: Job? = null
    private var alertWindowJob: Job? = null

    init {
        wsClient.connect()
        RoomAlertNotificationHelper.createChannel(getApplication())

        viewModelScope.launch { RoomEventBus.roomError.collect(::onRoomError) }
        viewModelScope.launch {
            RoomEventBus.connectionState.collect { state ->
                if (state == ConnectionState.ONLINE) {
                    val code = RoomEventBus.roomState.value?.code
                    if (!code.isNullOrBlank()) wsClient.sendRoomJoin(code)
                }
            }
        }
        viewModelScope.launch {
            RoomEventBus.roomState.collect { state ->
                val selected = _selectedMemberId.value
                if (state == null || (selected != null && state.members.none { it.userId == selected })) {
                    _selectedMemberId.value = null
                    _selectedIncidentId.value = null
                }
            }
        }
        viewModelScope.launch {
            RoomEventBus.roomState.map { it?.isSharing == true }.distinctUntilChanged().collect { active ->
                if (active) {
                    SosMonitorService.start(getApplication())
                    startShakeForwarding()
                    startLocationUpdates()
                } else {
                    SosMonitorService.stop(getApplication())
                    stopShakeForwarding()
                    stopLocationUpdates()
                    _activeAlert.value = null
                    _alertBannerVisible.value = false
                }
            }
        }
        viewModelScope.launch {
            RoomEventBus.roomState.map { it?.isSharing == true }.distinctUntilChanged().collect { active ->
                if (active) broadcastCurrentState()
            }
        }
        viewModelScope.launch {
            RoomEventBus.roomAlert.collect(::handleAlert)
        }
        viewModelScope.launch {
            RoomEventBus.focusUserId.collect { _focusedDeviceId.value = it }
        }
        viewModelScope.launch {
            RoomEventBus.roomLocationIntervalMs.collect {
                if (roomState.value?.isSharing == true) startLocationUpdates()
            }
        }
    }

    private fun handleAlert(alert: RoomAlert) {
        RoomAlertNotificationHelper.showAlert(
            context = getApplication(),
            alert = alert,
        )
        _activeAlert.value = alert
        _alertBannerVisible.value = true
        _permanentAlertDevices.value += alert.userId
        _alertedDeviceId.value = alert.userId
        _focusedDeviceId.value = alert.userId
        alertWindowJob?.cancel()
        alertWindowJob = viewModelScope.launch {
            delay(10_000)
            _alertedDeviceId.value = null
        }
    }

    fun refreshLocationUpdatesIfInRoom() {
        if (roomState.value?.isSharing == true) startLocationUpdates()
    }

    private fun startLocationUpdates() {
        locationJob?.cancel()
        locationJob = viewModelScope.launch {
            locationRepo.observeLocation(
                roomState.value?.locationIntervalMs
                    ?: RoomEventBus.roomLocationIntervalMs.value,
            )
                .catch { Log.e("RoomVM", "Location unavailable", it) }
                .collect { location ->
                    RoomEventBus.emitMemberLocation(deviceId, location)
                    if (roomState.value?.isSharing == true) {
                        wsClient.sendLocationUpdate(
                            location.latitude,
                            location.longitude,
                            location.accuracy,
                            location.speed,
                        )
                    }
                }
        }
    }

    private fun stopLocationUpdates() {
        locationJob?.cancel()
        locationJob = null
    }

    private fun startShakeForwarding() {
        if (shakeForwardJob != null) return
        shakeForwardJob = viewModelScope.launch {
            SosEventBus.shakeEvents.collect { shake ->
                if (roomState.value?.isSharing == true) {
                    _shakeFeedback.tryEmit(Unit)
                    wsClient.sendSosTrigger(shake)
                }
            }
        }
    }

    private fun stopShakeForwarding() {
        shakeForwardJob?.cancel()
        shakeForwardJob = null
    }

    private fun broadcastCurrentState() {
        val location = RoomEventBus.memberLocations.value[deviceId]
        if (location != null && location.latitude != 0.0 && location.longitude != 0.0) {
            wsClient.sendLocationUpdate(
                location.latitude,
                location.longitude,
                location.accuracy,
                location.speed,
            )
        }
    }

    fun focusOnDevice(userId: String?) { _focusedDeviceId.value = userId }

    fun selectMember(userId: String?) {
        _selectedMemberId.value = userId
        _selectedIncidentId.value = null
        if (userId != null) _focusedDeviceId.value = userId
    }

    fun selectIncident(incident: RoomAlert) {
        _selectedMemberId.value = incident.userId
        _selectedIncidentId.value = incident.incidentId
    }

    fun createRoom(
        shakeThreshold: Float = 3.5f,
        locationIntervalMs: Long = 10_000L,
    ) {
        clearError()
        wsClient.sendRoomCreate(shakeThreshold, locationIntervalMs)
    }

    fun joinRoom(code: String) {
        if (!code.matches(Regex("[A-Z0-9]{6}"))) {
            _error.value = "The Room code must contain 6 characters."
            return
        }
        clearError()
        wsClient.sendRoomJoin(code)
    }

    fun leaveRoom() {
        stopLocationUpdates()
        stopShakeForwarding()
        SosMonitorService.stop(getApplication())
        _activeAlert.value = null
        _alertBannerVisible.value = false
        _alertedDeviceId.value = null
        _permanentAlertDevices.value = emptySet()
        _selectedMemberId.value = null
        _selectedIncidentId.value = null
        wsClient.sendRoomLeave()
        RoomEventBus.clear()
    }

    fun startSharing() = wsClient.sendRoomStart()
    fun stopSharing() {
        if (RoomEventBus.isHost(deviceId)) wsClient.sendRoomStop()
    }

    fun dismissActiveAlertBanner() {
        _alertBannerVisible.value = false
    }

    fun updateRoomSettings(magnitude: Float, locationIntervalMs: Long) {
        if (!RoomEventBus.isHost(deviceId)) return
        if (roomState.value?.isSharing == true) {
            _error.value = "Stop the safety session before updating room settings"
            return
        }
        wsClient.sendRoomSettings(magnitude, locationIntervalMs)
        RoomEventBus.emitRoomShakeLevel(magnitude)
        RoomEventBus.emitRoomLocationIntervalMs(locationIntervalMs)
    }

    fun clearError() { _error.value = null }
    fun reconnect() {
        clearError()
        wsClient.reconnect()
    }
    fun disconnectWebSocket() = wsClient.disconnect()

    private fun onRoomError(message: String) {
        _error.value = message
        if (!isClosedRoomError(message)) return
        stopLocationUpdates()
        stopShakeForwarding()
        SosMonitorService.stop(getApplication())
        _activeAlert.value = null
        _alertBannerVisible.value = false
        _alertedDeviceId.value = null
        _permanentAlertDevices.value = emptySet()
        _selectedMemberId.value = null
        _selectedIncidentId.value = null
        RoomEventBus.clear()
    }

    private fun isClosedRoomError(message: String): Boolean {
        val lower = message.lowercase()
        return lower.contains("is closed") || lower.contains("not found")
    }

    override fun onCleared() {
        locationJob?.cancel()
        shakeForwardJob?.cancel()
        alertWindowJob?.cancel()
        SosMonitorService.stop(getApplication())
        wsClient.disconnect()
        super.onCleared()
    }
}

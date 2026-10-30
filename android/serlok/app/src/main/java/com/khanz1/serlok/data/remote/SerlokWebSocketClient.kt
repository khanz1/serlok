package com.khanz1.serlok.data.remote

import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.khanz1.serlok.BuildConfig
import com.khanz1.serlok.data.AppSettings
import com.khanz1.serlok.data.model.LocationData
import com.khanz1.serlok.data.model.RoomMember
import com.khanz1.serlok.data.model.RoomState
import com.khanz1.serlok.service.ConnectionState
import com.khanz1.serlok.service.RoomAlert
import com.khanz1.serlok.service.RoomEventBus
import com.khanz1.serlok.safety.GForceTrigger
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Single authenticated WebSocket connection for Room state and incidents. */
class SerlokWebSocketClient(
    private val serverUrl: String = DEFAULT_SERVER_URL,
    val deviceInstanceId: String = AppSettings.deviceInstanceId,
) {
    companion object {
        private const val TAG = "SerlokWS"
        private const val PING_INTERVAL_MS = 40_000L
        private const val RECONNECT_INITIAL_MS = 1_000L
        private const val RECONNECT_MAX_MS = 30_000L
        private val MEMBERSHIP_INDEPENDENT = setOf("room_create", "room_join", "ping")
        val DEFAULT_SERVER_URL: String get() = BuildConfig.WS_BASE_URL
    }

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(30, TimeUnit.SECONDS)
        .build()
    private val lock = Any()
    private val worker = Handler(Looper.getMainLooper())
    private var webSocket: WebSocket? = null
    @Volatile private var connected = false
    @Volatile private var connecting = false
    @Volatile private var autoReconnect = true
    private var reconnectAttempt = 0
    private var reconnectRunnable: Runnable? = null
    private var pingRunnable: Runnable? = null
    private val pendingMessages = mutableListOf<JSONObject>()

    fun connect() {
        synchronized(lock) {
            if (connected || connecting) return
            if (AppSettings.token.isBlank()) {
                RoomEventBus.emitError("No sign-in session was found.")
                return
            }
            connecting = true
        }
        RoomEventBus.emitConnectionState(ConnectionState.CONNECTING)
        webSocket?.cancel()
        val url = Uri.parse("$serverUrl/ws").buildUpon()
            .appendQueryParameter("deviceInstanceId", deviceInstanceId)
            .appendQueryParameter("token", AppSettings.token)
            .appendQueryParameter("manufacturer", Build.MANUFACTURER)
            .appendQueryParameter("model", Build.MODEL)
            .appendQueryParameter("androidVersion", Build.VERSION.RELEASE)
            .build()
            .toString()
        webSocket = client.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
            override fun onOpen(socket: WebSocket, response: Response) {
                if (!isCurrent(socket)) return
                reconnectAttempt = 0
                cancelReconnect()
                synchronized(lock) {
                    connecting = false
                    connected = true
                }
                RoomEventBus.emitConnectionState(ConnectionState.ONLINE)
                startPing()
                flushMembershipIndependent()
            }

            override fun onMessage(socket: WebSocket, text: String) {
                if (!isCurrent(socket)) return
                handleIncomingMessage(text)
            }

            override fun onClosing(socket: WebSocket, code: Int, reason: String) {
                if (!isCurrent(socket)) return
                socket.close(1000, null)
            }

            override fun onClosed(socket: WebSocket, code: Int, reason: String) {
                if (!isCurrent(socket)) return
                handleDrop(scheduleRetry = autoReconnect, dropPending = false)
            }

            override fun onFailure(socket: WebSocket, error: Throwable, response: Response?) {
                if (!isCurrent(socket)) return
                if (response?.code == 401) {
                    autoReconnect = false
                    handleDrop(scheduleRetry = false, dropPending = true)
                    AppSettings.clearAuth()
                    RoomEventBus.emitError("Your session ended. Please sign in again.")
                } else {
                    handleDrop(scheduleRetry = autoReconnect, dropPending = false)
                    Log.e(TAG, "WebSocket failure", error)
                }
            }
        })
    }

    fun reconnect() {
        autoReconnect = true
        reconnectAttempt = 0
        cancelReconnect()
        connect()
    }

    fun disconnect() {
        autoReconnect = false
        cancelReconnect()
        stopPing()
        synchronized(lock) {
            pendingMessages.clear()
            connecting = false
            connected = false
        }
        webSocket?.close(1000, "client closed")
        webSocket = null
        RoomEventBus.emitConnectionState(ConnectionState.OFFLINE)
    }

    private fun handleDrop(scheduleRetry: Boolean, dropPending: Boolean) {
        stopPing()
        synchronized(lock) {
            connecting = false
            connected = false
            if (dropPending) pendingMessages.clear()
        }
        RoomEventBus.emitConnectionState(ConnectionState.OFFLINE)
        if (scheduleRetry) scheduleReconnect()
    }

    private fun isCurrent(socket: WebSocket): Boolean = webSocket === socket

    fun sendSosTrigger(shake: GForceTrigger) {
        val idempotencyKey = UUID.randomUUID().toString()
        send(JSONObject().apply {
            put("type", "sos_trigger")
            put("payload", JSONObject().apply {
                put("magnitudeG", shake.magnitudeG.toDouble())
                put("x", shake.x.toDouble())
                put("y", shake.y.toDouble())
                put("z", shake.z.toDouble())
            })
            put("timestamp", shake.detectedAtMs)
            put("idempotencyKey", idempotencyKey)
        })
    }

    fun sendRoomSettings(magnitude: Float, locationIntervalMs: Long) = sendEnvelope(
        "room_update_settings",
        JSONObject()
            .put("shakeThreshold", magnitude.toDouble())
            .put("locationIntervalMs", locationIntervalMs),
    )

    fun sendRoomCreate(shakeThreshold: Float = 3.5f, locationIntervalMs: Long = 10_000L) = sendEnvelope(
        "room_create",
        JSONObject()
            .put("shakeThreshold", shakeThreshold.toDouble())
            .put("locationIntervalMs", locationIntervalMs),
    )
    fun sendRoomJoin(code: String) = sendEnvelope("room_join", JSONObject().put("code", code.uppercase()))
    fun sendRoomLeave() = sendEnvelope("room_leave")
    fun sendRoomStart() = sendEnvelope("room_start")
    fun sendRoomStop() = sendEnvelope("room_stop")

    fun sendLocationUpdate(
        latitude: Double,
        longitude: Double,
        accuracy: Float,
        speed: Float = 0f,
    ) = sendEnvelope(
        "location_update",
        JSONObject().apply {
            put("latitude", latitude)
            put("longitude", longitude)
            put("accuracy", accuracy.toDouble())
            put("speed", speed.toDouble())
        },
    )

    private fun sendEnvelope(type: String, payload: JSONObject? = null) {
        send(JSONObject().apply {
            put("type", type)
            if (payload != null) put("payload", payload)
            put("timestamp", System.currentTimeMillis())
        })
    }

    private fun parseRoomStatePayload(payload: JSONObject): RoomState {
        val masterId = payload.getString("masterId")
        val membersJson = payload.getJSONArray("members")
        val members = (0 until membersJson.length()).map { index ->
            val member = membersJson.getJSONObject(index)
            val userId = member.getString("userId")
            RoomMember(
                userId = userId,
                deviceInstanceId = member.optString("deviceInstanceId"),
                name = member.optString("name", userId),
                email = member.optString("email"),
                isMaster = userId == masterId,
                manufacturer = member.optString("manufacturer"),
                model = member.optString("model"),
                androidVersion = member.optString("androidVersion"),
            )
        }
        val threshold = payload.optDouble("shakeThreshold", 3.5).toFloat()
        val locationIntervalMs = payload.optLong("locationIntervalMs", 10_000L)
        RoomEventBus.emitRoomShakeLevel(threshold)
        RoomEventBus.emitRoomLocationIntervalMs(locationIntervalMs)
        return RoomState(
            code = payload.getString("code"),
            masterId = masterId,
            members = members,
            isSharing = payload.optBoolean("isSharing"),
            shakeThreshold = threshold,
            locationIntervalMs = locationIntervalMs,
        )
    }

    private fun handleIncomingMessage(text: String) {
        runCatching {
            val json = JSONObject(text)
            when (json.optString("type")) {
                "room_state" -> {
                    val payload = json.optJSONObject("payload")
                    if (payload == null) {
                        dropPendingMessages()
                        RoomEventBus.clear()
                    } else {
                        RoomEventBus.replaceIncidents(parseIncidents(payload))
                        RoomEventBus.emitRoomState(parseRoomStatePayload(payload))
                        flushDeferredWithFreshSosTimestamp()
                    }
                }
                "room_start", "room_stop" -> {
                    val payload = json.optJSONObject("payload")
                    if (payload != null) {
                        RoomEventBus.replaceIncidents(parseIncidents(payload))
                        RoomEventBus.emitRoomState(parseRoomStatePayload(payload))
                    } else {
                        val state = RoomEventBus.roomState.value ?: return
                        RoomEventBus.emitRoomState(
                            state.copy(isSharing = json.optString("type") == "room_start"),
                        )
                    }
                }
                "room_settings" -> {
                    val settings = json.optJSONObject("payload")
                    val threshold = settings?.optDouble("shakeThreshold", 3.5)?.toFloat() ?: 3.5f
                    val locationIntervalMs = settings?.optLong("locationIntervalMs", 10_000L) ?: 10_000L
                    RoomEventBus.emitRoomShakeLevel(threshold)
                    RoomEventBus.emitRoomLocationIntervalMs(locationIntervalMs)
                    RoomEventBus.roomState.value?.let { current ->
                        RoomEventBus.emitRoomState(
                            current.copy(
                                shakeThreshold = threshold,
                                locationIntervalMs = locationIntervalMs,
                            ),
                        )
                    }
                }
                "room_error" -> RoomEventBus.emitError(
                    json.optJSONObject("payload")?.optString("message") ?: "A Room error occurred.",
                )
                "room_alert" -> handleAlert(json)
                "location_update" -> handleLocation(json)
                "user_joined" -> RoomEventBus.emitNotification(
                    json.optJSONObject("payload")?.optString("name", "Member") + " joined",
                )
                "user_offline" -> RoomEventBus.emitNotification(
                    json.optJSONObject("payload")?.optString("name", "Member") + " is offline",
                )
            }
        }.onFailure { Log.e(TAG, "Invalid server message", it) }
    }

    private fun handleAlert(json: JSONObject) {
        val payload = json.optJSONObject("payload") ?: return
        RoomEventBus.emitAlert(parseAlertPayload(payload))
    }

    private fun parseIncidents(payload: JSONObject): List<RoomAlert> {
        val array = payload.optJSONArray("incidents") ?: return emptyList()
        return (0 until array.length()).map { index ->
            parseAlertPayload(array.getJSONObject(index))
        }
    }

    private fun parseAlertPayload(payload: JSONObject): RoomAlert {
        val location = payload.optJSONObject("location")
        return RoomAlert(
            incidentId = payload.getString("incidentId"),
            userId = payload.optString("userId"),
            name = payload.optString("name", "Member"),
            magnitudeG = payload.getDouble("magnitudeG").toFloat(),
            latitude = location?.let { nullableDouble(it, "latitude") },
            longitude = location?.let { nullableDouble(it, "longitude") },
            triggeredAt = payload.getString("triggeredAt"),
        )
    }

    private fun handleLocation(json: JSONObject) {
        val payload = json.optJSONObject("payload") ?: return
        val userId = payload.optString("userId")
        RoomEventBus.emitMemberLocation(
            userId,
            LocationData(
                latitude = payload.optDouble("latitude"),
                longitude = payload.optDouble("longitude"),
                accuracy = payload.optDouble("accuracy").toFloat(),
                speed = payload.optDouble("speed").toFloat(),
            ),
        )
    }

    private fun nullableDouble(json: JSONObject, name: String): Double? =
        if (!json.has(name) || json.isNull(name)) null else json.optDouble(name).takeIf { !it.isNaN() }

    private fun send(json: JSONObject) {
        if (connected) {
            webSocket?.send(json.toString())
            return
        }
        synchronized(lock) {
            pendingMessages += json
        }
        connect()
    }

    private fun flushMembershipIndependent() {
        val ready = mutableListOf<JSONObject>()
        synchronized(lock) {
            val deferred = pendingMessages.filterNot { it.optString("type") in MEMBERSHIP_INDEPENDENT }
            ready += pendingMessages.filter { it.optString("type") in MEMBERSHIP_INDEPENDENT }
            pendingMessages.clear()
            pendingMessages += deferred
        }
        ready.forEach { webSocket?.send(it.toString()) }
    }

    private fun flushDeferredWithFreshSosTimestamp() {
        val messages: List<JSONObject>
        synchronized(lock) {
            messages = pendingMessages.toList()
            pendingMessages.clear()
        }
        val now = System.currentTimeMillis()
        messages.forEach { json ->
            if (json.optString("type") == "sos_trigger") {
                json.put("timestamp", now)
            }
            webSocket?.send(json.toString())
        }
    }

    private fun dropPendingMessages() {
        synchronized(lock) { pendingMessages.clear() }
    }

    private fun scheduleReconnect() {
        if (!autoReconnect || AppSettings.token.isBlank()) return
        cancelReconnect()
        val delayMs = reconnectDelayMs()
        reconnectAttempt += 1
        val runnable = Runnable { connect() }
        reconnectRunnable = runnable
        worker.postDelayed(runnable, delayMs)
    }

    private fun reconnectDelayMs(): Long {
        val shift = reconnectAttempt.coerceAtMost(5)
        return (RECONNECT_INITIAL_MS shl shift).coerceAtMost(RECONNECT_MAX_MS)
    }

    private fun cancelReconnect() {
        reconnectRunnable?.let { worker.removeCallbacks(it) }
        reconnectRunnable = null
    }

    private fun startPing() {
        stopPing()
        val runnable = object : Runnable {
            override fun run() {
                if (!connected) return
                webSocket?.send(
                    JSONObject()
                        .put("type", "ping")
                        .put("timestamp", System.currentTimeMillis())
                        .toString(),
                )
                worker.postDelayed(this, PING_INTERVAL_MS)
            }
        }
        pingRunnable = runnable
        worker.postDelayed(runnable, PING_INTERVAL_MS)
    }

    private fun stopPing() {
        pingRunnable?.let { worker.removeCallbacks(it) }
        pingRunnable = null
    }
}

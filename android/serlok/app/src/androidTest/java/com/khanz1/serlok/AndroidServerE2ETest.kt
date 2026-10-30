package com.khanz1.serlok

import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.khanz1.serlok.data.AppSettings
import com.khanz1.serlok.data.remote.AuthApiClient
import com.khanz1.serlok.data.remote.AuthResult
import com.khanz1.serlok.safety.GForceDetector
import kotlinx.coroutines.runBlocking
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import java.net.Inet4Address
import java.net.InetAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
@LargeTest
class AndroidServerE2ETest {
    companion object {
        private const val TAG = "SerlokE2E"
        private const val PASSWORD = "SerlokE2E!2026"
        private const val TIMEOUT_SECONDS = 15L

        internal val IPV4_DNS = object : Dns {
            override fun lookup(hostname: String): List<InetAddress> =
                Dns.SYSTEM.lookup(hostname).filterIsInstance<Inet4Address>()
        }

        internal fun testClient(): OkHttpClient = OkHttpClient.Builder().dns(IPV4_DNS).build()
    }

    @Test
    fun androidClient_completesRestAndWebSocketFlow() = runBlocking {
        AppSettings.init(InstrumentationRegistry.getInstrumentation().targetContext)
        val prefix = "v46-${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(6)}"
        val hostDevice = "android-host-$prefix"
        val memberDevice = "android-member-$prefix"
        val hostAuth = register("Host E2E", "$prefix-host@example.invalid", hostDevice)
        val memberAuth = register("Member E2E", "$prefix-member@example.invalid", memberDevice)
        pass("Android REST authentication", "two demo identities authenticated", "Host and Member registered")

        val hostSocket = E2EWebSocket(hostAuth.token, hostDevice)
        val memberSocket = E2EWebSocket(memberAuth.token, memberDevice)
        try {
            hostSocket.connect()
            memberSocket.connect()
            pass("Authenticated WebSocket upgrade", "two connections open", "Host and Member connected")

            hostSocket.send("room_create", JSONObject().put("shakeThreshold", 3.5))
            val hostRoom = hostSocket.await("room_state")
            val roomCode = hostRoom.getJSONObject("payload").getString("code")
            assertEquals(6, roomCode.length)
            memberSocket.send("room_join", JSONObject().put("code", roomCode))
            memberSocket.await("room_state")
            pass("Room create and join", "one shared six-character Room", "Host and Member joined $roomCode")

            hostSocket.send("room_start")
            val started = memberSocket.await("room_start")
            assertTrue(started.optJSONObject("payload")?.optBoolean("isSharing") ?: true)
            pass("Safety Session", "Host activates sharing", "Member received room_start")

            val latitude = -6.2001
            val longitude = 106.8167
            memberSocket.send(
                "location_update",
                JSONObject()
                    .put("latitude", latitude)
                    .put("longitude", longitude)
                    .put("accuracy", 5.0),
            )
            val relayedLocation = hostSocket.await("location_update")
            val locationPayload = relayedLocation.getJSONObject("payload")
            assertEquals(memberAuth.user.id, locationPayload.getString("userId"))
            assertEquals(latitude, locationPayload.getDouble("latitude"), 0.000001)
            assertEquals(longitude, locationPayload.getDouble("longitude"), 0.000001)
            pass("Location relay", "Member location reaches Host", "latitude and longitude matched")

            val detector = GForceDetector(thresholdG = 3.5f)
            val trigger = detector.onSample(42f, 0f, 0f, 1_000L)
            assertNotNull(trigger)
            val detected = requireNotNull(trigger)
            pass(
                "GForceDetector synthetic samples",
                "one rising-edge crossing above 3.5 g",
                "single trigger at %.2f g".format(detected.magnitudeG),
            )

            memberSocket.send(
                "sos_trigger",
                JSONObject()
                    .put("magnitudeG", detected.magnitudeG.toDouble())
                    .put("x", detected.x.toDouble())
                    .put("y", detected.y.toDouble())
                    .put("z", detected.z.toDouble()),
                idempotencyKey = UUID.randomUUID().toString(),
            )
            val hostAlert = hostSocket.await("room_alert")
            val memberAlert = memberSocket.await("room_alert")
            val alert = memberAlert.getJSONObject("payload")
            val incidentId = alert.getString("incidentId")
            val alertLocation = alert.getJSONObject("location")
            assertFalse(incidentId.isBlank())
            assertFalse(alert.has("source"))
            assertFalse(alert.has("canResolve"))
            assertFalse(alert.has("status"))
            assertEquals(detected.magnitudeG.toDouble(), alert.getDouble("magnitudeG"), 0.001)
            assertEquals(latitude, alertLocation.getDouble("latitude"), 0.000001)
            assertEquals(longitude, alertLocation.getDouble("longitude"), 0.000001)
            assertEquals(incidentId, hostAlert.getJSONObject("payload").getString("incidentId"))
            pass("Shake SOS relay", "matching shake-only room_alert on both clients", "event, G-Force, and location matched")

            hostSocket.send("room_stop")
            memberSocket.await("room_stop")
            memberSocket.send("room_leave")
            hostSocket.send("room_leave")
            pass("Normal shutdown", "session stopped and both clients leave", "room_stop and room_leave sent")
        } finally {
            memberSocket.close()
            hostSocket.close()
        }
    }

    internal suspend fun register(name: String, email: String, deviceId: String): AuthResult =
        AuthApiClient(
            serverUrl = BuildConfig.HTTP_BASE_URL,
            deviceInstanceId = deviceId,
            deviceManufacturer = Build.MANUFACTURER,
            deviceModel = Build.MODEL,
            client = testClient(),
        ).register(name, email, PASSWORD).getOrThrow()

    internal fun pass(scenario: String, expected: String, actual: String) {
        Log.i(TAG, "[PASS] $scenario | expected: $expected | actual: $actual")
    }

    internal class E2EWebSocket(
        private val token: String,
        private val deviceId: String,
    ) : WebSocketListener() {
        private val client = OkHttpClient.Builder()
            .dns(IPV4_DNS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()
        private val opened = CountDownLatch(1)
        private val messages = LinkedBlockingQueue<JSONObject>()
        private var failure: Throwable? = null
        private var socket: WebSocket? = null

        fun connect() {
            val url = Uri.parse("${BuildConfig.WS_BASE_URL.trimEnd('/')}/ws").buildUpon()
                .appendQueryParameter("token", token)
                .appendQueryParameter("deviceInstanceId", deviceId)
                .appendQueryParameter("manufacturer", Build.MANUFACTURER)
                .appendQueryParameter("model", Build.MODEL)
                .appendQueryParameter("androidVersion", Build.VERSION.RELEASE)
                .build()
                .toString()
            socket = client.newWebSocket(Request.Builder().url(url).build(), this)
            assertTrue("WebSocket did not open", opened.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
            failure?.let { throw AssertionError("WebSocket connection failed", it) }
        }

        fun send(type: String, payload: JSONObject? = null, idempotencyKey: String? = null) {
            val envelope = JSONObject().put("type", type).put("timestamp", System.currentTimeMillis())
            if (payload != null) envelope.put("payload", payload)
            if (idempotencyKey != null) envelope.put("idempotencyKey", idempotencyKey)
            assertTrue("WebSocket send failed for $type", socket?.send(envelope.toString()) == true)
        }

        fun await(type: String): JSONObject {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS)
            while (System.nanoTime() < deadline) {
                failure?.let { throw AssertionError("WebSocket failed while waiting for $type", it) }
                val remaining = deadline - System.nanoTime()
                val message = messages.poll(remaining.coerceAtLeast(1), TimeUnit.NANOSECONDS) ?: break
                if (message.optString("type") == type) return message
                if (message.optString("type") == "room_error") {
                    throw AssertionError("Server returned room_error: ${message.optJSONObject("payload")}")
                }
            }
            throw AssertionError("Timed out waiting for $type")
        }

        fun close() {
            socket?.close(1000, "E2E complete")
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
        }

        override fun onOpen(webSocket: WebSocket, response: Response) {
            opened.countDown()
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            messages.offer(JSONObject(text))
        }

        override fun onFailure(webSocket: WebSocket, error: Throwable, response: Response?) {
            failure = error
            opened.countDown()
        }
    }
}

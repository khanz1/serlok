package com.khanz1.serlok

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.khanz1.serlok.data.AppSettings
import com.khanz1.serlok.data.remote.AuthResult
import com.khanz1.serlok.safety.GForceDetector
import kotlinx.coroutines.runBlocking
import okhttp3.Request
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
@LargeTest
class ControlledProductionTest {
    companion object {
        private const val TAG = "SerlokProduction"
        private const val LOCATION_TOTAL = 30
        private const val ALERT_TOTAL = 10
        private const val COOLDOWN_SPACING_MS = 3_200L
    }

    @Test
    fun app_completesControlledProductionRun() = runBlocking {
        assertEquals("https://serlok.sonangga.dev", BuildConfig.HTTP_BASE_URL.trimEnd('/'))
        assertEquals("wss://serlok.sonangga.dev", BuildConfig.WS_BASE_URL.trimEnd('/'))
        AppSettings.init(InstrumentationRegistry.getInstrumentation().targetContext)
        assertHealth("before test")
        assertReady("before test")

        val support = AndroidServerE2ETest()
        val prefix = "v46-prod-${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(6)}"
        val hostDevice = "prod-host-$prefix"
        val memberDevice = "prod-member-$prefix"
        var hostAuth: AuthResult? = null
        var memberAuth: AuthResult? = null
        var hostSocket: AndroidServerE2ETest.E2EWebSocket? = null
        var memberSocket: AndroidServerE2ETest.E2EWebSocket? = null

        try {
            hostAuth = support.register("Production Host", "$prefix-host@example.invalid", hostDevice)
            memberAuth = support.register("Production Member", "$prefix-member@example.invalid", memberDevice)
            pass("Demo identity isolation", "two new prefixed demo accounts", "Host and Member registered without real user data")

            hostSocket = AndroidServerE2ETest.E2EWebSocket(hostAuth.token, hostDevice)
            memberSocket = AndroidServerE2ETest.E2EWebSocket(memberAuth.token, memberDevice)
            hostSocket.connect()
            memberSocket.connect()

            hostSocket.send("room_create", JSONObject().put("shakeThreshold", 3.5))
            val roomCode = hostSocket.await("room_state").getJSONObject("payload").getString("code")
            memberSocket.send("room_join", JSONObject().put("code", roomCode))
            memberSocket.await("room_state")
            hostSocket.send("room_start")
            memberSocket.await("room_start")
            pass("Production Safety Session", "two authenticated clients enter one active Room", "Room created, joined, and started")

            var locationRelays = 0
            repeat(LOCATION_TOTAL) { index ->
                val latitude = -6.200000 - index * 0.000001
                val longitude = 106.816700 + index * 0.000001
                memberSocket.send(
                    "location_update",
                    JSONObject()
                        .put("latitude", latitude)
                        .put("longitude", longitude)
                        .put("accuracy", 5.0),
                )
                val payload = hostSocket.await("location_update").getJSONObject("payload")
                assertEquals(memberAuth.user.id, payload.getString("userId"))
                assertEquals(latitude, payload.getDouble("latitude"), 0.0000001)
                assertEquals(longitude, payload.getDouble("longitude"), 0.0000001)
                locationRelays++
            }
            assertEquals(LOCATION_TOTAL, locationRelays)
            pass("Production location relay", "30 of 30 updates received", "$locationRelays of $LOCATION_TOTAL matched")

            val detector = GForceDetector(thresholdG = 3.5f)
            var alerts = 0
            var sampleBase = 1_000L
            repeat(ALERT_TOTAL) { index ->
                assertEquals(null, detector.onSample(9.8f, 0f, 0f, sampleBase))
                val trigger = detector.onSample(42f, 0f, 0f, sampleBase + 100L)
                assertNotNull(trigger)
                val detected = requireNotNull(trigger)
                val idempotencyKey = UUID.randomUUID().toString()
                memberSocket.send(
                    "sos_trigger",
                    JSONObject()
                        .put("magnitudeG", detected.magnitudeG.toDouble())
                        .put("x", detected.x.toDouble())
                        .put("y", detected.y.toDouble())
                        .put("z", detected.z.toDouble()),
                    idempotencyKey = idempotencyKey,
                )
                val hostAlert = hostSocket.await("room_alert").getJSONObject("payload")
                val memberAlert = memberSocket.await("room_alert").getJSONObject("payload")
                val incidentId = memberAlert.getString("incidentId")
                assertEquals(incidentId, hostAlert.getString("incidentId"))
                assertTrue(!memberAlert.has("source"))
                assertTrue(!memberAlert.has("canResolve"))
                assertTrue(memberAlert.getDouble("magnitudeG") >= 3.5)
                assertTrue(memberAlert.has("location"))
                alerts++
                sampleBase += 4_000L
                if (index < ALERT_TOTAL - 1) Thread.sleep(COOLDOWN_SPACING_MS)
            }
            assertEquals(ALERT_TOTAL, alerts)
            pass("Production shake SOS", "10 of 10 alerts", "$alerts alerts matched")

            hostSocket.send("room_stop")
            memberSocket.await("room_stop")
            memberSocket.send("room_leave")
            hostSocket.send("room_leave")
        } finally {
            memberSocket?.close()
            hostSocket?.close()
        }

        pass("Production cleanup", "Room closed", "both clients left")
        assertHealth("after cleanup")
        assertReady("after cleanup")
        pass("Production health and readiness", "HTTP 200 after cleanup", "health and database readiness remained available")
    }

    private fun assertHealth(stage: String) {
        val request = Request.Builder()
            .url("${BuildConfig.HTTP_BASE_URL.trimEnd('/')}/api/health")
            .get()
            .build()
        AndroidServerE2ETest.testClient().newCall(request).execute().use { response ->
            assertTrue("Production health failed $stage with HTTP ${response.code}", response.isSuccessful)
            val body = JSONObject(response.body?.string().orEmpty())
            val status = body.optJSONObject("data")?.optString("status")
                ?: body.optString("status")
            assertEquals("ok", status)
        }
    }

    private fun assertReady(stage: String) {
        val request = Request.Builder()
            .url("${BuildConfig.HTTP_BASE_URL.trimEnd('/')}/api/ready")
            .get()
            .build()
        AndroidServerE2ETest.testClient().newCall(request).execute().use { response ->
            assertTrue("Production readiness failed $stage with HTTP ${response.code}", response.isSuccessful)
            val body = JSONObject(response.body?.string().orEmpty())
            val status = body.optJSONObject("data")?.optString("status")
                ?: body.optString("status")
            assertEquals("ready", status)
        }
    }

    private fun pass(scenario: String, expected: String, actual: String) {
        Log.i(TAG, "[PASS] $scenario | expected: $expected | actual: $actual")
    }
}

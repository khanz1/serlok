package com.khanz1.serlok

import android.Manifest
import android.content.ContentValues
import android.graphics.Bitmap
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.ViewModelProvider
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.khanz1.serlok.data.AppSettings
import com.khanz1.serlok.data.model.LocationData
import com.khanz1.serlok.data.model.RoomMember
import com.khanz1.serlok.data.model.RoomState
import com.khanz1.serlok.service.ConnectionState
import com.khanz1.serlok.service.RoomAlert
import com.khanz1.serlok.service.RoomEventBus
import com.khanz1.serlok.ui.main.MainScaffold
import com.khanz1.serlok.ui.screens.auth.AuthScreen
import com.khanz1.serlok.ui.screens.onboarding.OnboardingScreen
import com.khanz1.serlok.ui.screens.room.RoomContent
import com.khanz1.serlok.ui.screens.room.RoomViewModel
import com.khanz1.serlok.ui.theme.SerlokTheme
import org.junit.Rule
import org.junit.Test
import org.osmdroid.config.Configuration
import java.io.File
import java.io.FileOutputStream

@LargeTest
class ScreenshotCaptureTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun captureEveryScreen() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        AppSettings.init(context)
        AppSettings.saveUserProfile("Angga Maulana", "angga@example.invalid")
        Configuration.getInstance().load(
            context,
            context.getSharedPreferences("osmdroid", 0),
        )
        Configuration.getInstance().userAgentValue =
            "${context.packageName}/screenshot"
        instrumentation.uiAutomation.grantRuntimePermission(
            context.packageName,
            Manifest.permission.ACCESS_FINE_LOCATION,
        )
        instrumentation.uiAutomation.grantRuntimePermission(
            context.packageName,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            instrumentation.uiAutomation.grantRuntimePermission(
                context.packageName,
                Manifest.permission.POST_NOTIFICATIONS,
            )
        }

        val screen = mutableStateOf("onboarding")
        val room = mutableStateOf(sampleRoom(isSharing = false))
        val focused = mutableStateOf<String?>(null)
        val selectedMember = mutableStateOf<String?>(null)
        val selectedIncident = mutableStateOf<String?>(null)
        val alert = mutableStateOf<RoomAlert?>(null)
        val incidents = mutableStateOf<List<RoomAlert>>(emptyList())
        val locations = mapOf(
            "host" to LocationData(latitude = HOST_LAT, longitude = HOST_LNG, accuracy = 4f),
            "member" to LocationData(latitude = MEMBER_LAT, longitude = MEMBER_LNG, accuracy = 6f),
        )

        compose.setContent {
            SerlokTheme {
                when (screen.value) {
                    "onboarding" -> OnboardingScreen(onFinish = {})
                    "auth" -> AuthScreen(onNavigateToHome = {})
                    "main" -> MainScaffold(onEnterRoom = {}, onLogout = {})
                    else -> RoomContent(
                        state = room.value,
                        selfId = "host",
                        locations = locations,
                        timestamps = mapOf(
                            "host" to System.currentTimeMillis(),
                            "member" to System.currentTimeMillis(),
                        ),
                        incidents = incidents.value,
                        selectedMemberId = selectedMember.value,
                        selectedIncidentId = selectedIncident.value,
                        focusedUserId = focused.value,
                        activeAlert = alert.value,
                        alertBannerVisible = true,
                        connection = ConnectionState.ONLINE,
                        onFocus = { focused.value = it },
                        onSelectMember = { selectedMember.value = it },
                        onSelectIncident = { selectedIncident.value = it.incidentId },
                        onStart = {},
                        onStop = {},
                        onBack = {},
                        onLeaveRequest = {},
                        onDismissAlert = {},
                        onSettings = { _, _ -> },
                        onReconnect = {},
                        onLocationGranted = {},
                    )
                }
            }
        }

        capture("01-onboarding-location.png")
        compose.onNodeWithText(context.getString(R.string.onboarding_next)).performClick()
        capture("02-onboarding-shake-only.png")

        compose.runOnIdle { screen.value = "auth" }
        capture("03-authentication.png")
        compose.onNodeWithText(context.getString(R.string.auth_switch_register)).performClick()
        capture("03b-authentication-register.png")

        compose.runOnIdle { screen.value = "main" }
        showConnectedHome()
        capture("04-home.png", settleMs = 1_500)
        compose.onNodeWithText(context.getString(R.string.home_create)).performClick()
        showConnectedHome()
        capture("04b-create-room.png", settleMs = 1_200)
        compose.onNodeWithText(context.getString(R.string.action_cancel)).performClick()
        compose.onNodeWithText(context.getString(R.string.home_join)).performClick()
        showConnectedHome()
        capture("04c-join-room.png", settleMs = 1_200)
        compose.onNodeWithText(context.getString(R.string.action_cancel)).performClick()
        compose.onNodeWithText(context.getString(R.string.nav_settings)).performClick()
        capture("11-settings.png", settleMs = 1_200)
        compose.onNodeWithText(context.getString(R.string.settings_privacy)).performScrollTo()
        capture("11c-settings-privacy.png", settleMs = 800)

        compose.runOnIdle { screen.value = "room" }
        capture("05-room-ready.png", settleMs = 12_000)

        compose.runOnIdle { room.value = room.value.copy(isSharing = true) }
        capture("06-active-room.png", settleMs = 4_000)

        compose.runOnIdle { focused.value = "member" }
        capture("07-member-map.png", settleMs = 4_000)

        compose.runOnIdle { alert.value = sampleAlert(owner = false) }
        capture("08-incoming-shake.png", settleMs = 4_000)

        compose.runOnIdle {
            incidents.value = listOf(
                sampleAlert(owner = false),
                RoomAlert(
                    incidentId = "incoming-incident-2",
                    userId = "member",
                    name = "Kartika Widyasari",
                    magnitudeG = 3.9f,
                    latitude = INCIDENT2_LAT,
                    longitude = INCIDENT2_LNG,
                    triggeredAt = "2026-08-17T13:47:00.000Z",
                ),
            )
            selectedMember.value = "member"
            focused.value = "member"
        }
        capture("09-member-sos-alerts.png", settleMs = 4_000)

        compose.runOnIdle {
            incidents.value = emptyList()
            selectedMember.value = "host"
            focused.value = "host"
            selectedIncident.value = null
        }
        capture("10-member-sos-empty.png", settleMs = 2_000)

        compose.runOnIdle {
            incidents.value = listOf(sampleAlert(owner = false))
            selectedMember.value = "member"
            selectedIncident.value = "incoming-incident"
            focused.value = "member"
        }
        capture("12-member-sos-pin-selected.png", settleMs = 4_000)
    }

    private fun showConnectedHome() {
        compose.waitForIdle()
        Thread.sleep(400)
        compose.runOnIdle {
            ViewModelProvider(compose.activity)[RoomViewModel::class.java].clearError()
            RoomEventBus.emitConnectionState(ConnectionState.ONLINE)
        }
        compose.waitForIdle()
    }

    private fun capture(name: String, settleMs: Long = 700) {
        compose.waitForIdle()
        Thread.sleep(settleMs)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        val dir = File(context.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        FileOutputStream(File(dir, name)).use { stream ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream))
        }
        runCatching {
            val publicDir = File("/sdcard/Download/SerlokScreens").apply { mkdirs() }
            File(dir, name).copyTo(File(publicDir, name), overwrite = true)
            Log.i("SerlokScreenshot", "copied ${File(publicDir, name).absolutePath}")
        }
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, "image/png")
            put(MediaStore.Downloads.RELATIVE_PATH, "Download/SerlokScreens")
        }
        val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        if (uri != null) {
            context.contentResolver.openOutputStream(uri)?.use { stream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            }
        }
        bitmap.recycle()
    }

    private fun sampleRoom(isSharing: Boolean) = RoomState(
        code = "SRLK46",
        masterId = "host",
        members = listOf(
            RoomMember(userId = "host", name = "Angga Maulana", isMaster = true),
            RoomMember(userId = "member", name = "Kartika Widyasari", isMaster = false),
        ),
        isSharing = isSharing,
        shakeThreshold = 3.5f,
        locationIntervalMs = 10_000L,
    )

    private fun sampleAlert(owner: Boolean) = RoomAlert(
        incidentId = if (owner) "own-incident" else "incoming-incident",
        userId = if (owner) "host" else "member",
        name = if (owner) "Angga Maulana" else "Kartika Widyasari",
        magnitudeG = 4.3f,
        latitude = MEMBER_LAT,
        longitude = MEMBER_LNG,
        triggeredAt = "2026-08-17T14:00:00.000Z",
    )

    private companion object {
        const val HOST_LAT = -6.2000
        const val HOST_LNG = 106.8166
        const val MEMBER_LAT = -6.2038
        const val MEMBER_LNG = 106.8224
        const val INCIDENT2_LAT = -6.2025
        const val INCIDENT2_LNG = 106.8198
    }
}

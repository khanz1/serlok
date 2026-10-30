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
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.khanz1.serlok.data.AppSettings
import com.khanz1.serlok.data.model.LocationData
import com.khanz1.serlok.data.model.RoomMember
import com.khanz1.serlok.data.model.RoomState
import com.khanz1.serlok.service.ConnectionState
import com.khanz1.serlok.service.RoomAlert
import com.khanz1.serlok.service.RoomEventBus
import com.khanz1.serlok.service.SosMonitorService
import com.khanz1.serlok.ui.screens.room.RoomAlertNotificationHelper
import com.khanz1.serlok.ui.screens.room.RoomContent
import com.khanz1.serlok.ui.theme.SerlokTheme
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.io.FileOutputStream

/**
 * Internal evidence capture. Not a thesis result table by itself.
 * Host script injects emulator acceleration while the monitor is armed.
 */
@LargeTest
class FieldEvidenceCaptureTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun captureLiveBannerLockScreenAndHoldMonitor() {
        assumeTrue(
            InstrumentationRegistry.getArguments().getString("serlok.field") == "1",
        )
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        AppSettings.init(context)
        grantRuntimePermissions()
        RoomEventBus.emitRoomShakeLevel(3.5f)
        RoomAlertNotificationHelper.createChannel(context)

        val alert = mutableStateOf(
            RoomAlert(
                incidentId = "live-incoming",
                userId = "member",
                name = "Kartika Widyasari",
                magnitudeG = 4.1f,
                latitude = -6.2001,
                longitude = 106.8167,
                triggeredAt = "2026-08-24T00:00:00.000Z",
            ),
        )
        val room = RoomState(
            code = "SRLK46",
            masterId = "host",
            members = listOf(
                RoomMember(userId = "host", name = "Angga Maulana", isMaster = true),
                RoomMember(userId = "member", name = "Kartika Widyasari", isMaster = false),
            ),
            isSharing = true,
            shakeThreshold = 3.5f,
            locationIntervalMs = 10_000L,
        )
        val locations = mapOf(
            "host" to LocationData(latitude = -6.2000, longitude = 106.8166, accuracy = 4f),
            "member" to LocationData(latitude = -6.2001, longitude = 106.8167, accuracy = 6f),
        )

        compose.setContent {
            SerlokTheme {
                RoomContent(
                    state = room,
                    selfId = "host",
                    locations = locations,
                    timestamps = mapOf(
                        "host" to System.currentTimeMillis(),
                        "member" to System.currentTimeMillis(),
                    ),
                    focusedUserId = "member",
                    activeAlert = alert.value,
                    alertBannerVisible = true,
                    connection = ConnectionState.ONLINE,
                    onFocus = {},
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
        compose.waitForIdle()
        Thread.sleep(3_000)
        capturePng("08-live-shake.png")
        Log.i(TAG, "LIVE_BANNER_READY")

        RoomAlertNotificationHelper.showAlert(context, alert.value)
        Log.i(TAG, "LOCKSCREEN_NOTIF_READY")
        Thread.sleep(20_000)

        repeat(5) { index ->
            SosMonitorService.start(context)
            Log.i(TAG, "MONITOR_TRIAL_${index + 1}_READY")
            Thread.sleep(8_000)
            SosMonitorService.stop(context)
            Thread.sleep(2_000)
        }
        Log.i(TAG, "MONITOR_DONE")
    }

    private fun grantRuntimePermissions() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val packageName = instrumentation.targetContext.packageName
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.POST_NOTIFICATIONS,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissions.distinct().forEach { permission ->
            runCatching {
                instrumentation.uiAutomation.grantRuntimePermission(packageName, permission)
            }
        }
    }

    private fun capturePng(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        val dir = File(context.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        FileOutputStream(File(dir, name)).use { stream ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream))
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
        Log.i(TAG, "wrote ${File(dir, name).absolutePath}")
        bitmap.recycle()
    }

    companion object {
        private const val TAG = "SerlokField"
    }
}

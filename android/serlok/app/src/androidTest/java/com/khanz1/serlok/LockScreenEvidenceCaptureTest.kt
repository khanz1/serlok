package com.khanz1.serlok

import android.Manifest
import android.os.Build
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.khanz1.serlok.data.AppSettings
import com.khanz1.serlok.safety.GForceTrigger
import com.khanz1.serlok.service.RoomAlert
import com.khanz1.serlok.service.RoomEventBus
import com.khanz1.serlok.service.SosEventBus
import com.khanz1.serlok.service.SosMonitorService
import com.khanz1.serlok.ui.screens.room.RoomAlertNotificationHelper
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Internal evidence capture for lock-screen SOS.
 * The host script locks the emulator, then injects acceleration for the trigger case.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class LockScreenEvidenceCaptureTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun showPublicLockScreenNotification() {
        assumeTrue(
            InstrumentationRegistry.getArguments().getString("serlok.lockscreen") == "1",
        )
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        grantRuntimePermissions()
        RoomAlertNotificationHelper.createChannel(context)
        RoomAlertNotificationHelper.showAlert(
            context,
            RoomAlert(
                incidentId = "lockscreen-incoming",
                userId = "member",
                name = "Kartika Widyasari",
                magnitudeG = 4.1f,
                latitude = -6.2001,
                longitude = 106.8167,
                triggeredAt = "2026-08-27T00:00:00.000Z",
            ),
        )
        Log.i(TAG, "LOCKSCREEN_NOTIF_READY")
        Thread.sleep(HOLD_MS)
    }

    @Test
    fun triggerSosWhileScreenLocked() {
        assumeTrue(
            InstrumentationRegistry.getArguments().getString("serlok.locktrigger") == "1",
        )
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        AppSettings.init(context)
        AppSettings.setGforceDebugEnabled(true)
        AppSettings.saveUserProfile("Kartika Widyasari", "kartika@example.invalid")
        grantRuntimePermissions()
        RoomEventBus.emitRoomShakeLevel(3.5f)
        RoomAlertNotificationHelper.createChannel(context)

        val pending = CompletableDeferred<GForceTrigger>()
        val collector = CoroutineScope(Dispatchers.Default).launch {
            SosEventBus.shakeEvents.collect { trigger ->
                if (!pending.isCompleted) pending.complete(trigger)
            }
        }
        SosMonitorService.start(context)
        Log.i(TAG, "MONITOR_ARMED")
        val trigger = runBlocking {
            withTimeout(40_000) { pending.await() }
        }
        collector.cancel()
        RoomAlertNotificationHelper.showAlert(
            context,
            RoomAlert(
                incidentId = "lockscreen-trigger",
                userId = "member",
                name = "Kartika Widyasari",
                magnitudeG = trigger.magnitudeG,
                latitude = -6.2001,
                longitude = 106.8167,
                triggeredAt = "2026-08-27T00:00:00.000Z",
            ),
        )
        Log.i(
            TAG,
            "LOCKSCREEN_TRIGGER_READY trigger=${"%.2f".format(trigger.magnitudeG)} g",
        )
        Thread.sleep(HOLD_MS)
        SosMonitorService.stop(context)
    }

    private fun grantRuntimePermissions() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val packageName = instrumentation.targetContext.packageName
        val permissions = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.POST_NOTIFICATIONS,
        )
        permissions.forEach { permission ->
            runCatching {
                instrumentation.uiAutomation.grantRuntimePermission(packageName, permission)
            }
        }
    }

    companion object {
        private const val TAG = "SerlokLockScreen"
        private const val HOLD_MS = 20_000L
    }
}

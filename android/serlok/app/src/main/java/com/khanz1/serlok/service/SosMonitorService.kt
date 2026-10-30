package com.khanz1.serlok.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.khanz1.serlok.MainActivity
import com.khanz1.serlok.R
import com.khanz1.serlok.data.AppSettings
import com.khanz1.serlok.debug.DeveloperOptions
import com.khanz1.serlok.safety.DebugNotificationText
import com.khanz1.serlok.safety.DebugSessionExport
import com.khanz1.serlok.safety.DebugSnapshot
import com.khanz1.serlok.safety.GForceDebugGate
import com.khanz1.serlok.safety.GForceDetector
import com.khanz1.serlok.safety.GForceFieldLogger
import com.khanz1.serlok.safety.SessionDebugAggregator

/** Visible accelerometer monitor active only during a Safety Session. */
class SosMonitorService : Service(), SensorEventListener {
    companion object {
        private const val TAG = "SosMonitor"
        private const val CHANNEL_ID = "serlok_safety_monitor"
        private const val NOTIFICATION_ID = 2001
        private const val WAKE_LOCK_TIMEOUT_MS = 4 * 60 * 60 * 1_000L
        private const val BUS_EMIT_INTERVAL_MS = 200L
        private const val NOTIFICATION_INTERVAL_MS = 1_000L

        fun start(context: Context) {
            val intent = Intent(context, SosMonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, SosMonitorService::class.java))
        }
    }

    private var sensorManager: SensorManager? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var detector = GForceDetector(3.5f)
    private val aggregator = SessionDebugAggregator()
    private var monitoring = false
    private var sessionPeakG = 0f
    private var sessionLogged = false
    private var lastBusEmitAt = 0L
    private var lastNotifyAt = 0L
    private var lastOverlayEffective = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        arm()
        return START_NOT_STICKY
    }

    private fun arm() {
        if (monitoring) return
        createNotificationChannel()
        detector = GForceDetector(RoomEventBus.roomShakeLevel.value)
        sessionPeakG = 0f
        sessionLogged = false
        aggregator.reset(
            startedAtMs = System.currentTimeMillis(),
            thresholdG = RoomEventBus.roomShakeLevel.value,
        )
        SosEventBus.emitDebugSnapshot(aggregator.snapshot())
        SosEventBus.setDebugCsvProvider {
            DebugCsvExport(
                fileName = DebugSessionExport.fileName(aggregator.startedAtMs()),
                csv = aggregator.exportCsv(),
            )
        }
        lastBusEmitAt = 0L
        lastNotifyAt = 0L
        lastOverlayEffective = overlayEffective()
        if (!startAsForeground()) return

        sensorManager = getSystemService(SensorManager::class.java)
        val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (accelerometer == null) {
            Log.e(TAG, "No accelerometer available")
            disarm()
            return
        }
        sensorManager?.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME)
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "serlok:safety-monitor")
            .apply { acquire(WAKE_LOCK_TIMEOUT_MS) }
        monitoring = true
        SosEventBus.setActive(true)
    }

    private fun disarm() {
        logSessionStop()
        aggregator.markInactive()
        SosEventBus.emitDebugSnapshot(aggregator.snapshot())
        sensorManager?.unregisterListener(this)
        sensorManager = null
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        monitoring = false
        SosEventBus.setActive(false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onSensorChanged(event: SensorEvent) {
        val nowMs = System.currentTimeMillis()
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        val magnitudeG = GForceDetector.normalizedG(x, y, z)
        if (magnitudeG > sessionPeakG) {
            sessionPeakG = magnitudeG
            GForceFieldLogger.logPeak(sessionPeakG)
        }
        detector.updateThreshold(RoomEventBus.roomShakeLevel.value)
        val trigger = detector.onSample(
            x = x,
            y = y,
            z = z,
            nowMs = nowMs,
        )
        aggregator.onSample(
            magnitudeG = magnitudeG,
            nowMs = nowMs,
            detector = detector.debugState(nowMs),
            triggered = trigger != null,
        )
        publishDebug(nowMs)
        if (trigger == null) return
        val thresholdG = RoomEventBus.roomShakeLevel.value
        GForceFieldLogger.logTrigger(trigger.magnitudeG, sessionPeakG, thresholdG)
        GForceFieldLogger.appendTrigger(
            context = this,
            thresholdG = thresholdG,
            triggerG = trigger.magnitudeG,
            sessionPeakG = sessionPeakG,
        )
        SosEventBus.emitShakeEvent(trigger)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onDestroy() {
        logSessionStop()
        aggregator.markInactive()
        SosEventBus.emitDebugSnapshot(aggregator.snapshot())
        sensorManager?.unregisterListener(this)
        wakeLock?.let { if (it.isHeld) it.release() }
        SosEventBus.setActive(false)
        super.onDestroy()
    }

    private fun logSessionStop() {
        if (sessionLogged) return
        sessionLogged = true
        GForceFieldLogger.appendSessionStop(
            context = this,
            thresholdG = RoomEventBus.roomShakeLevel.value,
            sessionPeakG = sessionPeakG,
        )
    }

    private fun overlayEffective(): Boolean =
        GForceDebugGate.isEffective(
            developerOptionsEnabled = DeveloperOptions.isEnabled(this),
            overlayToggle = AppSettings.gforceDebugEnabled,
        )

    private fun publishDebug(nowMs: Long) {
        if (nowMs - lastBusEmitAt >= BUS_EMIT_INTERVAL_MS) {
            SosEventBus.emitDebugSnapshot(aggregator.snapshot())
            lastBusEmitAt = nowMs
        }
        val effective = overlayEffective()
        val overlayChanged = effective != lastOverlayEffective
        val due = lastNotifyAt == 0L || nowMs - lastNotifyAt >= NOTIFICATION_INTERVAL_MS
        val shouldNotify = overlayChanged || (effective && due)
        if (!shouldNotify) return
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, notification(aggregator.snapshot(), effective))
        lastNotifyAt = nowMs
        lastOverlayEffective = effective
    }

    @SuppressLint("ForegroundServiceType")
    private fun startAsForeground(): Boolean {
        val type = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ->
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            else -> 0
        }
        return runCatching {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification(aggregator.snapshot(), lastOverlayEffective),
                type,
            )
        }.onFailure {
            Log.e(TAG, "Could not start foreground monitor", it)
            stopSelf()
        }.isSuccess
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.monitor_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.monitor_channel_description)
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun notification(
        snapshot: DebugSnapshot,
        overlayEffective: Boolean,
    ): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val body = if (overlayEffective) {
            DebugNotificationText.body(snapshot.nowG, snapshot.peakG, snapshot.meanG)
        } else {
            getString(R.string.monitor_notification_body)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.monitor_notification_title))
            .setContentText(body)
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .build()
    }
}

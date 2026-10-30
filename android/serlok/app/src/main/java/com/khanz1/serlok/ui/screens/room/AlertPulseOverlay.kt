package com.khanz1.serlok.ui.screens.room

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay

/**
 * OSMDroid [Overlay] that draws three expanding, fading red concentric rings
 * around a [GeoPoint] to signal a high-magnitude SOS alert.
 *
 * Animates itself via a main-thread [Handler] at ~30 fps — no external
 * Compose animation state needed.
 */
class AlertPulseOverlay : Overlay() {

    private var alertPosition: GeoPoint? = null
    private var mapView: MapView? = null
    private var startTime: Long = 0L
    private var running = false

    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            mapView?.invalidate()
            handler.postDelayed(this, 33L) // ~30 fps
        }
    }

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.RED
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }

    // ── Public API ────────────────────────────────────────────────────────────

    fun startPulse(position: GeoPoint, mv: MapView) {
        alertPosition = position
        mapView = mv
        startTime = System.currentTimeMillis()
        running = true
        handler.post(tick)
    }

    fun stopPulse() {
        running = false
        alertPosition = null
        handler.removeCallbacks(tick)
        mapView?.invalidate()
        mapView = null
    }

    // ── Overlay.draw ──────────────────────────────────────────────────────────

    override fun draw(canvas: Canvas, mv: MapView, shadow: Boolean) {
        if (shadow || !running) return
        val position = alertPosition ?: return

        val proj = mv.projection
        val screenCenter = proj.toPixels(position, null)
        val cx = screenCenter.x.toFloat()
        val cy = screenCenter.y.toFloat()

        val elapsed = (System.currentTimeMillis() - startTime) % 2_000L
        val fraction = elapsed / 2_000f

        // Draw 3 rings offset by 1/3 period each so they appear continuous
        repeat(3) { i ->
            val offset = (fraction + i / 3f) % 1f
            val radius = offset * 140f + 30f          // 30 → 170 px
            val alpha  = ((1f - offset) * 220).toInt()
            ringPaint.alpha = alpha
            canvas.drawCircle(cx, cy, radius, ringPaint)
        }
    }
}

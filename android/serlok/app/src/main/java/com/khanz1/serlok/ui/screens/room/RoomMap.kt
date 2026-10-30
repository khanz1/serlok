package com.khanz1.serlok.ui.screens.room

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.khanz1.serlok.R
import com.khanz1.serlok.data.model.LocationData
import com.khanz1.serlok.data.model.RoomMember
import com.khanz1.serlok.service.RoomAlert
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.infowindow.InfoWindow
import kotlin.math.absoluteValue

@Composable
internal fun RoomMap(
    members: List<RoomMember>,
    locations: Map<String, LocationData>,
    selfId: String,
    focusedUserId: String?,
    alertingUserId: String?,
    onMarkerClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    incidentPins: List<RoomAlert> = emptyList(),
    selectedIncidentId: String? = null,
    incidentCamera: RoomAlert? = null,
    onIncidentClick: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val mapView = remember {
        Configuration.getInstance().userAgentValue = context.packageName
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            minZoomLevel = 4.0
            maxZoomLevel = 20.0
            controller.setZoom(12.0)
            controller.setCenter(GeoPoint(-6.2, 106.816666))
        }
    }
    val incidentWindow = remember(mapView) {
        IncidentInfoWindow(mapView)
    }

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_DESTROY -> mapView.onDetach()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(incidentCamera, focusedUserId, locations) {
        val incidentLat = incidentCamera?.latitude
        val incidentLng = incidentCamera?.longitude
        if (incidentLat != null && incidentLng != null) {
            mapView.controller.setZoom(17.5)
            mapView.controller.setCenter(GeoPoint(incidentLat, incidentLng))
            return@LaunchedEffect
        }
        val targetId = focusedUserId ?: selfId
        locations[targetId]?.takeIf { it.latitude != 0.0 || it.longitude != 0.0 }?.let {
            mapView.controller.setZoom(17.5)
            mapView.controller.setCenter(GeoPoint(it.latitude, it.longitude))
        }
    }

    Box(modifier) {
        androidx.compose.ui.viewinterop.AndroidView(
            factory = { mapView },
            update = { map ->
                InfoWindow.closeAllInfoWindowsOn(map)
                incidentWindow.selfId = selfId
                map.overlays.clear()
                members.forEach { member ->
                    val location = locations[member.userId] ?: return@forEach
                    if (location.latitude == 0.0 && location.longitude == 0.0) return@forEach
                    val border = when {
                        member.userId == alertingUserId -> Color.rgb(217, 74, 74)
                        member.userId == selfId -> Color.rgb(11, 118, 110)
                        else -> Color.WHITE
                    }
                    map.overlays += Marker(map).apply {
                        position = GeoPoint(location.latitude, location.longitude)
                        setInfoWindow(null)
                        icon = BitmapDrawable(
                            map.resources,
                            localMarker(member.userId, member.name, border),
                        )
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        setOnMarkerClickListener { _, _ ->
                            onMarkerClick(member.userId)
                            true
                        }
                    }
                }
                incidentPins.forEach { incident ->
                    val latitude = incident.latitude ?: return@forEach
                    val longitude = incident.longitude ?: return@forEach
                    val selected = incident.incidentId == selectedIncidentId
                    val marker = Marker(map).apply {
                        position = GeoPoint(latitude, longitude)
                        relatedObject = incident
                        setInfoWindow(incidentWindow)
                        icon = BitmapDrawable(
                            map.resources,
                            incidentMarker(incident.name, selected),
                        )
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        setOnMarkerClickListener { _, _ ->
                            onIncidentClick(incident.incidentId)
                            true
                        }
                    }
                    map.overlays += marker
                    if (selected) {
                        marker.showInfoWindow()
                    }
                }
                map.invalidate()
            },
            modifier = Modifier.fillMaxSize(),
        )
        if (locations[selfId] == null) {
            Card(
                modifier = Modifier.align(Alignment.Center),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                ),
            ) {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text(
                        stringResource(R.string.room_location_waiting),
                        modifier = Modifier.padding(start = 10.dp),
                    )
                }
            }
        }
    }
}

private fun localMarker(seed: String, name: String, borderColor: Int): Bitmap {
    val size = 128
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val palette = intArrayOf(
        Color.rgb(210, 240, 236),
        Color.rgb(255, 224, 204),
        Color.rgb(232, 222, 248),
        Color.rgb(220, 231, 255),
    )
    val fill = palette[seed.hashCode().absoluteValue % palette.size]
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.color = borderColor
    canvas.drawCircle(64f, 58f, 52f, paint)
    paint.color = fill
    canvas.drawCircle(64f, 58f, 44f, paint)
    paint.color = Color.rgb(16, 33, 38)
    paint.textSize = 34f
    paint.textAlign = Paint.Align.CENTER
    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    canvas.drawText(memberInitials(name), 64f, 70f, paint)
    paint.color = borderColor
    val path = android.graphics.Path().apply {
        moveTo(48f, 96f)
        lineTo(64f, 124f)
        lineTo(80f, 96f)
        close()
    }
    canvas.drawPath(path, paint)
    return bitmap
}

internal fun memberInitials(name: String): String {
    return name.trim().split(Regex("\\s+"))
        .take(2)
        .joinToString("") { it.take(1).uppercase() }
        .ifBlank { "S" }
}

private fun incidentMarker(name: String, selected: Boolean): Bitmap {
    val size = if (selected) 132 else 104
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val coral = Color.rgb(217, 74, 74)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val cx = size / 2f
    val cy = size / 2f - 8f
    val radius = if (selected) 46f else 36f
    if (selected) {
        paint.color = Color.WHITE
        canvas.drawCircle(cx, cy, radius + 8f, paint)
    }
    paint.color = coral
    canvas.drawCircle(cx, cy, radius, paint)
    val tip = android.graphics.Path().apply {
        moveTo(cx - 14f, cy + radius - 10f)
        lineTo(cx, size - 4f)
        lineTo(cx + 14f, cy + radius - 10f)
        close()
    }
    canvas.drawPath(tip, paint)
    paint.color = Color.WHITE
    paint.textSize = if (selected) 32f else 26f
    paint.textAlign = Paint.Align.CENTER
    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    val textY = cy - (paint.descent() + paint.ascent()) / 2f
    canvas.drawText(memberInitials(name), cx, textY, paint)
    return bitmap
}

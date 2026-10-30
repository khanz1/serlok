package com.khanz1.serlok.ui.screens.room

import android.view.View
import android.widget.TextView
import com.khanz1.serlok.R
import com.khanz1.serlok.service.RoomAlert
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.infowindow.InfoWindow

internal class IncidentInfoWindow(
    mapView: MapView,
) : InfoWindow(R.layout.incident_info_window, mapView) {
    var selfId: String = ""

    override fun onOpen(item: Any?) {
        val marker = item as? Marker ?: return
        val incident = marker.relatedObject as? RoomAlert ?: return
        val root = mView
        root.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        val initials = root.findViewById<TextView>(R.id.incident_popup_initials)
        val name = root.findViewById<TextView>(R.id.incident_popup_name)
        val you = root.findViewById<TextView>(R.id.incident_popup_you)
        val magnitude = root.findViewById<TextView>(R.id.incident_popup_magnitude)
        initials.text = memberInitials(incident.name)
        name.text = incident.name
        you.visibility = if (incident.userId == selfId) View.VISIBLE else View.GONE
        magnitude.text = root.context.getString(
            R.string.alert_source_shake,
            incident.magnitudeG,
        )
    }

    override fun onClose() = Unit
}

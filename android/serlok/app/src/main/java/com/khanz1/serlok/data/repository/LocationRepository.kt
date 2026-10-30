package com.khanz1.serlok.data.repository

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.khanz1.serlok.data.model.LocationData
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class LocationRepository(private val context: Context) {

    private val client: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    /**
     * Emits location updates as a cold [Flow].
     * Requires ACCESS_FINE_LOCATION permission to be granted before collecting.
     */
    @SuppressLint("MissingPermission")
    fun observeLocation(intervalMs: Long = 5_000L): Flow<LocationData> = callbackFlow {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMs)
            .setMinUpdateDistanceMeters(1f)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { loc ->
                    trySend(
                        LocationData(
                            latitude  = loc.latitude,
                            longitude = loc.longitude,
                            accuracy  = loc.accuracy,
                            speed     = loc.speed,
                            bearing   = loc.bearing,
                            altitude  = loc.altitude,
                            timestamp = loc.time,
                        )
                    )
                }
            }
        }

        client.requestLocationUpdates(request, callback, Looper.getMainLooper())

        awaitClose {
            client.removeLocationUpdates(callback)
        }
    }
}

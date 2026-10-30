package com.khanz1.serlok.data.model

data class LocationData(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val accuracy: Float = 0f,
    val speed: Float = 0f,
    val bearing: Float = 0f,
    val altitude: Double = 0.0,
    val timestamp: Long = System.currentTimeMillis(),
)

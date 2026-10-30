package com.khanz1.serlok.data.model

data class RoomMember(
    val userId:          String,
    val deviceInstanceId:String = "",
    val name:            String,
    val email:           String = "",
    val isMaster:        Boolean,
    val manufacturer:    String = "",
    val model:           String = "",
    val androidVersion:  String = "",
)

data class RoomState(
    val code:               String,
    val masterId:           String,
    val members:            List<RoomMember>,
    val isSharing:          Boolean,
    val shakeThreshold:     Float = 3.5f,
    val locationIntervalMs: Long = 10_000L,
)

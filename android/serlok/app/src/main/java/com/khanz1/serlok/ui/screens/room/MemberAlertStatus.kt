package com.khanz1.serlok.ui.screens.room

/**
 * Single projection of a Member's Alert state.
 *
 * Replaces three overlapping flows in [RoomViewModel]:
 *   - `_alertedDeviceId`         — currently-pulsing
 *   - `_permanentAlertDevices`   — has-ever-alerted
 *   - `_strobeActive`            — full-screen flash (deleted)
 *
 * Driven by the existing `_alertedDeviceId` (10s window) +
 * `_permanentAlertDevices` (cleared only on room-leave).
 */
sealed class MemberAlertStatus {
    data object None : MemberAlertStatus()

    /** Alert fired within the last 10 seconds. UI should pulse. */
    data class Pulsing(val since: Long) : MemberAlertStatus()

    /** Member has alerted earlier in this Room session. UI shows static red border. */
    data object Persistent : MemberAlertStatus()
}

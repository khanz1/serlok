package com.khanz1.serlok.service

import com.khanz1.serlok.safety.DebugSnapshot
import com.khanz1.serlok.safety.GForceTrigger
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/** Process-local bridge. Raw sensor samples never leave the foreground service. */
object SosEventBus {
    private val _shakeEvents = MutableSharedFlow<GForceTrigger>(extraBufferCapacity = 8)
    val shakeEvents: SharedFlow<GForceTrigger> = _shakeEvents.asSharedFlow()

    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

    private val _debugSnapshot = MutableStateFlow(DebugSnapshot.empty())
    val debugSnapshot: StateFlow<DebugSnapshot> = _debugSnapshot.asStateFlow()

    private var debugCsvProvider: (() -> DebugCsvExport)? = null

    fun emitShakeEvent(trigger: GForceTrigger) {
        _shakeEvents.tryEmit(trigger)
    }

    fun setActive(active: Boolean) {
        _isActive.value = active
    }

    fun emitDebugSnapshot(snapshot: DebugSnapshot) {
        _debugSnapshot.value = snapshot
    }

    fun setDebugCsvProvider(provider: (() -> DebugCsvExport)?) {
        debugCsvProvider = provider
    }

    fun debugCsvExport(): DebugCsvExport? = debugCsvProvider?.invoke()
}

data class DebugCsvExport(
    val fileName: String,
    val csv: String,
)

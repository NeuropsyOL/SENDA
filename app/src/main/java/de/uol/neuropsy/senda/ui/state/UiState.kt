// UiState.kt
package de.uol.neuropsy.senda.ui.state

import de.uol.neuropsy.senda.sensor.MovellaBridge

sealed class UiState {
    object Idle : UiState()
    object Scanning : UiState()
    data class DevicesDiscovered(
        val sensorNames: List<String>
    ) : UiState()
    data class Syncing(val progress: Int) : UiState()
    object Streaming : UiState()
    object Starting : UiState()
    object Stopping : UiState()
    data class Error(val message: String) : UiState()
}
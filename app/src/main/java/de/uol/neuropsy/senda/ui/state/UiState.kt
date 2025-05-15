// UiState.kt
package de.uol.neuropsy.senda.ui.state

import de.uol.neuropsy.senda.sensor.MovellaBridge

sealed class UiState {
    object Idle : UiState()
    object Scanning : UiState()
    data class DevicesDiscovered(
        val onboardSensors: List<String>,
        val movellaDevices: List<MovellaBridge>
    ) : UiState()
    data class Syncing(val progress: Int) : UiState()
    object Streaming : UiState()
    data class Error(val message: String) : UiState()
}
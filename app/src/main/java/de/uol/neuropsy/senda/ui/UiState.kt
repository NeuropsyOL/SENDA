// UiState.kt
package de.uol.neuropsy.senda.ui

import de.uol.neuropsy.senda.MovellaBridge

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
    data class ServiceEvent(val type: ServiceEventType, val error: String? = null) : UiState()
}

// Service event types for broadcasting from the service (future step)
enum class ServiceEventType { Started, Stopped, Failed }

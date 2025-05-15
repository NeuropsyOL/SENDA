// MainViewModel.kt (with SensorRepository integration)
package de.uol.neuropsy.senda.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import de.uol.neuropsy.senda.data.SensorRepositoryImpl
import de.uol.neuropsy.senda.sensor.MovellaBridge
import de.uol.neuropsy.senda.service.ServiceEvent
import de.uol.neuropsy.senda.ui.state.UiState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch

class MainViewModelFactory(
    private val app: Application,
    private val repository: SensorRepositoryImpl
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(app, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class MainViewModel(application: Application, private val repository: SensorRepositoryImpl) : AndroidViewModel(application) {
    private var streamingJob : Job?=null

    // Holds the fully initialized MovellaBridge instances from the last scan
    private val discoveredBridges = mutableListOf<MovellaBridge>()

    // UI state
    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        // Emit initial onboard sensors
        val onboard = repository.getAvailableOnboardSensors()
        _uiState.value = UiState.DevicesDiscovered(onboard, emptyList())
    }

    /**
     * Start BLE scan using repository
     */
    /** Populates discoveredBridges and emits a combined state */
    fun startScan() {
        viewModelScope.launch {
            repository.scanForMovellaDevices()
                .onStart { _uiState.value = UiState.Scanning }
                .collect { bridges ->
                    // Cache for later sync
                    discoveredBridges.clear()
                    discoveredBridges.addAll(bridges)
                    // Emit both onboard & movella lists
                    val onboard = repository.getAvailableOnboardSensors()
                    _uiState.value = UiState.DevicesDiscovered(
                        onboardSensors  = onboard,
                        movellaDevices  = bridges
                    )
                }
        }
    }


    fun startSelectedSensors(selected: List<String>) {
        // Cancel any in-flight sync/stream
        streamingJob?.cancel()
        streamingJob = viewModelScope.launch {
            // 1) pick the bridges they tapped
            val bridgesToSync = discoveredBridges.filter { selected.contains(it.displayName) }.mapNotNull { it.handle }

            // 2) sync if needed
            if (bridgesToSync.size >= 2) {
                repository
                    .syncMovellaDevices(bridgesToSync)
                    .onEach { progress : Int ->
                        _uiState.value = UiState.Syncing(progress)
                    }
                    .collect{} // suspends until sync done or cancelled
            }

            // 3) now fire up the LSLService
            repository
                .startStreaming(selected)
                .onStart {
                    _uiState.value = UiState.Streaming
                }
                .collect { success ->
                    if (!success) {
                        _uiState.value = UiState.Error("Failed to start streaming")
                    }
                }
        }
    }

    /** Cancels sync/stream and stops the service */
    fun stopStreaming() {
        streamingJob?.cancel()
        streamingJob = null
        repository.stopStreaming()
        _uiState.value = UiState.Idle
    }

    /**
     * Handle LSLService events
     */
    fun onServiceEvent(event: ServiceEvent) {
        when (event) {
            ServiceEvent.Started -> _uiState.value = UiState.Streaming
            ServiceEvent.Stopped -> _uiState.value = UiState.Idle
            is ServiceEvent.Failed -> _uiState.value = UiState.Error(event.error)
        }
    }

    fun clearError(){
        _uiState.value = UiState.Idle
    }
}

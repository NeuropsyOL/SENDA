// MainViewModel.kt (with SensorRepository integration)
package de.uol.neuropsy.senda

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xsens.dot.android.sdk.models.DotDevice
import de.uol.neuropsy.senda.data.SensorRepository
import de.uol.neuropsy.senda.ui.UiState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private var syncJob: Job? = null
    private var streamingJob : Job?=null
    private val context: Context = application.applicationContext
    private val repository: SensorRepository = SensorRepositoryImpl(context)
    // Holds the fully initialized MovellaBridge instances from the last scan
    private val discoveredBridges = mutableListOf<MovellaBridge>()

    // Unified UI state
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

    /** Sync if >=2 Dots, then start the service—cancellable via stopStreaming() */
    fun startSelectedSensors(selected: List<String>) {
        // Cancel any in-flight sync/stream
        streamingJob?.cancel()

        streamingJob = viewModelScope.launch {
            // 1) pick the bridges they tapped
            val bridgesToSync = discoveredBridges.filter { selected.contains(it.displayName) }
            val dotHandles   = bridgesToSync.mapNotNull { it.handle }

            // 2) sync if needed
            if (dotHandles.size >= 2) {
                repository
                    .syncMovellaDevices(dotHandles)
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

    /** Cancels sync/stream and stops the service */
    fun stopStreaming() {
        streamingJob?.cancel()
        streamingJob = null
        repository.stopStreaming()
        _uiState.value = UiState.Idle
    }

}

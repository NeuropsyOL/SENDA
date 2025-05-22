// MainViewModel.kt (with SensorRepository integration)
package de.uol.neuropsy.senda.ui.main

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import de.uol.neuropsy.senda.data.SensorRepositoryImpl
import de.uol.neuropsy.senda.service.LSLServiceClient
import de.uol.neuropsy.senda.service.LSLServiceClientImpl
import de.uol.neuropsy.senda.service.ServiceEvent
import de.uol.neuropsy.senda.ui.state.UiState
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onCompletion
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
    private val serviceClient : LSLServiceClient = LSLServiceClientImpl(application)
    // UI state
    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        // Emit initial onboard sensors
        val onboard = repository.getAvailableOnboardSensors()
        _uiState.value = UiState.DevicesDiscovered(onboard.map { it.name })
    }

    /**
     * Start BLE scan using repository
     */
    /** Populates discoveredBridges and emits a combined state */
    fun startScan() {
        viewModelScope.launch {
            repository.scanForMovellaDevices()
                .onStart { _uiState.value = UiState.Scanning }
                .onCompletion { _uiState.value=UiState.Idle }
                .collect { devices ->
                    // Emit both onboard & movella lists
                    val onboard = repository.getAvailableOnboardSensors()
                    _uiState.value = UiState.DevicesDiscovered(
                        onboard.map { it.name }+devices.map { it.name }
                    )
                }
        }
    }

    fun startSelectedSensors(selected: List<String>) {
        _uiState.value=UiState.Starting
        // Cancel any in-flight sync/stream
        streamingJob?.cancel()
        streamingJob = viewModelScope.launch {
            Log.e("MainViewModel","Names to start $selected")
            val configsToStart=repository.getAvailableSensors().filter { selected.contains(it.name) }
            Log.e("MainViewModel","Cached configs: ${repository.getAvailableSensors().map { it.name }}")
            Log.e("MainViewModel","Configs to start ${configsToStart.map { it.name }}")
            try {
                serviceClient.bindAndStart(configsToStart)
            }
            catch(_:TimeoutCancellationException){
                _uiState.value=UiState.Error("Sensor initialisation timed out!")
            }
            _uiState.value = UiState.Streaming
            // Pick the bridges they tapped
            //val bridgesToSync = discoveredBridges.filter { selected.contains(it) }.mapNotNull { it.handle }

            // Sync if needed
            //if (bridgesToSync.size >= 2) {
            //    repository
            //        .syncMovellaDevices(bridgesToSync)
            //        .onCompletion {  }
            //        .collect{ status : SyncStatus ->
            //                when(status){
            //                    is SyncStatus.Progress ->  _uiState.value = UiState.Syncing(status.progress)
            //                    is SyncStatus.Success -> {}
            //                    is SyncStatus.Failed -> {_uiState.value=UiState.Error("Could not sync movella devices")}
            //                }
            //        } //
            //}

//            // Now fire up the LSLService
//            repository
//                .startStreaming(selected)
//                .onStart {
//                    // Hack: Start the Movella bridges in the VM as the repository does not keep track
//                    // of the discovered bridges. This should be done in the service
//                    //discoveredBridges.filter { selected.contains(it.displayName) }.forEach{it.Start()}
//                    _uiState.value = UiState.Streaming
//                }
//                .collect { success ->
//                    if (!success) {
//                        _uiState.value = UiState.Error("Failed to start streaming")
//                    }
//                }
        }
    }

    /** Cancels sync/stream and stops the service */
    fun stopStreaming() {
        _uiState.value=UiState.Stopping
        streamingJob?.cancel()
        streamingJob = null
        serviceClient.stop()
        repository.stopStreaming()
        serviceClient.unbind()
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
            else -> true
        }
    }

    fun clearError(){
        _uiState.value = UiState.Idle
    }
}

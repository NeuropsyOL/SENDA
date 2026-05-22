package de.uol.neuropsy.senda.service

import android.app.Application
import android.content.*
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat
import de.uol.neuropsy.senda.sensor.SensorConfig
import de.uol.neuropsy.senda.ui.state.UiState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

/**
 * Interface defining a client of LSLService that manages binding
 * and controls sensor streaming lifecycle.
 */
interface LSLServiceClient {
    /**
     * Binds to LSLService (if not already), configures sensors, and starts streaming.
     */
    suspend fun bindAndStart(configs: List<SensorConfig>): ServiceEvent

    /**
     * Stops streaming but keeps the service bound.
     */
    fun stop()

    /**
     * Unbinds from LSLService and releases resources.
     */
    fun unbind()
}

/**
 * Concrete implementation of LSLServiceClient that handles
 * ServiceConnection and lifecycle of LSLService.
 */
class LSLServiceClientImpl(
    private val application: Application
) : LSLServiceClient {
    private val startStopMutex = Mutex()
    private var service: LSLService? = null
    private var boundDeferred: CompletableDeferred<Unit>? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            service = (binder as LSLService.LocalBinder).getService()
            boundDeferred?.complete(Unit)
        }

        override fun onServiceDisconnected(name: ComponentName) {
            service = null
        }
    }

    override suspend fun bindAndStart(configs: List<SensorConfig>) = startStopMutex.withLock {
        // Bind only once
        if (service == null) {
            boundDeferred = CompletableDeferred<Unit>().apply {
                invokeOnCompletion { if (isCancelled) cancel() }
            }
            try {
                // Compute the minimum FGS type required for the selected sensors.
                // connectedDevice is always included (no runtime permission needed).
                // microphone/location are added only when those sensor types are selected,
                // avoiding the SecurityException that fires when the matching runtime
                // permission (RECORD_AUDIO / ACCESS_FINE_LOCATION) hasn't been granted yet.
                val intent = Intent(application, LSLService::class.java).apply {
                    putExtra(LSLService.EXTRA_FGS_TYPE, fgsTypeFor(configs))
                }
                ContextCompat.startForegroundService(application, intent)
                application.bindService(intent, connection, Context.BIND_AUTO_CREATE)
                boundDeferred!!.await()
            } catch (e: Throwable) {
                unbind()
                throw e
            }
        }

        try {
            withTimeout(15_000L) {
                service!!.configureSensors(configs)
                service!!.events.first { it is ServiceEvent.Configured }
            }
            service!!.startStreaming()
            service!!.events.first { it is ServiceEvent.Started }
        } catch (e: Throwable){
            if (service!=null) {
                unbind()
            }
            throw e
        }
    }

    override fun stop() {
        service?.stopStreaming()
    }

    override fun unbind() {
        application.unbindService(connection)
        service = null
    }

    companion object {
        /** Computes the minimum foreground service type bitmask for the given sensor configs. */
        fun fgsTypeFor(configs: List<SensorConfig>): Int {
            // connectedDevice requires no runtime permission — always safe as the base.
            var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            for (cfg in configs) {
                when (cfg) {
                    is SensorConfig.Audio,
                    is SensorConfig.AudioClassification ->
                        type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                    is SensorConfig.Location ->
                        type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                    else -> { /* onboard / Movella: connectedDevice already set */ }
                }
            }
            return type
        }
    }
}

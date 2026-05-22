// SensorRepositoryImpl.kt
package de.uol.neuropsy.senda.data

import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.xsens.dot.android.sdk.interfaces.DotSyncCallback
import com.xsens.dot.android.sdk.models.DotDevice
import com.xsens.dot.android.sdk.models.DotSyncManager
import com.xsens.dot.android.sdk.utils.DotScanner
import de.uol.neuropsy.senda.domain.SensorRepository
import de.uol.neuropsy.senda.sensor.MovellaMetadata
import de.uol.neuropsy.senda.sensor.SensorConfig
import de.uol.neuropsy.senda.service.LSLService
import de.uol.neuropsy.senda.utils.Utils.SENSOR_NAMES
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.SealedObject

sealed class SyncStatus {
    data class Progress(val progress : Int) : SyncStatus()
    class Success : SyncStatus()
    class Failed : SyncStatus()
    class Stopped: SyncStatus()
}

/**
 * Concrete implementation of SensorRepository using Android sensors,
 * Movella DOT SDK, and LSLService for streaming.
 */
class SensorRepositoryImpl(private val context: Context) : SensorRepository {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val availableSensors = mutableListOf<SensorConfig>()

    override fun getAvailableOnboardSensors(): List<SensorConfig> {
        val available = mutableListOf<SensorConfig>()
        SENSOR_NAMES.forEach { (type, name) ->
            sensorManager.getDefaultSensor(type)
                ?.let { available.add(SensorConfig.Onboard(name = name, type = type)) }
        }
        available.add(SensorConfig.Audio)
        available.add(SensorConfig.AudioClassification)
        available.add(SensorConfig.Location)
        available.forEach { newSensor->if(availableSensors.none { oldSensor->oldSensor.name==newSensor.name }) availableSensors.add(newSensor) }
        Log.e("SensorRepositoryImpl","I have ${available.map { it.name }} and cached ${availableSensors.map { it.name }}")
        return available
    }

    suspend fun scanForMovellaDevicesOnce(
        timeoutMs: Long = 5_000L
    ): List<SensorConfig.Movella> = coroutineScope {
        val devices = ConcurrentHashMap.newKeySet<SensorConfig.Movella>()
        val scanner = DotScanner(context) { bt, _ ->
            launch {
                val deviceName = MovellaMetadata.getDeviceName(context, bt)
                if (devices.none { it.address == bt.address }) {
                    devices.add(SensorConfig.Movella(bt.address, deviceName))
                }
            }}.apply {
            setScanMode(ScanSettings.SCAN_MODE_BALANCED)
            startScan()
        }

        // Wait either until timeout or the scope is canceled
        try {
            withTimeout(timeoutMs) {
                // just suspend until timeout; callbacks keep adding to 'names'
                suspendCancellableCoroutine<Unit> { /* no-op */ }
            }
        } catch (_: TimeoutCancellationException) {
            // expected path: timeout expired
        } finally {
            scanner.stopScan()
        }
        devices.toList()
    }


    override fun scanForMovellaDevices(): Flow<List<SensorConfig.Movella>> = callbackFlow {
        val devices = mutableListOf<SensorConfig.Movella>()
        val scanner = DotScanner(context) { bt, _ ->
            // launch a coroutine to fetch the name
            launch {
                val deviceName = MovellaMetadata.getDeviceName(context, bt)
                if (devices.none { it.address == bt.address }) {
                    devices.add(SensorConfig.Movella(bt.address,deviceName))
                    if(!availableSensors.contains(SensorConfig.Movella(bt.address,deviceName)))
                        availableSensors.add(SensorConfig.Movella(bt.address,deviceName))
                    trySend(devices)
                }
            }
        }.apply {
            setScanMode(ScanSettings.SCAN_MODE_BALANCED)
            startScan()
            delay(5000)
            close()
        }
        awaitClose { scanner.stopScan() }
    }

    fun getAvailableSensors() : List<SensorConfig>{
        return availableSensors
    }
}

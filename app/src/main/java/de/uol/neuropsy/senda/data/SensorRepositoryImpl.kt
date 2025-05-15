// SensorRepositoryImpl.kt
package de.uol.neuropsy.senda.data

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorManager
import android.bluetooth.le.ScanSettings
import androidx.core.content.ContextCompat
import com.xsens.dot.android.sdk.interfaces.DotScannerCallback
import com.xsens.dot.android.sdk.interfaces.DotSyncCallback
import com.xsens.dot.android.sdk.models.DotDevice
import com.xsens.dot.android.sdk.models.DotSyncManager
import com.xsens.dot.android.sdk.utils.DotScanner
import de.uol.neuropsy.senda.service.LSLService
import de.uol.neuropsy.senda.domain.SensorRepository
import de.uol.neuropsy.senda.sensor.MovellaBridge
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion

/**
 * Concrete implementation of SensorRepository using Android sensors,
 * Xsens DOT SDK, and LSLService for streaming.
 */
class SensorRepositoryImpl(private val context: Context) : SensorRepository {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    override fun getAvailableOnboardSensors(): List<String> {
        val available = mutableListOf<String>()
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { available.add("Accelerometer") }
        sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)?.let { available.add("Light") }
        sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)?.let { available.add("Proximity") }
        sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)?.let { available.add("Gravity") }
        sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)?.let { available.add("Linear Acceleration") }
        sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)?.let { available.add("Rotation Vector") }
        sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)?.let { available.add("Step Count") }
        sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)?.let { available.add("Gyroscope") }
        available.add("Audio")
        available.add("Audio classifier")
        available.add("Location")
        return available
    }

    override fun scanForMovellaDevices(): Flow<List<MovellaBridge>> = callbackFlow {
        val bridges = mutableListOf<MovellaBridge>()
        val scanner = DotScanner(context, object : DotScannerCallback {
            override fun onDotScanned(bt: BluetoothDevice, rssi: Int) {
                // Create a bridge that only calls back to us when fully init-done
                MovellaBridge(context, bt, object : MovellaBridge.MovellaInitListener {
                    override fun onMovellaInitDone(bridge: MovellaBridge) {
                        // When init finishes, add to our list and emit
                        if (!bridges.contains(bridge)) {
                            bridges.add(bridge)
                            trySend(bridges.toList())
                        }
                    }
                })
                // we do NOT emit here—only in onDotInitDone
            }
        }).apply {
            setScanMode(ScanSettings.SCAN_MODE_BALANCED)
            startScan()
        }

              // stop the scan when the flow collector is done
        awaitClose {
            scanner.stopScan()
        }
    }

    override fun syncMovellaDevices(devices: List<DotDevice>): Flow<Int> = callbackFlow {
        if (devices.isEmpty()) {
            close()
            return@callbackFlow
        }
        // Set the first device as the root
        devices[0].isRootDevice = true
        val syncCallback = object : DotSyncCallback {
            override fun onSyncingStarted(deviceAddress: String?, isRoot: Boolean, count: Int) {
                trySend(0)
            }
            override fun onSyncingProgress(progress: Int, total: Int) {
                trySend(progress)
            }
            override fun onSyncingResult(deviceAddress: String?, success: Boolean, reason: Int) {
                // No-op
            }
            override fun onSyncingDone(results: HashMap<String, Boolean>, allSuccessful: Boolean, code: Int) {
                close()
            }
            override fun onSyncingStopped(deviceAddress: String?, isSuccess: Boolean, code: Int) {
                close()
            }
        }
        // The Movella SDK requires the callback at construction via getInstance(callback)
        DotSyncManager.getInstance(syncCallback).startSyncing(ArrayList(devices), 1)
        awaitClose {
            DotSyncManager.getInstance(syncCallback).stopSyncing()
        }
    }

    override fun startStreaming(selectedSensors: List<String>): Flow<Boolean> = flow {
        // Start LSLService with extras
        val intent = Intent(context, LSLService::class.java).apply {
            selectedSensors.forEach { putExtra(it, true) }
        }
        ContextCompat.startForegroundService(context, intent)
        emit(true)
    }.onCompletion {
        // nothing
    }

    override fun stopStreaming() {
        val intent = Intent(context, LSLService::class.java)
        context.stopService(intent)
    }
}

// SensorRepository.kt
package de.uol.neuropsy.senda.domain

import com.xsens.dot.android.sdk.models.DotDevice
import de.uol.neuropsy.senda.data.SyncStatus
import de.uol.neuropsy.senda.sensor.MovellaBridge
import de.uol.neuropsy.senda.sensor.SensorConfig
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface encapsulating all sensor and device operations.
 */
interface SensorRepository {
    /** Returns the list of available onboard sensor names. */
    fun getAvailableOnboardSensors(): List<SensorConfig>

    /**
     * Scans for Movella BLE devices and emits the current list of device names.
     * Collection is infinite; cancel to stop.
     */
    fun scanForMovellaDevices(): Flow<List<SensorConfig.Movella>>

    /**
     * Synchronizes the given Movella devices by address, emitting progress 0..100.
     */
    fun syncMovellaDevices(devices: List<DotDevice>): Flow<SyncStatus>

    /**
     * Starts streaming data for the selected sensors; emits true on success, false on failure.
     */
    fun startStreaming(selectedSensors: List<String>): Flow<Boolean>

    /**
     * Stops the current streaming session.
     */
    fun stopStreaming()
}

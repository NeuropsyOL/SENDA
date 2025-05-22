package de.uol.neuropsy.senda.sensor

import android.bluetooth.BluetoothDevice
import android.content.Context
import com.xsens.dot.android.sdk.events.DotData
import com.xsens.dot.android.sdk.interfaces.DotDeviceCallback
import com.xsens.dot.android.sdk.models.DotDevice
import com.xsens.dot.android.sdk.models.FilterProfileInfo
import com.xsens.dot.android.sdk.settings.DotSettingsManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import java.util.ArrayList
import java.util.concurrent.ConcurrentHashMap

object MovellaMetadata {
    private val nameCache   = ConcurrentHashMap<String, String>()
    private val mutexes     = ConcurrentHashMap<String, Mutex>()

    /**
     * Connects to the DotDevice, waits for init, reads tag, then disconnects and waits for
     * the disconnect to complete.
     */
    suspend fun getDeviceName(
        context: Context,
        device: BluetoothDevice,
        timeoutMs: Long = 5_000L
    ): String = coroutineScope {
        // fast-path if we already fetched it
        nameCache[device.address]?.let { return@coroutineScope it }

        // serialize access per device
        val mutex = mutexes.getOrPut(device.address) { Mutex() }
        val tag = mutex.withLock {
            // re-check inside the lock
            nameCache[device.address]?.let { return@withLock it }

            // prepare to wait for init & disconnect events
            val initDeferred       = CompletableDeferred<Unit>()
            val disconnectDeferred = CompletableDeferred<Unit>()

            val callback = object : DotDeviceCallback {
                override fun onDotInitDone(address: String?) {
                    if (address == device.address) initDeferred.complete(Unit)
                }
                override fun onDotConnectionChanged(address: String?, status: Int) {
                    if (address == device.address &&
                        status == DotDevice.CONN_STATE_DISCONNECTED
                    ) disconnectDeferred.complete(Unit)
                }
                // all other callbacks no-op
                override fun onDotServicesDiscovered(p0: String?, p1: Int) {}
                override fun onDotFirmwareVersionRead(p0: String?, p1: String?) {}
                override fun onDotTagChanged(p0: String?, p1: String?) {}
                override fun onDotBatteryChanged(p0: String?, p1: Int, p2: Int) {}
                override fun onDotDataChanged(p0: String?, p1: DotData?) {}
                override fun onDotButtonClicked(p0: String?, p1: Long) {}
                override fun onDotPowerSavingTriggered(p0: String?) {}
                override fun onReadRemoteRssi(p0: String?, p1: Int) {}
                override fun onDotOutputRateUpdate(p0: String?, p1: Int) {}
                override fun onDotFilterProfileUpdate(p0: String?, p1: Int) {}
                override fun onDotGetFilterProfileInfo(
                    p0: String?, p1: ArrayList<FilterProfileInfo>?
                ) {}
                override fun onSyncStatusUpdate(p0: String?, p1: Boolean) {}
            }
            // connect and wait for init
            val dot = DotDevice(context, device, callback)
            dot.connect()
            withTimeout(timeoutMs) {
                initDeferred.await()
            }

            // read the tag
            val discoveredTag = dot.tag
            nameCache[device.address] = discoveredTag

            // disconnect and wait for it
            dot.disconnect()
            withTimeout(timeoutMs) {
                disconnectDeferred.await()
            }

            // return from withLock
            discoveredTag
        }

        // return from coroutineScope
        tag
    }
}

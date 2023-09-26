package de.uol.neuropsy.senda.utils

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.os.Looper
import androidx.core.app.ActivityCompat


/**
 * This class is for some additional feature, such as: check Bluetooth adapter, check location premission...etc.
 */
object Utils {
    /**
     * Check the current thread is main thread or background thread.
     *
     * @return True - If running on main thread
     */
    val isMainThread: Boolean
        get() = Looper.myLooper() == Looper.getMainLooper()

    @JvmStatic
    fun SimpleSensorType(sensorType: Int): String? {
        val mSensorMap : HashMap<Int, String> = hashMapOf(
        Sensor.TYPE_ACCELEROMETER to "Accelerometer",
        Sensor.TYPE_PROXIMITY to "Proximity",
        Sensor.TYPE_GRAVITY to "Gravity",
        Sensor.TYPE_LINEAR_ACCELERATION to "Linear Acceleration",
        Sensor.TYPE_ROTATION_VECTOR to "Rotation Vector",
        Sensor.TYPE_STEP_COUNTER to "Step Count",
        Sensor.TYPE_LIGHT to "Light",
        )
        return if (mSensorMap.containsKey(sensorType)) mSensorMap[sensorType] else null
    }
}
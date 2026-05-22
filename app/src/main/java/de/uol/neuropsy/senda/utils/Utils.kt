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

    fun SimpleSensorType(sensorType: Int): String? {
        return if (SENSOR_NAMES.containsKey(sensorType)) SENSOR_NAMES[sensorType] else "Unknown"
    }

    val SENSOR_NAMES = mapOf(
        Sensor.TYPE_ACCELEROMETER to "Accelerometer",
        Sensor.TYPE_PROXIMITY to "Proximity",
        Sensor.TYPE_GRAVITY to "Gravity",
        Sensor.TYPE_LINEAR_ACCELERATION to "Linear Acceleration",
        Sensor.TYPE_ROTATION_VECTOR to "Rotation Vector",
        Sensor.TYPE_STEP_COUNTER to "Step Count",
        Sensor.TYPE_LIGHT to "Light",
        Sensor.TYPE_GYROSCOPE to "Gyroscope"
    )

    val CHANNEL_COUNTS = mapOf(
        Sensor.TYPE_ACCELEROMETER          to 3,
        Sensor.TYPE_GYROSCOPE              to 3,
        Sensor.TYPE_MAGNETIC_FIELD         to 3,
        Sensor.TYPE_ROTATION_VECTOR        to 5,
        Sensor.TYPE_GRAVITY                 to 3,
        Sensor.TYPE_LINEAR_ACCELERATION     to 3,
        Sensor.TYPE_PROXIMITY               to 1,
        Sensor.TYPE_LIGHT                   to 1,
        Sensor.TYPE_PRESSURE                to 1,
        Sensor.TYPE_STEP_COUNTER           to 1
    )
    fun getChannelCount(sensor: Sensor): Int =
        CHANNEL_COUNTS[sensor.type]
            ?: throw IllegalArgumentException("Unknown sensor type: ${sensor.type}")


}
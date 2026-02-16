package de.uol.neuropsy.senda.sensor

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Represents a sensor unique id and the corresponding human readable name to be streamed by LSLService.
 * Can be either an onboard sensor (identified by type) or a Movella device
 * (identified by its Bluetooth MAC address).
 */
sealed class SensorConfig(open val name : String) : Parcelable {
    /**
     * Onboard sensors available on the device, e.g., accelerometer, gyroscope.
     * @param name a human-readable name for the onboard sensor.
     * @param type the constant the sensor manager uses to describe the sensor
     */
    @Parcelize
    data class Onboard(
        override val name: String,
        val type: Int
    ) : SensorConfig(name)

    /**
     * External Movella sensor connected via Bluetooth.
     * @param address the MAC address of the Movella device.
     * @param tag the human-readable name for the the Movella device.
     */
    @Parcelize
    data class Movella(
        val address: String,
        val tag: String
    ) : SensorConfig(tag)

    /**
     * GPS sensor
     */
    @Parcelize
    object Location : SensorConfig("Location")

    @Parcelize
    object Audio : SensorConfig("Audio")

    @Parcelize
    object AudioClassification : SensorConfig("Audio Classification")
}

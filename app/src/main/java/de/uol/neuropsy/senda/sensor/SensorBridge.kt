package de.uol.neuropsy.senda.sensor

/**
 * Interface defining an abstract sensor bridge that starts and stops
 * its corresponding sensor and LSL streaming
 * and calls all necessary os functions internally
 */
interface SensorBridge {
    /// Start streaming
    fun Start()
    /// Stop streaming
    fun Stop()
}
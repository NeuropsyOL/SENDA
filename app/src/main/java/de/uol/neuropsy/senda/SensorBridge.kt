package de.uol.neuropsy.senda

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.os.Build
import android.util.Log
import de.uol.neuropsy.senda.utils.Utils.SimpleSensorType
import edu.ucsd.sccn.LSL
import edu.ucsd.sccn.LSL.StreamInfo
import edu.ucsd.sccn.LSL.StreamOutlet
import java.io.IOException

class SensorBridge internal constructor(dataSize: Int, var mSensor: Sensor) : SensorEventListener {
    private val mStreamInfo: StreamInfo
    private var mStreamOutlet: StreamOutlet? = null

    init {
        mStreamInfo = StreamInfo(
            SimpleSensorType(mSensor.type) + " " + Build.MODEL,
            "eeg", dataSize, LSL.IRREGULAR_RATE, LSL.ChannelFormat.float32, Build.FINGERPRINT
        )
        Log.e(TAG, "Created bridge for " + mStreamInfo.name())
    }

    fun Start() {
        try {
            mStreamOutlet = StreamOutlet(mStreamInfo)
        } catch (e: IOException) {
            Log.e("SensorBridge", e.toString())
            e.printStackTrace()
        }
    }

    fun Stop() {
        mStreamOutlet!!.close()
    }

    override fun onSensorChanged(sensorEvent: SensorEvent) {
        mStreamOutlet!!.push_chunk(sensorEvent.values)
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}

    companion object {
        var TAG = SensorBridge::class.java.simpleName
    }
}
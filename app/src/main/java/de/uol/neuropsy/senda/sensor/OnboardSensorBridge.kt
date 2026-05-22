package de.uol.neuropsy.senda.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.util.Log
import de.uol.neuropsy.senda.utils.Utils.SimpleSensorType
import edu.ucsd.sccn.LSL
import edu.ucsd.sccn.LSL.StreamInfo
import edu.ucsd.sccn.LSL.StreamOutlet
import java.io.IOException

class OnboardSensorBridge internal constructor(dataSize: Int, var mSensor: Sensor, context : Context) : SensorEventListener, de.uol.neuropsy.senda.sensor.SensorBridge {
    private val mStreamInfo: StreamInfo
    private var mStreamOutlet: StreamOutlet? = null
    private val sensorManager = context.getSystemService(SensorManager::class.java)
    init {
        mSensor.stringType
        mStreamInfo = StreamInfo(
            SimpleSensorType(mSensor.type) + " " + Build.MODEL,
            mSensor.stringType.removePrefix("android.sensor."), dataSize, LSL.IRREGULAR_RATE, LSL.ChannelFormat.float32, Build.FINGERPRINT
        )
        Log.e(TAG, "Created bridge for " + mStreamInfo.name())
    }

    override fun Start() {
        sensorManager.registerListener(this,mSensor, SensorManager.SENSOR_DELAY_UI)
        try {
            mStreamOutlet = StreamOutlet(mStreamInfo)
        } catch (e: IOException) {
            Log.e("SensorBridge", e.toString())
            e.printStackTrace()
        }
    }

    override fun Stop() {
        sensorManager.unregisterListener(this)
        mStreamOutlet?.close()
        mStreamOutlet=null
    }

    override fun onSensorChanged(sensorEvent: SensorEvent) {
        mStreamOutlet?.push_chunk(sensorEvent.values)
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}

    companion object {
        var TAG = OnboardSensorBridge::class.java.simpleName
    }
}
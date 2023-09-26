package de.uol.neuropsy.senda;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.os.Build;
import android.util.Log;

import java.io.IOException;
import java.util.Random;

import de.uol.neuropsy.senda.utils.Utils;
import edu.ucsd.sccn.LSL;

public class SensorBridge implements SensorEventListener {
    static String TAG=SensorBridge.class.getSimpleName();
    private final LSL.StreamInfo mStreamInfo;
    private LSL.StreamOutlet mStreamOutlet;
    public Sensor mSensor;

    SensorBridge(int dataSize, Sensor sensor) {
        mSensor=sensor;
        mStreamInfo = new LSL.StreamInfo(Utils.SimpleSensorType(sensor.getType()) + " " + Build.MODEL,
                "eeg", dataSize, LSL.IRREGULAR_RATE, LSL.ChannelFormat.float32, Build.FINGERPRINT);
        Log.e(TAG, "Created bridge for "+mStreamInfo.name());
    }

    public void Start() {
        try {
            mStreamOutlet = new LSL.StreamOutlet(mStreamInfo);
        } catch (IOException e) {
            Log.e("SensorBridge", e.toString());
            e.printStackTrace();
        }
    }

    public void Stop() {
        mStreamOutlet.close();
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        mStreamOutlet.push_chunk(sensorEvent.values);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

}


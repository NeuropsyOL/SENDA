package com.example.aliayubkhan.senda;
import edu.ucsd.sccn.LSL;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.os.Build;
import android.util.Log;

import static com.example.aliayubkhan.senda.utils.Utils.*;

import java.io.IOException;
import java.util.Random;

public class SensorBridge implements SensorEventListener {
    static String TAG=SensorBridge.class.getSimpleName();
    private final LSL.StreamInfo mStreamInfo;
    private LSL.StreamOutlet mStreamOutlet;
    public Sensor mSensor;

    SensorBridge(int dataSize, Sensor sensor) {
        mSensor=sensor;
        mStreamInfo = new LSL.StreamInfo(SimpleSensorType(sensor.getType()) + " " + Build.MODEL,
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
        //if (mStreamOutlet.info().channel_count() != sensorEvent.values.length)
        //    throw new RuntimeException(mStreamOutlet.info().name() + ": Expected sensor channel count of " + mStreamOutlet.info().channel_count() + ". Got: " + sensorEvent.values.length);
        mStreamOutlet.push_chunk(sensorEvent.values);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    public String generate_random_String() {

        int leftLimit = 97; // letter 'a'
        int rightLimit = 122; // letter 'z'
        int targetStringLength = 10;
        Random random = new Random();
        StringBuilder buffer = new StringBuilder(targetStringLength);
        for (int i = 0; i < targetStringLength; i++) {
            int randomLimitedInt = leftLimit + (int)
                    (random.nextFloat() * (rightLimit - leftLimit + 1));
            buffer.append((char) randomLimitedInt);
        }
        return buffer.toString();
    }


}


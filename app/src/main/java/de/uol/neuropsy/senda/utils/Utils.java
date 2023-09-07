package de.uol.neuropsy.senda.utils;

import android.hardware.Sensor;

import java.util.HashMap;

public class Utils {
    static private HashMap<Integer, String> mSensorMap = new HashMap<>();

    static {
        mSensorMap.put(Sensor.TYPE_ACCELEROMETER, "Accelerometer");
        mSensorMap.put(Sensor.TYPE_PROXIMITY, "Proximity");
        mSensorMap.put(Sensor.TYPE_GRAVITY, "Gravity");
        mSensorMap.put(Sensor.TYPE_LINEAR_ACCELERATION, "Linear Acceleration");
        mSensorMap.put(Sensor.TYPE_ROTATION_VECTOR, "Rotation Vector");
        mSensorMap.put(Sensor.TYPE_STEP_COUNTER, "Step Count");
        mSensorMap.put(Sensor.TYPE_LIGHT, "Light");
    }

    static public String SimpleSensorType(int sensorType) {
        if (mSensorMap.containsKey(sensorType))
            return mSensorMap.get(sensorType);
        else
            return null;
    }
}

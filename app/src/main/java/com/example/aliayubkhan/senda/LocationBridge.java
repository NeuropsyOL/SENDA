package com.example.aliayubkhan.senda;
import edu.ucsd.sccn.LSL;
import android.os.Build;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.util.Log;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;

import java.io.IOException;
import java.util.Random;

public class LocationBridge {


    // GoogleApiClient instance to connect to Google Play Services
    private FusedLocationProviderClient mlocationProviderClient;
    private LocationRequest mlocationRequest;
    private LocationCallback mlocationCallback;

    private LSL.StreamOutlet mStreamOutlet;

    LocationBridge() {
        LSL.StreamInfo mStreamInfo = new LSL.StreamInfo("Location" + " " + Build.MODEL + generate_random_String(),
                "eeg", 2, LSL.IRREGULAR_RATE, LSL.ChannelFormat.float32, Build.FINGERPRINT);
        try {
            mStreamOutlet = new LSL.StreamOutlet(mStreamInfo);
        } catch (IOException e) {
            Log.e("LocationBridge", e.toString());
            e.printStackTrace();
        }
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
        String generatedString = buffer.toString();
        return generatedString;
    }

}
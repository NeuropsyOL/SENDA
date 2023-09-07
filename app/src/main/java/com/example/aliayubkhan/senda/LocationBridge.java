package com.example.aliayubkhan.senda;

import android.content.Context;
import android.location.Location;
import android.os.Build;
import android.util.Log;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;

import java.io.IOException;
import java.util.Random;

import edu.ucsd.sccn.LSL;

public class LocationBridge implements LocationListener {


    // GoogleApiClient instance to connect to Google Play Services
    private final FusedLocationProviderClient mlocationProviderClient;
    private final LocationRequest mlocationRequest;
    private final LocationCallback mlocationCallback;

    private LSL.StreamOutlet mStreamOutlet;

    LocationBridge(Context context) {
        LSL.StreamInfo mStreamInfo = new LSL.StreamInfo("Location" + " " + Build.MODEL,
                "eeg", 4, LSL.IRREGULAR_RATE, LSL.ChannelFormat.float32, Build.FINGERPRINT);
        try {
            mStreamOutlet = new LSL.StreamOutlet(mStreamInfo);
        } catch (IOException e) {
            Log.e("LocationBridge", e.toString());
            e.printStackTrace();
        }
        if(context==null)
           Log.e("LocationBridge","Context is null!");
        mlocationProviderClient = LocationServices.getFusedLocationProviderClient(context);
        mlocationRequest = LocationRequest.create();
        mlocationRequest.setInterval(1000);
        mlocationRequest.setFastestInterval(500);
        mlocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        mlocationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                // Handle the received location updates
                if (locationResult != null) {
                    Location location = locationResult.getLastLocation();
                    if (location != null) {
                        double[] loc = {location.getLatitude(), location.getLatitude(), location.getAltitude(),location.getAccuracy()};
                        mStreamOutlet.push_sample(loc);
                        Log.i("LocationBridge", location.getLatitude() +" "+
                                location.getLongitude());
                    }
                }
            }
        };
    }
    void Start() {
        Log.i("LocationBridge","Start()");
        mlocationProviderClient.requestLocationUpdates(mlocationRequest, mlocationCallback, null);
    }

    void Stop() {
        Log.i("LocationBridge","Stop()");
        mlocationProviderClient.removeLocationUpdates(mlocationCallback);
        mStreamOutlet.close();
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

    @Override
    public void onLocationChanged(Location location) {

    }
}
package de.uol.neuropsy.senda;

import android.annotation.SuppressLint;
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

public class LocationBridge {

    static String TAG = LocationBridge.class.getSimpleName();

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
                        double[] loc = {location.getLatitude(), location.getLongitude(), location.getAltitude(),location.getAccuracy()};
                        mStreamOutlet.push_sample(loc);
                    }
                }
            }
        };
    }
    @SuppressLint("MissingPermissions")
    void Start() {
        mlocationProviderClient.requestLocationUpdates(mlocationRequest, mlocationCallback, null);
    }

    void Stop() {
        mlocationProviderClient.removeLocationUpdates(mlocationCallback);
        mStreamOutlet.close();
    }

    @Override
    protected void finalize() {
    }
}
package de.uol.neuropsy.senda.sensor

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.Looper
import android.util.Log
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import edu.ucsd.sccn.LSL
import edu.ucsd.sccn.LSL.StreamInfo
import edu.ucsd.sccn.LSL.StreamOutlet
import java.io.IOException

class LocationBridge internal constructor(context: Context?) : SensorBridge {
    // GoogleApiClient instance to connect to Google Play Services
    private val mlocationProviderClient: FusedLocationProviderClient
    private val mlocationRequest: LocationRequest
    private val mlocationCallback: LocationCallback
    private var mStreamOutlet: StreamOutlet? = null

    init {
        val mStreamInfo = StreamInfo(
            "Location" + " " + Build.MODEL,
            "other", 4, LSL.IRREGULAR_RATE, LSL.ChannelFormat.float32, Build.FINGERPRINT
        )
        try {
            mStreamOutlet = StreamOutlet(mStreamInfo)
        } catch (e: IOException) {
            Log.e("LocationBridge", e.toString())
            e.printStackTrace()
        }
        if (context == null) Log.e("LocationBridge", "Context is null!")
        mlocationProviderClient = LocationServices.getFusedLocationProviderClient(context!!)
        mlocationRequest =
            LocationRequest.Builder(1000).setPriority(Priority.PRIORITY_HIGH_ACCURACY).build()
        mlocationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                // Handle the received location updates
                if (locationResult != null) {
                    val location = locationResult.lastLocation
                    if (location != null) {
                        val loc = doubleArrayOf(
                            location.latitude,
                            location.longitude,
                            location.altitude,
                            location.accuracy.toDouble()
                        )
                        mStreamOutlet!!.push_sample(loc)
                    }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    override fun Start() {
        mlocationProviderClient.requestLocationUpdates(mlocationRequest, mlocationCallback, Looper.getMainLooper())
    }

    override fun Stop() {
        mlocationProviderClient.removeLocationUpdates(mlocationCallback)
        mStreamOutlet!!.close()
    }

    protected fun finalize() {}

    companion object {
        var TAG = LocationBridge::class.java.simpleName
    }
}
package de.uol.neuropsy.senda

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorManager
import android.net.wifi.WifiManager
import android.net.wifi.WifiManager.MulticastLock
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.PowerManager.WakeLock
import android.util.Log
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.LinearInterpolator
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.google.mediapipe.tasks.audio.core.RunningMode
import java.util.Vector

/**
 * Created by aliayubkhan on 19/04/2018.
 */
class LSLService : Service() {
    private val sensorBridges = Vector<SensorBridge>()
    private var mLocationBridge: LocationBridge? = null
    private var mAudioBridge: AudioBridge? = null
    private var mAudioClassifier: AudioClassifierHelper? = null
    var uniqueID = Build.FINGERPRINT
    var deviceName = Build.MODEL

    //Wake Lock
    private lateinit var wakelock: WakeLock
    private lateinit var multicastLock: MulticastLock

    //Animation for Streaming
    var animation: Animation = AlphaAnimation(0.5.toFloat(), 0f)
    @SuppressLint("WakelockTimeout")
    override fun onCreate() {
        val pm = (getSystemService(POWER_SERVICE) as PowerManager)
        wakelock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, javaClass.canonicalName)
        wakelock.acquire()
        val wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
        if (wifiManager != null) {
            multicastLock = wifiManager.createMulticastLock("Log_Tag")
            multicastLock.acquire()
        }
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {

        // this method is part of the mechanisms that allow this to be a foreground channel
        createNotificationChannel()
        if (MainActivity.Companion.streamingNow == null) {
            throw AssertionError("StreamingNow is Null")
        }
        MainActivity.Companion.streamingNow.setVisibility(View.VISIBLE)
        MainActivity.Companion.streamingNowBtn!!.setVisibility(View.INVISIBLE)
        animation.duration = 850
        animation.interpolator = LinearInterpolator() // do not alter
        // animation rate
        animation.repeatCount = Animation.INFINITE // Repeat animation
        // infinitely
        animation.repeatMode = Animation.REVERSE // Reverse animation at the
        // end so the button will fade back in
        // streamingNowBtn.startAnimation(animation);
        MainActivity.Companion.streamingNow.startAnimation(animation)
        Log.i(TAG, "Service onStartCommand")
        Toast.makeText(this, "Starting LSL!", Toast.LENGTH_SHORT).show()

        //Setting All sensors
        val msensorManager = (getSystemService(SENSOR_SERVICE) as SensorManager)
        if (intent.getBooleanExtra("Accelerometer", false)) sensorBridges.add(
            SensorBridge(
                3, msensorManager.getDefaultSensor(
                    Sensor.TYPE_ACCELEROMETER
                )
            )
        )
        if (intent.getBooleanExtra("Light", false)) sensorBridges.add(
            SensorBridge(
                1, msensorManager.getDefaultSensor(
                    Sensor.TYPE_LIGHT
                )
            )
        )
        if (intent.getBooleanExtra("Proximity", false)) sensorBridges.add(
            SensorBridge(
                1, msensorManager.getDefaultSensor(
                    Sensor.TYPE_PROXIMITY
                )
            )
        )
        if (intent.getBooleanExtra("Gravity", false)) sensorBridges.add(
            SensorBridge(
                3, msensorManager.getDefaultSensor(
                    Sensor.TYPE_GRAVITY
                )
            )
        )
        if (intent.getBooleanExtra("Linear Acceleration", false)) sensorBridges.add(
            SensorBridge(
                3, msensorManager.getDefaultSensor(
                    Sensor.TYPE_LINEAR_ACCELERATION
                )
            )
        )
        if (intent.getBooleanExtra("Rotation Vector", false)) sensorBridges.add(
            SensorBridge(
                5, msensorManager.getDefaultSensor(
                    Sensor.TYPE_ROTATION_VECTOR
                )
            )
        )
        if (intent.getBooleanExtra("Gyroscope", false)) sensorBridges.add(
            SensorBridge(
                3, msensorManager.getDefaultSensor(
                    Sensor.TYPE_GYROSCOPE
                )
            )
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            if (intent.getBooleanExtra("Step Count", false)) sensorBridges.add(
                SensorBridge(
                    1, msensorManager.getDefaultSensor(
                        Sensor.TYPE_STEP_COUNTER
                    )
                )
            )
        }
        for (sensorBridge in sensorBridges) {
            msensorManager.registerListener(
                sensorBridge,
                sensorBridge.mSensor,
                SensorManager.SENSOR_DELAY_UI
            )
            sensorBridge.Start()
        }
        if (intent.getBooleanExtra("Location", false)) {
            mLocationBridge = LocationBridge(this)
            mLocationBridge!!.Start()
        }
        if (intent.getBooleanExtra("Audio", false)) {
            mAudioBridge = AudioBridge(this)
            mAudioBridge!!.Start()
        }
        if (intent.getBooleanExtra("Audio classifier", false)) {
            mAudioClassifier = AudioClassifierHelper(
                this,
                AudioClassifierHelper.DISPLAY_THRESHOLD,
                AudioClassifierHelper.DEFAULT_OVERLAP,
                AudioClassifierHelper.DEFAULT_NUM_OF_RESULTS,
                RunningMode.AUDIO_STREAM,
                null
            )
        }
        MainActivity.Companion.isRunning = true

        // This service is killed by the OS if it is not started as background service
        // This feature is only supported in Android 10 or higher
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startMyOwnForeground()
            Toast.makeText(this, "SENDA can safely run in background!", Toast.LENGTH_LONG).show()
        } else {
            startForeground(1, Notification())
            Toast.makeText(this, "SENDA might be killed when in background!", Toast.LENGTH_LONG)
                .show()
        }
        return START_NOT_STICKY
    }

    // From https://stackoverflow.com/questions/47531742/startforeground-fail-after-upgrade-to-android-8-1
    // and https://androidwave.com/foreground-service-android-example/
    @RequiresApi(api = Build.VERSION_CODES.O)
    private fun startMyOwnForeground() {
        val NOTIFICATION_CHANNEL_ID = "de.uol.neuropsy.senda"
        val channelName = "SENDA Background Service"
        val chan = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            channelName,
            NotificationManager.IMPORTANCE_DEFAULT
        )
        chan.lightColor = Color.GREEN
        chan.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        val manager = (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
        manager.createNotificationChannel(chan)
        val notificationBuilder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
        val notification = notificationBuilder.setOngoing(true)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentTitle("SENDA is running in background!")
            .setPriority(NotificationManager.IMPORTANCE_DEFAULT)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
        val information_id =
            35 // this must be unique and not 0, otherwise it does not have a meaning
        startForeground(
            information_id,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                "FOREGROUNDCHANNELSENDA",
                "Foreground Service Channel SENDA",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(
                NotificationManager::class.java
            )
            manager.createNotificationChannel(serviceChannel)
        }
    }

    override fun onBind(arg0: Intent): IBinder? {
        Log.i(TAG, "Service onBind")
        return null
    }

    override fun onDestroy() {
        MainActivity.Companion.isRunning = false
        Log.i(TAG, "Service onDestroy")
        Toast.makeText(this, "Closing LSL!", Toast.LENGTH_SHORT).show()
        MainActivity.Companion.streamingNow!!.setVisibility(View.INVISIBLE)
        MainActivity.Companion.streamingNowBtn!!.setVisibility(View.INVISIBLE)
        MainActivity.Companion.streamingNowBtn!!.clearAnimation()
        MainActivity.Companion.streamingNow!!.clearAnimation()
        wakelock!!.release()
        multicastLock!!.release()
        //Unregister all sensor listeners
        val msensorManager = (getSystemService(SENSOR_SERVICE) as SensorManager)
        for (sensorBridge in sensorBridges) {
            msensorManager.unregisterListener(sensorBridge)
            sensorBridge.Stop()
        }
        if (mLocationBridge != null) {
            mLocationBridge!!.Stop()
        }
        if (mAudioBridge != null) {
            mAudioBridge!!.Stop()
        }
        if (mAudioClassifier != null) mAudioClassifier!!.stopAudioClassification()
    }

    companion object {
        private val TAG = LSLService::class.java.simpleName
    }
}
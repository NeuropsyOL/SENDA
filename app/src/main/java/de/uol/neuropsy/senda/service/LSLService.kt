package de.uol.neuropsy.senda.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.hardware.SensorManager
import android.net.wifi.WifiManager
import android.net.wifi.WifiManager.MulticastLock
import android.os.Binder
import android.os.IBinder
import android.os.PowerManager
import android.os.PowerManager.WakeLock
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.google.mediapipe.tasks.audio.core.RunningMode
import de.uol.neuropsy.senda.R
import de.uol.neuropsy.senda.sensor.AudioBridge
import de.uol.neuropsy.senda.sensor.AudioClassifierHelper
import de.uol.neuropsy.senda.sensor.LocationBridge
import de.uol.neuropsy.senda.sensor.SensorBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Vector

/**
 * Created by aliayubkhan on 19/04/2018.
 */


sealed class ServiceEvent {
    object Started : ServiceEvent()
    object Stopped : ServiceEvent()
    data class Failed(val error: String) : ServiceEvent()
}

class LSLService : Service() {

    private val binder = LocalBinder()
    private val _events = MutableSharedFlow<ServiceEvent>(replay = 1)
    val events: SharedFlow<ServiceEvent> = _events
    private val sensorBridges = Vector<SensorBridge>()
    private var locationBridge: LocationBridge? = null
    private var audioBridge: AudioBridge? = null
    private var audioClassifier: AudioClassifierHelper? = null

    //Wake Lock
    private lateinit var wakelock: WakeLock
    private lateinit var multicastLock: MulticastLock


    inner class LocalBinder : Binder() {
        fun getService(): LSLService = this@LSLService
    }
    override fun onBind(intent: Intent): IBinder = binder

    @SuppressLint("WakelockTimeout")
    override fun onCreate() {
        super.onCreate()
        val pm = getSystemService(PowerManager::class.java)
        wakelock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, javaClass.canonicalName)
        wakelock.acquire()
        val wifi = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
        multicastLock = wifi.createMulticastLock("LSLService")
        multicastLock.acquire()
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        CoroutineScope(Dispatchers.Default).launch {
            try {
                createNotificationChannel()
                startForegroundServiceNotification()
                _events.emit(ServiceEvent.Started)

                setupSensors(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start LSLService", e)
                _events.emit(ServiceEvent.Failed(e.message ?: "Unknown error"))
                stopSelf()
            }
        }
        Toast.makeText(this, "SENDA can safely run in background!", Toast.LENGTH_LONG).show()
        return START_NOT_STICKY
    }

    private fun startForegroundServiceNotification() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentTitle("SENDA is running")
            .setOngoing(true)
            .build()
        startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
    }

    private suspend fun setupSensors(intent: Intent) = withContext(Dispatchers.Default) {
        val sm = getSystemService(SensorManager::class.java)
        // Onboard sensors
        if (intent.getBooleanExtra("Accelerometer", false)) {
            sm.getDefaultSensor(android.hardware.Sensor.TYPE_ACCELEROMETER)?.let {
                sensorBridges.add(SensorBridge(3, it))
            }
        }
        if (intent.getBooleanExtra("Light", false)) {
            sm.getDefaultSensor(android.hardware.Sensor.TYPE_LIGHT)?.let {
                sensorBridges.add(SensorBridge(1, it))
            }
        }
        if (intent.getBooleanExtra("Proximity", false)) {
            sm.getDefaultSensor(android.hardware.Sensor.TYPE_PROXIMITY)?.let {
                sensorBridges.add(SensorBridge(1, it))
            }
        }
        if (intent.getBooleanExtra("Gravity", false)) {
            sm.getDefaultSensor(android.hardware.Sensor.TYPE_GRAVITY)?.let {
                sensorBridges.add(SensorBridge(3, it))
            }
        }
        if (intent.getBooleanExtra("Linear Acceleration", false)) {
            sm.getDefaultSensor(android.hardware.Sensor.TYPE_LINEAR_ACCELERATION)?.let {
                sensorBridges.add(SensorBridge(3, it))
            }
        }
        if (intent.getBooleanExtra("Rotation Vector", false)) {
            sm.getDefaultSensor(android.hardware.Sensor.TYPE_ROTATION_VECTOR)?.let {
                sensorBridges.add(SensorBridge(5, it))
            }
        }
        if (intent.getBooleanExtra("Gyroscope", false)) {
            sm.getDefaultSensor(android.hardware.Sensor.TYPE_GYROSCOPE)?.let {
                sensorBridges.add(SensorBridge(3, it))
            }
        }
        if (intent.getBooleanExtra("Step Count", false)) {
            sm.getDefaultSensor(android.hardware.Sensor.TYPE_STEP_COUNTER)?.let {
                sensorBridges.add(SensorBridge(1, it))
            }
        }
        sensorBridges.forEach {
            it.Start()
            sm.registerListener(it,it.mSensor,SensorManager.SENSOR_DELAY_UI)
        }

        // Other sensors not managed by the sensor manager
        if (intent.getBooleanExtra("Location", false)) {
            locationBridge = LocationBridge(this@LSLService)
            locationBridge?.Start()
        }
        if (intent.getBooleanExtra("Audio", false)) {
            audioBridge = AudioBridge(this@LSLService)
            audioBridge?.Start()
        }
        if (intent.getBooleanExtra("Audio classifier", false)) {
            audioClassifier = AudioClassifierHelper(
                this@LSLService,
                AudioClassifierHelper.DISPLAY_THRESHOLD,
                AudioClassifierHelper.DEFAULT_OVERLAP,
                AudioClassifierHelper.DEFAULT_NUM_OF_RESULTS,
                RunningMode.AUDIO_STREAM,
                null
            )
        }


    }

    private fun createNotificationChannel() {
        val chan = NotificationChannel(
            CHANNEL_ID,
            "SENDA Background Service",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            lightColor = Color.GREEN
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(chan)
    }

    override fun onDestroy() {
        super.onDestroy()
        _events.tryEmit(ServiceEvent.Stopped)
        wakelock.release()
        multicastLock.release()

        val sm = getSystemService(SensorManager::class.java)
        sensorBridges.forEach {
            sm.unregisterListener(it)
            it.Stop()
        }
        locationBridge?.Stop()
        audioBridge?.Stop()
        audioClassifier?.stopAudioClassification()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.e("LSLService","onTaskRemoved")
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    companion object {
        private const val TAG = "LSLService"
        private const val CHANNEL_ID = "de.uol.neuropsy.senda.channel"
        private const val NOTIF_ID = 1
    }
}
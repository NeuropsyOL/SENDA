package de.uol.neuropsy.senda.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothManager
import android.content.Context
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
import de.uol.neuropsy.senda.sensor.MovellaBridge
import de.uol.neuropsy.senda.sensor.SensorBridge
import de.uol.neuropsy.senda.sensor.OnboardSensorBridge
import de.uol.neuropsy.senda.sensor.SensorConfig
import de.uol.neuropsy.senda.utils.Utils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

sealed class ServiceEvent {
    object Started : ServiceEvent()
    object Stopped : ServiceEvent()
    object Configured : ServiceEvent()
    data class Failed(val error: String) : ServiceEvent()
}

class LSLService : Service() {
    private val binder = LocalBinder()
    private val _events = MutableSharedFlow<ServiceEvent>(replay = 1)
    val events: SharedFlow<ServiceEvent> = _events
    private var sensorBridges : MutableList<SensorBridge> = mutableListOf()
    private var movellaBridges : MutableList<MovellaBridge> = mutableListOf()

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

        Thread.setDefaultUncaughtExceptionHandler { _, _ ->
            stopStreaming()
        }
    }
    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                createNotificationChannel()
                startForegroundServiceNotification()

            } catch (e: Throwable) {
                Log.e(TAG, "Failed to start LSLService", e)
                _events.emit(ServiceEvent.Failed(e.message ?: "Unknown error"))
                stopStreaming()
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

    @SuppressLint("MissingPermission")
    suspend fun configureSensors(configs: List<SensorConfig>) {
        sensorBridges.clear()
        movellaBridges.clear()

        val btManager=applicationContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = btManager.adapter
            ?: throw IllegalStateException("Device doesn't support Bluetooth")
        val sm = getSystemService(SensorManager::class.java)
        for (cfg in configs) {
            when (cfg) {
                is SensorConfig.Onboard -> {
                    sm.getDefaultSensor(cfg.type)?.let { sensor->
                        sensorBridges.add(OnboardSensorBridge(Utils.getChannelCount(sensor), sensor, applicationContext))
                    }
                }
                is SensorConfig.Movella  -> {
                    val btDevice=bluetoothAdapter.getRemoteDevice(cfg.address)
                    val mb=MovellaBridge(applicationContext,btDevice){}
                    movellaBridges+=mb
                    mb.Initialize()
                }
                is SensorConfig.Audio -> {
                    sensorBridges.add(AudioBridge(this@LSLService))
                }
                is SensorConfig.AudioClassification -> {
                    sensorBridges.add(AudioClassifierHelper(
                        this@LSLService,
                        AudioClassifierHelper.DISPLAY_THRESHOLD,
                        AudioClassifierHelper.DEFAULT_OVERLAP,
                        AudioClassifierHelper.DEFAULT_NUM_OF_RESULTS,
                        RunningMode.AUDIO_STREAM,
                        null
                    ))
                }
                is SensorConfig.Location -> {
                    sensorBridges.add(LocationBridge(this@LSLService))
                }
            }
        }
        // await **all** the onDotInitDone callbacks
        movellaBridges.forEach { it.awaitInit() }
        _events.emit(ServiceEvent.Configured)
    }

    suspend fun startStreaming(){
        sensorBridges.forEach {
            it.Start()
        }




        movellaBridges.forEach { it.Start() }
        _events.emit(ServiceEvent.Started)
    }

    fun stopStreaming(){
        val sm = getSystemService(SensorManager::class.java)
        sensorBridges.forEach {
            it.Stop()
        }
        movellaBridges.forEach { it.Stop() }
        movellaBridges.clear()
        _events.tryEmit(ServiceEvent.Stopped)
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
        stopStreaming()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.e("LSLService","onTaskRemoved")
        stopStreaming()
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
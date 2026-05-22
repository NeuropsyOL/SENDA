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
import com.xsens.dot.android.sdk.interfaces.DotSyncCallback
import com.xsens.dot.android.sdk.models.DotDevice
import com.xsens.dot.android.sdk.models.DotSyncManager
import de.uol.neuropsy.senda.R
import de.uol.neuropsy.senda.data.SyncStatus
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
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

sealed class ServiceEvent {
    object Started : ServiceEvent()
    object Stopped : ServiceEvent()
    object Configured : ServiceEvent()
    class Syncing(val progress : Int) : ServiceEvent()
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
        // startForeground() MUST be called synchronously here, before onStartCommand returns.
        // The FGS type is passed by the caller so we only request location/microphone types
        // when those sensors are actually selected and their runtime permissions are granted.
        val fgsType = intent.getIntExtra(
            EXTRA_FGS_TYPE,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        )
        try {
            createNotificationChannel()
            startForegroundServiceNotification(fgsType)
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to start LSLService as foreground service", e)
            CoroutineScope(Dispatchers.Main).launch {
                _events.emit(ServiceEvent.Failed(e.message ?: "Unknown error"))
            }
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun startForegroundServiceNotification(fgsType: Int) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentTitle("SENDA is running")
            .setOngoing(true)
            .build()
        startForeground(NOTIF_ID, notification, fgsType)
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

        if(movellaBridges.size>1 /*TODO and syncing is active in settings*/){
            val terminalStatus: SyncStatus = syncMovellaDevices()
                .onEach { status ->
                    when (status) {
                        is SyncStatus.Progress ->
                            _events.tryEmit(ServiceEvent.Syncing(status.progress))
                        else -> { /* ignore */ }
                    }
                }
                .filter { it is SyncStatus.Success || it is SyncStatus.Failed }
                .first()
            when (terminalStatus) {
                is SyncStatus.Failed  ->
                    _events.tryEmit(ServiceEvent.Failed("Could not sync Movella devices!"))
                else -> {}
                }
            }
        movellaBridges.forEach { it.Start() }
        _events.emit(ServiceEvent.Started)
        }

    fun stopStreaming(){
        sensorBridges.forEach {
            it.Stop()
        }
        movellaBridges.forEach { it.Stop() }
        stopSyncing()
        movellaBridges.forEach { it.Disconnect() }
        movellaBridges.clear()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        _events.tryEmit(ServiceEvent.Stopped)
    }

    private fun syncMovellaDevices(): Flow<SyncStatus> = callbackFlow {
        // Set the first device as the root
        movellaBridges[0].handle?.isRootDevice = true
        var syncSuccessful=false
        val syncCallback = object : DotSyncCallback {
            override fun onSyncingStarted(deviceAddress: String?, isRoot: Boolean, count: Int) {
                trySend(SyncStatus.Progress(0))
            }
            override fun onSyncingProgress(progress: Int, total: Int) {
                trySend(SyncStatus.Progress(progress))
            }
            override fun onSyncingResult(deviceAddress: String?, success: Boolean, reason: Int) {
                // No-op
            }
            override fun onSyncingDone(results: HashMap<String, Boolean>, allSuccessful: Boolean, code: Int) {
                syncSuccessful=allSuccessful
                if(allSuccessful)
                    trySend(SyncStatus.Success())
                else
                    trySend(SyncStatus.Failed())
                close()
            }
            override fun onSyncingStopped(deviceAddress: String?, isSuccess: Boolean, code: Int) {
            }
        }
        DotSyncManager.getInstance(syncCallback).startSyncing(ArrayList(movellaBridges.map { it.handle }), 1)
        awaitClose { if(!syncSuccessful) DotSyncManager.getInstance(syncCallback).stopSyncing()
        }
    }


    private fun stopSyncing() : Flow<SyncStatus> = callbackFlow {
        val syncCallback = object : DotSyncCallback {
            override fun onSyncingStarted(deviceAddress: String?, isRoot: Boolean, count: Int) {
            }
            override fun onSyncingProgress(progress: Int, total: Int) {
            }
            override fun onSyncingResult(deviceAddress: String?, success: Boolean, reason: Int) {
            }
            override fun onSyncingDone(results: HashMap<String, Boolean>, allSuccessful: Boolean, code: Int) {
            }
            override fun onSyncingStopped(deviceAddress: String?, isSuccess: Boolean, code: Int) {
            }
        }
        //movellaBridges.map { it.handle }.filterNotNull().toTypedArray()
        DotSyncManager.getInstance(syncCallback).stopSyncing()
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
        stopStreaming()
        wakelock.release()
        multicastLock.release()
        _events.tryEmit(ServiceEvent.Stopped)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        stopStreaming()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }


    companion object {
        const val EXTRA_FGS_TYPE = "de.uol.neuropsy.senda.FGS_TYPE"
        private const val TAG = "LSLService"
        private const val CHANNEL_ID = "de.uol.neuropsy.senda.channel"
        private const val NOTIF_ID = 1
    }
}
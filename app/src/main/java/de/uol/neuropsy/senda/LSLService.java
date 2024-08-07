package de.uol.neuropsy.senda;

import static android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA;
import static android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION;
import static android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE;
import static de.uol.neuropsy.senda.MainActivity.streamingNow;
import static de.uol.neuropsy.senda.MainActivity.streamingNowBtn;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;

import com.google.mediapipe.tasks.audio.core.RunningMode;

import java.util.Vector;


/**
 * Created by aliayubkhan on 19/04/2018.
 */

public class LSLService extends Service {

    private static final String TAG = LSLService.class.getSimpleName();

    private final Vector<SensorBridge> sensorBridges = new Vector<>();

    private LocationBridge mLocationBridge = null;

    private AudioBridge mAudioBridge = null;

    private AudioClassifierHelper mAudioClassifier = null;

    public LSLService() {
        super();
    }

    String uniqueID = Build.FINGERPRINT;
    String deviceName = Build.MODEL;

    //Wake Lock
    PowerManager.WakeLock wakelock;

    WifiManager.MulticastLock multicastLock;

    //Animation for Streaming
    Animation animation = new AlphaAnimation((float) 0.5, 0);

    @SuppressLint("WakelockTimeout")
    @Override
    public void onCreate() {
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        assert pm != null;
        wakelock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, getClass().getCanonicalName());
        wakelock.acquire();
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wifiManager != null) {
            multicastLock = wifiManager.createMulticastLock("Log_Tag");
            multicastLock.acquire();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        // this method is part of the mechanisms that allow this to be a foreground channel
        createNotificationChannel();

        if (streamingNow == null) {
            throw new AssertionError("StreamingNow is Null");
        }
        streamingNow.setVisibility(View.VISIBLE);
        streamingNowBtn.setVisibility(View.INVISIBLE);

        animation.setDuration(850);
        animation.setInterpolator(new LinearInterpolator()); // do not alter
        // animation rate
        animation.setRepeatCount(Animation.INFINITE); // Repeat animation
        // infinitely
        animation.setRepeatMode(Animation.REVERSE); // Reverse animation at the
        // end so the button will fade back in
        // streamingNowBtn.startAnimation(animation);
        streamingNow.startAnimation(animation);

        Log.i(TAG, "Service onStartCommand");
        Toast.makeText(this, "Starting LSL!", Toast.LENGTH_SHORT).show();

        //Setting All sensors
        SensorManager msensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        assert msensorManager != null;
        if (intent.getBooleanExtra("Accelerometer", false))
            sensorBridges.add(new SensorBridge(3, msensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)));
        if (intent.getBooleanExtra("Light", false))
            sensorBridges.add(new SensorBridge(1, msensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)));
        if (intent.getBooleanExtra("Proximity", false))
            sensorBridges.add(new SensorBridge(1, msensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)));
        if (intent.getBooleanExtra("Gravity", false))
            sensorBridges.add(new SensorBridge(3, msensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)));
        if (intent.getBooleanExtra("Linear Acceleration", false))
            sensorBridges.add(new SensorBridge(3, msensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)));
        if (intent.getBooleanExtra("Rotation Vector", false))
            sensorBridges.add(new SensorBridge(5, msensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)));
        if (intent.getBooleanExtra("Gyroscope", false))
            sensorBridges.add(new SensorBridge(3, msensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            if (intent.getBooleanExtra("Step Count", false))
                sensorBridges.add(new SensorBridge(1, msensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)));
        }

        for (SensorBridge sensorBridge : sensorBridges) {
            msensorManager.registerListener(sensorBridge, sensorBridge.mSensor, SensorManager.SENSOR_DELAY_UI);
            sensorBridge.Start();
        }

        if (intent.getBooleanExtra("Location", false)) {
            mLocationBridge = new LocationBridge(this);
            mLocationBridge.Start();
        }

        if (intent.getBooleanExtra("Audio", false)) {
            mAudioBridge = new AudioBridge(this);
            mAudioBridge.Start();
        }

        if (intent.getBooleanExtra("Audio classifier", false)) {
            mAudioClassifier = new AudioClassifierHelper(this, AudioClassifierHelper.DISPLAY_THRESHOLD, AudioClassifierHelper.DEFAULT_OVERLAP, AudioClassifierHelper.DEFAULT_NUM_OF_RESULTS, RunningMode.AUDIO_STREAM, null);
        }

        MainActivity.isRunning = true;

        // This service is killed by the OS if it is not started as background service
        // This feature is only supported in Android 10 or higher
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startMyOwnForeground();
            Toast.makeText(this, "SENDA can safely run in background!", Toast.LENGTH_LONG).show();
        } else {
            startForeground(1, new Notification());
            Toast.makeText(this, "SENDA might be killed when in background!", Toast.LENGTH_LONG).show();
        }
        return START_NOT_STICKY;
    }

    // From https://stackoverflow.com/questions/47531742/startforeground-fail-after-upgrade-to-android-8-1
    // and https://androidwave.com/foreground-service-android-example/
    @RequiresApi(api = Build.VERSION_CODES.O)
    private void startMyOwnForeground() {
        String NOTIFICATION_CHANNEL_ID = "de.uol.neuropsy.senda";
        String channelName = "SENDA Background Service";
        NotificationChannel chan = new NotificationChannel(NOTIFICATION_CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_DEFAULT);
        chan.setLightColor(Color.GREEN);
        chan.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        assert manager != null;
        manager.createNotificationChannel(chan);

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID);
        Notification notification = notificationBuilder.setOngoing(true)
                .setSmallIcon(R.mipmap.ic_launcher_round)
                .setContentTitle("SENDA is running in background!")
                .setPriority(NotificationManager.IMPORTANCE_DEFAULT)
                .setCategory(Notification.CATEGORY_SERVICE)
                .build();
        int information_id = 35; // this must be unique and not 0, otherwise it does not have a meaning
        startForeground(information_id, notification, FOREGROUND_SERVICE_TYPE_LOCATION | FOREGROUND_SERVICE_TYPE_CAMERA | FOREGROUND_SERVICE_TYPE_MICROPHONE);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    "FOREGROUNDCHANNELSENDA",
                    "Foreground Service Channel SENDA",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(serviceChannel);
        }


    }

    @Override
    public IBinder onBind(Intent arg0) {
        Log.i(TAG, "Service onBind");
        return null;
    }

    @Override
    public void onDestroy() {

        MainActivity.isRunning = false;

        Log.i(TAG, "Service onDestroy");
        Toast.makeText(this, "Closing LSL!", Toast.LENGTH_SHORT).show();

        streamingNow.setVisibility(View.INVISIBLE);
        streamingNowBtn.setVisibility(View.INVISIBLE);
        streamingNowBtn.clearAnimation();
        streamingNow.clearAnimation();
        wakelock.release();
        multicastLock.release();
        //Unregister all sensor listeners
        SensorManager msensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        assert msensorManager != null;

        for (SensorBridge sensorBridge : sensorBridges) {
            msensorManager.unregisterListener(sensorBridge);
            sensorBridge.Stop();
        }

        if (mLocationBridge != null) {
            mLocationBridge.Stop();
        }

        if (mAudioBridge != null) {
            mAudioBridge.Stop();
        }
        if (mAudioClassifier != null)
            mAudioClassifier.stopAudioClassification();
    }
}
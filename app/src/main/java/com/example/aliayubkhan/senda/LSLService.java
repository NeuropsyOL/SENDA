package com.example.aliayubkhan.senda;
import edu.ucsd.sccn.LSL;
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
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
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

import java.io.IOException;
import java.util.Random;
import java.util.Vector;

import static com.example.aliayubkhan.senda.MainActivity.streamingNow;
import static com.example.aliayubkhan.senda.MainActivity.streamingNowBtn;
import static com.example.aliayubkhan.senda.MainActivity.isAudio;
import static com.example.aliayubkhan.senda.MainActivity.isLocation;
import static com.example.aliayubkhan.senda.MainActivity.isAccelerometer;
import static com.example.aliayubkhan.senda.MainActivity.isGravity;
import static com.example.aliayubkhan.senda.MainActivity.isLight;
import static com.example.aliayubkhan.senda.MainActivity.isLinearAcceleration;
import static com.example.aliayubkhan.senda.MainActivity.isStepCounter;
import static com.example.aliayubkhan.senda.MainActivity.isProximity;
import static com.example.aliayubkhan.senda.MainActivity.isRotation;


/**
 * Created by aliayubkhan on 19/04/2018.
 */

public class LSLService extends Service {

    private static final String TAG = "LSLService";

    //LSL Outlets
    static LSL.StreamOutlet audioOutlet, locationOutlet = null;

    //LSL Streams
    private LSL.StreamInfo audio, location = null;

    // sensor sampling options
    private static final int AUDIO_RECORDING_RATE = 44100;

    // the pull-values thread sleeps for this amount of ms in every iteration before pulling new sensor values from MainActivity and pushing them
    private static final int THREAD_INTERVAL = 8;
    // the sampling rate of every stream depends on the thread sleep interval, not the OS
    private static final int SAMPLING_RATE = 1000 / THREAD_INTERVAL; // how many values do we receive per ms

    // audio settings
    private static final int CHANNEL = AudioFormat.CHANNEL_IN_STEREO;
    private int audio_channel_count = 2;
    private static final int FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private AudioRecord recorder = null;

    /**
     * Factor by that the minimum buffer size is multiplied. The bigger the factor is the less
     * likely it is that samples will be dropped, but more memory will be used. The minimum buffer
     * size is determined by {@link AudioRecord#getMinBufferSize(int, int, int)} and depends on the
     * recording settings.
     */
    private static final int BUFFER_SIZE_FACTOR = 2;

    /**
     * Size of the buffer where the audio data is stored by Android
     */
    private static final int BUFFER_SIZE = AudioRecord.getMinBufferSize(AUDIO_RECORDING_RATE, CHANNEL, FORMAT) * BUFFER_SIZE_FACTOR;
    short[] audio_buffer = new short[BUFFER_SIZE];

    private Vector<SensorBridge> sensorBridges = new Vector<>();

    public LSLService() {
        super();
    }

    String uniqueID = Build.FINGERPRINT;
    String deviceName = Build.MODEL;


    // Data Variables
    double[] locationData = new double[2];

    //Wake Lock
    PowerManager.WakeLock wakelock;

    //Animation for Streaming
    Animation animation = new AlphaAnimation((float) 0.5, 0);

    @SuppressLint("WakelockTimeout")
    @Override
    public void onCreate() {
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        assert pm != null;
        wakelock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, getClass().getCanonicalName());
        wakelock.acquire();
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
        if (isAccelerometer)
            sensorBridges.add(new SensorBridge(3, msensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)));
        if (isLight)
            sensorBridges.add(new SensorBridge(1, msensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)));
        if (isProximity)
            sensorBridges.add(new SensorBridge(1, msensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)));
        if (isGravity)
            sensorBridges.add(new SensorBridge(3, msensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)));
        if (isLinearAcceleration)
            sensorBridges.add(new SensorBridge(3, msensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)));
        if (isRotation)
            sensorBridges.add(new SensorBridge(5, msensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            if (isStepCounter)
                sensorBridges.add(new SensorBridge(1, msensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)));
        }

        for (SensorBridge sensorBridge : sensorBridges) {
            msensorManager.registerListener(sensorBridge, sensorBridge.mSensor, SensorManager.SENSOR_DELAY_UI);
            sensorBridge.Start();
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                if (isLocation) {
                    location = new LSL.StreamInfo("Location " + deviceName + generate_random_String(),
                            "eeg", 2, LSL.IRREGULAR_RATE, LSL.ChannelFormat.float32, "myuidstep" + uniqueID);
                    try {
                        locationOutlet = new LSL.StreamOutlet(location);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                while (!MainActivity.checkFlag) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    if (isLocation) {
                        if (MainActivity.hasNewLocation) {
                            Log.e("Location", "Got new location: " + MainActivity.latitude + " " + MainActivity.longitude);
                            locationData[0] = MainActivity.latitude;
                            locationData[1] = MainActivity.longitude;
                            locationOutlet.push_sample(locationData);
                            MainActivity.hasNewLocation = false;
                        } else {
                            Log.e("Location", "No new location");
                        }
                    }
                }
                stopSelf();
            }
        }).start();

        // Audio gets its own thread without pauses
        new Thread(new Runnable() {
            @Override
            public void run() {

                if (isAudio) {
                    audio = new LSL.StreamInfo("Audio " + deviceName + generate_random_String(),
                            "audio", audio_channel_count, AUDIO_RECORDING_RATE, LSL.ChannelFormat.float32, "myuidaudio" + uniqueID);
                    try {
                        audioOutlet = new LSL.StreamOutlet(audio);
                        recorder = new AudioRecord(MediaRecorder.AudioSource.MIC, AUDIO_RECORDING_RATE, CHANNEL, FORMAT, BUFFER_SIZE);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                while (!MainActivity.checkFlag) {
                    if (isAudio) {
                        recorder.startRecording();
                        recorder.read(audio_buffer, 0, audio_buffer.length);
                        audioOutlet.push_chunk(audio_buffer);
                    }
                }
                stopSelf();
            }
        }).start();


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
        String NOTIFICATION_CHANNEL_ID = "com.example.aliayubkhan.senda";
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
        startForeground(information_id, notification);
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

        //Unregister all sensor listeners
        SensorManager msensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        assert msensorManager != null;

        for (SensorBridge sensorBridge : sensorBridges) {
            msensorManager.unregisterListener(sensorBridge);
            sensorBridge.Stop();
        }

        if (isLocation) {

            locationOutlet.close();
            location.destroy();
        }

        if (isAudio) {
            audioOutlet.close();
            audio.destroy();

            if (null != recorder) {
                try {
                    recorder.stop();
                    recorder.release();
                } catch (RuntimeException ex) {
                    recorder.release();
                }
            }
        }
    }
}
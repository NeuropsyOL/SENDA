package com.example.aliayubkhan.senda;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
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
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Random;
import java.util.UUID;

import static com.example.aliayubkhan.senda.MainActivity.isAccelerometer;
import static com.example.aliayubkhan.senda.MainActivity.isAudio;
import static com.example.aliayubkhan.senda.MainActivity.isGravity;
import static com.example.aliayubkhan.senda.MainActivity.isLight;
import static com.example.aliayubkhan.senda.MainActivity.isLinearAcceleration;
import static com.example.aliayubkhan.senda.MainActivity.isProximity;
import static com.example.aliayubkhan.senda.MainActivity.isRotation;
import static com.example.aliayubkhan.senda.MainActivity.isStepCounter;
import static com.example.aliayubkhan.senda.MainActivity.streamingNow;
import static com.example.aliayubkhan.senda.MainActivity.streamingNowBtn;

/**
 * Created by aliayubkhan on 19/04/2018.
 */

public class LSLService extends Service {

    private static final String TAG = "LSLService";

    //LSL Outlets
    static LSL.StreamOutlet accelerometerOutlet, lightOutlet, proximityOutlet, linearAccelerationOutlet, rotationOutlet, gravityOutlet, stepCountOutlet, audioOutlet = null;

    //LSL Streams
    private LSL.StreamInfo accelerometer, light, proximity, linearAcceleration, rotation, gravity, stepCount, audio = null;

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


    public LSLService(){
        super();
    }

    String uniqueID = Build.FINGERPRINT;
    String deviceName = Build.MODEL;


    // Data Variables
    float[] accelerometerData = new float[3];
    float[] linearAccelerationData = new float[3];
    float[] gravityData = new float[3];
    float[] rotationData = new float[4];
    float[] lightData = new float[1];
    float[] proximityData = new float[1];
    float[] stepCountData = new float[1];

    //Wake Lock
    PowerManager.WakeLock wakelock;

    //Animation for Streaming
    Animation animation = new AlphaAnimation((float) 0.5, 0);

    @SuppressLint("WakelockTimeout")
    @Override
    public void onCreate() {
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        assert pm != null;
        wakelock= pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, getClass().getCanonicalName());
        wakelock.acquire();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

            // this method is part of the mechanisms that allow this to be a foreground channel
            createNotificationChannel();

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
            Toast.makeText(this,"Starting LSL!", Toast.LENGTH_SHORT).show();

            // Create outlets for all streams
            new Thread(new Runnable() {
                private double [][] all_acc;

                @Override
                public void run() {

                    if(isAccelerometer){
                        accelerometer = new LSL.StreamInfo("Accelerometer "+ deviceName + generate_random_String(),
                                "eeg", 3, SAMPLING_RATE, LSL.ChannelFormat.float32, "myuidaccelerometer"+uniqueID);
                        try {
                            accelerometerOutlet = new LSL.StreamOutlet(accelerometer);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }

                    if(isLight){
                        light = new LSL.StreamInfo("Light "+ deviceName+ generate_random_String(),
                                "eeg", 1, SAMPLING_RATE, LSL.ChannelFormat.float32, "myuidlight"+uniqueID);
                        try {
                            lightOutlet = new LSL.StreamOutlet(light);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }

                    if(isProximity){
                        proximity = new LSL.StreamInfo("Proximity "+ deviceName + generate_random_String(),
                                "eeg", 1,SAMPLING_RATE, LSL.ChannelFormat.float32, "myuidproximity"+uniqueID);
                        try {
                            proximityOutlet = new LSL.StreamOutlet(proximity);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }

                    if(isLinearAcceleration){
                        linearAcceleration = new LSL.StreamInfo("LinearAcceleration "+ deviceName + generate_random_String(),
                                "eeg", 3,SAMPLING_RATE, LSL.ChannelFormat.float32, "myuidlinearacceleration"+uniqueID);
                        try {
                            linearAccelerationOutlet = new LSL.StreamOutlet(linearAcceleration);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }

                    }

                    if(isRotation){
                        rotation = new LSL.StreamInfo("Rotation "+ deviceName + generate_random_String(),
                                "eeg", 3, SAMPLING_RATE, LSL.ChannelFormat.float32, "myuidrotation"+uniqueID);
                        try {
                            rotationOutlet = new LSL.StreamOutlet(rotation);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }

                    }

                    if(isGravity){
                        gravity = new LSL.StreamInfo("Gravity "+ deviceName + generate_random_String(),
                                "eeg", 3, SAMPLING_RATE, LSL.ChannelFormat.float32, "myuidgravity"+uniqueID);
                        try {
                            gravityOutlet = new LSL.StreamOutlet(gravity);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }

                    }

                    if(isStepCounter){
                        stepCount = new LSL.StreamInfo("StepCount " + deviceName + generate_random_String(),
                                "eeg", 1, SAMPLING_RATE, LSL.ChannelFormat.float32, "myuidstep"+uniqueID);
                        try {
                            stepCountOutlet = new LSL.StreamOutlet(stepCount);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }

                    // push samples while activity is running
                    while (!MainActivity.checkFlag) {
                        try {
                            Thread.sleep(THREAD_INTERVAL);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        if(isAccelerometer){
                            //Setting Accelerometer Data
                            accelerometerData[0] = MainActivity.ax;
                            accelerometerData[1] = MainActivity.ay;
                            accelerometerData[2] = MainActivity.az;
                           accelerometerOutlet.push_sample(accelerometerData);
                        }

                        if(isLight){
                            //Setting Light Data
                            lightData[0] =  MainActivity.lightInt;
                            lightOutlet.push_sample(lightData);
                        }

                        if(isProximity){
                            //Setting Proximity Data
                            proximityData[0] = MainActivity.proximity;
                            proximityOutlet.push_sample(proximityData);
                        }

                        if(isLinearAcceleration){
                            //Setting Linear Acceleration Data
                            linearAccelerationData[0] = MainActivity.linear_x;
                            linearAccelerationData[1] = MainActivity.linear_y;
                            linearAccelerationData[2] = MainActivity.linear_z;
                            linearAccelerationOutlet.push_sample(linearAccelerationData);
                        }

                        if(isRotation){
                            //Setting Rotation Data
                            rotationData[0] = MainActivity.rotVec_x;
                            rotationData[1] = MainActivity.rotVec_y;
                            rotationData[2] = MainActivity.rotVec_z;
                            rotationData[3] = MainActivity.rotVec_scalar;
                            rotationOutlet.push_sample(rotationData);
                        }

                        if(isGravity){
                            //Setting Gravity Data
                            gravityData[0] = MainActivity.grav_x;
                            gravityData[1] = MainActivity.grav_y;
                            gravityData[2] = MainActivity.grav_z;
                            gravityOutlet.push_sample(gravityData);
                        }

                        if(isStepCounter){
                            //Setting Step Data
                            stepCountData[0] = MainActivity.stepCounter;
                            stepCountOutlet.push_sample(stepCountData);
                        }
                    }
                    //Stop service once it finishes its task
                    stopSelf();
                }
            }).start();

        // Audio gets its own thread without pauses
        new Thread(new Runnable() {
            @Override
            public void run() {

                if(isAudio){
                    audio = new LSL.StreamInfo("Audio "+ deviceName + generate_random_String(),
                            "audio", audio_channel_count, AUDIO_RECORDING_RATE, LSL.ChannelFormat.float32, "myuidaudio"+uniqueID);
                    try {
                        audioOutlet = new LSL.StreamOutlet(audio);
                        recorder = new AudioRecord(MediaRecorder.AudioSource.MIC, AUDIO_RECORDING_RATE, CHANNEL, FORMAT, BUFFER_SIZE);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                while (!MainActivity.checkFlag) {
                    if(isAudio){
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

        return(generatedString);
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
        Toast.makeText(this,"Closing LSL!", Toast.LENGTH_SHORT).show();
        MainActivity.stepCounter = 0;

        streamingNow.setVisibility(View.INVISIBLE);
        streamingNowBtn.setVisibility(View.INVISIBLE);
        streamingNowBtn.clearAnimation();
        streamingNow.clearAnimation();
        wakelock.release();

        if(isAccelerometer){
            accelerometerOutlet.close();
            accelerometer.destroy();
        }

        if(isLight){
            lightOutlet.close();
            light.destroy();

        }

        if(isProximity){
            proximityOutlet.close();
            proximity.destroy();
        }

        if(isLinearAcceleration){
            linearAccelerationOutlet.close();
            linearAcceleration.destroy();
        }

        if(isRotation){
            rotationOutlet.close();
            rotation.destroy();
        }

        if(isGravity){
            gravityOutlet.close();
            gravity.destroy();
        }

        if(isStepCounter){
            stepCountOutlet.close();
            stepCount.destroy();
        }

        if(isAudio){
            audioOutlet.close();
            audio.destroy();

            if (null != recorder) {
                try{
                    //my_stopRecording();
                    recorder.stop();
                    recorder.release();
                } catch(RuntimeException ex){
                    recorder.release();
                }
            }
        }
    }
}
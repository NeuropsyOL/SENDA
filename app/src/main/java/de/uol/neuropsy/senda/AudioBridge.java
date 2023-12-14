package de.uol.neuropsy.senda;

import android.annotation.SuppressLint;
import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.util.Log;

import java.io.IOException;

import edu.ucsd.sccn.LSL;

public class AudioBridge {
    static String TAG = AudioBridge.class.getSimpleName();
    //LSL Outlets
    Boolean checkFlag = false;
    Thread mAudioThread;
    static LSL.StreamOutlet audioOutlet = null;

    //LSL Streams
    private LSL.StreamInfo audio = null;

    // sensor sampling options
    private static final int AUDIO_RECORDING_RATE = 44100;

    // the pull-values thread sleeps for this amount of ms in every iteration before pulling new sensor values from MainActivity and pushing them
    private static final int THREAD_INTERVAL = 8;
    // the sampling rate of every stream depends on the thread sleep interval, not the OS
    private static final int SAMPLING_RATE = 1000 / THREAD_INTERVAL; // how many values do we receive per ms

    // audio settings
    private static final int CHANNEL = AudioFormat.CHANNEL_IN_STEREO;
    private final int audio_channel_count = 2;
    private static final int FORMAT = AudioFormat.ENCODING_PCM_FLOAT;
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
    float[] audio_buffer = new float[BUFFER_SIZE];

    public AudioBridge(Context context) {
        mAudioThread = new Thread(new Runnable() {
            @Override
            public void run() {
                audio = new LSL.StreamInfo("Audio " + Build.MODEL,
                        "audio", audio_channel_count, AUDIO_RECORDING_RATE, LSL.ChannelFormat.float32, Build.FINGERPRINT);
                try {
                    audioOutlet = new LSL.StreamOutlet(audio);
                    recorder = new AudioRecord(MediaRecorder.AudioSource.MIC, AUDIO_RECORDING_RATE, CHANNEL, FORMAT, BUFFER_SIZE);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                while (!checkFlag) {
                    recorder.startRecording();
                    recorder.read(audio_buffer, 0, audio_buffer.length, AudioRecord.READ_BLOCKING);
                    audioOutlet.push_chunk(audio_buffer);
                }
            }
        });
        mAudioThread.start();
    }

    public void Start() {
    }

    public void Stop() {
        Log.e(TAG, "Stopping audio bridge");
        checkFlag = true;
        try {
            mAudioThread.join();
        } catch (InterruptedException e) {

        }
        audioOutlet.close();
        audio.destroy();
        audio = null;

        if (null != recorder) {
            try {
                recorder.stop();
                recorder.release();
            } catch (RuntimeException ex) {
                Log.e("AudioBridge","Error while stopping audio recording: "+ex.toString());
                recorder.release();
            }
        }
    }

    @Override
    protected void finalize() {
    }
}

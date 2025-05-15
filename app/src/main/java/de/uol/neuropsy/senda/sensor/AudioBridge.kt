package de.uol.neuropsy.senda.sensor

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import edu.ucsd.sccn.LSL
import edu.ucsd.sccn.LSL.StreamInfo
import edu.ucsd.sccn.LSL.StreamOutlet
import java.io.IOException
import kotlin.system.exitProcess

class AudioBridge(context: Context?) {
    //LSL Outlets
    var checkFlag = false
    var mAudioThread: Thread

    //LSL Streams
    private lateinit var audio: StreamInfo
    private val audio_channel_count = 2
    private var recorder: AudioRecord? = null
    var audio_buffer = FloatArray(BUFFER_SIZE)

    companion object {
        var TAG = AudioBridge::class.java.simpleName
        var audioOutlet: StreamOutlet? = null

        // sensor sampling options
        private const val AUDIO_RECORDING_RATE = 44100

        // the pull-values thread sleeps for this amount of ms in every iteration before pulling new sensor values from MainActivity and pushing them
        private const val THREAD_INTERVAL = 8

        // the sampling rate of every stream depends on the thread sleep interval, not the OS
        private const val SAMPLING_RATE =
            1000 / THREAD_INTERVAL // how many values do we receive per ms

        // audio settings
        private const val CHANNEL = AudioFormat.CHANNEL_IN_STEREO
        private const val FORMAT = AudioFormat.ENCODING_PCM_FLOAT

        /**
         * Factor by that the minimum buffer size is multiplied. The bigger the factor is the less
         * likely it is that samples will be dropped, but more memory will be used. The minimum buffer
         * size is determined by [AudioRecord.getMinBufferSize] and depends on the
         * recording settings.
         */
        private const val BUFFER_SIZE_FACTOR = 2

        /**
         * Size of the buffer where the audio data is stored by Android
         */
        private val BUFFER_SIZE =
            AudioRecord.getMinBufferSize(AUDIO_RECORDING_RATE, CHANNEL, FORMAT) * BUFFER_SIZE_FACTOR
    }

    init {
        mAudioThread = Thread {
            audio = StreamInfo(
                "Audio " + Build.MODEL,
                "audio",
                audio_channel_count,
                AUDIO_RECORDING_RATE.toDouble(),
                LSL.ChannelFormat.float32,
                Build.FINGERPRINT
            )
            try {
                audioOutlet = StreamOutlet(audio)
                recorder = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    AUDIO_RECORDING_RATE,
                    CHANNEL,
                    FORMAT,
                    BUFFER_SIZE
                )
            } catch (e: IOException) {
                e.printStackTrace()
            }
            catch(e: SecurityException){
                Log.e(TAG,"Encountered security exception while trying to initialize audio recorder. Please check permissions.")
                exitProcess(-1)
            }
            while (!checkFlag) {
                recorder!!.startRecording()
                recorder!!.read(audio_buffer, 0, audio_buffer.size, AudioRecord.READ_BLOCKING)
                audioOutlet!!.push_chunk(audio_buffer)
            }
        }
        mAudioThread.start()
    }

    fun Start() {}
    fun Stop() {
        Log.e(TAG, "Stopping audio bridge")
        checkFlag = true
        try {
            mAudioThread.join()
        } catch (e: InterruptedException) {
        }

        audioOutlet!!.close()
        recorder?.release()
        recorder=null

        }
    }
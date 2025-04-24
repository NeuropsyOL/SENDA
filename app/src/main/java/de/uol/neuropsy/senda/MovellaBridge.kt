package de.uol.neuropsy.senda

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.os.Build
import android.util.Log
import com.xsens.dot.android.sdk.events.DotData
import com.xsens.dot.android.sdk.interfaces.DotDeviceCallback
import com.xsens.dot.android.sdk.models.DotDevice
import com.xsens.dot.android.sdk.models.DotPayload
import com.xsens.dot.android.sdk.models.FilterProfileInfo
import edu.ucsd.sccn.LSL
import edu.ucsd.sccn.LSL.StreamInfo
import edu.ucsd.sccn.LSL.StreamOutlet
import java.io.IOException

class MovellaBridge(context: Context, btDevice: BluetoothDevice?, hostActivity: MainActivity) :
    DotDeviceCallback {

    private var mMarkerStreamInfo: StreamInfo? = null
    private var mMarkerStreamOutlet: StreamOutlet? = null
    private var mDataStreamInfo: StreamInfo? = null
    private var mDataStreamOutlet: StreamOutlet? = null
    val handle: DotDevice?
        get() = if (!mDevice.isInitDone) {
            null
        } else mDevice
    private var mHost: MainActivity? = hostActivity
    private val mContext = context
    private val mDevice = DotDevice(mContext, btDevice, this)

    init {
        mDevice.connect()
    }

    fun Start() {
        try {
            mMarkerStreamOutlet = StreamOutlet(mMarkerStreamInfo)
        } catch (e: IOException) {
            Log.e(TAG, e.toString())
            e.printStackTrace()
        }
        try {
            mDataStreamOutlet = StreamOutlet(mDataStreamInfo)
        } catch (e: IOException) {
            Log.e(TAG, e.toString())
            e.printStackTrace()
        }
        assert(mDataStreamOutlet != null)
        mDevice!!.startMeasuring()
        Log.i(TAG, displayName + " StartMeasuring")
    }

    fun Stop() {
        if (mDevice != null) {
            Log.e("MovellaBridge", displayName + " " + mDevice.connectionState)
            if (mDevice.connectionState == DotDevice.CONN_STATE_CONNECTED) {
                Log.e("MovellaBridge", displayName + " " + mDevice.measurementState)
                if (mDevice.measurementState == DotDevice.MEASUREMENT_STATE_ON) mDevice.stopMeasuring()
            }
        }
        Log.e("MovellaBridge", displayName + ": Finished handling device")
        if (mDataStreamOutlet != null) {
            Log.e("MovellaBridge", displayName + ": Close data stream")
            mDataStreamOutlet!!.close()
            mDataStreamOutlet = null
        }
        if (mMarkerStreamOutlet != null) {
            Log.e("MovellaBridge", displayName + ": Close marker stream")
            mMarkerStreamOutlet!!.close()
            mMarkerStreamOutlet = null
        }
    }

    fun Disconnect(){
        mDevice.disconnect()
    }

    fun IsSynced() : Boolean {
        return mDevice.isSynced
    }

    fun Address() : String {
        return mDevice.address
    }

    val displayName: String
        get() = mDevice!!.name + " " + mDevice.tag

    override fun onDotConnectionChanged(s: String, i: Int) {}
    override fun onDotServicesDiscovered(s: String, i: Int) {}
    override fun onDotFirmwareVersionRead(s: String, s1: String) {}
    override fun onDotTagChanged(s: String, s1: String) {}
    override fun onDotBatteryChanged(s: String, i: Int, i1: Int) {}
    override fun onDotDataChanged(s: String, dotData: DotData) {
        val data = FloatArray(7)
        for (i in 0..2) {
            data[i] = dotData.freeAcc[i]
            data[i + 3] = dotData.euler[i].toFloat()
        }
        data[6] = dotData.sampleTimeFine.toFloat()
        if (mDataStreamOutlet != null) {
            mDataStreamOutlet!!.push_sample(data)
        } else {
            Log.e(TAG, displayName + " mStreamOutlet is Null!")
        }
    }

    override fun onDotInitDone(s: String) {
        Log.i(
            TAG,
            "Movella initialized " + s + " " + mDevice!!.tag + " " + mDevice.serialNumber + "!"
        )
        mDevice.measurementMode = DotPayload.PAYLOAD_TYPE_COMPLETE_EULER
        mHost!!.onInitDone(this)
        mDataStreamInfo = StreamInfo(
            displayName,
            "misc",
            7,
            mDevice.currentOutputRate.toDouble(),
            LSL.ChannelFormat.float32,
            Build.FINGERPRINT
        )
        mMarkerStreamInfo = StreamInfo(
            displayName + " Marker",
            "Markers",
            1,
            LSL.IRREGULAR_RATE,
            LSL.ChannelFormat.string,
            Build.FINGERPRINT
        )
    }

    override fun onDotButtonClicked(s: String, l: Long) {
        val sample = arrayOfNulls<String>(1)
        sample[0] = mDevice!!.tag
        Log.i(TAG, displayName + " button pressed!")
        if (mMarkerStreamOutlet != null) mMarkerStreamOutlet!!.push_sample(sample)
    }

    override fun onDotPowerSavingTriggered(s: String) {}
    override fun onReadRemoteRssi(s: String, i: Int) {}
    override fun onDotOutputRateUpdate(s: String, i: Int) {}
    override fun onDotFilterProfileUpdate(s: String, i: Int) {}
    override fun onDotGetFilterProfileInfo(s: String, arrayList: ArrayList<FilterProfileInfo>) {}
    override fun onSyncStatusUpdate(s: String, b: Boolean) {}

    companion object {
        var TAG = MovellaBridge::class.java.simpleName
    }
}
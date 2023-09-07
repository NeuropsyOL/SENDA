package de.uol.neuropsy.senda;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.os.Build;
import android.util.Log;

import com.xsens.dot.android.sdk.events.DotData;
import com.xsens.dot.android.sdk.interfaces.DotDeviceCallback;
import com.xsens.dot.android.sdk.models.DotDevice;
import com.xsens.dot.android.sdk.models.DotPayload;
import com.xsens.dot.android.sdk.models.FilterProfileInfo;

import java.io.IOException;
import java.util.ArrayList;

import edu.ucsd.sccn.LSL;

public class MovellaBridge implements DotDeviceCallback {

    static String TAG = MovellaBridge.class.getSimpleName();

    private LSL.StreamInfo mStreamInfo;
    private LSL.StreamOutlet mStreamOutlet;

    public MovellaBridge(Context context, BluetoothDevice btDevice, MainActivity hostActivity) {
        mHost = hostActivity;
        mContext = context;
        mDevice = new DotDevice(mContext, btDevice, this);
        mDevice.connect();
        Log.e(TAG, "Waiting for connection to " + btDevice.getAddress() + "...");
    }

    public DotDevice getDevice() {
        if (!mDevice.isInitDone()) {
            Log.e(TAG, mDevice.getAddress() + " waiting for init to be done...");
            return null;
        }

        return mDevice;
    }

    private MainActivity mHost = null;
    private Boolean mIsConnected = false;
    private final DotDevice mDevice;
    private Context mContext;

    public Boolean isConnected() {
        return mIsConnected;
    }

    void Start() {
        try {
            mStreamOutlet = new LSL.StreamOutlet(mStreamInfo);
        } catch (IOException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
        }
        mDevice.setMeasurementMode(DotPayload.PAYLOAD_TYPE_COMPLETE_EULER);
        mDevice.startMeasuring();
        Log.e(TAG, "Start measuring: " + mDevice.getTag() + " pushing into " + mStreamOutlet.info().name());
    }

    void Stop() {
        if (mDevice != null)
            mDevice.stopMeasuring();
        if (mStreamOutlet != null)
            mStreamOutlet.close();
    }

    public String getDisplayName(){
        return mDevice.getName()+" "+mDevice.getTag();
    }
    @Override
    public void onDotConnectionChanged(String s, int i) {
    }

    @Override
    public void onDotServicesDiscovered(String s, int i) {

    }

    @Override
    public void onDotFirmwareVersionRead(String s, String s1) {

    }

    @Override
    public void onDotTagChanged(String s, String s1) {

    }

    @Override
    public void onDotBatteryChanged(String s, int i, int i1) {

    }

    @Override
    public void onDotDataChanged(String s, DotData dotData) {
        if (dotData.getAcc() != null) {
            mStreamOutlet.push_sample(dotData.getFreeAcc());
        }
        else
            Log.e(TAG, "Could not get acceleration!");
    }

    @Override
    public void onDotInitDone(String s) {
        Log.e(TAG, "Movella initialized " + s + " " + mDevice.getTag() + " " + mDevice.getSerialNumber() + "!");
        mHost.onInitDone(this);
        mStreamInfo = new LSL.StreamInfo(mDevice.getName() + " " + mDevice.getTag(),
                "eeg", 3, LSL.IRREGULAR_RATE, LSL.ChannelFormat.float32, Build.FINGERPRINT);
        Log.e(TAG, "New stream info: " + mStreamInfo.name());
    }

    @Override
    public void onDotButtonClicked(String s, long l) {

    }

    @Override
    public void onDotPowerSavingTriggered(String s) {

    }

    @Override
    public void onReadRemoteRssi(String s, int i) {

    }

    @Override
    public void onDotOutputRateUpdate(String s, int i) {

    }

    @Override
    public void onDotFilterProfileUpdate(String s, int i) {

    }

    @Override
    public void onDotGetFilterProfileInfo(String s, ArrayList<FilterProfileInfo> arrayList) {

    }

    @Override
    public void onSyncStatusUpdate(String s, boolean b) {

    }
}

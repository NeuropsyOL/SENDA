package de.uol.neuropsy.senda;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.ScanSettings;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.widget.AppCompatCheckBox;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.xsens.dot.android.sdk.interfaces.DotScannerCallback;
import com.xsens.dot.android.sdk.interfaces.DotSyncCallback;
import com.xsens.dot.android.sdk.models.DotDevice;
import com.xsens.dot.android.sdk.models.DotSyncManager;
import com.xsens.dot.android.sdk.utils.DotScanner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

import com.xsens.dot.android.sdk.DotSdk;

public class MainActivity extends Activity implements DotScannerCallback, DotSyncCallback {

    private Boolean isScanning = false;
    private DotScanner mXsScanner;
    public HashMap<String, MovellaBridge> mConnectedDevices = new HashMap<>();
    public HashMap<String, MovellaBridge> mActiveDevices = new HashMap<>();
    static String TAG = MainActivity.class.getSimpleName();

    @SuppressLint("StaticFieldLeak")
    static TextView tv;

    static boolean isRunning = false;

    List<String> SensorName = new ArrayList<>();
    ArrayAdapter<String> adapter;
    ListView lv;
    public static ArrayList<String> selectedItems = new ArrayList<>();

    //Streaming Identification
    @SuppressLint("StaticFieldLeak")
    static ImageView streamingNowBtn;
    @SuppressLint("StaticFieldLeak")
    static TextView streamingNow;

    ProgressBar progressBar;

    int backButtonCount = 0;

    //Settings button
    ImageView settings_button;

    //Requesting run-time permissions
    //Create placeholder for user's consent to record_audio and access location permissions.
    //This will be used in handling callback
    private final int PERMISSIONS_REQUEST_CODE = 1;

    //
    private final int START_SCAN_REQUEST_CODE = 2000;

    public static List<Intent> POWERMANAGER_INTENTS = Arrays.asList(new Intent().setComponent(new ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")), new Intent().setComponent(new ComponentName("com.letv.android.letvsafe", "com.letv.android.letvsafe.AutobootManageActivity")), new Intent().setComponent(new ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity")), new Intent().setComponent(new ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")), new Intent().setComponent(new ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity")), new Intent().setComponent(new ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity")), new Intent().setComponent(new ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity")), new Intent().setComponent(new ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager")), new Intent().setComponent(new ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")), new Intent().setComponent(new ComponentName("com.asus.mobilemanager", "com.asus.mobilemanager.entry.FunctionActivity")).setData(android.net.Uri.parse("mobilemanager://function/entry/AutoStart")));

    private Intent LSLIntent = null;

    // Override the necessary lifecycle methods
    @Override
    protected void onStart() {
        super.onStart();
        Log.e("Location", "onStart called");
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.e(TAG, "MainActivity::OnStop()");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.e(TAG, "MainActivity::OnDestroy()");
        for (MovellaBridge device : mActiveDevices.values())
            device.Stop();
    }

    public MainActivity() {
    }

    /**
     * Setup for Xsens DOT SDK.
     */
    private void initDotSdk() {
        // Get the version name of SDK.
        String version = DotSdk.getSdkVersion();
        Log.i(TAG, "initDotSdk() - version: $version");
        // Enable this feature to monitor logs from SDK.
        DotSdk.setDebugEnabled(false);
        // Enable this feature then SDK will start reconnection when the connection is lost.
        DotSdk.setReconnectEnabled(true);
    }


    /**
     * Called when the activity is first created.
     */
    @SuppressLint("SetTextI18n")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initDotSdk();
        setContentView(R.layout.activity_main);
        tv = (TextView) findViewById(R.id.tv);

        bindButtons();

        streamingNow = (TextView) findViewById(R.id.streamingNow);
        streamingNowBtn = (ImageView) findViewById(R.id.streamingNowBtn);

        progressBar = (ProgressBar) findViewById(R.id.progressBar);
        startPowerSaverIntent(this);

        tv.setText("Available Streams: ");
        lv = (ListView) findViewById(R.id.sensors);
        lv.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        adapter = new ArrayAdapter<>(getApplicationContext(), R.layout.list_view_text, R.id.streamsSelected, SensorName);
        lv.setAdapter(adapter);

        SwipeRefreshLayout mSwipeRefreshLayout = (SwipeRefreshLayout) findViewById(R.id.swiperefresh);

        mSwipeRefreshLayout.setOnRefreshListener(() -> {
            checkAvailableSensors();
            StartScan();
        });
        mXsScanner = new DotScanner(this, this);
        mXsScanner.setScanMode(ScanSettings.SCAN_MODE_BALANCED);

        checkAvailableSensors();

        lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                // selected item
                String selectedItem = ((TextView) view).getText().toString();
                if (selectedItem.contains("Audio") && lv.isItemChecked(position)) {
                    if (!checkAudioPermission()) {
                        lv.setItemChecked(position, false);
                        requestAudioPermissions(1000 + position);
                    }
                }
                if (selectedItem.contains("Location") && lv.isItemChecked(position)) {
                    if (!checkLocationPermission()) {
                        requestLocationPermissions(1000 + position);
                        lv.setItemChecked(position, false);
                    }
                }
            }
        });
    } // end onCreate

    public Boolean isActivated(String s) {
        for (String item : selectedItems) {
            if (item.equals(s)) return true;
        }
        return false;
    }

    private void myStartForegroundService(Intent intent) {
        intent.putExtra("inputExtra", "SENDA Foreground Service in Android");
        ContextCompat.startForegroundService(this, intent);
    }

    // Check if the permissions are already granted
    private boolean checkLocationPermission() {
        boolean hasFineLocationPermission = (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED);
        boolean hasBackgroundLocationPermission = (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED);
        return hasFineLocationPermission && hasBackgroundLocationPermission;
    }

    private boolean checkBackgroundLocationPermission() {
        return (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED);
    }

    private boolean checkAudioPermission() {
        return (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED);
    }

    private boolean checkBluetoothPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            return (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED);
        else return true;
    }

    private void requestAudioPermissions(int requestCode) {
        String[] permissions = new String[]{Manifest.permission.RECORD_AUDIO};
        ActivityCompat.requestPermissions(this, permissions, requestCode);
    }

    private void requestBluetoothPermissions(int requestCode) {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            String[] permissions = new String[]{Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT};
            ActivityCompat.requestPermissions(this, permissions, requestCode);
        }
    }

    private boolean checkAndRequestBluetoothEnabled() {
        BluetoothManager bluetoothManager = getSystemService(BluetoothManager.class);
        BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "This device does not support Bluetooth", Toast.LENGTH_SHORT).show();
            return false;
        }
        if (!bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, 1002);
            return false;
        }
        return true;
    }

    private void requestLocationPermissions(int requestCode) {
        String[] permissions;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
            permissions = new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION};
        else {
            permissions = new String[]{Manifest.permission.ACCESS_FINE_LOCATION};
        }
        ActivityCompat.requestPermissions(this, permissions, requestCode);
    }

    private void requestBackgroundLocationPermission(int requestCode) {
        String[] permissions = new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION};
        ActivityCompat.requestPermissions(this, permissions, requestCode);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        for (int ii = 0; ii < permissions.length; ii++) {
            if (grantResults[ii] == PackageManager.PERMISSION_GRANTED) {
                // FINE_LOCATION needs special treatment b/c we need to request BACKGROUND_LOCATION after it
                if (permissions[ii].equals(Manifest.permission.ACCESS_FINE_LOCATION)) {
                    // Background location has to be requested after fine location is granted.
                    if (!checkBackgroundLocationPermission()) {
                        requestBackgroundLocationPermission(requestCode);
                    }
                }
                // All other cases (including background location) set the list item checked if we came from the OnClickListener
                else if (requestCode >= 1000 && requestCode < 2000) {
                    lv.setItemChecked(requestCode - 1000, true);
                }
                // We came from StartScan(), commence scan
                else if (requestCode >= 2000) {
                    Log.e(TAG, "Coming from StartScan, commencing scan");
                    StartScan();
                }
            } else {
                // Denied permission and should not show rationale -> Permission request is invisible to user, show error message
                if (!shouldShowRequestPermissionRationale(permissions[ii])) {
                    //TODO Map permissions string to human readable permission
                    try {
                        Toast.makeText(this, "Missing permission: " + this.getPackageManager().getPermissionInfo(permissions[ii], 0).loadLabel(this.getPackageManager()), Toast.LENGTH_SHORT).show();
                    } catch (Exception e) {
                        Toast.makeText(this, "Missing a permission and encountered an error trying to find out which.", Toast.LENGTH_SHORT).show();
                        Log.e(TAG, "Missing a permission and encountered an error trying to find out which:" + e.toString());
                    }
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    Uri uri = Uri.fromParts("package", getPackageName(), null);
                    intent.setData(uri);
                    startActivity(intent);
                }
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (backButtonCount >= 1) {
            if (isRunning) {
                for (MovellaBridge device : mActiveDevices.values()) {
                    device.Stop();
                }
                stopService(LSLIntent);
            }
            for (MovellaBridge device : mConnectedDevices.values()) {
                SensorName.remove(device.getDisplayName());
                device.getDevice().disconnect();
                mConnectedDevices.remove(device);
                adapter.notifyDataSetChanged();
            }
            this.finishAffinity();
            backButtonCount = 0;
        } else {
            Toast.makeText(this, "Press the back button once again to close the application.", Toast.LENGTH_SHORT).show();
            backButtonCount++;
        }
    }

    public static void startPowerSaverIntent(final Context context) {
        SharedPreferences settings = context.getSharedPreferences("ProtectedApps", Context.MODE_PRIVATE);
        boolean skipMessage = settings.getBoolean("skipProtectedAppCheck", false);
        if (!skipMessage) {
            final SharedPreferences.Editor editor = settings.edit();
            boolean foundCorrectIntent = false;
            for (final Intent intent : POWERMANAGER_INTENTS) {
                if (isCallable(context, intent)) {
                    foundCorrectIntent = true;
                    final AppCompatCheckBox dontShowAgain = new AppCompatCheckBox(context);
                    dontShowAgain.setText(R.string.dont_show_again);
                    dontShowAgain.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                        @Override
                        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                            editor.putBoolean("skipProtectedAppCheck", isChecked);
                            editor.apply();
                        }
                    });

                    new AlertDialog.Builder(context).setTitle(Build.MANUFACTURER + " Protected Apps").setMessage(String.format("%s requires to be enabled in 'Protected Apps' to function properly.%n", context.getString(R.string.app_name))).setView(dontShowAgain).setPositiveButton("Go to settings", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            context.startActivity(intent);
                        }
                    }).setNegativeButton(android.R.string.cancel, null).show();
                    break;
                }
            }
            if (!foundCorrectIntent) {
                editor.putBoolean("skipProtectedAppCheck", true);
                editor.apply();
            }
        }
    }

    private static boolean isCallable(Context context, Intent intent) {
        List<ResolveInfo> list = context.getPackageManager().queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
        return list.size() > 0;
    }

    @Override
    public void onDotScanned(BluetoothDevice bluetoothDevice, int i) {
        new MovellaBridge(this, bluetoothDevice, this);
        Log.e(TAG, "Initializing " + bluetoothDevice.getAddress());
    }

    void StartScan() {
        // TODO trigger scan in permission result callback when we come from here
        if (!checkBluetoothPermission()) {
            Log.i(TAG, "Do not have Bluetooth permission, asking for it");
            requestBluetoothPermissions(START_SCAN_REQUEST_CODE);
            ((SwipeRefreshLayout) findViewById(R.id.swiperefresh)).setRefreshing(false);
            return;
        } else if (!checkAndRequestBluetoothEnabled()) return;
        Log.e(TAG, "Starting scan");
        for (MovellaBridge device : mConnectedDevices.values()) {
            // Do not disconnect currently active devices
            if (device.getDevice() != null && isRunning && !mActiveDevices.containsKey(device.getDevice().getAddress())) {
                SensorName.remove(device.getDisplayName());
                device.getDevice().disconnect();
            }
        }
        adapter.notifyDataSetChanged();
        mConnectedDevices.clear();
        ((SwipeRefreshLayout) findViewById(R.id.swiperefresh)).setRefreshing(true);
        mXsScanner.startScan();
        isScanning = true;

        final Handler handler = new Handler(Looper.getMainLooper());
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                StopScan();
            }
        }, 5000);
    }


    void StopScan() {
        Log.e(TAG, "Stopping scan");
        mXsScanner.stopScan();
        isScanning = false;
        ((SwipeRefreshLayout) findViewById(R.id.swiperefresh)).setRefreshing(false);
    }

    public void onInitDone(MovellaBridge device) {
        mConnectedDevices.put(device.getDevice().getAddress(), device);
        if (!SensorName.contains(device.getDisplayName())) {
            SensorName.add(device.getDisplayName());
            adapter.notifyDataSetChanged();
        }
    }

    void bindButtons() {
        LSLIntent = new Intent(this, LSLService.class);
        Button start = (Button) findViewById(R.id.startLSL);
        start.setOnClickListener(v -> {
            onStartButtonPressedPreSync();
            syncMovellaSensors();
            // onStartButtonPressedPostSync(); Called by onSyncDone callback
        });

        Button stop = (Button) findViewById(R.id.stopLSL);
        stop.setOnLongClickListener(v -> {
            if (backButtonCount < 2) {
                backButtonCount++;
                return true;
            }

            return true;
        });

        stop.setOnClickListener(v -> {
            if (isRunning) {
                DotSyncManager.getInstance(this).stopSyncing();
                for (MovellaBridge device : mActiveDevices.values()) {
                    device.Stop();
                }
                stopService(LSLIntent);
                lv.setEnabled(true);
                lv.setAlpha(1f);
            }
        });
    }

    void syncMovellaSensors() {
        if (mActiveDevices.size() < 2) {
            Log.e("syncMovellaSensors", "No syncing needed");
            //No syncing needed, proceed to PostSync
            onStartButtonPressedPostSync();
            return;
        }
        Log.e("MainActivity", "Try syncing");
        streamingNow.setVisibility(View.VISIBLE);
        streamingNow.setText("Syncing Movella sensors...");


        //Animation for Streaming
        Animation animation = new AlphaAnimation((float) 0.5, 0);
        animation.setDuration(850);
        animation.setInterpolator(new LinearInterpolator()); // do not alter
        // animation rate
        animation.setRepeatCount(Animation.INFINITE); // Repeat animation
        // infinitely
        animation.setRepeatMode(Animation.REVERSE); // Reverse animation at the
        // end so the button will fade back in
        // streamingNowBtn.startAnimation(animation);
        streamingNow.startAnimation(animation);

        ArrayList<DotDevice> activeDeviceList = mActiveDevices.values().stream().map(MovellaBridge::getDevice).collect(Collectors.toCollection(ArrayList::new));
        activeDeviceList.get(0).setRootDevice(true);
        activeDeviceList.forEach(v -> Log.e("syncMovellaSensors", "I must sync: " + v.getTag()));
        DotSyncManager.getInstance(this).startSyncing(activeDeviceList, 1);
    }

    void checkAvailableSensors() {
        SensorName.clear();
        SensorManager sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        //Not available in Java 7: sensor.stream().anyMatch(s -> s.getType() == Sensor.TYPE_ACCELEROMETER))
        if (sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null)
            SensorName.add("Accelerometer");
        if (sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT) != null) SensorName.add("Light");
        if (sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY) != null)
            SensorName.add("Proximity");
        if (sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY) != null) SensorName.add("Gravity");
        if (sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION) != null)
            SensorName.add("Linear Acceleration");
        if (sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) != null)
            SensorName.add("Rotation Vector");
        if (sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null)
            SensorName.add("Step Count");
        // Do not need to check: Asking for audio permission if user selects this item
        SensorName.add("Audio");
        // Do not need to check: Asking for audio permission if user selects this item
        SensorName.add("Audio classifier");
        // Do not need to check: Asking for location permission if user selects this item
        SensorName.add("Location");
        adapter.notifyDataSetChanged();
    }

    void onStartButtonPressedPreSync() {
        Log.e("MainActivity", "OnStartButtonPressedPreSync " + android.os.Process.myTid());
        if (!isRunning) {
            lv.setEnabled(false);
            lv.setAlpha(0.1f);
            // Build the list of selected items and give it over to the LSLIntent
            SparseBooleanArray checked = lv.getCheckedItemPositions();
            for (int i = 0; i < lv.getAdapter().getCount(); i++) {
                Log.e(TAG, lv.getItemAtPosition(i).toString() + " " + checked.get(i));
                LSLIntent.putExtra(lv.getItemAtPosition(i).toString(), checked.get(i));
            }
            for (MovellaBridge device : mConnectedDevices.values()) {
                if (LSLIntent.getBooleanExtra(device.getDisplayName(), false)) {
                    mActiveDevices.put(device.getDevice().getAddress(), device);
                    if (device.getDevice().isSynced()) {
                        Log.e("MainActivity", device.getDisplayName() + " already synced, stopping sync...");
                        DotSyncManager.getInstance(this).stopSyncing();
                    }
                    Log.e(TAG, "Adding movella device to list of active devices:" + device.getDisplayName());
                }
            }
        }
    }

    void onStartButtonPressedPostSync() {
        Log.e("MainActivity", "OnStartButtonPressedPostSync " + android.os.Process.myTid());
        runOnUiThread(new Thread(() -> {
            streamingNow.setVisibility(View.INVISIBLE);
            streamingNow.setText("Streaming Data...");
            progressBar.setVisibility(View.GONE);
        }
        ));
        for (MovellaBridge device : mActiveDevices.values()) {
            Log.e(TAG, "Starting movella device " + device.getDisplayName());
            device.Start();
        }
        // make this a foreground service so that android does not kill it while it is in the background
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            myStartForegroundService(LSLIntent);

        } else { // try our best with older Androids
            startService(LSLIntent);
        }

    }

    @Override
    public void onSyncingStarted(String s, boolean b, int i) {
        progressBar.setVisibility(View.VISIBLE); // Make ProgressBar visible
        progressBar.setProgress(0);
        Log.e("MainActivity", "onSyncingStarted " + b + " " + i);
    }

    @Override
    public void onSyncingProgress(int i, int i1) {
        progressBar.setProgress(i);
        Log.e("MainActivity", "onSyncingProgress " + i + " " + i1);
    }

    @Override
    public void onSyncingResult(String s, boolean b, int i) {
        Log.e("MainActivity", "onSyncingResult " + s + " " + b + " " + i);
    }

    @Override
    public void onSyncingDone(HashMap<String, Boolean> hashMap, boolean b, int i) {
        Log.e("MainActivity", "onSyncingDone " + b + " " + i);
        if (b) onStartButtonPressedPostSync();
        else {
            runOnUiThread(new Thread(() -> {
                lv.setEnabled(true);
                lv.setAlpha(1.0f);
                Log.e("MainActivity", "SYNC FAILED");
                Toast.makeText(this, "Syncing Failed!", Toast.LENGTH_LONG);
                streamingNow.setText("Syncing Failed!");
                progressBar.setProgress(0);
                progressBar.setVisibility(View.GONE);
                mActiveDevices.clear();
            }));
        }
    }

    @Override
    public void onSyncingStopped(String s, boolean b, int i) {
        Log.e("MainActivity", "onSyncingStopped");
    }
}


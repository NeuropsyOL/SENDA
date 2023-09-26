package de.uol.neuropsy.senda;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothDevice;
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
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.widget.AppCompatCheckBox;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.xsens.dot.android.sdk.interfaces.DotScannerCallback;
import com.xsens.dot.android.sdk.utils.DotScanner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;


public class MainActivity extends Activity implements DotScannerCallback {

    private Boolean isScanning = false;
    private DotScanner mXsScanner;
    //public ArrayList<MovellaBridge> mDevices = new ArrayList<>();
    public HashMap<String, MovellaBridge> mDevices = new HashMap<>();

    static String TAG = MainActivity.class.getSimpleName();

    @SuppressLint("StaticFieldLeak")
    static TextView tv;

    static boolean isRunning = false;
    static boolean checkFlag = false;

    List<String> SensorName = new ArrayList<>();
    ArrayAdapter<String> adapter;
    ListView lv;
    public static ArrayList<String> selectedItems = new ArrayList<>();

    //Streaming Identification
    @SuppressLint("StaticFieldLeak")
    static ImageView streamingNowBtn;
    @SuppressLint("StaticFieldLeak")
    static TextView streamingNow;

    int backButtonCount = 0;

    //Settings button
    ImageView settings_button;

    //Requesting run-time permissions
    //Create placeholder for user's consent to record_audio and access location permissions.
    //This will be used in handling callback
    private final int PERMISSIONS_REQUEST_CODE = 1;

    public static boolean audioPermission = true;
    public static boolean locationPermission = true;

    public static List<Intent> POWERMANAGER_INTENTS = Arrays.asList(new Intent().setComponent(new ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")), new Intent().setComponent(new ComponentName("com.letv.android.letvsafe", "com.letv.android.letvsafe.AutobootManageActivity")), new Intent().setComponent(new ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity")), new Intent().setComponent(new ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")), new Intent().setComponent(new ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity")), new Intent().setComponent(new ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity")), new Intent().setComponent(new ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity")), new Intent().setComponent(new ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager")), new Intent().setComponent(new ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")), new Intent().setComponent(new ComponentName("com.asus.mobilemanager", "com.asus.mobilemanager.entry.FunctionActivity")).setData(android.net.Uri.parse("mobilemanager://function/entry/AutoStart")));

    private Intent LSLIntent = null;

    // Override the necessary lifecycle methods
    @Override
    protected void onStart() {
        super.onStart();
        Log.e("Location", "onStart called");
        // Check if the location permission is granted
        if (checkLocationPermission()) {
            // Request location updates

        } else {
            Log.w("onStart", "Do not have location permissions!");
            // Request the location permission

        }
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
     * Called when the activity is first created.
     */
    @SuppressLint("SetTextI18n")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        tv = (TextView) findViewById(R.id.tv);

        bindButtons();

        streamingNow = (TextView) findViewById(R.id.streamingNow);
        streamingNowBtn = (ImageView) findViewById(R.id.streamingNowBtn);

        startPowerSaverIntent(this);


        tv.setText("Available Streams: ");
        Log.i(TAG, "Bluetooth permission: " + Boolean.toString(checkBluetoothPermission()));

        lv = (ListView)

                findViewById(R.id.sensors);
        lv.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        adapter = new ArrayAdapter<>(getApplicationContext(), R.layout.list_view_text, R.id.streamsSelected, SensorName);
        lv.setAdapter(adapter);


        mXsScanner = new DotScanner(this, this);
        mXsScanner.setScanMode(ScanSettings.SCAN_MODE_BALANCED);

        checkAvailableSensors();

        lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                // selected item
                String selectedItem = ((TextView) view).getText().toString();
                if (selectedItems.contains(selectedItem))
                    selectedItems.remove(selectedItem); //remove deselected item from the list of selected items
                else
                    selectedItems.add(selectedItem); //add selected item to the list of selected items
                getSelectedItems();
            }
        });
    } // end onCreate

    public Boolean isActivated(String s) {
        for (String item : selectedItems) {
            if (item.equals(s))
                return true;
        }
        return false;
    }

    private void myStartForegroundService(Intent intent) {
        intent.putExtra("inputExtra", "SENDA Foreground Service in Android");
        ContextCompat.startForegroundService(this, intent);
    }

    // Check if the permissions are already granted
    private boolean checkPermissions() {
        return checkAudioPermission() && checkLocationPermission();
    }

    private boolean checkLocationPermission() {
        boolean hasFineLocationPermission = (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED);
        boolean hasBackgroundLocationPermission = (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED);
        Log.e("LOCATION", "Location and Background permission: " + hasFineLocationPermission + " " + hasBackgroundLocationPermission);
        return hasFineLocationPermission && hasBackgroundLocationPermission;
    }

    private boolean checkAudioPermission() {
        return (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED);
    }

    private boolean checkBluetoothPermission() {
        return (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED);
    }

    private void requestAllPermissions() {
        String[] permissions = new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT};
        ActivityCompat.requestPermissions(this, permissions, PERMISSIONS_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            for (int ii = 0; ii < permissions.length; ii++) {
                if (permissions[ii].equals(Manifest.permission.RECORD_AUDIO))
                    audioPermission = (grantResults[ii] == PackageManager.PERMISSION_GRANTED);
                if (permissions[ii].equals(Manifest.permission.ACCESS_FINE_LOCATION)) {
                    Log.w("PermissionResult", Manifest.permission.ACCESS_FINE_LOCATION);
                    locationPermission = (grantResults[ii] == PackageManager.PERMISSION_GRANTED);
                }
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (backButtonCount >= 1) {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_HOME);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
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

    private void getSelectedItems() {
        for (String item : selectedItems) {
            if (LSLIntent != null)
                LSLIntent.putExtra(item, true);
        }
    }

    public static void showText(String s) {
        tv.setText(s);
    }

    @Override
    public void onDotScanned(BluetoothDevice bluetoothDevice, int i) {
        new MovellaBridge(this, bluetoothDevice, this);
        Log.e(TAG, "Initializing " + bluetoothDevice.getAddress());
    }

    void TriggerScan() {
        Button scan = findViewById(R.id.btnScan);
        if (isScanning) {
            Log.e(TAG, "Stopping scan");
            mXsScanner.stopScan();
            isScanning = false;
            scan.setText("Start Scan");
        } else {
            Log.e(TAG, "Starting scan");
            scan.setText("Stop Scan");
            for (MovellaBridge device : mDevices.values()) {
                if (device.getDevice() != null) {
                    SensorName.remove(device.getDisplayName());
                    device.getDevice().disconnect();
                }
            }
            adapter.notifyDataSetChanged();
            mDevices.clear();

            mXsScanner.startScan();
            isScanning = true;
        }
    }

    public void onInitDone(MovellaBridge device) {
        mDevices.put(device.getDevice().getAddress(), device);
        if (!SensorName.contains(device.getDisplayName())) {
            SensorName.add(device.getDisplayName());
            adapter.notifyDataSetChanged();
        }
    }

    void bindButtons() {
        LSLIntent = new Intent(this, LSLService.class);
        Button start = (Button) findViewById(R.id.startLSL);
        start.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isRunning) {
                    for (MovellaBridge device : mDevices.values()) {
                        if (selectedItems.contains(device.getDisplayName())) {
                            Log.e(TAG,"Starting movella device "+device.getDisplayName());
                            device.Start();
                        }
                    }
                    //TODO("Check permissions")
                    // make this a foreground service so that android does not kill it while it is in the background
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        myStartForegroundService(LSLIntent);

                    } else { // try our best with older Androids
                        startService(LSLIntent);
                    }
                }

            }
        });

        Button scan = findViewById(R.id.btnScan);
        scan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                TriggerScan();
            }
        });

        Button stop = (Button) findViewById(R.id.stopLSL);
        stop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isRunning) {
                    for (MovellaBridge device : mDevices.values()) {
                        if (selectedItems.contains(device.getDisplayName())) {
                            device.Stop();
                        }
                    }
                    stopService(LSLIntent);
                }
            }
        });
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
        if (checkAudioPermission()) {
            SensorName.add("Audio");
            SensorName.add("Audio classifier");
        }
        if (checkLocationPermission())
            SensorName.add("Location");
        adapter.notifyDataSetChanged();
        TriggerScan();
        Handler handler = new Handler(Looper.getMainLooper());
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (isScanning)
                    TriggerScan();
            }
        }, 10000);
    }
}


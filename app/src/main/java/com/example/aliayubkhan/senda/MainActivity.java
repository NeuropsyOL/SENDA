package com.example.aliayubkhan.senda;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
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

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;

import androidx.appcompat.widget.AppCompatCheckBox;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


public class MainActivity extends Activity {

    // Override the necessary lifecycle methods
    @Override
    protected void onStart() {
        super.onStart();
        Log.e("Location", "onStart called");
        // Check if the location permission is granted
        if (checkLocationPermission()) {
            // Request location updates
            isLocation = true;
        } else {
            Log.w("onStart", "Do not have location permissions!");
            // Request the location permission
            isLocation = false;
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    // Start requesting location updates


    @SuppressLint("StaticFieldLeak")
    static TextView tv;
    Button start, stop;

    static boolean isRunning = false;
    static boolean checkFlag = false;

    //List<Sensor> sensor;
    List<String> SensorName = new ArrayList<>();
    ArrayAdapter<String> adapter;
    ListView lv;
    public static ArrayList<String> selectedItems = new ArrayList<>();

    //Sensors Checklist
    static Boolean isAccelerometer = false;
    static Boolean isLight = false;
    static Boolean isProximity = false;
    static Boolean isGravity = false;
    static Boolean isLinearAcceleration = false;
    static Boolean isRotation = false;
    static Boolean isStepCounter = false;
    static Boolean isAudio = false;
    static Boolean isLocation = true;

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

    public static int SENSOR_COUNT;

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
        start = (Button) findViewById(R.id.startLSL);
        stop = (Button) findViewById(R.id.stopLSL);
        streamingNow = (TextView) findViewById(R.id.streamingNow);
        streamingNowBtn = (ImageView) findViewById(R.id.streamingNowBtn);


        startPowerSaverIntent(this);

        final Intent intent = new Intent(this, LSLService.class);

        start.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isRunning) {
                    //TODO("Check permissions")
                    // make this a foreground service so that android does not kill it while it is in the background
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        myStartForegroundService(intent);
                    } else { // try our best with older Androids
                        startService(intent);
                    }
                }

            }
        });

        stop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopService(intent);
            }
        });

        tv.setText("Available Streams: ");

        SensorManager msensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);

        lv = (ListView)

                findViewById(R.id.sensors);
        lv.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);

        adapter = new ArrayAdapter<>(getApplicationContext(), R.layout.list_view_text, R.id.streamsSelected, SensorName);
        lv.setAdapter(adapter);
        //Not available in Java 7: sensor.stream().anyMatch(s -> s.getType() == Sensor.TYPE_ACCELEROMETER))
        if (msensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null)
            SensorName.add("Accelerometer");
        if (msensorManager.getDefaultSensor(Sensor.TYPE_LIGHT) != null) SensorName.add("Light");
        if (msensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY) != null)
            SensorName.add("Proximity");
        if (msensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY) != null) SensorName.add("Gravity");
        if (msensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION) != null)
            SensorName.add("Linear Acceleration");
        if (msensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) != null)
            SensorName.add("Rotation Vector");
        if (msensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null)
            SensorName.add("Step Count");
        SensorName.add("Audio");
        SensorName.add("Location");

        SENSOR_COUNT = lv.getAdapter().getCount();

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
        Log.e("Location", "Location and Background permission: " + hasFineLocationPermission + " " + hasBackgroundLocationPermission);
        return hasFineLocationPermission && hasBackgroundLocationPermission;
    }

    private boolean checkAudioPermission() {
        return (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED);
    }

    private void requestPermissions() {
        String[] permissions = new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_BACKGROUND_LOCATION};
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

            if (item.contains("Accelerometer")) {
                isAccelerometer = true;
            }
            if (item.contains("Light")) {
                isLight = true;
            }
            if (item.contains("Proximity")) {
                isProximity = true;
            }
            if (item.contains("Gravity")) {
                isGravity = true;
            }
            if (item.contains("Linear Acceleration")) {
                isLinearAcceleration = true;
            }
            if (item.contains("Rotation Vector")) {
                isRotation = true;
            }
            if (item.contains("Step Count")) {
                isStepCounter = true;
            }
            if (item.contains("Audio")) {
                isAudio = true;
            }
            if (item.contains("Location")) {
                isLocation = true;
            }

            //            if(selItems=="")
            //                selItems=item;
            //            else
            //                selItems+="/"+item;
        }
        //Toast.makeText(this, selItems, Toast.LENGTH_LONG).show();
    }

    public static void showText(String s) {
        tv.setText(s);
    }
}


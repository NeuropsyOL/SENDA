package de.uol.neuropsy.senda

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanSettings
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.LinearInterpolator
import android.widget.AdapterView.OnItemClickListener
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageView
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.AppCompatCheckBox
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.xsens.dot.android.sdk.DotSdk
import com.xsens.dot.android.sdk.interfaces.DotScannerCallback
import com.xsens.dot.android.sdk.interfaces.DotSyncCallback
import com.xsens.dot.android.sdk.models.DotDevice
import com.xsens.dot.android.sdk.models.DotSyncManager
import com.xsens.dot.android.sdk.utils.DotScanner
import java.util.Arrays
import java.util.function.Consumer
import java.util.function.Supplier
import java.util.stream.Collectors

class MainActivity : Activity(), DotScannerCallback, DotSyncCallback {
    private var isScanning = false
    private var mXsScanner: DotScanner? = null
    var mConnectedDevices = HashMap<String, MovellaBridge>()
    var mActiveDevices = HashMap<String, MovellaBridge>()
    var SensorName: MutableList<String?> = java.util.ArrayList()
    var adapter: ArrayAdapter<String?>? = null
    var lv: ListView? = null
    var progressBar: ProgressBar? = null
    var backButtonCount = 0

    //Settings button
    var settings_button: ImageView? = null

    //Requesting run-time permissions
    //Create placeholder for user's consent to record_audio and access location permissions.
    //This will be used in handling callback
    private val PERMISSIONS_REQUEST_CODE = 1

    //
    private val START_SCAN_REQUEST_CODE = 2000
    private var LSLIntent: Intent? = null

    // Override the necessary lifecycle methods
    override fun onStart() {
        super.onStart()
        Log.e("Location", "onStart called")
    }

    override fun onStop() {
        super.onStop()
        Log.e(TAG, "MainActivity::OnStop()")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.e(TAG, "MainActivity::OnDestroy()")
        for (device in mActiveDevices.values) device.Stop()
    }

    /**
     * Setup for Xsens DOT SDK.
     */
    private fun initDotSdk() {
        // Get the version name of SDK.
        val version = DotSdk.getSdkVersion()
        Log.i(TAG, "initDotSdk() - version: \$version")
        // Enable this feature to monitor logs from SDK.
        DotSdk.setDebugEnabled(false)
        // Enable this feature then SDK will start reconnection when the connection is lost.
        DotSdk.setReconnectEnabled(true)
    }

    /**
     * Called when the activity is first created.
     */
    @SuppressLint("SetTextI18n")
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initDotSdk()
        setContentView(R.layout.activity_main)
        tv = findViewById<View>(R.id.tv) as TextView
        bindButtons()
        streamingNow = findViewById<View>(R.id.streamingNow) as TextView
        streamingNowBtn = findViewById<View>(R.id.streamingNowBtn) as ImageView
        progressBar = findViewById<View>(R.id.progressBar) as ProgressBar
        startPowerSaverIntent(this)
        tv!!.text = "Available Streams: "
        lv = findViewById<View>(R.id.sensors) as ListView
        lv!!.choiceMode = ListView.CHOICE_MODE_MULTIPLE
        adapter = ArrayAdapter(
            applicationContext,
            R.layout.list_view_text,
            R.id.streamsSelected,
            SensorName
        )
        lv!!.adapter = adapter
        val mSwipeRefreshLayout = findViewById<View>(R.id.swiperefresh) as SwipeRefreshLayout
        mSwipeRefreshLayout.setOnRefreshListener {
            checkAvailableSensors()
            StartScan()
        }
        mXsScanner = DotScanner(this, this)
        mXsScanner!!.setScanMode(ScanSettings.SCAN_MODE_BALANCED)
        checkAvailableSensors()
        lv!!.onItemClickListener =
            OnItemClickListener { parent, view, position, id -> // selected item
                val selectedItem = (view as TextView).text.toString()
                if (selectedItem.contains("Audio") && lv!!.isItemChecked(position)) {
                    if (!checkAudioPermission()) {
                        lv!!.setItemChecked(position, false)
                        requestAudioPermissions(1000 + position)
                    }
                }
                if (selectedItem.contains("Location") && lv!!.isItemChecked(position)) {
                    if (!checkLocationPermission()) {
                        requestLocationPermissions(1000 + position)
                        lv!!.setItemChecked(position, false)
                    }
                }
            }
        val sharedPref = applicationContext.getSharedPreferences("MyPref", 0)
        if (sharedPref.getBoolean("shouldShowTutorial", true)) {
            val tutorialIntent = Intent(this, TutorialActivity::class.java)
            startActivity(tutorialIntent)
        }
        val tutorialButton = findViewById<Button>(R.id.tutorialButton)
        tutorialButton.setOnClickListener { v: View? ->
            val tutorialIntent = Intent(this, TutorialActivity::class.java)
            startActivity(tutorialIntent)
        }
    } // end onCreate

    fun isActivated(s: String): Boolean {
        for (item in selectedItems) {
            if (item == s) return true
        }
        return false
    }

    private fun myStartForegroundService(intent: Intent?) {
        intent!!.putExtra("inputExtra", "SENDA Foreground Service in Android")
        ContextCompat.startForegroundService(this, intent)
    }

    // Check if the permissions are already granted
    private fun checkLocationPermission(): Boolean {
        val hasFineLocationPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasBackgroundLocationPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return hasFineLocationPermission && hasBackgroundLocationPermission
    }

    private fun checkBackgroundLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun checkAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun checkBluetoothPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.BLUETOOTH_SCAN
        ) == PackageManager.PERMISSION_GRANTED else true
    }

    private fun requestAudioPermissions(requestCode: Int) {
        val permissions = arrayOf(Manifest.permission.RECORD_AUDIO)
        ActivityCompat.requestPermissions(this, permissions, requestCode)
    }

    private fun requestBluetoothPermissions(requestCode: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val permissions =
                arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
            ActivityCompat.requestPermissions(this, permissions, requestCode)
        }
    }

    private fun checkAndRequestBluetoothEnabled(): Boolean {
        val bluetoothManager = getSystemService(
            BluetoothManager::class.java
        )
        val bluetoothAdapter = bluetoothManager.adapter
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "This device does not support Bluetooth", Toast.LENGTH_SHORT)
                .show()
            return false
        }
        if (!bluetoothAdapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBtIntent, 1002)
            return false
        }
        return true
    }

    private fun requestLocationPermissions(requestCode: Int) {
        val permissions: Array<String>
        permissions = if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION) else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        ActivityCompat.requestPermissions(this, permissions, requestCode)
    }

    private fun requestBackgroundLocationPermission(requestCode: Int) {
        val permissions = arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        ActivityCompat.requestPermissions(this, permissions, requestCode)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        for (ii in permissions.indices) {
            if (grantResults[ii] == PackageManager.PERMISSION_GRANTED) {
                // FINE_LOCATION needs special treatment b/c we need to request BACKGROUND_LOCATION after it
                if (permissions[ii] == Manifest.permission.ACCESS_FINE_LOCATION) {
                    // Background location has to be requested after fine location is granted.
                    if (!checkBackgroundLocationPermission()) {
                        requestBackgroundLocationPermission(requestCode)
                    }
                } else if (requestCode >= 1000 && requestCode < 2000) {
                    lv!!.setItemChecked(requestCode - 1000, true)
                } else if (requestCode >= 2000) {
                    Log.e(TAG, "Coming from StartScan, commencing scan")
                    StartScan()
                }
            } else {
                // Denied permission and should not show rationale -> Permission request is invisible to user, show error message
                if (!shouldShowRequestPermissionRationale(permissions[ii])) {
                    //TODO Map permissions string to human readable permission
                    try {
                        Toast.makeText(
                            this, "Missing permission: " + this.packageManager.getPermissionInfo(
                                permissions[ii], 0
                            ).loadLabel(this.packageManager), Toast.LENGTH_SHORT
                        ).show()
                    } catch (e: Exception) {
                        Toast.makeText(
                            this,
                            "Missing a permission and encountered an error trying to find out which.",
                            Toast.LENGTH_SHORT
                        ).show()
                        Log.e(
                            TAG,
                            "Missing a permission and encountered an error trying to find out which:$e"
                        )
                    }
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    val uri = Uri.fromParts("package", packageName, null)
                    intent.data = uri
                    startActivity(intent)
                }
            }
        }
    }

    override fun onBackPressed() {
        if (backButtonCount >= 1) {
            if (isRunning) {
                for (device in mActiveDevices.values) {
                    device.Stop()
                }
                stopService(LSLIntent)
            }
            for (device in mConnectedDevices.values) {
                SensorName.remove(device.displayName)
                device.Disconnect()
                mConnectedDevices.remove(device.Address())
                adapter!!.notifyDataSetChanged()
            }
            finishAffinity()
            backButtonCount = 0
        } else {
            Toast.makeText(
                this,
                "Press the back button once again to close the application.",
                Toast.LENGTH_SHORT
            ).show()
            backButtonCount++
        }
    }

    override fun onDotScanned(bluetoothDevice: BluetoothDevice, i: Int) {
        MovellaBridge(this, bluetoothDevice, this)
        Log.e(TAG, "Initializing " + bluetoothDevice.address)
    }

    fun StartScan() {
        // TODO trigger scan in permission result callback when we come from here
        if (!checkBluetoothPermission()) {
            Log.i(TAG, "Do not have Bluetooth permission, asking for it")
            requestBluetoothPermissions(START_SCAN_REQUEST_CODE)
            (findViewById<View>(R.id.swiperefresh) as SwipeRefreshLayout).isRefreshing = false
            return
        } else if (!checkAndRequestBluetoothEnabled()) return
        Log.e(TAG, "Starting scan")
        for (device in mConnectedDevices.values) {
            // Do not disconnect currently active devices
            if (device.handle != null && isRunning && !mActiveDevices.containsKey(device.Address())) {
                SensorName.remove(device.displayName)
                device.Disconnect()
            }
        }
        adapter!!.notifyDataSetChanged()
        mConnectedDevices.clear()
        (findViewById<View>(R.id.swiperefresh) as SwipeRefreshLayout).isRefreshing = true
        mXsScanner!!.startScan()
        isScanning = true
        val handler = Handler(Looper.getMainLooper())
        handler.postDelayed({ StopScan() }, 5000)
    }

    fun StopScan() {
        Log.e(TAG, "Stopping scan")
        mXsScanner!!.stopScan()
        isScanning = false
        (findViewById<View>(R.id.swiperefresh) as SwipeRefreshLayout).isRefreshing = false
    }

    fun onInitDone(device: MovellaBridge) {
        mConnectedDevices[device.Address()] = device
        if (!SensorName.contains(device.displayName)) {
            SensorName.add(device.displayName)
            adapter!!.notifyDataSetChanged()
        }
    }

    fun bindButtons() {
        LSLIntent = Intent(this, LSLService::class.java)
        val start = findViewById<View>(R.id.startLSL) as Button
        start.setOnClickListener { v: View? ->
            onStartButtonPressedPreSync()
            syncMovellaSensors()
        }
        val stop = findViewById<View>(R.id.stopLSL) as Button
        stop.setOnLongClickListener { v: View? ->
            if (backButtonCount < 2) {
                backButtonCount++
                return@setOnLongClickListener true
            }
            true
        }
        stop.setOnClickListener { v: View? ->
            if (isRunning) {
                DotSyncManager.getInstance(this).stopSyncing()
                for (device in mActiveDevices.values) {
                    device.Stop()
                }
                stopService(LSLIntent)
                lv!!.isEnabled = true
                lv!!.alpha = 1f
            }
        }
    }

    fun syncMovellaSensors() {
        if (mActiveDevices.size < 2) {
            Log.e("syncMovellaSensors", "No syncing needed")
            //No syncing needed, proceed to PostSync
            onStartButtonPressedPostSync()
            return
        }
        Log.e("MainActivity", "Try syncing")
        streamingNow!!.visibility = View.VISIBLE
        streamingNow!!.text = "Syncing Movella sensors..."


        //Animation for Streaming
        val animation: Animation = AlphaAnimation(0.5.toFloat(), 0f)
        animation.duration = 850
        animation.interpolator = LinearInterpolator() // do not alter
        // animation rate
        animation.repeatCount = Animation.INFINITE // Repeat animation
        // infinitely
        animation.repeatMode = Animation.REVERSE // Reverse animation at the
        // end so the button will fade back in
        // streamingNowBtn.startAnimation(animation);
        streamingNow!!.startAnimation(animation)
        val activeDeviceList =
            mActiveDevices.values.stream().map { obj: MovellaBridge -> obj.handle }
                .collect(
                    Collectors.toCollection(
                        Supplier { ArrayList() })
                )
        activeDeviceList[0]!!.isRootDevice = true
        activeDeviceList.forEach(Consumer { v: DotDevice? ->
            Log.e(
                "syncMovellaSensors",
                "I must sync: " + v!!.tag
            )
        })
        DotSyncManager.getInstance(this).startSyncing(activeDeviceList, 1)
    }

    fun checkAvailableSensors() {
        SensorName.clear()
        val sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        //Not available in Java 7: sensor.stream().anyMatch(s -> s.getType() == Sensor.TYPE_ACCELEROMETER))
        if (sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null) SensorName.add("Accelerometer")
        if (sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT) != null) SensorName.add("Light")
        if (sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY) != null) SensorName.add("Proximity")
        if (sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY) != null) SensorName.add("Gravity")
        if (sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION) != null) SensorName.add(
            "Linear Acceleration"
        )
        if (sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) != null) SensorName.add("Rotation Vector")
        if (sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null) SensorName.add("Step Count")
        if (sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null) SensorName.add("Gyroscope")
        // Do not need to check: Asking for audio permission if user selects this item
        SensorName.add("Audio")
        // Do not need to check: Asking for audio permission if user selects this item
        SensorName.add("Audio classifier")
        // Do not need to check: Asking for location permission if user selects this item
        SensorName.add("Location")
        adapter!!.notifyDataSetChanged()
    }

    fun onStartButtonPressedPreSync() {
        Log.e("MainActivity", "OnStartButtonPressedPreSync " + Process.myTid())
        if (!isRunning) {
            lv!!.isEnabled = false
            lv!!.alpha = 0.1f
            // Build the list of selected items and give it over to the LSLIntent
            val checked = lv!!.checkedItemPositions
            for (i in 0 until lv!!.adapter.count) {
                Log.e(TAG, lv!!.getItemAtPosition(i).toString() + " " + checked[i])
                LSLIntent!!.putExtra(lv!!.getItemAtPosition(i).toString(), checked[i])
            }
            for (device in mConnectedDevices.values) {
                if (LSLIntent!!.getBooleanExtra(device.displayName, false)) {
                    mActiveDevices[device.Address()] = device
                    if (device.IsSynced()) {
                        Log.e(
                            "MainActivity",
                            device.displayName + " already synced, stopping sync..."
                        )
                        DotSyncManager.getInstance(this).stopSyncing()
                    }
                    Log.e(
                        TAG,
                        "Adding movella device to list of active devices:" + device.displayName
                    )
                }
            }
        }
    }

    fun onStartButtonPressedPostSync() {
        Log.e("MainActivity", "OnStartButtonPressedPostSync " + Process.myTid())
        runOnUiThread(Thread {
            streamingNow!!.visibility = View.INVISIBLE
            streamingNow!!.text = "Streaming Data..."
            progressBar!!.visibility = View.GONE
        })
        for (device in mActiveDevices.values) {
            Log.e(TAG, "Starting movella device " + device.displayName)
            device.Start()
        }
        // make this a foreground service so that android does not kill it while it is in the background
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            myStartForegroundService(LSLIntent)
        } else { // try our best with older Androids
            startService(LSLIntent)
        }
    }

    override fun onSyncingStarted(s: String, b: Boolean, i: Int) {
        progressBar!!.visibility = View.VISIBLE // Make ProgressBar visible
        progressBar!!.progress = 0
        Log.e("MainActivity", "onSyncingStarted $b $i")
    }

    override fun onSyncingProgress(i: Int, i1: Int) {
        progressBar!!.progress = i
        Log.e("MainActivity", "onSyncingProgress $i $i1")
    }

    override fun onSyncingResult(s: String, b: Boolean, i: Int) {
        Log.e("MainActivity", "onSyncingResult $s $b $i")
    }

    override fun onSyncingDone(hashMap: HashMap<String, Boolean>, b: Boolean, i: Int) {
        Log.e("MainActivity", "onSyncingDone $b $i")
        if (b) onStartButtonPressedPostSync() else {
            runOnUiThread(Thread {
                lv!!.isEnabled = true
                lv!!.alpha = 1.0f
                Log.e("MainActivity", "SYNC FAILED")
                Toast.makeText(this, "Syncing Failed!", Toast.LENGTH_LONG)
                streamingNow!!.text = "Syncing Failed!"
                progressBar!!.progress = 0
                progressBar!!.visibility = View.GONE
                mActiveDevices.clear()
            })
        }
    }

    override fun onSyncingStopped(s: String, b: Boolean, i: Int) {
        Log.e("MainActivity", "onSyncingStopped")
    }

    companion object {
        var TAG = MainActivity::class.java.simpleName

        lateinit var tv: TextView
        var isRunning = false
        var selectedItems = java.util.ArrayList<String>()

        //Streaming Identification
        @SuppressLint("StaticFieldLeak")
        lateinit var streamingNowBtn: ImageView

        lateinit var streamingNow: TextView
        var POWERMANAGER_INTENTS = Arrays.asList(
            Intent().setComponent(
                ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"
                )
            ),
            Intent().setComponent(
                ComponentName(
                    "com.letv.android.letvsafe",
                    "com.letv.android.letvsafe.AutobootManageActivity"
                )
            ),
            Intent().setComponent(
                ComponentName(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.optimize.process.ProtectActivity"
                )
            ),
            Intent().setComponent(
                ComponentName(
                    "com.coloros.safecenter",
                    "com.coloros.safecenter.permission.startup.StartupAppListActivity"
                )
            ),
            Intent().setComponent(
                ComponentName(
                    "com.coloros.safecenter",
                    "com.coloros.safecenter.startupapp.StartupAppListActivity"
                )
            ),
            Intent().setComponent(
                ComponentName(
                    "com.oppo.safe",
                    "com.oppo.safe.permission.startup.StartupAppListActivity"
                )
            ),
            Intent().setComponent(
                ComponentName(
                    "com.iqoo.secure",
                    "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"
                )
            ),
            Intent().setComponent(
                ComponentName(
                    "com.iqoo.secure",
                    "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"
                )
            ),
            Intent().setComponent(
                ComponentName(
                    "com.vivo.permissionmanager",
                    "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
                )
            ),
            Intent().setComponent(
                ComponentName(
                    "com.asus.mobilemanager",
                    "com.asus.mobilemanager.entry.FunctionActivity"
                )
            ).setData(
                Uri.parse("mobilemanager://function/entry/AutoStart")
            )
        )

        fun startPowerSaverIntent(context: Context) {
            val settings = context.getSharedPreferences("ProtectedApps", MODE_PRIVATE)
            val skipMessage = settings.getBoolean("skipProtectedAppCheck", false)
            if (!skipMessage) {
                val editor = settings.edit()
                var foundCorrectIntent = false
                for (intent in POWERMANAGER_INTENTS) {
                    if (isCallable(context, intent)) {
                        foundCorrectIntent = true
                        val dontShowAgain = AppCompatCheckBox(context)
                        dontShowAgain.setText(R.string.dont_show_again)
                        dontShowAgain.setOnCheckedChangeListener { buttonView, isChecked ->
                            editor.putBoolean("skipProtectedAppCheck", isChecked)
                            editor.apply()
                        }
                        AlertDialog.Builder(context)
                            .setTitle(Build.MANUFACTURER + " Protected Apps").setMessage(
                            String.format(
                                "%s requires to be enabled in 'Protected Apps' to function properly.%n",
                                context.getString(R.string.app_name)
                            )
                        ).setView(dontShowAgain)
                            .setPositiveButton("Go to settings") { dialog, which ->
                                context.startActivity(intent)
                            }
                            .setNegativeButton(android.R.string.cancel, null).show()
                        break
                    }
                }
                if (!foundCorrectIntent) {
                    editor.putBoolean("skipProtectedAppCheck", true)
                    editor.apply()
                }
            }
        }

        private fun isCallable(context: Context, intent: Intent): Boolean {
            val list = context.packageManager.queryIntentActivities(
                intent,
                PackageManager.MATCH_DEFAULT_ONLY
            )
            return list.size > 0
        }
    }
}
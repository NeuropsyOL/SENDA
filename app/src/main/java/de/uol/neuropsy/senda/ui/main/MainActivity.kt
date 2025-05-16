package de.uol.neuropsy.senda.ui.main

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.LinearInterpolator
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckedTextView
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import de.uol.neuropsy.senda.R
import de.uol.neuropsy.senda.data.SensorRepositoryImpl
import de.uol.neuropsy.senda.service.LSLService
import de.uol.neuropsy.senda.ui.state.UiState
import de.uol.neuropsy.senda.utils.PermissionManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private val REQ_BACKGROUND_LOCATION= 4711
    private val viewModel: MainViewModel by viewModels {
        MainViewModelFactory(application, SensorRepositoryImpl(applicationContext))
    }
    private lateinit var sensorListView: ListView
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private lateinit var streamingStatus: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var sensorAdapter: ArrayAdapter<String>
    private lateinit var permissionManager : PermissionManager
    private val sensorPermissions = mapOf(
        "Audio"             to arrayOf(android.Manifest.permission.RECORD_AUDIO),
        "Audio classifier"  to arrayOf(android.Manifest.permission.RECORD_AUDIO),
        "Location"          to arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION)
    )

    private var lslService: LSLService? = null
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            val local = binder as LSLService.LocalBinder
            lslService = local.getService()
            lifecycleScope.launchWhenStarted {
                lslService!!.events.collect { viewModel.onServiceEvent(it) }
            }
        }
        override fun onServiceDisconnected(name: ComponentName) {
            lslService = null
        }
    }

    private fun bindService() {
        val intent = Intent(this, LSLService::class.java)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun unbindService() {
        lslService?.let { unbindService(serviceConnection) }
        lslService = null
    }



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        bindViews()
        bindListeners()
        permissionManager = PermissionManager(this)
        lifecycleScope.launch {
            viewModel.uiState.collectLatest { state ->
                render(state)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        bindService()
    }

    override fun onStop() {
        super.onStop()
        unbindService()
    }


    override fun onDestroy() {
        super.onDestroy()
        viewModel.stopStreaming()
    }

    private fun render(state: UiState) {
        // 1) Always hide all transient indicators at the start:
        swipeRefreshLayout.isRefreshing = false
        progressBar.visibility     = View.GONE
        streamingStatus.clearAnimation()
        streamingStatus.visibility = View.INVISIBLE

        // 2) Then handle each state:
        when (state) {
            UiState.Idle -> {
                startButton.isEnabled = true
                stopButton .isEnabled = false
                bindDeviceList(emptyList(), emptyList())
            }
            is UiState.Scanning -> {
                swipeRefreshLayout.isRefreshing = true
                startButton.isEnabled = false
                stopButton .isEnabled = false
            }
            is UiState.DevicesDiscovered -> {
                startButton.isEnabled = true
                stopButton .isEnabled = false
                bindDeviceList(state.onboardSensors, state.movellaDevices.map { it.displayName })
            }
            is UiState.Syncing -> {
                progressBar.visibility = View.VISIBLE
                progressBar.progress   = state.progress
                startButton.isEnabled  = false
                stopButton .isEnabled  = false
            }
            UiState.Streaming -> {
                startButton.isEnabled  = false
                stopButton .isEnabled  = true
                // kick off the pulsing animation
                val anim = AlphaAnimation(0.5f, 0f).apply {
                    duration        = 850
                    interpolator    = LinearInterpolator()
                    repeatCount     = Animation.INFINITE
                    repeatMode      = Animation.REVERSE
                }
                streamingStatus.visibility = View.VISIBLE
                streamingStatus.startAnimation(anim)
            }
            is UiState.Error -> {
                Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()
                // then immediately ask VM to clear error:
                viewModel.clearError()
            }
        }
    }

    // Helper to update the ListView adapter in one shot
    private fun bindDeviceList(onboard: List<String>, movella: List<String>) {
        val all = onboard + movella
        sensorAdapter.clear()
        sensorAdapter.addAll(all)
        sensorAdapter.notifyDataSetChanged()
    }

    private fun bindViews() {
        sensorListView = findViewById(R.id.sensors)
        swipeRefreshLayout = findViewById(R.id.swiperefresh)
        startButton = findViewById(R.id.startLSL)
        stopButton = findViewById(R.id.stopLSL)
        streamingStatus = findViewById(R.id.streamingNow)
        progressBar = findViewById(R.id.progressBar)

        sensorAdapter = ArrayAdapter(this,
            R.layout.list_view_text,
            R.id.streamsSelected, mutableListOf())
        sensorListView.adapter = sensorAdapter
        sensorListView.choiceMode = ListView.CHOICE_MODE_MULTIPLE
    }

    private fun bindListeners() {
        swipeRefreshLayout.setOnRefreshListener {
            // Determine which Bluetooth permissions we need on this API level
            val bluetoothPerms =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    arrayOf(
                        android.Manifest.permission.BLUETOOTH_SCAN,
                        android.Manifest.permission.BLUETOOTH_CONNECT
                    )
                } else {
                    arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION) // pre-S requires location for BLE scans
                }

            if (bluetoothPerms.isNotEmpty()) {
                lifecycleScope.launch {
                    val result = permissionManager
                        .requestPermissions(android.Manifest.permission.BLUETOOTH_SCAN, android.Manifest.permission.BLUETOOTH_CONNECT)
                    if (result.values.all { it }) {
                        viewModel.startScan()
                    } else {
                        swipeRefreshLayout.isRefreshing = false
                        Toast.makeText(this@MainActivity, "Bluetooth permission required", Toast.LENGTH_SHORT).show()
                    }
                }

            } else {
                // No runtime perms needed: go right ahead
                viewModel.startScan()
            }
        }

        sensorListView.setOnItemClickListener { parent, view, position, _ ->
            val name = parent.getItemAtPosition(position) as String
            val isChecked = (view as CheckedTextView).isChecked

            if (!isChecked) {
                // Un‐checking is always allowed
                return@setOnItemClickListener
            }

            when (name) {
                "Location" -> {
                    // Step 1: ask for fine location via our PermissionManager
                    lifecycleScope.launch {
                        val result = permissionManager
                            .requestPermissions(android.Manifest.permission.ACCESS_FINE_LOCATION)
                        if (result[android.Manifest.permission.ACCESS_FINE_LOCATION] == true) {
                            // Step 2: now ask for background via ActivityCompat
                            ActivityCompat.requestPermissions(
                                this@MainActivity,
                                arrayOf(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION),
                                REQ_BACKGROUND_LOCATION
                            )
                        } else {
                            // user denied fine => rollback
                            sensorListView.setItemChecked(position, false)
                            Toast.makeText(
                                this@MainActivity,
                                "Fine location required for Location sensor",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }

                "Audio","Audio Classification"->{
                    askForPermissions(
                        position, name,
                        arrayOf(android.Manifest.permission.RECORD_AUDIO)
                    )
                }

                else -> {
                    val perms = sensorPermissions[name] ?: emptyArray()
                    if (perms.isNotEmpty()) {
                        lifecycleScope.launch {
                            val results=permissionManager
                                .requestPermissions(*perms)
                            if (!results.values.all { it }) {
                                sensorListView.setItemChecked(position, false)
                                Toast.makeText(this@MainActivity,
                                    "Permission required to select $name",
                                    Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
        }

        startButton.setOnClickListener {
            // Gather which sensors the user checked:
            val selectedSensors = sensorAdapter
                .getAllItems()
                .filter { sensorListView.isItemChecked(sensorAdapter.getPosition(it)) }

            if (selectedSensors.isEmpty()) {
                Toast.makeText(this, "Please select at least one sensor", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Launch our new single-entrypoint in the ViewModel:
            lifecycleScope.launch {
                viewModel.startSelectedSensors(selectedSensors)
            }
        }

        stopButton.setOnClickListener {
            viewModel.stopStreaming()    // cancel any sync + stop the service
        }
    }

    private fun askForPermissions(position: Int, sensor: String, perms: Array<String>) {
        lifecycleScope.launch {
            val results = permissionManager.requestPermissions(*perms)
            if (results.values.all { it }) {
                // leave the checkbox checked
            } else {
                // user denied → revert
                sensorListView.setItemChecked(position, false)
                Toast.makeText(
                    this@MainActivity,
                    "$sensor permission required",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    // Special treatment only for background location
    override fun onRequestPermissionsResult(requestCode: Int,permissions: Array<out String>,grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQ_BACKGROUND_LOCATION -> {
                // See if background was granted
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Accept the checkbox (it’s already checked)
                } else {
                    // rollback the “Location” checkbox
                    val pos = (0 until sensorAdapter.count)
                        .first { sensorAdapter.getItem(it) == "Location" }
                    sensorListView.setItemChecked(pos, false)
                    Toast.makeText(
                        this,
                        "Background location is required for Location sensor",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
    private fun <T> ArrayAdapter<T>.getAllItems(): List<T> {
        val result = mutableListOf<T>()
        for (i in 0 until count) {
            getItem(i)?.let { result.add(it) }
        }
        return result
    }
}
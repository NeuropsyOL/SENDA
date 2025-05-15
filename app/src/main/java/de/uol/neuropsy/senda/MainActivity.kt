package de.uol.neuropsy.senda

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
import de.uol.neuropsy.senda.ui.UiState
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private val REQ_BACKGROUND_LOCATION= 4711
    private val viewModel: MainViewModel by viewModels()
    private var lslIntent: Intent? = null
    private var lslService: LSLService? = null
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

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            lslService = (binder as LSLService.LocalBinder).getService()
            // Collect service events
            lifecycleScope.launch {
                lslService?.events?.collectLatest { event ->
                    when (event) {
                        ServiceEvent.Started -> viewModel.onServiceEvent(event)
                        ServiceEvent.Stopped -> viewModel.onServiceEvent(event)
                        is ServiceEvent.Failed -> viewModel.onServiceEvent(event)
                    }
                }
            }
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            lslService = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        bindViews()
        bindStateObserver()
        bindListeners()
        permissionManager = PermissionManager(this)
        lslIntent = Intent(this, LSLService::class.java)
        // Bind service early so that events can be collected
        bindService(lslIntent!!, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    override fun onDestroy() {
        super.onDestroy()
        unbindService(serviceConnection)
        viewModel.stopStreaming()
    }

    private fun bindViews() {
        sensorListView = findViewById(R.id.sensors)
        swipeRefreshLayout = findViewById(R.id.swiperefresh)
        startButton = findViewById(R.id.startLSL)
        stopButton = findViewById(R.id.stopLSL)
        streamingStatus = findViewById(R.id.streamingNow)
        progressBar = findViewById(R.id.progressBar)

        sensorAdapter = ArrayAdapter(this, R.layout.list_view_text, R.id.streamsSelected, mutableListOf())
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
            // 1) Gather exactly which sensors the user checked:
            val selectedSensors = sensorAdapter
                .getAllItems()
                .filter { sensorListView.isItemChecked(sensorAdapter.getPosition(it)) }

            if (selectedSensors.isEmpty()) {
                Toast.makeText(this, "Please select at least one sensor", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 2) Disable the list while we do our work
            sensorListView.isEnabled = false
            sensorListView.alpha = 0.1f

            // 3) Launch our new single-entrypoint in the ViewModel:
            lifecycleScope.launch {
                viewModel.startSelectedSensors(selectedSensors)
            }
        }

        stopButton.setOnClickListener {
            viewModel.stopStreaming()    // cancel any sync + stop the service
            sensorListView.isEnabled = true
            sensorListView.alpha = 1.0f
        }
    }

    private fun bindStateObserver() {
        lifecycleScope.launch {
            viewModel.uiState.collectLatest { state ->
                when (state) {
                    is UiState.Idle -> renderIdle()
                    is UiState.Scanning -> renderScanning()
                    is UiState.DevicesDiscovered -> renderDevices(state)
                    is UiState.Syncing -> renderSyncing(state.progress)
                    is UiState.Streaming -> renderStreaming()
                    is UiState.Error -> renderError(state.message)
                    else -> {}
                }
            }
        }
    }

    // Special treatment only for background location
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
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




    private fun renderIdle() {
        startButton.isEnabled = true
        stopButton .isEnabled = false
        swipeRefreshLayout.isRefreshing = false
        progressBar.visibility = View.GONE
        streamingStatus.clearAnimation()
        streamingStatus.visibility = View.INVISIBLE
        sensorListView.isEnabled = true
        sensorListView.alpha = 1.0f
    }

    private fun renderScanning() {
        startButton.isEnabled = false
        stopButton .isEnabled = false
        swipeRefreshLayout.isRefreshing = true
    }

    private fun renderDevices(state: UiState.DevicesDiscovered) {
        swipeRefreshLayout.isRefreshing = false
        startButton.isEnabled = true
        stopButton .isEnabled = false
        // Update onboard sensors if any
        sensorAdapter.clear()
        sensorAdapter.addAll(state.onboardSensors + state.movellaDevices.map { it.displayName })
        sensorAdapter.notifyDataSetChanged()
    }

    private fun renderSyncing(progress: Int) {
        startButton.isEnabled = false
        stopButton .isEnabled = false
        swipeRefreshLayout.isRefreshing = false
        progressBar.visibility = View.VISIBLE
        progressBar.progress = progress
    }

    private fun renderStreaming() {
        startButton.isEnabled = false
        stopButton .isEnabled = true
        progressBar.visibility = View.GONE
        val animation: Animation = AlphaAnimation(0.5f, 0f).apply {
            duration = 850
            interpolator = LinearInterpolator()
            repeatCount = Animation.INFINITE
            repeatMode = Animation.REVERSE
        }
        streamingStatus.visibility = View.VISIBLE
        streamingStatus.startAnimation(animation)
    }

    private fun renderError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        renderIdle()
    }


    private fun <T> ArrayAdapter<T>.getAllItems(): List<T> {
        val result = mutableListOf<T>()
        for (i in 0 until count) {
            getItem(i)?.let { result.add(it) }
        }
        return result
    }
}
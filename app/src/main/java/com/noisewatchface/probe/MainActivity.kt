package com.noisewatchface.probe

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.lifecycleScope
import com.noisewatchface.probe.ble.ProbeManager
import com.noisewatchface.probe.ui.ProbeScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private lateinit var probe: ProbeManager

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
            val missing = granted.filterValues { !it }.keys
            if (missing.isEmpty()) {
                probe.startScan()
            } else {
                Toast.makeText(this, "Bluetooth permissions are required: $missing", Toast.LENGTH_LONG)
                    .show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        probe = ProbeManager(applicationContext)

        setContent {
            MaterialTheme {
                val devices = probe.devices.collectAsState().value
                val connectedTo = probe.connectedTo.collectAsState().value
                val scanning = probe.scanning.collectAsState().value
                val mtu = probe.negotiatedMtu.collectAsState().value
                val identity = probe.identity.collectAsState().value
                val lines = probe.lines.collectAsState().value

                ProbeScreen(
                    devices = devices,
                    connectedTo = connectedTo,
                    scanning = scanning,
                    mtu = mtu,
                    identity = identity,
                    lines = lines,
                    onScan = { requestScan() },
                    onStop = { probe.stopScan() },
                    onConnect = { probe.connect(it.device) },
                    onDisconnect = { probe.disconnect() },
                    onClear = { probe.clearLog() },
                    onExport = { shareLog() },
                )
            }
        }
    }

    private fun requestScan() {
        if (!probe.isBluetoothReady()) {
            Toast.makeText(this, "Turn Bluetooth on first", Toast.LENGTH_SHORT).show()
            return
        }
        val needed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
            )
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        permissionLauncher.launch(needed)
    }

    /** Shares the transcript as text so it can be pasted back for analysis. */
    private fun shareLog() {
        lifecycleScope.launch {
            val text = withContext(Dispatchers.IO) { probe.exportText() }
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Noise Watchface Probe log")
                putExtra(Intent.EXTRA_TEXT, text)
            }
            startActivity(Intent.createChooser(intent, "Export probe log"))
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        runCatching { probe.stopScan() }
        runCatching { probe.disconnect() }
    }
}

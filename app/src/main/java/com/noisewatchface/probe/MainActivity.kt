package com.noisewatchface.probe

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import com.noisewatchface.probe.ble.ConnectionGate
import com.noisewatchface.probe.ble.ProbeManager
import com.noisewatchface.probe.dial.DialCandidate
import com.noisewatchface.probe.dial.TopStepParser
import com.noisewatchface.probe.ui.GateScreen
import com.noisewatchface.probe.ui.InspectScreen
import com.noisewatchface.probe.ui.ProbeScreen
import com.noisewatchface.probe.ui.SendScreen
import com.noisewatchface.probe.ui.WatchFaceScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Screens reachable from the watch-face flow. */
private enum class Screen { PROBE, GATE, WATCHFACE, INSPECT, SEND }

/**
 * Display name of the document backing a content Uri, or null if unavailable.
 * Safe to call from any thread.
 */
private fun resolveDisplayName(
    resolver: android.content.ContentResolver,
    uri: Uri,
): String? = runCatching {
    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { c ->
            if (c.moveToFirst()) {
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) c.getString(idx) else null
            } else {
                null
            }
        }
}.getOrNull()

class MainActivity : ComponentActivity() {

    private lateinit var probe: ProbeManager
    private lateinit var gate: ConnectionGate

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
        gate = ConnectionGate(applicationContext)
        probe.refreshDeviceState()

        setContent {
            MaterialTheme {
                // Device state changes while we look at it, since pairing happens
                // in Settings or the official app.
                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) probe.refreshDeviceState()
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }

                var screen by remember { mutableStateOf(Screen.PROBE) }
                var gateStatus by remember { mutableStateOf<ConnectionGate.Status?>(null) }
                var candidate by remember { mutableStateOf<DialCandidate?>(null) }
                var rebuiltSize by remember { mutableStateOf(0) }

                fun validateWatch(): Boolean {
                    val status = gate.check()
                    gateStatus = status
                    return status.canProceed
                }

                fun openWatchFaces() {
                    if (validateWatch()) {
                        screen = Screen.WATCHFACE
                    } else {
                        screen = Screen.GATE
                    }
                }

                val pickDial = rememberLauncherForActivityResult(
                    ActivityResultContracts.OpenDocument(),
                ) { uri ->
                    if (uri != null) {
                        lifecycleScope.launch {
                            candidate = withContext(Dispatchers.IO) { readCandidate(uri) }
                        }
                    }
                }

                when (screen) {
                    Screen.PROBE -> {
                        val paired = probe.paired.collectAsState().value
                        val inUse = probe.inUse.collectAsState().value
                        val devices = probe.devices.collectAsState().value
                        val connectedTo = probe.connectedTo.collectAsState().value
                        val scanning = probe.scanning.collectAsState().value
                        val mtu = probe.negotiatedMtu.collectAsState().value
                        val identity = probe.identity.collectAsState().value
                        val lines = probe.lines.collectAsState().value
                        val connectFailed = probe.connectFailed.collectAsState().value
                        val busyDevice = probe.busyDevice.collectAsState().value

                        ProbeScreen(
                            devices = devices,
                            paired = paired,
                            inUse = inUse,
                            connectedTo = connectedTo,
                            connectFailed = connectFailed,
                            busyDevice = busyDevice,
                            scanning = scanning,
                            mtu = mtu,
                            identity = identity,
                            lines = lines,
                            onScan = { requestScan() },
                            onStop = { probe.stopScan() },
                            onConnect = { probe.connect(it.device) },
                            onConnectPaired = { probe.connect(it) },
                            onRecheck = {
                                probe.clearConnectError()
                                probe.refreshDeviceState()
                            },
                            onDisconnect = { probe.disconnect() },
                            onClear = { probe.clearLog() },
                            onExport = { shareLog() },
                            onWatchFaces = { openWatchFaces() },
                        )
                    }

                    Screen.GATE -> GateScreen(
                        status = gateStatus,
                        onValidate = { validateWatch() },
                        onContinue = { screen = Screen.WATCHFACE },
                        onBack = { screen = Screen.PROBE },
                    )

                    Screen.WATCHFACE -> WatchFaceScreen(
                        watchName = gateStatus?.watchName,
                        candidate = candidate,
                        onPickFile = { pickDial.launch(arrayOf("*/*")) },
                        onInspect = {
                            val pkg = candidate?.parsed
                            if (pkg != null) {
                                rebuiltSize = TopStepParser.serialise(pkg).size
                                screen = Screen.INSPECT
                            }
                        },
                        onSend = { screen = Screen.SEND },
                        onBack = { screen = Screen.PROBE },
                    )

                    Screen.INSPECT -> {
                        val pkg = candidate?.parsed
                        if (pkg == null) {
                            screen = Screen.WATCHFACE
                        } else {
                            InspectScreen(
                                pkg = pkg,
                                rebuiltSize = rebuiltSize,
                                onBack = { screen = Screen.WATCHFACE },
                            )
                        }
                    }

                    Screen.SEND -> SendScreen(onBack = { screen = Screen.WATCHFACE })
                }
            }
        }
    }

    /** Reads the chosen file and tries to parse it. Never writes anywhere. */
    private fun readCandidate(uri: Uri): DialCandidate {
        val name = resolveDisplayName(contentResolver, uri) ?: uri.lastPathSegment ?: "selected file"
        return try {
            val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return DialCandidate(name, 0, null, "Could not open the selected file")
            DialCandidate.from(name, bytes)
        } catch (e: Exception) {
            DialCandidate(name, 0, null, e.message ?: "Could not read the selected file")
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

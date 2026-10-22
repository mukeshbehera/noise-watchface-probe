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
import com.noisewatchface.probe.ui.GateScreen
import com.noisewatchface.probe.ui.InspectScreen
import com.noisewatchface.probe.ui.ProbeScreen
import com.noisewatchface.probe.ui.SendScreen
import com.noisewatchface.probe.ui.WatchFaceScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.noisewatchface.probe.dial.DialBuilder
import com.noisewatchface.probe.dial.DialTemplate
import com.noisewatchface.probe.dial.FaceDraft
import com.noisewatchface.probe.dial.ImageScaler
import com.noisewatchface.probe.dial.TopStepPackage
import com.noisewatchface.probe.dial.TopStepParser
import com.noisewatchface.probe.ui.PreviewScreen

/** Screens reachable from the watch-face flow. */
private enum class Screen { PROBE, GATE, WATCHFACE, INSPECT, SEND, PREVIEW }

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
                var templates by remember { mutableStateOf(loadTemplates()) }
                var selectedTemplate by remember { mutableStateOf<DialTemplate?>(null) }
                var draft by remember { mutableStateOf<FaceDraft?>(null) }
                var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
                var builtPackage by remember { mutableStateOf<TopStepPackage?>(null) }
                var templateError by remember { mutableStateOf<String?>(null) }

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

                // Image picker: decodes, scales to the template's panel size,
                // quantises to RGB565, and builds a container in memory.
                val pickImage = rememberLauncherForActivityResult(
                    ActivityResultContracts.OpenDocument(),
                ) { uri ->
                    val template = selectedTemplate
                    if (uri != null && template != null) {
                        lifecycleScope.launch {
                            val result = withContext(Dispatchers.IO) {
                                buildFaceFromImage(uri, template)
                            }
                            if (result == null) {
                                draft = FaceDraft(
                                    template,
                                    null,
                                    false,
                                    failure ?: "Could not read that image",
                                )
                            } else {
                                draft = result.draft
                                previewBitmap = result.preview
                                builtPackage = result.pkg
                            }
                        }
                    }
                }

                // Loads a template container chosen by the user and keeps a copy
                // in app storage so it survives a restart.
                val pickTemplate = rememberLauncherForActivityResult(
                    ActivityResultContracts.OpenDocument(),
                ) { uri ->
                    if (uri != null) {
                        lifecycleScope.launch {
                            val loaded = withContext(Dispatchers.IO) { importTemplate(uri) }
                            if (loaded != null) {
                                templates = (templates + loaded)
                                    .distinctBy { it.displayName }
                                selectedTemplate = loaded
                                draft = null
                                previewBitmap = null
                                builtPackage = null
                                templateError = null
                            } else {
                                templateError = templateFailure
                                    ?: "That file is not a usable TOPSTEP container"
                            }
                        }
                    }
                }

                // Optional: inspect an existing container instead of building one.
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
                        templates = templates,
                        selectedTemplate = selectedTemplate,
                        templateError = templateError,
                        draft = draft,
                        onLoadTemplate = { pickTemplate.launch(arrayOf("*/*")) },
                        onSelectTemplate = { t ->
                            if (t.displayName != selectedTemplate?.displayName) {
                                selectedTemplate = t
                                draft = null
                                previewBitmap = null
                                builtPackage = null
                            }
                        },
                        onPickImage = { pickImage.launch(arrayOf("image/*")) },
                        onPreview = { screen = Screen.PREVIEW },
                        onSend = { screen = Screen.SEND },
                        onBack = { screen = Screen.PROBE },
                    )

                    Screen.PREVIEW -> PreviewScreen(
                        bitmap = previewBitmap,
                        width = selectedTemplate?.width ?: 0,
                        height = selectedTemplate?.height ?: 0,
                        summary = builtPackage?.let {
                            "container \"${it.name}\"  ${it.totalSize} bytes  " +
                                "payload ${it.payloadSize}"
                        } ?: "no container built",
                        onBack = { screen = Screen.WATCHFACE },
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

    /** Result of turning a user image into a container. */
    private data class BuiltFace(
        val draft: FaceDraft,
        val preview: Bitmap,
        val pkg: TopStepPackage,
    )

    /**
     * Decodes the chosen image, scales it to the template's panel, and packs it
     * into a copy of the template container.
     *
     * The container is built in memory only; nothing is written to storage and
     * nothing is sent to the watch.
     */
    /**
     * Returns the built face, or null with [failure] set describing why not.
     */
    private var failure: String? = null

    private fun buildFaceFromImage(uri: Uri, template: DialTemplate): BuiltFace? {
        failure = null
        return try {
            buildFaceOrThrow(uri, template)
        } catch (e: Exception) {
            failure = e.message ?: "Could not build the face"
            null
        }
    }

    private fun buildFaceOrThrow(uri: Uri, template: DialTemplate): BuiltFace {
        val name = resolveDisplayName(contentResolver, uri) ?: "image"
        val decoded = contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it)
        } ?: throw IllegalStateException("Could not decode that image")

        val scaled = ImageScaler.coverCrop(
            ImageScaler.Bitmap(
                decoded.width,
                decoded.height,
                IntArray(decoded.width * decoded.height).also { out ->
                    decoded.getPixels(out, 0, decoded.width, 0, 0, decoded.width, decoded.height)
                },
            ),
            template.width,
            template.height,
        )
        val rgb565 = DialBuilder.argbToRgb565Le(scaled.pixels)

        val pkg = DialBuilder.replaceBackground(template.parsed, rgb565)

        // Render the quantised result back, so the preview shows what is stored
        // rather than what was picked.
        val preview = Bitmap.createBitmap(
            IntArray(scaled.pixels.size) { i ->
                val lo = rgb565[i * 2].toInt() and 0xFF
                val hi = rgb565[i * 2 + 1].toInt() and 0xFF
                val v = lo or (hi shl 8)
                val r = (v shr 11) and 0x1F
                val g = (v shr 5) and 0x3F
                val b = v and 0x1F
                (0xFF shl 24) or
                    ((r shl 3) shl 16) or
                    ((g shl 2) shl 8) or
                    (b shl 3)
            },
            template.width,
            template.height,
            Bitmap.Config.ARGB_8888,
        )

        return BuiltFace(
            draft = FaceDraft(
                template = template,
                imageName = name,
                ready = true,
                note = "Scaled to ${template.width}×${template.height}, quantised to " +
                    "RGB565, packed into a ${pkg.totalSize}-byte container",
            ),
            preview = preview,
            pkg = pkg,
        )
    }

    /**
     * Loads templates from two places: bundled assets (none ship, by design) and
     * containers the user has imported, kept in app storage.
     */
    private fun loadTemplates(): List<DialTemplate> {
        val bundled = runCatching {
            assets.list("templates").orEmpty()
                .filter { !it.endsWith(".txt", ignoreCase = true) }
                .mapNotNull { file ->
                    runCatching {
                        DialTemplate.from(file, assets.open("templates/$file").readBytes())
                    }.getOrNull()
                }
        }.getOrDefault(emptyList())

        val imported = runCatching {
            templatesDir().listFiles().orEmpty()
                .filter { it.isFile }
                .mapNotNull { DialTemplate.from(it.name, it.readBytes()) }
        }.getOrDefault(emptyList())

        return (bundled + imported).distinctBy { it.displayName }
    }

    private fun templatesDir() = java.io.File(filesDir, "templates").apply { mkdirs() }

    /** Set when an import fails, so the UI can explain why. */
    private var templateFailure: String? = null

    /**
     * Copies a chosen container into app storage and registers it as a template.
     * Returns null if the file is not a usable container.
     */
    private fun importTemplate(uri: Uri): DialTemplate? {
        templateFailure = null
        return try {
            val name = resolveDisplayName(contentResolver, uri) ?: "template.bin"
            val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: run {
                    templateFailure = "Could not open that file"
                    return null
                }
            val template = DialTemplate.from(name, bytes)
            if (template == null) {
                templateFailure = explainTemplateRejection(bytes)
                return null
            }
            java.io.File(templatesDir(), name).writeBytes(bytes)
            template
        } catch (e: Exception) {
            templateFailure = e.message ?: "Could not import that file"
            null
        }
    }

    /** Says specifically why a file cannot be used as a template. */
    private fun explainTemplateRejection(bytes: ByteArray): String {
        if (bytes.size < TopStepPackage.HEADER_SIZE) {
            return "Too small (${bytes.size} bytes) — a container header alone is " +
                "${TopStepPackage.HEADER_SIZE} bytes"
        }
        val head = String(bytes, 0, 8, Charsets.US_ASCII)
        if (!head.startsWith(TopStepPackage.MAGIC)) {
            return "Not a TOPSTEP container — file starts with \"$head\", expected " +
                "\"${TopStepPackage.MAGIC}\""
        }
        val parsed = runCatching { TopStepParser.parse(bytes) }.getOrNull()
            ?: return "Header present but the container failed to parse"
        val bg = runCatching { DialBuilder.backgroundEntry(parsed.payload) }.getOrNull()
            ?: return "Container has no BMP background image to replace"
        return "Unusable template (background ${bg.width}×${bg.height})"
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

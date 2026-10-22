package com.noisewatchface.probe.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Read-only BLE probe.
 *
 * Deliberately does not write to any characteristic. Phase 1 of the project is
 * to learn what the watch exposes and how it frames data, before any command is
 * sent. The one exception is enabling notifications, which requires writing the
 * CCCD — that is a subscribe operation, not a command to the watch.
 */
@SuppressLint("MissingPermission")
class ProbeManager(private val context: Context) {

    private val _lines = MutableStateFlow<List<LogLine>>(emptyList())
    val lines: StateFlow<List<LogLine>> = _lines.asStateFlow()

    private val _devices = MutableStateFlow<List<ScanResult>>(emptyList())
    val devices: StateFlow<List<ScanResult>> = _devices.asStateFlow()

    private val _connectedTo = MutableStateFlow<String?>(null)
    val connectedTo: StateFlow<String?> = _connectedTo.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private val _negotiatedMtu = MutableStateFlow<Int?>(null)
    val negotiatedMtu: StateFlow<Int?> = _negotiatedMtu.asStateFlow()

    private val adapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    private var gatt: BluetoothGatt? = null
    private val seen = mutableSetOf<String>()
    private var notifying = 0
    private var notifyTotal = 0

    fun log(kind: LogLine.Kind, text: String) {
        _lines.value = _lines.value + LogLine.of(kind, text)
    }

    fun clearLog() {
        _lines.value = emptyList()
        seen.clear()
    }

    fun isBluetoothReady(): Boolean = adapter?.isEnabled == true

    fun startScan() {
        val a = adapter
        if (a == null) {
            log(LogLine.Kind.ERROR, "No Bluetooth adapter")
            return
        }
        val scanner = a.bluetoothLeScanner
        if (scanner == null) {
            log(LogLine.Kind.ERROR, "No BLE scanner (is Bluetooth off?)")
            return
        }
        if (_scanning.value) return

        _devices.value = emptyList()
        _scanning.value = true
        log(LogLine.Kind.INFO, "Scanning for BLE devices…")

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        scanner.startScan(null, settings, callback)
    }

    private val callback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val name = result.device.name ?: result.scanRecord?.deviceName
            val current = _devices.value
            val key = result.device.address
            if (current.none { it.device.address == key }) {
                _devices.value = current + result
                log(LogLine.Kind.INFO, "Found ${name ?: "(no name)"} ${result.device.address} rssi=${result.rssi}")
            }
        }

        override fun onScanFailed(errorCode: Int) {
            _scanning.value = false
            log(LogLine.Kind.ERROR, "Scan failed code=$errorCode")
        }
    }

    fun stopScan() {
        val a = adapter
        if (a == null) {
            _scanning.value = false
            return
        }
        runCatching { a.bluetoothLeScanner?.stopScan(callback) }
        if (_scanning.value) log(LogLine.Kind.INFO, "Scan stopped")
        _scanning.value = false
    }

    fun connect(device: BluetoothDevice) {
        stopScan()
        log(LogLine.Kind.INFO, "Connecting to ${device.address}…")
        _connectedTo.value = null
        gatt?.close()
        gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } else {
            device.connectGatt(context, false, gattCallback)
        }
    }

    fun disconnect() {
        runCatching { gatt?.disconnect() }
        runCatching { gatt?.close() }
        gatt = null
        _connectedTo.value = null
        _negotiatedMtu.value = null
        log(LogLine.Kind.INFO, "Disconnected")
    }

    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                _connectedTo.value = g.device.address
                log(LogLine.Kind.INFO, "Connected to ${g.device.address} (status=$status)")
                // Ask for a large MTU; the real MTU is reported in onMtuChanged.
                g.requestMtu(517)
                g.discoverServices()
                log(LogLine.Kind.INFO, "Discovering services…")
            } else {
                _connectedTo.value = null
                log(LogLine.Kind.INFO, "Disconnected (status=$status)")
                g.close()
                gatt = null
            }
        }

        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
            _negotiatedMtu.value = mtu
            // 3 bytes of ATT header per write; this is the usable payload per packet.
            log(LogLine.Kind.INFO, "MTU=$mtu  usable write payload≈${mtu - 3} bytes (status=$status)")
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                log(LogLine.Kind.ERROR, "Service discovery failed status=$status")
                return
            }
            log(LogLine.Kind.INFO, "── GATT table ─────────────────────────────")
            g.services.forEach { service ->
                val svcLabel = uuidLabel(service.uuid)?.let { "  « $it" } ?: ""
                log(LogLine.Kind.SERVICE, "Service ${service.uuid}$svcLabel")
                service.characteristics.forEach { ch ->
                    val props = propsOf(ch)
                    val chLabel = charLabel(ch.uuid)?.let { "  « $it" } ?: ""
                    log(LogLine.Kind.CHAR, "   │ ${ch.uuid} [$props]$chLabel")
                    if (props.contains("N") || props.contains("I")) {
                        subscribe(g, ch)
                    }
                }
            }
            log(LogLine.Kind.INFO, "── end GATT table ─────────────────────────")
            log(LogLine.Kind.INFO, "Subscribed to ${notifyTotal} notifying characteristics.")
            log(
                LogLine.Kind.INFO,
                "Now reproduce a watch-face transfer in NoiseFit and watch frames appear below.",
            )
        }

        @Deprecated("Required for API < 33")
        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            ch: BluetoothGattCharacteristic,
        ) {
            @Suppress("DEPRECATION")
            recordFrame(ch.uuid, ch.value)
        }

        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            ch: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            recordFrame(ch.uuid, value)
        }

        override fun onDescriptorWrite(
            g: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int,
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) notifying++
        }
    }

    private fun subscribe(g: BluetoothGatt, ch: BluetoothGattCharacteristic) {
        val ok = runCatching { g.setCharacteristicNotification(ch, true) }.getOrDefault(false)
        if (!ok) {
            log(LogLine.Kind.ERROR, "   │ could not enable notifications for ${ch.uuid}")
            return
        }
        val cccd = ch.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
        if (cccd == null) {
            log(LogLine.Kind.INFO, "   │ ${ch.uuid} has no CCCD; local notify only")
            return
        }
        val notify = (ch.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0
        val value = if (notify) {
            BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        } else {
            BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching { g.writeDescriptor(cccd, value) }
        } else {
            @Suppress("DEPRECATION")
            runCatching {
                cccd.value = value
                g.writeDescriptor(cccd)
            }
        }
        notifyTotal++
    }

    private fun recordFrame(uuid: UUID, value: ByteArray) {
        val key = uuid.toString()
        if (seen.add(key)) {
            log(LogLine.Kind.INFO, "First frame from $key")
        }
        val label = charLabel(uuid) ?: uuidLabel(uuid) ?: key
        log(LogLine.Kind.FRAME, "$label  ${value.size}B  ${value.toHex()}")
    }

    private fun propsOf(ch: BluetoothGattCharacteristic): String {
        val p = ch.properties
        val sb = StringBuilder()
        if (p and BluetoothGattCharacteristic.PROPERTY_READ != 0) sb.append('R')
        if (p and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) sb.append('W')
        if (p and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) sb.append('w')
        if (p and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) sb.append('N')
        if (p and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) sb.append('I')
        if (p and BluetoothGattCharacteristic.PROPERTY_SIGNED_WRITE != 0) sb.append('S')
        return if (sb.isEmpty()) "-" else sb.toString()
    }

    /** Plain-text transcript for sharing back for analysis. */
    fun exportText(): String = buildString {
        appendLine("Noise Watchface Probe — transcript")
        appendLine("connected to: ${_connectedTo.value ?: "none"}")
        appendLine("negotiated MTU: ${_negotiatedMtu.value ?: "unknown"}")
        appendLine("─".repeat(50))
        _lines.value.forEach { appendLine("${it.time}  ${it.kind.name.padEnd(7)} ${it.text}") }
    }
}

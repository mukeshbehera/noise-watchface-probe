package com.noisewatchface.probe.ble

import android.bluetooth.le.ScanResult
import java.util.Locale

/**
 * Helpers that turn a raw [ScanResult] into evidence a human can judge.
 *
 * Nothing here guesses a device's identity. It surfaces the signals the watch
 * actually broadcasts (name, address kind, advertised services, vendor data) so
 * the operator can tell a router from a wearable.
 */

/** BLE addresses are either public (IEEE-assigned OUI) or randomly generated. */
fun addressKind(address: String): String {
    val first = address.substringBefore(':').toIntOrNull(16) ?: return "unknown"
    // Bluetooth Core spec: the two least significant bits of a random address are
    // 00 for public, 01/11 for random (static/resolvable). Bit 1 set => random.
    return if (first and 0x02 != 0) "random" else "public"
}

fun advertisedName(result: ScanResult): String? =
    result.scanRecord?.deviceName?.takeIf { it.isNotBlank() }
        ?: result.device.name?.takeIf { it.isNotBlank() }

private val SERVICE_NAMES = mapOf(
    "0000180d-0000-1000-8000-00805f9b34fb" to "HeartRate",
    "0000180f-0000-1000-8000-00805f9b34fb" to "Battery",
    "0000180a-0000-1000-8000-00805f9b34fb" to "DeviceInfo",
    "00001800-0000-1000-8000-00805f9b34fb" to "GenericAccess",
    "00001801-0000-1000-8000-00805f9b34fb" to "GATT",
    "00001812-0000-1000-8000-00805f9b34fb" to "HID",
    "0000fe59-0000-1000-8000-00805f9b34fb" to "NordicDFU",
    "0000fee7-0000-1000-8000-00805f9b34fb" to "FEE7-vendor",
    "0000fee0-0000-1000-8000-00805f9b34fb" to "FEE0-vendor",
    "0000fff0-0000-1000-8000-00805f9b34fb" to "FFF0-vendor",
)

fun advertisedServices(result: ScanResult): List<String> =
    result.scanRecord?.serviceUuids.orEmpty().map { parcel ->
        val full = parcel.uuid.toString().lowercase(Locale.US)
        SERVICE_NAMES[full] ?: "0x" + full.substring(4, 8).uppercase(Locale.US)
    }

private val COMPANY_NAMES = mapOf(
    0x004C to "Apple",
    0x0006 to "Microsoft",
    0x00E0 to "Google",
    0x0075 to "Samsung",
    0x038F to "Xiaomi",
    0x0157 to "Huami",
    0x000F to "Broadcom",
)

fun companyLabel(id: Int): String =
    COMPANY_NAMES[id]?.let { "0x%04X $it".format(id) } ?: "0x%04X".format(id)

fun manufacturerSummary(result: ScanResult): String? {
    val data = result.scanRecord?.manufacturerSpecificData ?: return null
    if (data.size() == 0) return null
    return (0 until data.size()).joinToString(", ") { i ->
        companyLabel(data.keyAt(i))
    }
}

/**
 * Conservative heuristic, used only to sort and badge — never to auto-connect.
 *
 * A name match is strong. Advertised health/device-info or vendor services are
 * reasonable. A random address is weak on its own (phones do it too), so it is
 * shown as a label rather than counted as proof.
 */
data class ScanVerdict(val likely: Boolean, val reason: String?)

fun assess(result: ScanResult): ScanVerdict {
    val name = advertisedName(result)?.lowercase(Locale.US).orEmpty()

    val nameHit = listOf("noise", "evolve", "colorfit", "watch", "nsw", "bolt")
        .firstOrNull { name.contains(it) }
    if (nameHit != null) return ScanVerdict(true, "name contains \"$nameHit\"")

    val services = advertisedServices(result)
    val serviceHit = services.firstOrNull {
        it in setOf("FEE7-vendor", "FEE0-vendor", "FFF0-vendor", "HeartRate", "NordicDFU")
    }
    if (serviceHit != null) return ScanVerdict(true, "advertises $serviceHit")

    return ScanVerdict(false, null)
}

/** One-line evidence summary for the log and the UI. */
fun describe(result: ScanResult): String {
    val parts = mutableListOf<String>()
    parts += advertisedName(result) ?: "(no name)"
    parts += result.device.address
    parts += addressKind(result.device.address)
    if (result.isConnectable) parts += "connectable"
    val services = advertisedServices(result)
    if (services.isNotEmpty()) parts += "services=" + services.joinToString("/")
    manufacturerSummary(result)?.let { parts += "mfr=$it" }
    parts += "rssi=${result.rssi}"
    return parts.joinToString("  ")
}

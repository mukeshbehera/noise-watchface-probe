package com.noisewatchface.probe.ble

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** A single line in the probe transcript. */
data class LogLine(
    val time: String,
    val kind: Kind,
    val text: String,
) {
    enum class Kind { INFO, SERVICE, CHAR, FRAME, ERROR }

    companion object {
        private val fmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

        fun of(kind: Kind, text: String) = LogLine(fmt.format(Date()), kind, text)
    }
}

/** Cheap hex dump for byte payloads, capped so one big frame cannot flood the UI. */
fun ByteArray.toHex(maxBytes: Int = 96): String {
    if (isEmpty()) return "(empty)"
    val n = minOf(size, maxBytes)
    val sb = StringBuilder(n * 3)
    for (i in 0 until n) {
        sb.append(String.format(Locale.US, "%02X", this[i]))
        if (i != n - 1) sb.append(' ')
    }
    if (size > n) sb.append(" … (+${size - n}B)")
    return sb.toString()
}

/**
 * Maps a 16-bit or 128-bit UUID to a human label for services we expect to see
 * on the watch families this project targets.
 *
 * These labels are hints for the operator, never assumptions: the probe reports
 * whatever the watch actually advertises, and unknown UUIDs are shown as-is.
 */
fun uuidLabel(uuid: java.util.UUID): String? {
    val s = uuid.toString().lowercase(Locale.US)
    return when {
        s == "0000fee7-0000-1000-8000-00805f9b34fb" -> "FEE7 (claimed zhapp/service by prior art — unverified)"
        s == "0000fee8-0000-1000-8000-00805f9b34fb" -> "FEE8 (claimed write by prior art — unverified)"
        s == "0000fee9-0000-1000-8000-00805f9b34fb" -> "FEE9 (claimed notify by prior art — unverified)"
        s == "0000180a-0000-1000-8000-00805f9b34fb" -> "Device Information Service"
        s == "0000180f-0000-1000-8000-00805f9b34fb" -> "Battery Service"
        s == "00001800-0000-1000-8000-00805f9b34fb" -> "Generic Access"
        s == "00001801-0000-1000-8000-00805f9b34fb" -> "Generic Attribute"
        s == "00002902-0000-1000-8000-00805f9b34fb" -> "Client Characteristic Configuration (CCCD)"
        s == "0000fe59-0000-1000-8000-00805f9b34fb" -> "Nordic Secure DFU"
        s == "00001530-1212-efde-1523-785feabcd123" -> "Nordic Legacy DFU"
        s == "0000ff01-0000-1000-8000-00805f9b34fb" -> "FF01 (vendor-specific)"
        s == "0000ff02-0000-1000-8000-00805f9b34fb" -> "FF02 (vendor-specific)"
        s == "0000ff03-0000-1000-8000-00805f9b34fb" -> "FF03 (vendor-specific)"
        else -> null
    }
}

/** Names for the standard characteristic UUIDs worth calling out. */
fun charLabel(uuid: java.util.UUID): String? {
    val s = uuid.toString().lowercase(Locale.US)
    return when {
        s == "00002a19-0000-1000-8000-00805f9b34fb" -> "Battery Level"
        s == "00002a24-0000-1000-8000-00805f9b34fb" -> "Model Number String"
        s == "00002a25-0000-1000-8000-00805f9b34fb" -> "Serial Number String"
        s == "00002a26-0000-1000-8000-00805f9b34fb" -> "Firmware Revision String"
        s == "00002a27-0000-1000-8000-00805f9b34fb" -> "Hardware Revision String"
        s == "00002a28-0000-1000-8000-00805f9b34fb" -> "Software Revision String"
        s == "00002a29-0000-1000-8000-00805f9b34fb" -> "Manufacturer Name String"
        s == "00002a00-0000-1000-8000-00805f9b34fb" -> "Device Name"
        s == "00002a04-0000-1000-8000-00805f9b34fb" -> "Peripheral Preferred Connection Parameters"
        s == "00002a05-0000-1000-8000-00805f9b34fb" -> "Service Changed"
        s == "00002902-0000-1000-8000-00805f9b34fb" -> "CCCD"
        else -> null
    }
}

package com.noisewatchface.probe.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context

/**
 * Answers one question: is the watch already connected on this phone?
 *
 * This class never connects anything. The user is responsible for having the
 * watch connected (normally by NoiseFit). The app only validates that state
 * before offering watch-face features.
 *
 * Limitation worth knowing: Android reports GATT connections it can see, which
 * on many versions excludes links owned by other apps. A negative answer is
 * therefore "not observed", not "definitely disconnected".
 */
class ConnectionGate(private val context: Context) {

    data class Status(
        val connected: Boolean,
        /** Addresses observed as connected, for display. */
        val addresses: Set<String>,
        /** Name of the matching watch if one was found in the paired list. */
        val watchName: String?,
        /** Why the check could not conclude anything, if applicable. */
        val problem: String?,
    ) {
        val canProceed: Boolean get() = connected
    }

    private val adapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    fun bluetoothEnabled(): Boolean = adapter?.isEnabled == true

    @SuppressLint("MissingPermission")
    fun check(): Status {
        val a = adapter
            ?: return Status(false, emptySet(), null, "This device has no Bluetooth adapter")
        if (!a.isEnabled) {
            return Status(false, emptySet(), null, "Bluetooth is turned off")
        }

        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            ?: return Status(false, emptySet(), null, "Bluetooth service unavailable")

        val connected = runCatching {
            manager.getConnectedDevices(BluetoothProfile.GATT)
                .filter {
                    manager.getConnectionState(it, BluetoothProfile.GATT) ==
                        BluetoothProfile.STATE_CONNECTED
                }
                .map { it.address }
                .toSet()
        }.getOrElse { emptySet<String>() }

        val paired = runCatching { a.bondedDevices.orEmpty() }.getOrDefault(emptySet())
        val watch = findWatch(paired, connected)

        return Status(
            connected = connected.isNotEmpty(),
            addresses = connected,
            watchName = watch?.name ?: watch?.address,
            problem = null,
        )
    }

    /** Prefers a connected device whose name looks like the watch. */
    @SuppressLint("MissingPermission")
    private fun findWatch(
        paired: Set<BluetoothDevice>,
        connected: Set<String>,
    ): BluetoothDevice? {
        val connectedDevices = paired.filter { it.address in connected }
        return connectedDevices.firstOrNull { looksLikeWatch(it.name) }
            ?: connectedDevices.firstOrNull()
    }

    private fun looksLikeWatch(name: String?): Boolean {
        val lower = name?.lowercase() ?: return false
        return WATCH_HINTS.any { lower.contains(it) }
    }

    companion object {
        val WATCH_HINTS = listOf("noise", "evolve", "colorfit", "watch", "nsw", "bolt")
    }
}

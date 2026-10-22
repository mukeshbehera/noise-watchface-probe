package com.noisewatchface.probe.ui

import android.bluetooth.le.ScanResult
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noisewatchface.probe.ble.LogLine
import com.noisewatchface.probe.ble.addressKind
import com.noisewatchface.probe.ble.advertisedName
import com.noisewatchface.probe.ble.advertisedServices
import com.noisewatchface.probe.ble.assess

@Composable
fun ProbeScreen(
    devices: List<ScanResult>,
    connectedTo: String?,
    scanning: Boolean,
    mtu: Int?,
    identity: Map<String, String>,
    lines: List<LogLine>,
    onScan: () -> Unit,
    onStop: () -> Unit,
    onConnect: (ScanResult) -> Unit,
    onDisconnect: () -> Unit,
    onClear: () -> Unit,
    onExport: () -> Unit,
) {
    val listState = rememberLazyListState()

    // Follow the tail as frames stream in.
    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) listState.animateScrollToItem(lines.size - 1)
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
            Text(
                "Noise Watchface Probe",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Read-only GATT inspection. Reproduce a watch-face transfer in NoiseFit " +
                    "and the frames will stream below.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))

            StatusRow(connectedTo = connectedTo, mtu = mtu, scanning = scanning)

            if (identity.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Column(Modifier.padding(8.dp)) {
                        identity.forEach { (k, v) ->
                            Row {
                                Text(
                                    "$k: ",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(v, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onScan, enabled = !scanning) { Text("Scan") }
                OutlinedButton(onClick = onStop, enabled = scanning) { Text("Stop") }
                OutlinedButton(onClick = onDisconnect, enabled = connectedTo != null) {
                    Text("Disconnect")
                }
            }

            if (devices.isNotEmpty() && connectedTo == null) {
                Spacer(Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    LazyColumn(modifier = Modifier.height(180.dp).fillMaxWidth()) {
                        items(devices) { result ->
                            DeviceRow(result) { onConnect(result) }
                            Divider()
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onClear) { Text("Clear") }
                OutlinedButton(onClick = onExport) { Text("Export log") }
            }

            Spacer(Modifier.height(8.dp))
            // weight(1f) so the transcript takes exactly the space left over and
            // never overflows the column.
            Card(
                modifier = Modifier.fillMaxWidth().weight(1f),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF11131A)),
                shape = RoundedCornerShape(10.dp),
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(8.dp),
                ) {
                    items(lines) { line -> LogRow(line) }
                }
            }
        }
    }
}

@Composable
private fun StatusRow(connectedTo: String?, mtu: Int?, scanning: Boolean) {
    val status = when {
        connectedTo != null -> "Connected: $connectedTo" + (mtu?.let { "  MTU=$it" } ?: "")
        scanning -> "Scanning…"
        else -> "Not connected"
    }
    val color = when {
        connectedTo != null -> Color(0xFF57D9A3)
        scanning -> Color(0xFFF2C94C)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(status, style = MaterialTheme.typography.bodyMedium, color = color)
}

@Composable
private fun DeviceRow(result: ScanResult, onClick: () -> Unit) {
    val name = advertisedName(result) ?: "(no name)"
    val verdict = assess(result)
    val kind = addressKind(result.device.address)
    val services = advertisedServices(result)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                if (verdict.likely) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "LIKELY WATCH",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0B2A1F),
                        modifier = Modifier
                            .background(Color(0xFF57D9A3), RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 2.dp),
                    )
                }
            }
            Text(
                "${result.device.address}  ($kind)",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (services.isNotEmpty()) {
                Text(
                    "advertises: " + services.joinToString(", "),
                    fontSize = 10.sp,
                    color = Color(0xFF7FD1FF),
                )
            }
            // Weak signal on its own, shown only when nothing stronger matched.
            if (!verdict.likely && kind == "random") {
                Text(
                    "random address — typical of wearables and phones",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            verdict.reason?.let {
                Text(it, fontSize = 10.sp, color = Color(0xFF57D9A3))
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text("${result.rssi} dBm", fontSize = 11.sp)
            Text(if (result.isConnectable) "conn" else "n/conn", fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun LogRow(line: LogLine) {
    val color = when (line.kind) {
        LogLine.Kind.ERROR -> Color(0xFFFF7A7A)
        LogLine.Kind.FRAME -> Color(0xFF7FD1FF)
        LogLine.Kind.SERVICE -> Color(0xFFF2C94C)
        LogLine.Kind.CHAR -> Color(0xFFB8B8C8)
        LogLine.Kind.INFO -> Color(0xFFB8B8C8)
    }
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
        Text(
            line.time,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            color = Color(0xFF6B6B7B),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            line.text,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            color = color,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

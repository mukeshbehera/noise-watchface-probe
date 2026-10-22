package com.noisewatchface.probe.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noisewatchface.probe.ble.ConnectionGate

/**
 * Validates that the watch is already connected. It never opens a connection:
 * the user is expected to have the watch connected (normally via the official
 * app) before coming here.
 */
@Composable
fun GateScreen(
    status: ConnectionGate.Status?,
    onValidate: () -> Unit,
    onContinue: () -> Unit,
    onBack: () -> Unit,
) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().padding(12.dp)) {
            ScreenHeader("Check connection", "the app does not connect for you", onBack)
            Spacer(Modifier.height(14.dp))

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = when {
                        status == null -> MaterialTheme.colorScheme.surfaceVariant
                        status.canProceed -> Color(0xFF12301F)
                        else -> Color(0xFF3A1F1F)
                    },
                ),
                shape = RoundedCornerShape(10.dp),
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        when {
                            status == null -> "Not checked yet"
                            status.canProceed -> "Watch is connected"
                            else -> "No connected watch found"
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                    )
                    Spacer(Modifier.height(6.dp))

                    when {
                        status == null -> Text(
                            "Tap validate to read the current Bluetooth connection state.",
                            fontSize = 11.sp,
                        )
                        status.problem != null -> Text(
                            status.problem,
                            fontSize = 11.sp,
                            color = Color(0xFFFFB4B4),
                        )
                        status.canProceed -> {
                            status.watchName?.let {
                                Text("Device: $it", fontSize = 12.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                            }
                            Text(
                                "Connected: " + status.addresses.joinToString(),
                                fontSize = 11.sp,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                color = Color(0xFFB8B8C8),
                            )
                        }
                        else -> Text(
                            "Android reports no active GATT connection. Connect the watch in " +
                                "the official app first, then validate again.",
                            fontSize = 11.sp,
                            color = Color(0xFFFFB4B4),
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onValidate) { Text("Validate") }
                Button(onClick = onContinue, enabled = status?.canProceed == true) {
                    Text("Continue")
                }
            }

            if (status != null && !status.canProceed) {
                Spacer(Modifier.height(14.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2418)),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(
                        "This check reads connection state only. On some Android versions " +
                            "connections owned by another app are not reported, so a negative " +
                            "result means \"not observed\" rather than \"definitely off\".",
                        modifier = Modifier.padding(10.dp),
                        fontSize = 10.sp,
                        color = Color(0xFFF2C94C),
                    )
                }
            }
        }
    }
}

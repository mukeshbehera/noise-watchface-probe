package com.noisewatchface.probe.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Shown when the transfer path is not implemented. Deliberately states what is
 * missing rather than presenting a progress bar that would never move.
 */
@Composable
fun SendScreen(onBack: () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().padding(12.dp)) {
            ScreenHeader("Send to watch", "not implemented", onBack)
            Spacer(Modifier.height(14.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF3A1F1F)),
                shape = RoundedCornerShape(10.dp),
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        "No transfer path exists yet",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color(0xFFFFB4B4),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Installing a dial means pushing the resource to the watch and then " +
                            "calling the watch's own install and apply operations. Neither is " +
                            "implemented, because the BLE framing has not been observed for " +
                            "this model.",
                        fontSize = 11.sp,
                        color = Color(0xFFDDDDDD),
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Text("What is known", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            Spacer(Modifier.height(4.dp))
            Step("✓", "Container format parses and rebuilds byte-exactly")
            Step("✓", "Watch exposes install and apply operations (observed in the vendor app)")
            Step("✗", "BLE framing and handshake for this model")
            Step("✗", "Whether the firmware rejects unsigned packages")

            Spacer(Modifier.height(12.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2418)),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(
                    "The last unknown is the important one. If the watch requires a signature, " +
                        "no unsigned dial can be installed regardless of framing, and the project " +
                        "would need a different approach.",
                    modifier = Modifier.padding(10.dp),
                    fontSize = 11.sp,
                    color = Color(0xFFF2C94C),
                )
            }
        }
    }
}

@Composable
private fun Step(mark: String, text: String) {
    Row(Modifier.padding(vertical = 2.dp)) {
        Text(
            mark,
            fontSize = 12.sp,
            modifier = Modifier.padding(end = 8.dp),
            color = if (mark == "✓") Color(0xFF57D9A3) else Color(0xFFFFB4B4),
        )
        Text(text, fontSize = 11.sp)
    }
}

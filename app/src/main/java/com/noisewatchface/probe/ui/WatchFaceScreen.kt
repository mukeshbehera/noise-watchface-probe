package com.noisewatchface.probe.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noisewatchface.probe.dial.DialCandidate

@Composable
fun WatchFaceScreen(
    watchName: String?,
    candidate: DialCandidate?,
    onPickFile: () -> Unit,
    onInspect: () -> Unit,
    onSend: () -> Unit,
    onBack: () -> Unit,
) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
        ) {
            ScreenHeader("Watch faces", watchName, onBack)

            Spacer(Modifier.height(10.dp))
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
                shape = RoundedCornerShape(10.dp),
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text("1 · Choose a dial file", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text(
                        "A .bin or .zip containing a TOPSTEP container, as shipped in the " +
                            "official app.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onPickFile) { Text("Pick file") }
                }
            }

            if (candidate != null) {
                Spacer(Modifier.height(10.dp))
                CandidateCard(candidate)
            }

            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onInspect, enabled = candidate != null) {
                    Text("2 · Inspect")
                }
                Button(
                    onClick = onSend,
                    enabled = candidate?.valid == true,
                ) {
                    Text("3 · Send to watch")
                }
            }

            Spacer(Modifier.height(12.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2418)),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(
                    "Sending is not implemented. The container can be parsed and rebuilt " +
                        "locally, but the transfer path and apply step do not exist yet, so " +
                        "this button will not do anything.",
                    modifier = Modifier.padding(10.dp),
                    fontSize = 11.sp,
                    color = Color(0xFFF2C94C),
                )
            }
        }
    }
}

@Composable
private fun CandidateCard(candidate: DialCandidate) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (candidate.valid) Color(0xFF12301F) else Color(0xFF3A1F1F),
        ),
        shape = RoundedCornerShape(10.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(candidate.displayName, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text("${candidate.byteCount} bytes", fontSize = 11.sp, color = Color(0xFFB8B8C8))
            Spacer(Modifier.height(6.dp))
            Text(
                if (candidate.valid) "Recognised TOPSTEP container" else "Not usable",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (candidate.valid) Color(0xFF57D9A3) else Color(0xFFFFB4B4),
            )
            Text(
                candidate.payloadSummary,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = Color(0xFFB8B8C8),
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
fun ScreenHeader(title: String, subtitle: String?, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "‹ back",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clickable(onClick = onBack)
                .padding(end = 10.dp),
        )
        Column {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (subtitle != null) {
                Text(subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

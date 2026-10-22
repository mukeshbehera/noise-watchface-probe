package com.noisewatchface.probe.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noisewatchface.probe.dial.TopStepPackage

@Composable
fun InspectScreen(pkg: TopStepPackage, rebuiltSize: Int, onBack: () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
        ) {
            ScreenHeader("Inspect", "read-only view of the container", onBack)
            Spacer(Modifier.height(10.dp))

            Field("container name", pkg.name)
            Field("header size", "${pkg.headerSize} bytes")
            Field("payload size", "${pkg.payloadSize} bytes")
            Field("total size", "${pkg.totalSize} bytes")
            Field("version", "0x%08X".format(pkg.version))
            Field("payload magic", "\"${pkg.payloadMagic}\"")
            Field("rebuilt size", "$rebuiltSize bytes")

            Spacer(Modifier.height(10.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF11131A)),
                shape = RoundedCornerShape(8.dp),
            ) {
                Column(Modifier.padding(10.dp)) {
                    Text(
                        "Header (first 96 bytes)",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFF2C94C),
                    )
                    Spacer(Modifier.height(4.dp))
                    hexRows(pkg.header, 96)
                }
            }

            Spacer(Modifier.height(10.dp))
            Text(
                "Fields not modelled by the parser are preserved verbatim, which is why an " +
                    "unmodified container rebuilds to the same size. Whether the rebuilt bytes " +
                    "are accepted by the watch is unknown until the push path exists.",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Field(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            label,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(end = 10.dp),
        )
        Text(value, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun hexRows(bytes: ByteArray, limit: Int) {
    val n = minOf(bytes.size, limit)
    var i = 0
    while (i < n) {
        val end = minOf(i + 16, n)
        val row = StringBuilder()
        for (j in i until end) {
            row.append("%02X ".format(bytes[j]))
        }
        Text(
            "%04X  %s".format(i, row.toString().trimEnd()),
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            color = Color(0xFFB8B8C8),
        )
        i = end
    }
}

package com.noisewatchface.probe.ui

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
import com.noisewatchface.probe.dial.DialTemplate
import com.noisewatchface.probe.dial.FaceDraft

@Composable
fun WatchFaceScreen(
    watchName: String?,
    templates: List<DialTemplate>,
    selectedTemplate: DialTemplate?,
    templateError: String?,
    draft: FaceDraft?,
    onLoadTemplate: () -> Unit,
    onSelectTemplate: (DialTemplate) -> Unit,
    onPickImage: () -> Unit,
    onPreview: () -> Unit,
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
            SectionTitle("1 · Choose a template")
            Text(
                "A template is a dial the official app ships. It supplies everything the " +
                    "watch expects; you replace only its background image.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))

            if (templates.isEmpty()) {
                Notice(
                    "No templates loaded yet. A template is a dial container the app can " +
                        "reuse; load one to continue. Files starting with \"TOPSTEP\" work, " +
                        "such as a dial .bin extracted from the official app.",
                    Color(0xFF2A2418),
                    Color(0xFFF2C94C),
                )
            } else {
                templates.forEach { t ->
                    TemplateRow(
                        template = t,
                        selected = t.displayName == selectedTemplate?.displayName,
                    ) { onSelectTemplate(t) }
                }
            }

            Spacer(Modifier.height(6.dp))
            OutlinedButton(onClick = onLoadTemplate) { Text("Load template file") }

            if (templateError != null) {
                Spacer(Modifier.height(6.dp))
                Notice(templateError, Color(0xFF3A1F1F), Color(0xFFFFB4B4))
            }

            Spacer(Modifier.height(12.dp))
            SectionTitle("2 · Choose your image")
            Text(
                "Any photo. It is scaled to fill the screen and centre-cropped to " +
                    "${selectedTemplate?.let { "${it.width}×${it.height}" } ?: "the watch size"}.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Button(onClick = onPickImage, enabled = selectedTemplate != null) {
                Text("Pick image")
            }
            if (selectedTemplate == null) {
                Text(
                    "Load or select a template first — the image replaces that template's " +
                        "background.",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            if (draft != null) {
                Spacer(Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (draft.ready) Color(0xFF12301F) else Color(0xFF3A1F1F),
                    ),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Column(Modifier.padding(10.dp)) {
                        Text(
                            draft.imageName ?: "image",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(draft.note, fontSize = 11.sp, color = Color(0xFFB8B8C8))
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            SectionTitle("3 · Build and send")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onPreview, enabled = draft?.ready == true) {
                    Text("Preview")
                }
                Button(onClick = onSend, enabled = draft?.ready == true) {
                    Text("Send to watch")
                }
            }

            Spacer(Modifier.height(12.dp))
            Notice(
                "Sending is not implemented. The app can build a valid container locally, " +
                    "but it has no channel to the watch while the official app holds the " +
                    "connection.",
                Color(0xFF2A2418),
                Color(0xFFF2C94C),
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, fontSize = 13.sp, fontWeight = FontWeight.Bold)
}

@Composable
private fun Notice(text: String, bg: Color, fg: Color) {
    Card(colors = CardDefaults.cardColors(containerColor = bg), shape = RoundedCornerShape(8.dp)) {
        Text(text, modifier = Modifier.padding(10.dp), fontSize = 11.sp, color = fg)
    }
}

@Composable
private fun TemplateRow(template: DialTemplate, selected: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .padding(vertical = 3.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(template.displayName, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "${template.width}×${template.height}",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(if (selected) "selected" else "choose", fontSize = 11.sp)
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

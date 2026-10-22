package com.noisewatchface.probe.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Shows the processed image at the templates's exact pixel dimensions, before it
 * would be packed into a container.
 */
@Composable
fun PreviewScreen(
    bitmap: Bitmap?,
    width: Int,
    height: Int,
    summary: String,
    onBack: () -> Unit,
) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().padding(12.dp)) {
            ScreenHeader("Preview", "$width×$height RGB565", onBack)
            Spacer(Modifier.height(12.dp))

            if (bitmap == null) {
                Text("Nothing to preview.", fontSize = 12.sp)
            } else {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Processed watch face image",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(width.toFloat() / height)
                        .clip(RoundedCornerShape(12.dp)),
                )
            }

            Spacer(Modifier.height(12.dp))
            Text(
                summary,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = Color(0xFFB8B8C8),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "This is how the pixels will be stored. Note the image is quantised to " +
                    "16-bit colour, so gradients may band compared with the original.",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

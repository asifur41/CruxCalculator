package com.tapecalc.feature.tools

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.tapecalc.core.ui.rememberStoredInt
import com.tapecalc.core.ui.setValue
import com.tapecalc.core.ui.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.unit.dp
import com.tapecalc.core.math.qr.QrCode
import com.tapecalc.core.math.qr.QrEcc
import com.tapecalc.core.math.qr.QrMatrix

/**
 * QR code maker. The code is drawn dark-on-white with the 4-module quiet zone the standard asks for, so it
 * scans even in dark mode. Nothing is uploaded: the encoder is plain Kotlin inside the app.
 */
@Composable
internal fun QrPane(text: String, onTextChange: (String) -> Unit, modifier: Modifier = Modifier) {
    var eccIndex by rememberStoredInt("tool.qr.ecc", 1)
    val ecc = if (eccIndex == 0) QrEcc.L else QrEcc.M
    val clipboard = LocalClipboardManager.current

    val code: QrMatrix? = remember(text, ecc) {
        if (text.isEmpty() || !QrCode.fits(text, ecc)) null else QrCode.encode(text, ecc)
    }

    Column(modifier = modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "Share a result, a data list or an equation as a QR code that another phone can scan.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
            value = text,
            onValueChange = { onTextChange(it.take(1500)) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Text for the QR code") },
            minLines = 3
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = eccIndex == 0, onClick = { eccIndex = 0 }, label = { Text("Level L (more data)") })
            FilterChip(selected = eccIndex == 1, onClick = { eccIndex = 1 }, label = { Text("Level M (safer)") })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                val pasted = clipboard.getText()?.text
                if (!pasted.isNullOrEmpty()) onTextChange(pasted.take(1500))
            }) { Text("Paste") }
            OutlinedButton(onClick = { onTextChange("") }) { Text("Clear") }
        }

        if (text.isNotEmpty() && code == null) {
            Text(
                "That is too long for one QR code (about ${QrCode.maxBytes(ecc)} bytes at this level). Shorten it or use level L.",
                color = MaterialTheme.colorScheme.error
            )
        }
        if (code != null) {
            val quiet = 4
            Canvas(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
                drawRect(color = Color.White, topLeft = Offset.Zero, size = size)
                val modules = code.size + quiet * 2
                val cell = size.width / modules
                for (y in 0 until code.size) {
                    for (x in 0 until code.size) {
                        if (code[x, y]) {
                            drawRect(
                                color = Color.Black,
                                topLeft = Offset((x + quiet) * cell, (y + quiet) * cell),
                                // a hair larger than one cell so no thin seams show between neighbours
                                size = Size(cell + 0.6f, cell + 0.6f)
                            )
                        }
                    }
                }
            }
            Text(
                "Version ${code.version}, ${code.size}×${code.size} modules. Turn the screen brightness up when scanning.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

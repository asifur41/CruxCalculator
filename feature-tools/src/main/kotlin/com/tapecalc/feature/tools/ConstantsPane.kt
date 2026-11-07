package com.tapecalc.feature.tools

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.tapecalc.core.ui.rememberStoredText
import com.tapecalc.core.ui.setValue
import com.tapecalc.core.ui.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tapecalc.core.math.Constants
import com.tapecalc.core.precision.NumberFormatter

/** The built-in constants table. Tap a row to put the constant into the calculator. */
@Composable
internal fun ConstantsPane(onUseInCalculator: (String) -> Unit, modifier: Modifier = Modifier) {
    var query by rememberStoredText("tool.constants.query", "")
    val items = remember(query) {
        val q = query.trim().lowercase()
        Constants.all.filter {
            q.isEmpty() || it.name.lowercase().contains(q) || it.description.lowercase().contains(q) || it.symbol.lowercase() == q
        }
    }
    Column(modifier = modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Type the short name in any calculator, for example 2*c0 or hP*3. Tap a row to calculate it.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it.take(30) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Search") },
            singleLine = true
        )
        var lastGroup = ""
        items.forEach { c ->
            if (c.group != lastGroup) {
                lastGroup = c.group
                Text(
                    c.group,
                    modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onUseInCalculator(c.name) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(Modifier.padding(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(c.name, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary)
                        Text("(${c.symbol})", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(c.description, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "${NumberFormatter.formatScientific(c.value.decimal)} ${c.unit}".trim(),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

package com.tapecalc.feature.tools

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tapecalc.core.math.EvalContext
import com.tapecalc.core.math.stats.RegressionType
import com.tapecalc.core.math.stats.StatColumn
import com.tapecalc.core.math.stats.StatTableEngine
import com.tapecalc.core.math.stats.StatTableState
import com.tapecalc.core.math.tools.ToolResult
import com.tapecalc.core.ui.ExpressionField
import com.tapecalc.core.ui.LocalToolStateStore

private const val STAT_KEY = "tool.stattable.state"

/**
 * The statistics data-entry screen: the table of a physical calculator's STAT mode. Choose 1-variable (X) or 2-variable
 * (X, Y), switch the FREQ column on or off, type the data row by row (a new empty row appears under the last one), then
 * Calculate. The table is kept on the device as it is edited.
 */
@Composable
internal fun StatTablePane(
    ctx: EvalContext,
    onSaveToTape: (String, String, String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val store = LocalToolStateStore.current
    var state by remember(store) { mutableStateOf(StatTableState.decode(store.get(STAT_KEY))) }
    var result by remember { mutableStateOf<ToolResult?>(null) }
    val clipboard = LocalClipboardManager.current

    fun update(next: StatTableState) {
        state = next
        result = null // an answer for the old data would be wrong now
        store.put(STAT_KEY, next.encode())
    }

    fun calculate() {
        val computed = try {
            StatTableEngine.run(state, ctx)
        } catch (e: Exception) {
            ToolResult.Err("Could not work that out. Please check the table.")
        } catch (e: StackOverflowError) {
            ToolResult.Err("That input is too complicated.")
        }
        result = computed
        if (computed is ToolResult.Ok && computed.lines.isNotEmpty()) {
            val summary = computed.lines.joinToString("; ") { if (it.label.isEmpty()) it.value else "${it.label} = ${it.value}" }
            onSaveToTape("Statistics table", StatTableEngine.inputText(state), summary, computed.copyText)
        }
    }

    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                "Type the data like on a calculator's STAT screen. Cells can hold expressions such as 1/2 or sqrt(2).",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !state.twoVar, onClick = { update(state.withMode(false)) }, label = { Text("1-Variable  (X)") })
                FilterChip(selected = state.twoVar, onClick = { update(state.withMode(true)) }, label = { Text("2-Variable  (X, Y)") })
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Switch(checked = state.useFreq, onCheckedChange = { update(state.withFreq(it)) })
                Text("Frequency column (FREQ)", style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (state.twoVar) {
            item {
                // one lazy item must have a single root: several composables would be drawn on top of each other
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Curve for the regression", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        RegressionType.entries.forEachIndexed { index, type ->
                            FilterChip(
                                selected = state.regression == index,
                                onClick = { update(state.withRegression(index)) },
                                label = { Text(type.label.substringBefore("  ")) }
                            )
                        }
                    }
                }
            }
        }
        item { HeaderRow(twoVar = state.twoVar, useFreq = state.useFreq) }
        items(count = state.rows.size, key = { it }) { index ->
            val row = state.rows[index]
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "${index + 1}",
                    modifier = Modifier.padding(end = 2.dp).size(width = 26.dp, height = 20.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Cell(row.x, "x", Modifier.weight(1f)) { update(state.withCell(index, StatColumn.X, it)) }
                if (state.twoVar) Cell(row.y, "y", Modifier.weight(1f)) { update(state.withCell(index, StatColumn.Y, it)) }
                if (state.useFreq) Cell(row.f, "1", Modifier.weight(0.7f)) { update(state.withCell(index, StatColumn.F, it)) }
                IconButton(onClick = { update(state.withRowRemoved(index)) }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Outlined.Close, contentDescription = "Delete row ${index + 1}", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { update(state.withRowAdded()) }, enabled = state.rows.size < StatTableState.MAX_ROWS) { Text("+ Row") }
                OutlinedButton(onClick = { update(state.cleared()) }) { Text("Clear data") }
                Text(
                    "${state.filledRows} of ${StatTableState.MAX_ROWS} rows",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (state.twoVar) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExpressionField(
                        value = state.predictY,
                        onValueChange = { update(state.withPredictY(it)) },
                        modifier = Modifier.fillMaxWidth(),
                        label = "Predict y for x = (optional)"
                    )
                    ExpressionField(
                        value = state.estimateX,
                        onValueChange = { update(state.withEstimateX(it)) },
                        modifier = Modifier.fillMaxWidth(),
                        label = "Estimate x for y = (optional)"
                    )
                }
            }
        }
        item {
            Button(onClick = { calculate() }, modifier = Modifier.fillMaxWidth()) { Text("Calculate") }
        }
        val r = result
        if (r != null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        when (r) {
                            is ToolResult.Err -> Text(r.message, color = MaterialTheme.colorScheme.error)
                            is ToolResult.Ok -> r.lines.forEach { ResultRow(it) }
                        }
                    }
                }
            }
            if (r is ToolResult.Ok) {
                item {
                    OutlinedButton(onClick = { clipboard.setText(AnnotatedString(r.copyText)) }) { Text("Copy") }
                }
            }
        }
        item { Box(Modifier.size(16.dp)) }
    }
}

@Composable
private fun HeaderRow(twoVar: Boolean, useFreq: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = 28.dp, height = 20.dp))
        HeaderLabel("X", Modifier.weight(1f))
        if (twoVar) HeaderLabel("Y", Modifier.weight(1f))
        if (useFreq) HeaderLabel("FREQ", Modifier.weight(0.7f))
        Box(Modifier.size(36.dp))
    }
}

@Composable
private fun HeaderLabel(text: String, modifier: Modifier) {
    Text(
        text,
        modifier = modifier,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun Cell(value: String, placeholder: String, modifier: Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = modifier,
        singleLine = true,
        textStyle = TextStyle(fontSize = 15.sp, fontFamily = FontFamily.Monospace),
        placeholder = { Text(placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next)
    )
}

package com.tapecalc.feature.converter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tapecalc.core.math.CalcException
import com.tapecalc.core.math.Calculator
import com.tapecalc.core.math.EvalOutcome
import com.tapecalc.core.math.UnitCategory
import com.tapecalc.core.math.UnitDef
import com.tapecalc.core.math.UnitRegistry
import com.tapecalc.core.precision.NumberFormatter

private sealed interface Conversion {
    data class Done(val text: String, val summary: String) : Conversion
    data class Problem(val message: String) : Conversion
}

private fun convert(input: String, from: UnitDef, to: UnitDef): Conversion {
    val text = input.trim().ifEmpty { "1" }
    return when (val outcome = Calculator.evaluate(text)) {
        is EvalOutcome.Failure -> Conversion.Problem(outcome.error.message)
        is EvalOutcome.Success -> try {
            val converted = UnitRegistry.convert(outcome.result.value, from, to)
            val value = NumberFormatter.format(converted.decimal)
            Conversion.Done(
                text = "$value ${to.symbol}",
                summary = "${outcome.result.display} ${from.symbol} = $value ${to.symbol}"
            )
        } catch (e: CalcException) {
            Conversion.Problem(e.error.message)
        }
    }
}

/**
 * Offline unit converter. Works for 12 categories; the same conversions are available inline
 * in every calculator tab (for example `12 km to mi`).
 */
@Composable
fun ConverterScreen(
    onUseInCalculator: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val categories = UnitCategory.entries
    var categoryIndex by rememberSaveable { mutableStateOf(0) }
    val category = categories[categoryIndex.coerceIn(0, categories.lastIndex)]
    val units = remember(category) { UnitRegistry.byCategory(category) }

    var fromId by rememberSaveable { mutableStateOf("") }
    var toId by rememberSaveable { mutableStateOf("") }
    var input by rememberSaveable { mutableStateOf("1") }

    val from = units.firstOrNull { it.id == fromId } ?: units.first()
    val to = units.firstOrNull { it.id == toId } ?: units.getOrElse(1) { units.first() }
    val conversion = remember(input, from, to) { convert(input, from, to) }
    val clipboard = LocalClipboardManager.current

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Convert", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(categories) { c ->
                FilterChip(
                    selected = c == category,
                    onClick = {
                        categoryIndex = c.ordinal
                        fromId = ""
                        toId = ""
                    },
                    label = { Text(c.title) }
                )
            }
        }

        OutlinedTextField(
            value = input,
            onValueChange = { input = it.take(60) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Value (you can type an expression such as 2*5)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
        )

        UnitPicker("From", from, units) { fromId = it.id }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            IconButton(onClick = {
                val f = from.id
                fromId = to.id
                toId = f
            }) {
                Icon(Icons.Outlined.SwapVert, contentDescription = "Swap units")
            }
        }

        UnitPicker("To", to, units) { toId = it.id }

        Card(
            modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                when (conversion) {
                    is Conversion.Done -> {
                        Text(conversion.text, fontSize = 32.sp, fontWeight = FontWeight.SemiBold)
                        Text(conversion.summary, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    is Conversion.Problem -> Text(conversion.message, color = MaterialTheme.colorScheme.error)
                }
            }
        }

        if (conversion is Conversion.Done) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { clipboard.setText(AnnotatedString(conversion.text)) }) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = null)
                    Spacer(Modifier.padding(horizontal = 4.dp))
                    Text("Copy")
                }
                Button(onClick = { onUseInCalculator("${input.trim().ifEmpty { "1" }} ${from.symbol} to ${to.symbol}") }) {
                    Text("Use in calculator")
                }
            }
        }

        Text(
            "Tip: type 12 km to mi, 5 kg to lb or 72 F to C directly into the calculator (keyboard icon).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun UnitPicker(label: String, selected: UnitDef, units: List<UnitDef>, onSelect: (UnitDef) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text("${selected.name} (${selected.symbol})", modifier = Modifier.weight(1f))
            Text("▾")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            units.forEach { u ->
                DropdownMenuItem(
                    text = { Text("${u.name} (${u.symbol})") },
                    onClick = {
                        expanded = false
                        onSelect(u)
                    }
                )
            }
        }
    }
}

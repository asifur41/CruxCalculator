package com.tapecalc.feature.converter

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.setValue
import com.tapecalc.core.ui.getValue
import com.tapecalc.core.ui.rememberStoredInt
import com.tapecalc.core.ui.rememberStoredText
import com.tapecalc.core.ui.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tapecalc.core.math.BaseConversion
import com.tapecalc.core.math.CalcException
import com.tapecalc.core.math.Calculator
import com.tapecalc.core.math.EvalOutcome
import com.tapecalc.core.math.NumberBase
import com.tapecalc.core.math.NumberBases
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
 * Offline converter. Works for 12 unit categories (the same conversions are available inline in
 * every calculator tab, for example `12 km to mi`) plus a "Number system" mode for binary, octal,
 * decimal, hexadecimal and other bases.
 */
@Composable
fun ConverterScreen(
    onUseInCalculator: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val categories = UnitCategory.entries
    // One extra chip after the unit categories selects the number-system mode.
    val numberSystemIndex = categories.size
    var categoryIndex by rememberStoredInt("convert.category", 0)
    val numberMode = categoryIndex == numberSystemIndex
    val category = categories[categoryIndex.coerceIn(0, categories.lastIndex)]
    val units = remember(category) { UnitRegistry.byCategory(category) }

    var fromId by rememberStoredText("convert.from", "")
    var toId by rememberStoredText("convert.to", "")
    var input by rememberStoredText("convert.input", "1")

    var baseInput by rememberStoredText("convert.baseInput", "255")
    var baseFromRadix by rememberStoredInt("convert.baseFrom", NumberBases.DECIMAL.radix)
    var baseToRadix by rememberStoredInt("convert.baseTo", NumberBases.HEXADECIMAL.radix)
    val baseFrom = NumberBases.all.firstOrNull { it.radix == baseFromRadix } ?: NumberBases.DECIMAL
    val baseTo = NumberBases.all.firstOrNull { it.radix == baseToRadix } ?: NumberBases.HEXADECIMAL

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

        // A static grid (three rows) so every category is visible at once: no sideways scrolling.
        CategoryGrid(
            categories = categories,
            numberMode = numberMode,
            selected = category,
            onSelectCategory = { c ->
                categoryIndex = c.ordinal
                fromId = ""
                toId = ""
            },
            onSelectNumberSystem = { categoryIndex = numberSystemIndex }
        )

        if (numberMode) {
            NumberSystemPane(
                input = baseInput,
                onInputChange = { baseInput = it },
                from = baseFrom,
                onFromChange = { baseFromRadix = it.radix },
                to = baseTo,
                onToChange = { baseToRadix = it.radix },
                onSwap = {
                    val f = baseFromRadix
                    baseFromRadix = baseToRadix
                    baseToRadix = f
                },
                onUseInCalculator = onUseInCalculator
            )
        } else {
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
        }

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

/**
 * Number-system converter: whole numbers between binary, octal, decimal, hexadecimal and other bases.
 * All state is held by [ConverterScreen] so it survives switching between chips.
 */
@Composable
private fun NumberSystemPane(
    input: String,
    onInputChange: (String) -> Unit,
    from: NumberBase,
    onFromChange: (NumberBase) -> Unit,
    to: NumberBase,
    onToChange: (NumberBase) -> Unit,
    onSwap: () -> Unit,
    onUseInCalculator: (String) -> Unit
) {
    val clipboard = LocalClipboardManager.current
    val conversion = remember(input, from, to) {
        if (input.isBlank()) null else NumberBases.convert(input, from, to)
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = input,
            onValueChange = { onInputChange(it.take(60)) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Number in ${from.label} (digits ${NumberBases.digitHint(from)})") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                keyboardType = KeyboardType.Ascii
            )
        )

        BasePicker("From", from, onFromChange)

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            IconButton(onClick = onSwap) {
                Icon(Icons.Outlined.SwapVert, contentDescription = "Swap number systems")
            }
        }

        BasePicker("To", to, onToChange)

        Card(
            modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                when (conversion) {
                    null -> Text(
                        "Type a number to convert",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    is BaseConversion.Invalid -> Text(conversion.message, color = MaterialTheme.colorScheme.error)
                    is BaseConversion.Ok -> {
                        Text(
                            conversion.text,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            "${input.trim()} (base ${from.radix}) = ${conversion.text} (base ${to.radix})",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        NumberBases.common.forEach { base ->
                            Row(verticalAlignment = Alignment.Top) {
                                Text(
                                    base.tag,
                                    modifier = Modifier.width(48.dp),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    NumberBases.formatGrouped(conversion.value, base),
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }
        }

        if (conversion is BaseConversion.Ok) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { clipboard.setText(AnnotatedString(conversion.text)) }) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = null)
                    Spacer(Modifier.padding(horizontal = 4.dp))
                    Text("Copy")
                }
                // The calculator works in decimal, so it receives the value as a decimal number.
                Button(onClick = { onUseInCalculator(conversion.value.toString()) }) {
                    Text("Use in calculator")
                }
            }
        }

        Text(
            "Whole numbers only. Spaces and prefixes such as 1111 1111, 0b1010 or 0xFF are accepted.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun BasePicker(label: String, selected: NumberBase, onSelect: (NumberBase) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text(selected.label, modifier = Modifier.weight(1f))
            Text("▾")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            NumberBases.all.forEach { base ->
                DropdownMenuItem(
                    text = { Text(base.label) },
                    onClick = {
                        expanded = false
                        onSelect(base)
                    }
                )
            }
        }
    }
}

private const val GRID_COLUMNS = 5

/** Short names so five tiles fit across a phone; the full name is what TalkBack reads. */
private fun shortLabel(c: UnitCategory): String = when (c) {
    UnitCategory.TEMPERATURE -> "Temp"
    UnitCategory.FUEL -> "Fuel"
    else -> c.title
}

/**
 * All unit categories plus "Number system" as a fixed grid: 5 columns, so the 14 entries
 * fill exactly 3 rows (5 + 5 + 4). Nothing scrolls sideways.
 */
@Composable
private fun CategoryGrid(
    categories: List<UnitCategory>,
    numberMode: Boolean,
    selected: UnitCategory,
    onSelectCategory: (UnitCategory) -> Unit,
    onSelectNumberSystem: () -> Unit
) {
    val total = categories.size + 1
    val rows = (total + GRID_COLUMNS - 1) / GRID_COLUMNS
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (r in 0 until rows) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (col in 0 until GRID_COLUMNS) {
                    val index = r * GRID_COLUMNS + col
                    val tileModifier = Modifier.weight(1f)
                    when {
                        index < categories.size -> {
                            val c = categories[index]
                            CategoryTile(
                                label = shortLabel(c),
                                description = c.title,
                                selected = !numberMode && c == selected,
                                onClick = { onSelectCategory(c) },
                                modifier = tileModifier
                            )
                        }
                        index == categories.size -> CategoryTile(
                            label = "Number system",
                            description = "Number system",
                            selected = numberMode,
                            onClick = onSelectNumberSystem,
                            modifier = tileModifier
                        )
                        else -> Spacer(tileModifier)
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryTile(
    label: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val container = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val content = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = modifier
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(container)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = description }
            .padding(horizontal = 2.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = content,
            fontSize = 12.sp,
            lineHeight = 14.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            // The tile's own description (full category name) is what TalkBack reads.
            modifier = Modifier.clearAndSetSemantics { }
        )
    }
}

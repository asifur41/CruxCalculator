package com.tapecalc.feature.tools

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.Casino
import androidx.compose.material.icons.outlined.Equalizer
import androidx.compose.material.icons.outlined.FormatListNumbered
import androidx.compose.material.icons.outlined.Functions
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.QrCode
import androidx.compose.material.icons.outlined.Rule
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material.icons.outlined.TrendingFlat
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.tapecalc.core.math.Constants
import androidx.compose.ui.unit.sp
import com.tapecalc.core.math.AngleMode
import com.tapecalc.core.math.EvalContext
import com.tapecalc.core.math.tools.ResultLine
import com.tapecalc.core.math.tools.ToolHistory
import com.tapecalc.core.math.tools.ToolInput
import com.tapecalc.core.math.tools.ToolResult
import com.tapecalc.core.math.tools.ToolSpec
import com.tapecalc.core.math.tools.Tools
import com.tapecalc.core.ui.ExpressionField
import com.tapecalc.core.ui.LocalToolStateStore

private const val GRAPH_ID = "graph"
private const val CONSTANTS_ID = "constants"
private const val QR_ID = "qr"
private const val STAT_TABLE_ID = "stattable"
private const val SHEET_ID = "sheet"

/** Tools whose single answer is a number the calculator can read back. */
private val numericResultTools = setOf("solve", "calculus", "dist")

private class HubEntry(val id: String, val title: String, val summary: String)

/** One distinctive icon per tool, so the list can be scanned by picture as well as by name. */
private fun iconFor(id: String): ImageVector = when (id) {
    "poly" -> Icons.Outlined.Timeline
    "linear" -> Icons.Outlined.Rule
    "solve" -> Icons.Outlined.TrendingFlat
    "calculus" -> Icons.Outlined.Analytics
    "algebra" -> Icons.Outlined.Functions
    "stats1" -> Icons.Outlined.BarChart
    STAT_TABLE_ID -> Icons.Outlined.Equalizer
    SHEET_ID -> Icons.Outlined.TableChart
    "stats2" -> Icons.Outlined.TrendingUp
    "dist" -> Icons.Outlined.Casino
    "basen" -> Icons.Outlined.Memory
    "matrix" -> Icons.Outlined.GridOn
    "eigen" -> Icons.Outlined.Layers
    "vector" -> Icons.Outlined.Straighten
    "complex" -> Icons.Outlined.Calculate
    "steps" -> Icons.Outlined.FormatListNumbered
    GRAPH_ID -> Icons.Outlined.ShowChart
    CONSTANTS_ID -> Icons.Outlined.Science
    QR_ID -> Icons.Outlined.QrCode
    else -> Icons.Outlined.Functions
}

private val hubEntries: List<HubEntry> = buildList {
    for (tool in Tools.all) {
        // the statistics data-entry table sits just before the other statistics tools
        if (tool.id == "stats1") {
            add(HubEntry(STAT_TABLE_ID, "Statistics table", "Enter 1-variable or 2-variable data (X, Y, FREQ) in a table, like a calculator's STAT mode"))
        }
        add(HubEntry(tool.id, tool.title, tool.summary))
    }
    add(HubEntry(GRAPH_ID, "Graph", "Plot up to three functions, pan, zoom, trace, find roots and peaks"))
    add(HubEntry(SHEET_ID, "Spreadsheet", "A grid of 5 columns (A–E) by 45 rows with formulas, sum and mean"))
    add(HubEntry(CONSTANTS_ID, "Constants", "${Constants.all.size} physical constants: tap one to use it"))
    add(HubEntry(QR_ID, "QR code", "Turn any text, result or equation into a QR code (works offline)"))
}

/**
 * The "Tools" tab: a list of extra maths tools (equation solvers, statistics, matrices, graphing...).
 * Everything runs on the phone; nothing is sent anywhere.
 */
@Composable
fun ToolsScreen(
    angleMode: AngleMode,
    /** The id of the open tool, or "" for the list. The caller keeps it on the device so it is still open when the person returns. */
    currentTool: String,
    onCurrentChange: (String) -> Unit,
    qrText: String,
    onQrTextChange: (String) -> Unit,
    onUseInCalculator: (String) -> Unit,
    /** Called with (tool title, what was entered, result on one line, result one value per line) after each calculation. */
    onSaveToTape: (String, String, String, String) -> Unit = { _, _, _, _ -> },
    modifier: Modifier = Modifier
) {
    val ctx = remember(angleMode) { EvalContext(angleMode = angleMode) }
    // A remembered id that no longer exists (after an update) just shows the list.
    val current = if (hubEntries.any { it.id == currentTool }) currentTool else ""

    // The graph takes the whole area (no title bar, no padding); it has its own back button.
    if (current == GRAPH_ID) {
        GraphPane(angleMode = angleMode, onBack = { onCurrentChange("") }, modifier = modifier.fillMaxSize())
        return
    }

    Column(modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        if (current.isEmpty()) {
            ToolsHub(onOpen = onCurrentChange)
        } else {
            val entry = hubEntries.firstOrNull { it.id == current }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { onCurrentChange("") }) { Text("‹ Tools") }
                Spacer(Modifier.padding(horizontal = 6.dp))
                Icon(iconFor(current), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.padding(horizontal = 4.dp))
                Text(
                    entry?.title ?: "",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(8.dp))
            when (current) {
                CONSTANTS_ID -> ConstantsPane(onUseInCalculator = onUseInCalculator, modifier = Modifier.weight(1f))
                QR_ID -> QrPane(text = qrText, onTextChange = onQrTextChange, modifier = Modifier.weight(1f))
                STAT_TABLE_ID -> StatTablePane(ctx = ctx, onSaveToTape = onSaveToTape, modifier = Modifier.weight(1f))
                SHEET_ID -> SheetPane(angleMode = angleMode, onUseInCalculator = onUseInCalculator, modifier = Modifier.weight(1f))
                else -> {
                    val spec = Tools.byId(current)
                    if (spec != null) {
                        FormTool(
                            spec = spec,
                            ctx = ctx,
                            onUseInCalculator = onUseInCalculator,
                            onMakeQr = { onQrTextChange(it); onCurrentChange(QR_ID) },
                            onSaveToTape = onSaveToTape,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolsHub(onOpen: (String) -> Unit) {
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Tools", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "Solvers, statistics, matrices, graphs and more. All offline.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        hubEntries.forEach { entry ->
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onOpen(entry.id) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(iconFor(entry.id), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.padding(horizontal = 7.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(entry.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            entry.summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

/**
 * Draws any [ToolSpec]: its text boxes, its chips, a Calculate button and the answer.
 *
 * Everything the person typed or picked, and whether an answer was showing, is kept on the device as it changes, so a
 * tool comes back exactly as it was left: after switching tabs, leaving the app, or closing it. Every successful
 * Calculate is also written to the tape.
 */
@Composable
private fun FormTool(
    spec: ToolSpec,
    ctx: EvalContext,
    onUseInCalculator: (String) -> Unit,
    onMakeQr: (String) -> Unit,
    onSaveToTape: (String, String, String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val store = LocalToolStateStore.current
    val textKey = { fieldId: String -> "tool.${spec.id}.t.$fieldId" }
    val choiceKey = { optionId: String -> "tool.${spec.id}.c.$optionId" }
    val ranKey = "tool.${spec.id}.ran"

    val texts = remember(spec.id, store) {
        mutableStateMapOf<String, String>().apply { spec.fields.forEach { put(it.id, store.get(textKey(it.id)) ?: it.default) } }
    }
    val choices = remember(spec.id, store) {
        mutableStateMapOf<String, Int>().apply {
            spec.options.forEach { put(it.id, store.get(choiceKey(it.id))?.toIntOrNull()?.takeIf { i -> i in it.choices.indices } ?: it.default) }
        }
    }
    var result by remember(spec.id) { mutableStateOf<ToolResult?>(null) }
    val clipboard = LocalClipboardManager.current

    fun compute(): ToolResult = try {
        spec.run(ToolInput(texts.toMap(), choices.toMap(), ctx))
    } catch (e: Exception) {
        ToolResult.Err("Could not work that out. Please check the input.")
    } catch (e: StackOverflowError) {
        ToolResult.Err("That input is too complicated.")
    }

    // Coming back to a tool that was showing an answer: work the answer out again from the saved boxes.
    LaunchedEffect(spec.id, store) {
        if (store.get(ranKey) == "1") result = compute()
    }

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            spec.summary,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // The chips and boxes may depend on earlier chips (Distribution / Function / Data), so they are asked for afresh.
        val picked = spec.choiceLookup(choices.toMap())
        spec.optionsFor(picked).forEach { option ->
            Text(option.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                option.choices.forEachIndexed { index, label ->
                    FilterChip(
                        selected = (choices[option.id] ?: option.default) == index,
                        onClick = {
                            choices[option.id] = index
                            store.put(choiceKey(option.id), index.toString())
                            store.put(ranKey, null)
                            result = null
                        },
                        label = { Text(label) }
                    )
                }
            }
        }

        spec.fieldsFor(picked).forEach { field ->
            ExpressionField(
                value = texts[field.id] ?: "",
                onValueChange = {
                    val v = it.take(600)
                    texts[field.id] = v
                    store.put(textKey(field.id), v)
                },
                modifier = Modifier.fillMaxWidth(),
                label = field.label,
                placeholder = field.hint.ifEmpty { null },
                singleLine = !field.multiline,
                minLines = if (field.multiline) 3 else 1,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
            )
        }

        Button(
            onClick = {
                val computed = compute()
                result = computed
                store.put(ranKey, "1")
                // every successful calculation is also written to the tape
                ToolHistory.record(spec, texts.toMap(), choices.toMap(), computed)?.let {
                    onSaveToTape(spec.title, it.input, it.summary, it.full)
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Calculate") }

        val r = result
        if (r != null) {
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
            if (r is ToolResult.Ok) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { clipboard.setText(AnnotatedString(r.copyText)) }) { Text("Copy") }
                    OutlinedButton(onClick = { onMakeQr(r.copyText) }) { Text("QR code") }
                    if (r.lines.size == 1 && spec.id in numericResultTools) {
                        Button(onClick = { onUseInCalculator(r.lines[0].value) }) { Text("Use") }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
internal fun ResultRow(line: ResultLine) {
    Column {
        if (line.label.isNotEmpty()) {
            Text(line.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            line.value,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace
        )
    }
}

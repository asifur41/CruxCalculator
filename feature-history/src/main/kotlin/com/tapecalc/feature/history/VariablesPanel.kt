package com.tapecalc.feature.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tapecalc.core.data.domain.VariableItem
import kotlinx.coroutines.launch

// "E" is left out on purpose: it is the reserved name of Euler's number and cannot be a variable.
private val ScalarNames: List<String> = ('A'..'Z').filter { it != 'E' }.map { it.toString() } + listOf("x", "y")
private val MatrixNames = listOf("MatA", "MatB", "MatC", "MatD")
private val VectorNames = listOf("VctA", "VctB", "VctC", "VctD")
private const val MATRIX_PREFIX = "mat:"
private const val VECTOR_PREFIX = "vec:"

/**
 * The variables screen: every value the calculator can remember, in one place.
 * Letters A to Z (and x, y), the last two answers, four 3×3 matrices and four vectors.
 * Each row has a box to type the value, a trash can to empty it and an arrow that sends it to the calculator.
 * Nothing here opens the keyboard until a box is tapped.
 */
@Composable
fun VariablesPanel(
    variables: List<VariableItem>,
    ans: String?,
    preAns: String?,
    actions: HistoryActions,
    modifier: Modifier = Modifier
) {
    val byName = remember(variables) { variables.associateBy { it.name } }
    val others = remember(variables) {
        variables.filter { it.name !in ScalarNames && it.name !in MatrixNames && it.name !in VectorNames }
    }
    LazyColumn(
        modifier = modifier.padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                "Type a value into a box and press Done. The arrow sends it to the calculator, the bin empties it.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        item { SectionLabel("Previous answers") }
        item { AnswerRow("Ans", ans, actions) }
        item { AnswerRow("PreAns", preAns, actions) }

        item { SectionLabel("Variables") }
        items(ScalarNames, key = { "s$it" }) { name -> ScalarRow(name, byName[name], actions) }

        item { SectionLabel("Matrices (3×3)") }
        items(MatrixNames, key = { "m$it" }) { name -> GridCard(name, byName[name], rows = 3, cols = 3, actions) }

        item { SectionLabel("Vectors") }
        items(VectorNames, key = { "v$it" }) { name -> GridCard(name, byName[name], rows = 1, cols = 3, actions) }

        if (others.isNotEmpty()) {
            item { SectionLabel("Other saved values") }
            items(others, key = { "o${it.name}" }) { v -> ScalarRow(v.name, v, actions) }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        modifier = Modifier.padding(top = 10.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun AnswerRow(name: String, value: String?, actions: HistoryActions) {
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(Modifier.fillMaxWidth().padding(start = 14.dp, top = 2.dp, bottom = 2.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(name, fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.width(72.dp))
            Text(
                value ?: "no answer yet",
                modifier = Modifier.weight(1f),
                color = if (value == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                fontFamily = FontFamily.Monospace,
                maxLines = 1
            )
            // PreAns cannot be typed in an expression, so its arrow sends the number itself
            IconButton(
                onClick = { actions.onInsertText(if (name == "Ans") "ans" else value.orEmpty()) },
                enabled = value != null
            ) { Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = "Send $name to the calculator") }
        }
    }
}

@Composable
private fun ScalarRow(name: String, item: VariableItem?, actions: HistoryActions) {
    var text by remember(name, item?.displayText) { mutableStateOf(item?.displayText ?: "") }
    var error by remember(name) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun save() {
        val typed = text.trim()
        if (typed.isEmpty()) return
        scope.launch { error = actions.onAddVariable("$name = $typed") }
    }

    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(name, fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.width(34.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(60); error = null },
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text("empty") },
                isError = error != null,
                supportingText = { error?.let { Text(it) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { save() })
            )
            if (text.trim() != (item?.displayText ?: "") && text.isNotBlank()) {
                IconButton(onClick = { save() }) { Icon(Icons.Outlined.Check, contentDescription = "Save $name") }
            }
            IconButton(onClick = { text = ""; error = null; actions.onDeleteVariable(name) }, enabled = item != null) {
                Icon(Icons.Outlined.Delete, contentDescription = "Empty $name")
            }
            IconButton(onClick = { actions.onInsertText(name) }, enabled = item != null) {
                Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = "Send $name to the calculator")
            }
        }
    }
}

/** Splits stored matrix / vector text ("mat:1,2,3;4,5,6;7,8,9") into [rows] × [cols] cells, padding with blanks. */
private fun parseCells(item: VariableItem?, rows: Int, cols: Int): List<String> {
    val body = item?.valueText?.substringAfter(':', "").orEmpty()
    val parsed = if (body.isEmpty()) emptyList() else body.split(';').map { it.split(',') }
    return List(rows * cols) { i -> parsed.getOrNull(i / cols)?.getOrNull(i % cols).orEmpty() }
}

/** A matrix (several rows) or a vector (one row) drawn as a bracketed grid of boxes. */
@Composable
private fun GridCard(name: String, item: VariableItem?, rows: Int, cols: Int, actions: HistoryActions) {
    val cells = remember(name, item?.valueText) { mutableStateListOf<String>().apply { addAll(parseCells(item, rows, cols)) } }
    val isMatrix = rows > 1
    val bracket = TextStyle(fontSize = (24 + (rows - 1) * 18).sp, fontWeight = FontWeight.Light, color = MaterialTheme.colorScheme.onSurface)

    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.weight(1f))
                IconButton(onClick = {
                    val rowsText = (0 until rows).joinToString(";") { r -> (0 until cols).joinToString(",") { c -> cells[r * cols + c].trim() } }
                    val prefix = if (isMatrix) MATRIX_PREFIX else VECTOR_PREFIX
                    actions.onSaveRaw(name, prefix + rowsText, if (isMatrix) "$rows×$cols matrix" else "vector")
                }) { Icon(Icons.Outlined.Check, contentDescription = "Save $name") }
                IconButton(onClick = {
                    for (i in cells.indices) cells[i] = ""
                    actions.onDeleteVariable(name)
                }, enabled = item != null || cells.any { it.isNotBlank() }) {
                    Icon(Icons.Outlined.Delete, contentDescription = "Empty $name")
                }
                IconButton(onClick = {
                    val text = if (isMatrix) {
                        (0 until rows).joinToString("\n") { r -> (0 until cols).joinToString(" ") { c -> cells[r * cols + c].trim().ifEmpty { "0" } } }
                    } else {
                        (0 until cols).joinToString(", ") { c -> cells[c].trim().ifEmpty { "0" } }
                    }
                    actions.onSendToTool(if (isMatrix) "matrix" else "vector", name, text)
                }) { Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = "Send $name to the ${if (isMatrix) "Matrices" else "Vectors"} tool") }
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("[", style = bracket)
                Column(Modifier.weight(1f).padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (r in 0 until rows) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            for (c in 0 until cols) {
                                val index = r * cols + c
                                Cell(
                                    value = cells[index],
                                    onChange = { cells[index] = it.take(12) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
                Text("]", style = bracket)
            }
        }
    }
}

/** One compact box of a matrix or vector. */
@Composable
private fun Cell(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(8.dp)
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp, textAlign = TextAlign.Center),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
        modifier = modifier
            .height(40.dp)
            .background(MaterialTheme.colorScheme.surface, shape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .padding(horizontal = 4.dp, vertical = 10.dp)
    )
}

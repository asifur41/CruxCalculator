package com.tapecalc.feature.history

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tapecalc.core.data.domain.HistoryEntry
import com.tapecalc.core.ui.LocalCalcLook

data class EditPreview(val text: String, val isError: Boolean)

class EntryEditorActions(
    val onSave: (expression: String, label: String, note: String, tags: String) -> Unit,
    val onTogglePin: () -> Unit,
    val onDuplicate: () -> Unit,
    val onDelete: () -> Unit,
    val onUseResult: () -> Unit,
    val onCopyExpression: () -> Unit,
    val onCopyResult: () -> Unit
)

/**
 * Edit an earlier calculation. Changing the expression re-evaluates it, and later entries that
 * use `ans` or an edited variable are updated (or marked stale, depending on the setting).
 */
@Composable
fun EntryEditorDialog(
    entry: HistoryEntry,
    autoRecalc: Boolean,
    preview: (String) -> EditPreview,
    actions: EntryEditorActions,
    onDismiss: () -> Unit
) {
    var expression by remember(entry.id) { mutableStateOf(entry.expression) }
    var label by remember(entry.id) { mutableStateOf(entry.label ?: "") }
    var note by remember(entry.id) { mutableStateOf(entry.note ?: "") }
    var tags by remember(entry.id) { mutableStateOf(entry.tags.joinToString(", ")) }
    var confirmDelete by remember(entry.id) { mutableStateOf(false) }
    val livePreview = remember(expression) { preview(expression) }
    val colors = LocalCalcLook.current.colors

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit calculation") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = expression,
                    onValueChange = { expression = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Expression") }
                )
                Text(
                    text = livePreview.text,
                    color = if (livePreview.isError) colors.error else MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                )
                Text(
                    if (autoRecalc) "Later results that use ans or this variable will update automatically."
                    else "Later results that use ans or this variable will be marked stale until you recalculate.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.muted
                )
                OutlinedTextField(
                    value = label, onValueChange = { label = it.take(40) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text("Label (for example Rent)") }
                )
                OutlinedTextField(
                    value = note, onValueChange = { note = it.take(500) },
                    modifier = Modifier.fillMaxWidth(), minLines = 2,
                    label = { Text("Note") }
                )
                OutlinedTextField(
                    value = tags, onValueChange = { tags = it.take(100) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text("Tags, separated by commas") }
                )
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(onClick = actions.onTogglePin, label = { Text(if (entry.pinned) "Unpin" else "Pin") })
                    AssistChip(onClick = actions.onDuplicate, label = { Text("Duplicate") })
                    AssistChip(onClick = actions.onUseResult, label = { Text("Use result") })
                    AssistChip(onClick = actions.onCopyExpression, label = { Text("Copy expression") })
                    AssistChip(onClick = actions.onCopyResult, label = { Text("Copy result") })
                }
                if (confirmDelete) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = actions.onDelete) { Text("Yes, delete") }
                        TextButton(onClick = { confirmDelete = false }) { Text("Keep") }
                    }
                } else {
                    TextButton(onClick = { confirmDelete = true }, modifier = Modifier.padding(0.dp)) {
                        Text("Delete this calculation", color = colors.error)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { actions.onSave(expression, label, note, tags) }, enabled = expression.isNotBlank()) {
                Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

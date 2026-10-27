package com.tapecalc.pro.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.tapecalc.core.data.domain.Workspace

/** Free-text entry, for example `12 km to mi`, `rate = 0.18` or `sqrt(2)*5`. */
@Composable
fun TypeExpressionDialog(initial: String, onCalculate: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Type an expression") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Expression") },
                    placeholder = { Text("12 km to mi") }
                )
                Text(
                    "Examples:\n• 12 km to mi     • 5 kg to lb     • 72 F to C\n• rate = 0.18      • 200 * rate\n• sin(30) + 2^8    • 1200 + 15%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = { onCalculate(text) }, enabled = text.isNotBlank()) { Text("Calculate") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** Switch between, add, rename or delete workspaces (Personal, Work, Study, Engineering, ...). */
@Composable
fun WorkspaceDialog(
    workspaces: List<Workspace>,
    activeId: Long,
    onSelect: (Long) -> Unit,
    onAdd: (String) -> Unit,
    onRename: (Long, String) -> Unit,
    onDelete: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    var newName by remember { mutableStateOf("") }
    var renameText by remember(activeId) { mutableStateOf(workspaces.firstOrNull { it.id == activeId }?.name ?: "") }
    var confirmDelete by remember { mutableStateOf(false) }
    val active = workspaces.firstOrNull { it.id == activeId }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Workspaces") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                workspaces.forEach { w ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .selectable(selected = w.id == activeId, role = Role.RadioButton) {
                                onSelect(w.id)
                                onDismiss()
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = w.id == activeId, onClick = null)
                        Text(w.name, modifier = Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyLarge)
                    }
                }
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it.take(40) },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    singleLine = true,
                    label = { Text("New workspace") },
                    trailingIcon = {
                        TextButton(onClick = {
                            onAdd(newName)
                            newName = ""
                            onDismiss()
                        }, enabled = newName.isNotBlank()) { Text("Add") }
                    }
                )
                if (active != null) {
                    OutlinedTextField(
                        value = renameText,
                        onValueChange = { renameText = it.take(40) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Rename \"${active.name}\"") },
                        trailingIcon = {
                            TextButton(onClick = {
                                onRename(active.id, renameText)
                                onDismiss()
                            }, enabled = renameText.isNotBlank() && renameText != active.name) { Text("Save") }
                        }
                    )
                    if (workspaces.size > 1) {
                        if (confirmDelete) {
                            Text(
                                "Delete \"${active.name}\" and every calculation in it? This cannot be undone.",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = {
                                    onDelete(active.id)
                                    onDismiss()
                                }) { Text("Delete") }
                                TextButton(onClick = { confirmDelete = false }) { Text("Keep") }
                            }
                        } else {
                            TextButton(onClick = { confirmDelete = true }) {
                                Text("Delete \"${active.name}\"", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}

/** After picking an export format: share as text, or save it as a file. */
@Composable
fun ExportDialog(
    workspaceName: String,
    formatLabel: String,
    onShare: () -> Unit,
    onSaveFile: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export $workspaceName") },
        text = {
            Text("$formatLabel is ready. Share it with another app, or save it as a file on this device. Nothing is uploaded by CruxCalculator.")
        },
        confirmButton = { Button(onClick = onShare) { Text("Share") } },
        dismissButton = {
            Row {
                TextButton(onClick = onSaveFile) { Text("Save file") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}

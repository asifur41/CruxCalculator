package com.tapecalc.feature.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tapecalc.core.data.domain.EntryStatus
import com.tapecalc.core.data.domain.ExportFormat
import com.tapecalc.core.data.domain.HistoryEntry
import com.tapecalc.core.data.domain.HistoryFilter
import com.tapecalc.core.data.domain.VariableItem
import com.tapecalc.core.precision.NumberFormatter
import com.tapecalc.core.ui.LocalCalcLook
import com.tapecalc.core.ui.StatusChip
import kotlinx.coroutines.launch

enum class HistorySection(val title: String) { TAPE("Tape"), VARIABLES("Variables"), NOTES("Notes") }

class HistoryActions(
    val onEntryClick: (HistoryEntry) -> Unit,
    val onExport: (ExportFormat) -> Unit,
    val onRecalculateStale: () -> Unit,
    val onClearTape: () -> Unit,
    /** Returns an error message, or null when the variable was saved. */
    val onAddVariable: suspend (String) -> String?,
    val onDeleteVariable: (String) -> Unit,
    val onInsertText: (String) -> Unit
)

private fun grouped(text: String, enabled: Boolean) = if (enabled) NumberFormatter.group(text) else text

/** Tape, variables and notes. Pass [onSectionChange] to show section chips, or drive [section] from outside. */
@Composable
fun HistoryHub(
    entries: List<HistoryEntry>,
    variables: List<VariableItem>,
    section: HistorySection,
    onSectionChange: ((HistorySection) -> Unit)?,
    workspaceName: String,
    groupDigits: Boolean,
    actions: HistoryActions,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        if (onSectionChange != null) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HistorySection.entries.forEach { s ->
                    FilterChip(selected = s == section, onClick = { onSectionChange(s) }, label = { Text(s.title) })
                }
            }
        }
        when (section) {
            HistorySection.TAPE -> TapePanel(entries, workspaceName, groupDigits, actions, Modifier.weight(1f))
            HistorySection.VARIABLES -> VariablesPanel(variables, actions, Modifier.weight(1f))
            HistorySection.NOTES -> NotesPanel(entries, groupDigits, actions, Modifier.weight(1f))
        }
    }
}

@Composable
fun TapePanel(
    entries: List<HistoryEntry>,
    workspaceName: String,
    groupDigits: Boolean,
    actions: HistoryActions,
    modifier: Modifier = Modifier
) {
    var query by rememberSaveable { mutableStateOf("") }
    var pinnedOnly by rememberSaveable { mutableStateOf(false) }
    var exportMenu by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    val filtered = remember(entries, query, pinnedOnly) { HistoryFilter.filter(entries, query, pinnedOnly) }
    val staleCount = entries.count { it.status == EntryStatus.STALE }
    val listState = rememberLazyListState()

    LaunchedEffect(entries.size, query, pinnedOnly) {
        if (query.isEmpty() && !pinnedOnly && filtered.isNotEmpty()) listState.animateScrollToItem(filtered.lastIndex)
    }

    Column(modifier) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                placeholder = { Text("Search expressions, labels, notes, #tags", maxLines = 1, overflow = TextOverflow.Ellipsis) }
            )
            Box {
                IconButton(onClick = { exportMenu = true }) {
                    Icon(Icons.Outlined.Share, contentDescription = "Export $workspaceName")
                }
                DropdownMenu(expanded = exportMenu, onDismissRequest = { exportMenu = false }) {
                    ExportFormat.entries.forEach { f ->
                        DropdownMenuItem(
                            text = { Text(f.label) },
                            onClick = {
                                exportMenu = false
                                actions.onExport(f)
                            }
                        )
                    }
                }
            }
            IconButton(onClick = { confirmClear = true }, enabled = entries.isNotEmpty()) {
                Icon(Icons.Outlined.Delete, contentDescription = "Clear this tape")
            }
        }
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = pinnedOnly,
                onClick = { pinnedOnly = !pinnedOnly },
                label = { Text("Pinned") },
                leadingIcon = { Icon(Icons.Outlined.PushPin, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
            Text(
                "${filtered.size} of ${entries.size}",
                modifier = Modifier.align(Alignment.CenterVertically),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (staleCount > 0) {
            val warn = LocalCalcLook.current.colors.warning
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(Modifier.padding(start = 14.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "$staleCount result${if (staleCount == 1) " is" else "s are"} stale because an earlier entry changed.",
                        color = warn,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    TextButton(onClick = actions.onRecalculateStale) { Text("Recalculate") }
                }
            }
        }

        if (filtered.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    if (entries.isEmpty()) "Your tape is empty.\nPress = in the calculator and every calculation lands here. Tap one to edit it."
                    else "Nothing matches your search.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filtered, key = { it.id }) { entry ->
                    EntryRow(entry, groupDigits) { actions.onEntryClick(entry) }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear $workspaceName?") },
            text = { Text("All ${entries.size} calculations in this tape will be deleted from this device. Variables are kept.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    actions.onClearTape()
                }) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun EntryRow(entry: HistoryEntry, groupDigits: Boolean, onClick: () -> Unit) {
    val colors = LocalCalcLook.current.colors
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            Modifier
                .clickable(onClickLabel = "Edit this calculation", onClick = onClick)
                .semantics(mergeDescendants = true) {}
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            val label = entry.label
            val note = entry.note
            val fraction = entry.fraction
            val hasBadges = !label.isNullOrBlank() || entry.pinned || entry.status != EntryStatus.OK
            if (hasBadges) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (!label.isNullOrBlank()) StatusChip(label)
                    when (entry.status) {
                        EntryStatus.STALE -> StatusChip(
                            "Stale", container = colors.warning.copy(alpha = 0.18f), content = colors.warning
                        )
                        EntryStatus.UPDATED -> StatusChip(
                            "Updated", container = MaterialTheme.colorScheme.primary, content = MaterialTheme.colorScheme.onPrimary
                        )
                        EntryStatus.OK -> Unit
                    }
                    if (entry.pinned) {
                        Icon(Icons.Outlined.PushPin, contentDescription = "Pinned", modifier = Modifier.size(16.dp), tint = colors.muted)
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
            Text(
                entry.expression,
                color = colors.muted,
                fontSize = 15.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (entry.isError) entry.displayResult else "= " + grouped(entry.displayResult, groupDigits),
                color = if (entry.isError) colors.error else MaterialTheme.colorScheme.onSurface,
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (!entry.isError && fraction != null) {
                Text("= $fraction", color = colors.muted, fontSize = 14.sp)
            }
            if (!note.isNullOrBlank()) {
                Text(note, color = colors.muted, fontSize = 14.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            if (entry.tags.isNotEmpty()) {
                Text(entry.tags.joinToString("  ") { "#$it" }, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
            }
        }
    }
}

@Composable
fun NotesPanel(
    entries: List<HistoryEntry>,
    groupDigits: Boolean,
    actions: HistoryActions,
    modifier: Modifier = Modifier
) {
    val annotated = remember(entries) {
        entries.filter { !it.label.isNullOrBlank() || !it.note.isNullOrBlank() || it.tags.isNotEmpty() }.reversed()
    }
    if (annotated.isEmpty()) {
        Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text(
                "Add a label, note or #tag to any calculation (tap it on the tape) and it shows up here, for example \"Rent\", \"Client quote\" or \"Physics homework\".",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(annotated, key = { it.id }) { entry ->
                EntryRow(entry, groupDigits) { actions.onEntryClick(entry) }
            }
        }
    }
}

@Composable
fun VariablesPanel(
    variables: List<VariableItem>,
    actions: HistoryActions,
    modifier: Modifier = Modifier
) {
    var input by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun submit() {
        val text = input
        if (text.isBlank()) return
        scope.launch {
            val problem = actions.onAddVariable(text)
            if (problem == null) {
                input = ""
                error = null
            } else {
                error = problem
            }
        }
    }

    Column(modifier.padding(horizontal = 16.dp)) {
        Text(
            "Save values once and reuse them in any expression. You can also type  x = 5  into the calculator.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it; error = null },
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text("name = value, e.g. rate = 0.18") },
                isError = error != null,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() })
            )
            Button(onClick = { submit() }, enabled = input.isNotBlank()) { Text("Save") }
        }
        if (error != null) {
            Text(error ?: "", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(8.dp))
        if (variables.isEmpty()) {
            Text("No variables yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(variables, key = { it.name }) { v ->
                    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable(onClickLabel = "Insert ${v.name} into the calculator") { actions.onInsertText(v.name) }
                                .padding(start = 14.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(v.name, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                Text(v.displayText, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { actions.onInsertText(v.name) }) {
                                Icon(Icons.Outlined.Add, contentDescription = "Insert ${v.name}")
                            }
                            IconButton(onClick = { actions.onDeleteVariable(v.name) }) {
                                Icon(Icons.Outlined.Delete, contentDescription = "Delete ${v.name}")
                            }
                        }
                    }
                }
            }
        }
    }
}

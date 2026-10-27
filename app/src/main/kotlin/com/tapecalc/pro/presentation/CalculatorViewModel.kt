package com.tapecalc.pro.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tapecalc.core.data.domain.ExportFormat
import com.tapecalc.core.data.domain.HistoryEntry
import com.tapecalc.core.data.domain.SubmitResult
import com.tapecalc.core.data.domain.TapeRecalculator
import com.tapecalc.core.data.domain.VariableItem
import com.tapecalc.core.data.domain.Workspace
import com.tapecalc.core.data.repo.TapeRepository
import com.tapecalc.core.data.settings.AppSettings
import com.tapecalc.core.data.settings.SettingsRepository
import com.tapecalc.core.math.AngleMode
import com.tapecalc.core.math.CalcValue
import com.tapecalc.core.math.Calculator
import com.tapecalc.core.math.EvalContext
import com.tapecalc.core.math.EvalOutcome
import com.tapecalc.core.math.EvalResult
import com.tapecalc.core.math.SmartPaste
import com.tapecalc.core.precision.NumberFormatter
import com.tapecalc.core.ui.KeyAction
import com.tapecalc.feature.basic.DisplayState
import com.tapecalc.feature.basic.MiniTapeLine
import com.tapecalc.feature.history.EditPreview
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A result that has just been calculated with "=". */
data class CommittedResult(val expression: String, val result: EvalResult)

data class CalcUiState(
    val expression: String = "",
    val committed: CommittedResult? = null,
    /** null = follow the "fractions by default" setting. */
    val fractionOverride: Boolean? = null,
    val error: String? = null
)

sealed interface UiEvent {
    data class Message(val text: String) : UiEvent
    data class CopyText(val text: String, val message: String) : UiEvent
    data class ExportReady(val text: String, val format: ExportFormat, val workspaceName: String) : UiEvent
    data object RequestPaste : UiEvent
}

@HiltViewModel
class CalculatorViewModel @Inject constructor(
    private val repo: TapeRepository,
    private val settingsRepo: SettingsRepository
) : ViewModel() {

    val settings: StateFlow<AppSettings> =
        settingsRepo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    val workspaces: StateFlow<List<Workspace>> =
        repo.observeWorkspaces().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val variables: StateFlow<List<VariableItem>> =
        repo.observeVariables().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val activeWorkspace: StateFlow<Workspace?> = combine(settings, workspaces) { s, list ->
        list.firstOrNull { it.id == s.workspaceId } ?: list.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val entries: StateFlow<List<HistoryEntry>> = activeWorkspace
        .map { it?.id ?: -1L }
        .distinctUntilChanged()
        .flatMapLatest { id -> repo.observeEntries(id) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _ui = MutableStateFlow(CalcUiState())
    val ui: StateFlow<CalcUiState> = _ui.asStateFlow()

    private val _events = Channel<UiEvent>(Channel.BUFFERED)
    val events: Flow<UiEvent> = _events.receiveAsFlow()

    /** Id of the tape entry being edited in the dialog, or null. */
    private val _editingId = MutableStateFlow<Long?>(null)
    val editingId: StateFlow<Long?> = _editingId.asStateFlow()

    val display: StateFlow<DisplayState> =
        combine(_ui, settings, entries, variables, activeWorkspace) { ui, s, list, vars, ws ->
            buildDisplay(ui, s, list, vars, ws)
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.Eagerly, DisplayState())

    val miniTape: StateFlow<List<MiniTapeLine>> = combine(_ui, entries) { ui, list ->
        val source = if (ui.committed != null) list.dropLast(1) else list
        source.takeLast(2).map { MiniTapeLine(it.expression, it.displayResult) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        viewModelScope.launch { repo.ensureDefaults() }
    }

    // ------------------------------------------------------------------ display

    private fun variableMap(items: List<VariableItem>): Map<String, CalcValue> {
        val out = LinkedHashMap<String, CalcValue>()
        for (v in items) CalcValue.deserialize(v.valueText)?.let { out[v.name] = it }
        return out
    }

    private fun group(text: String, on: Boolean) = if (on) NumberFormatter.group(text) else text

    private fun buildDisplay(
        ui: CalcUiState,
        s: AppSettings,
        list: List<HistoryEntry>,
        vars: List<VariableItem>,
        ws: Workspace?
    ): DisplayState {
        val angle = AngleMode.fromName(s.angleMode)
        val base = DisplayState(angleLabel = angle.label, workspaceName = ws?.name ?: "")
        val committed = ui.committed
        if (committed != null) {
            val r = committed.result
            val showFraction = (ui.fractionOverride ?: s.fractionByDefault) && r.fraction != null
            val unitSuffix = if (r.unit != null) " ${r.unit}" else ""
            val main: String
            val secondary: String?
            if (showFraction) {
                main = r.fraction.orEmpty() + unitSuffix
                secondary = "≈ ${group(r.display, s.groupDigits)}$unitSuffix"
            } else {
                main = group(r.display, s.groupDigits) + unitSuffix
                secondary = r.fraction?.let { f ->
                    buildString {
                        append("= ").append(f)
                        if (r.mixed != null && r.mixed!!.contains(' ')) append(" (").append(r.mixed).append(')')
                        if (r.repeating != null) append("  =  ").append(r.repeating)
                    }
                }
            }
            return base.copy(
                expression = committed.expression,
                result = if (r.assignedName != null) "${r.assignedName} = $main" else main,
                resultExpression = committed.expression,
                secondaryResult = secondary,
                warning = r.warnings.firstOrNull(),
                canToggleFraction = r.fraction != null
            )
        }
        if (ui.error != null) return base.copy(expression = ui.expression, error = ui.error)
        val text = ui.expression
        if (text.isBlank()) return base
        val previewText = Calculator.previewText(text)
        if (previewText.isBlank() || Calculator.isPlainNumber(previewText)) return base.copy(expression = text)
        val ans = TapeRecalculator.ansBefore(list, list.size)
        val outcome = Calculator.evaluate(previewText, EvalContext(angle, variableMap(vars), ans))
        return when (outcome) {
            is EvalOutcome.Success -> {
                val r = outcome.result
                val unit = if (r.unit != null) " ${r.unit}" else ""
                base.copy(
                    expression = text,
                    preview = "= " + group(r.display, s.groupDigits) + unit,
                    warning = r.warnings.firstOrNull()
                )
            }
            is EvalOutcome.Failure -> base.copy(expression = text)
        }
    }

    // ------------------------------------------------------------------ keypad

    fun onKey(action: KeyAction) {
        when (action) {
            is KeyAction.Insert -> insertToken(action.text)
            KeyAction.Backspace -> _ui.update { s ->
                if (s.committed != null) s.copy(committed = null, fractionOverride = null, error = null)
                else s.copy(expression = InputEditor.backspace(s.expression), error = null)
            }
            KeyAction.ClearEntry, KeyAction.AllClear -> _ui.value = CalcUiState()
            KeyAction.Equals -> calculate()
            KeyAction.ToggleSign -> _ui.update { s ->
                s.copy(expression = InputEditor.toggleSign(s.expression), committed = null, fractionOverride = null, error = null)
            }
            KeyAction.Paste -> _events.trySend(UiEvent.RequestPaste)
            KeyAction.Ans -> insertToken("ans")
            KeyAction.SmartParen -> insertToken("(")
            KeyAction.CycleAngle -> updateSettings { it.copy(angleMode = AngleMode.fromName(it.angleMode).next().name) }
            KeyAction.ToggleFraction -> _ui.update { s ->
                val current = s.fractionOverride ?: settings.value.fractionByDefault
                s.copy(fractionOverride = !current)
            }
        }
    }

    private fun insertToken(token: String) {
        _ui.update { s ->
            s.copy(
                expression = InputEditor.insert(s.expression, token, afterResult = s.committed != null),
                committed = null,
                fractionOverride = null,
                error = null
            )
        }
    }

    /** Replaces the expression with typed text (for example "12 km to mi"). */
    fun setExpression(text: String) {
        _ui.value = CalcUiState(expression = text.take(InputEditor.MAX_LENGTH))
    }

    fun submitText(text: String) {
        setExpression(text)
        calculate()
    }

    fun onPaste(clipboardText: String?) {
        val extracted = clipboardText?.let { SmartPaste.extract(it) }
        if (extracted == null) {
            _events.trySend(UiEvent.Message("Nothing to paste: no number or expression found on the clipboard."))
            return
        }
        _ui.update { s ->
            val after = s.committed != null
            val base = if (after) "" else s.expression
            s.copy(
                expression = if (base.isEmpty()) extracted.take(InputEditor.MAX_LENGTH) else InputEditor.insert(base, extracted, false),
                committed = null,
                fractionOverride = null,
                error = null
            )
        }
    }

    private fun calculate() {
        val s = _ui.value
        if (s.committed != null) return
        val text = Calculator.autoClose(s.expression).trim()
        if (text.isEmpty()) return
        val wid = activeWorkspace.value?.id ?: return
        val angle = AngleMode.fromName(settings.value.angleMode)
        viewModelScope.launch {
            when (val r = repo.submit(wid, text, angle)) {
                is SubmitResult.Added -> _ui.update {
                    CalcUiState(expression = text, committed = CommittedResult(text, r.result))
                }
                is SubmitResult.Failed -> _ui.update { it.copy(error = r.error.message) }
            }
        }
    }

    fun insertText(text: String) = insertToken(text)

    fun copyResult() {
        val c = _ui.value.committed
        val text = when {
            c != null -> c.result.full.let { full -> if (c.result.unit != null) "$full ${c.result.unit}" else full }
            else -> display.value.preview?.removePrefix("= ")
        }
        if (text.isNullOrBlank()) _events.trySend(UiEvent.Message("Nothing to copy yet"))
        else _events.trySend(UiEvent.CopyText(text, "Result copied"))
    }

    fun copyExpression() {
        val text = _ui.value.committed?.expression ?: _ui.value.expression
        if (text.isBlank()) _events.trySend(UiEvent.Message("Nothing to copy yet"))
        else _events.trySend(UiEvent.CopyText(text, "Expression copied"))
    }

    // ------------------------------------------------------------------ tape editing

    fun openEditor(entry: HistoryEntry) {
        _editingId.value = entry.id
    }

    fun closeEditor() {
        _editingId.value = null
    }

    fun previewEdit(entryId: Long, text: String): EditPreview {
        val list = entries.value
        val index = list.indexOfFirst { it.id == entryId }
        if (index < 0 || text.isBlank()) return EditPreview("", false)
        val ctx = EvalContext(list[index].angleMode, variableMap(variables.value), TapeRecalculator.ansBefore(list, index))
        return when (val outcome = Calculator.evaluate(Calculator.autoClose(text.trim()), ctx)) {
            is EvalOutcome.Success -> {
                val r = outcome.result
                EditPreview("= " + r.displayWithUnit, false)
            }
            is EvalOutcome.Failure -> EditPreview(outcome.error.message, true)
        }
    }

    fun saveEdit(entryId: Long, expression: String, label: String, note: String, tags: String) {
        val original = entries.value.firstOrNull { it.id == entryId } ?: return
        val cleaned = Calculator.autoClose(expression.trim())
        viewModelScope.launch {
            if (cleaned != original.expression) repo.editEntry(entryId, cleaned, settings.value.autoRecalc)
            repo.updateMeta(
                entryId,
                label.trim().ifEmpty { null },
                note.trim().ifEmpty { null },
                tags.split(',', ';').map { it.trim().removePrefix("#") }.filter { it.isNotEmpty() }
            )
            _editingId.value = null
            _events.send(UiEvent.Message("Saved"))
        }
    }

    fun togglePin(entryId: Long) {
        viewModelScope.launch { repo.togglePin(entryId) }
    }

    fun duplicate(entryId: Long) {
        viewModelScope.launch {
            repo.duplicate(entryId)
            _editingId.value = null
            _events.send(UiEvent.Message("Duplicated at the end of the tape"))
        }
    }

    fun deleteEntry(entryId: Long) {
        viewModelScope.launch {
            repo.delete(entryId)
            _editingId.value = null
        }
    }

    fun useResultOf(entry: HistoryEntry) {
        if (entry.isError) return
        _editingId.value = null
        insertToken(entry.resultText)
    }

    fun copyEntryText(text: String, message: String) {
        _events.trySend(UiEvent.CopyText(text, message))
    }

    fun recalculateStale() {
        val wid = activeWorkspace.value?.id ?: return
        viewModelScope.launch {
            repo.recalculateStale(wid)
            _events.send(UiEvent.Message("Tape recalculated"))
        }
    }

    fun clearTape() {
        val wid = activeWorkspace.value?.id ?: return
        viewModelScope.launch {
            repo.clearWorkspace(wid)
            _ui.value = CalcUiState()
        }
    }

    fun export(format: ExportFormat) {
        val ws = activeWorkspace.value ?: return
        viewModelScope.launch {
            val text = repo.export(ws.id, format)
            _events.send(UiEvent.ExportReady(text, format, ws.name))
        }
    }

    // ------------------------------------------------------------------ variables

    /** Returns an error message, or null when the variable was saved. */
    suspend fun addVariable(text: String): String? =
        when (val outcome = repo.setVariable(text, AngleMode.fromName(settings.value.angleMode))) {
            is EvalOutcome.Success -> null
            is EvalOutcome.Failure -> outcome.error.message
        }

    fun deleteVariable(name: String) {
        viewModelScope.launch { repo.deleteVariable(name) }
    }

    // ------------------------------------------------------------------ workspaces and settings

    fun selectWorkspace(id: Long) {
        _ui.value = CalcUiState()
        updateSettings { it.copy(workspaceId = id) }
    }

    fun addWorkspace(name: String) {
        viewModelScope.launch {
            val id = repo.addWorkspace(name)
            selectWorkspace(id)
        }
    }

    fun renameWorkspace(id: Long, name: String) {
        viewModelScope.launch { repo.renameWorkspace(id, name) }
    }

    fun deleteWorkspace(id: Long) {
        viewModelScope.launch {
            if (repo.deleteWorkspace(id)) {
                _ui.value = CalcUiState()
                updateSettings { it.copy(workspaceId = 0) }
            } else {
                _events.send(UiEvent.Message("You need at least one workspace"))
            }
        }
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { settingsRepo.update(transform) }
    }
}

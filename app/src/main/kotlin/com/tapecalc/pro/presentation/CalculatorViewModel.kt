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
import com.tapecalc.core.math.NumberNotation
import com.tapecalc.core.math.SmartPaste
import com.tapecalc.core.math.editor.MathEngineText
import com.tapecalc.core.precision.NumberFormatter
import com.tapecalc.core.ui.Dir
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

/**
 * A result that has just been calculated with "=". [expression] is the plain engine text that was calculated;
 * [shown] is the line as the person typed it (with fraction boxes and so on), used to draw and to re-open it.
 */
data class CommittedResult(val expression: String, val result: EvalResult, val shown: String = expression)

/** What the next letter key should do after STO or RCL. */
enum class Pending { STORE, RECALL }

data class CalcUiState(
    /** The input line, possibly with template markers (see core-math's Markup). */
    val expression: String = "",
    /** Cursor position in [expression]; -1 means "at the end". */
    val cursor: Int = -1,
    val committed: CommittedResult? = null,
    /** null = follow the "fractions by default" setting. */
    val fractionOverride: Boolean? = null,
    val error: String? = null,
    val shift: Boolean = false,
    val alpha: Boolean = false,
    val hyp: Boolean = false,
    val pending: Pending? = null,
    /** Index in the tape of the line being replayed with the up / down arrows. */
    val replayIndex: Int? = null
) {
    val editor: EditorState
        get() = EditorState(expression, if (cursor < 0 || cursor > expression.length) expression.length else cursor)
}

/** The state after an edit: the new line and cursor, with any shown result, error and replay cleared. */
private fun CalcUiState.withEditor(e: EditorState) = copy(
    expression = e.text, cursor = e.cursor, committed = null, fractionOverride = null, error = null, replayIndex = null
)

sealed interface UiEvent {
    data class Message(val text: String) : UiEvent
    data class CopyText(val text: String, val message: String) : UiEvent
    data class ExportReady(val text: String, val format: ExportFormat, val workspaceName: String) : UiEvent
}

@HiltViewModel
class CalculatorViewModel @Inject constructor(
    private val repo: TapeRepository,
    private val settingsRepo: SettingsRepository
) : ViewModel() {

    // Starts from the saved settings (read instantly), not from defaults, so the first frame already
    // has the user's theme and there is no flash of the default one.
    val settings: StateFlow<AppSettings> =
        settingsRepo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, settingsRepo.initialSettings())

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

    /** One paste per gesture: swallows a second paste arriving within a fraction of a second. */
    private val pasteGuard = ActionDebouncer(minGapMillis = 400)

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
        source.takeLast(4).map { MiniTapeLine(it.expression, it.displayResult) }
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
        val notation = NumberNotation.fromName(s.numberNotation)
        val base = DisplayState(
            angleLabel = angle.label,
            workspaceName = ws?.name ?: "",
            cursor = ui.editor.cursor,
            shift = ui.shift,
            alpha = ui.alpha,
            hyp = ui.hyp,
            pendingLabel = when (ui.pending) {
                Pending.STORE -> "STO: press a letter"
                Pending.RECALL -> "RCL: press a letter"
                null -> null
            }
        )
        val committed = ui.committed
        if (committed != null) {
            val r = committed.result
            val exact = r.exactForm
            val showFraction = (ui.fractionOverride ?: s.fractionByDefault) && exact != null
            val unitSuffix = if (r.unit != null) " ${r.unit}" else ""
            val decimal = group(r.formatted(notation, s.fixDigits), s.groupDigits)
            val main: String
            var secondary: String?
            if (showFraction) {
                main = exact.orEmpty() + unitSuffix
                secondary = "≈ $decimal$unitSuffix"
            } else {
                main = decimal + unitSuffix
                secondary = exact?.let { f ->
                    buildString {
                        append("= ").append(f)
                        if (r.mixed != null && r.mixed!!.contains(' ')) append(" (").append(r.mixed).append(')')
                        if (r.repeating != null) append("  =  ").append(r.repeating)
                    }
                }
            }
            // Extra outputs: the angle of Pol(), and degrees-minutes-seconds when the input used degrees.
            val more = ArrayList<String>()
            r.extras.forEach { (name, v) -> more.add("$name = ${group(NumberFormatter.format(v.decimal), s.groupDigits)}") }
            if (r.dms != null && (committed.expression.contains('°') || committed.expression.contains('\''))) more.add("= ${r.dms}")
            if (more.isNotEmpty()) secondary = (listOfNotNull(secondary) + more).joinToString("   ·   ")
            return base.copy(
                expression = committed.expression,
                result = if (r.assignedName != null) "${r.assignedName} = $main" else main,
                resultExpression = committed.shown,
                secondaryResult = secondary,
                warning = r.warnings.firstOrNull(),
                canToggleFraction = exact != null
            )
        }
        if (ui.error != null) return base.copy(expression = ui.expression, error = ui.error)
        val text = ui.expression
        if (text.isBlank()) return base
        val engine = MathEngineText.convert(text)
        // a template with an empty box cannot be calculated yet, so there is no live answer to show
        if (engine.hasBlank) return base.copy(expression = text)
        val previewText = Calculator.previewText(engine.text)
        if (previewText.isBlank() || Calculator.isPlainNumber(previewText)) return base.copy(expression = text)
        val ans = TapeRecalculator.ansBefore(list, list.size)
        val outcome = Calculator.evaluate(previewText, EvalContext(angle, variableMap(vars), ans))
        return when (outcome) {
            is EvalOutcome.Success -> {
                val r = outcome.result
                val unit = if (r.unit != null) " ${r.unit}" else ""
                base.copy(
                    expression = text,
                    preview = "= " + group(r.formatted(notation, s.fixDigits), s.groupDigits) + unit,
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
                val c = s.committed
                if (c != null) s.copy(expression = c.shown, cursor = -1, committed = null, fractionOverride = null, error = null)
                else s.withEditor(MathEditor.backspace(s.editor))
            }
            KeyAction.ClearEntry, KeyAction.AllClear -> _ui.value = CalcUiState()
            KeyAction.Equals -> calculate()
            KeyAction.ToggleSign -> _ui.update { s ->
                val c = s.committed
                if (c != null) s.withEditor(MathEditor.toggleSign(EditorState(c.shown)))
                else s.withEditor(MathEditor.toggleSign(s.editor))
            }
            KeyAction.Paste -> Unit // needs the clipboard, so the UI calls onPaste(text) directly
            KeyAction.Ans -> insertToken("ans")
            KeyAction.SmartParen -> insertToken("(")
            KeyAction.CycleAngle -> updateSettings { it.copy(angleMode = AngleMode.fromName(it.angleMode).next().name) }
            KeyAction.ToggleFraction -> _ui.update { s ->
                val current = s.fractionOverride ?: settings.value.fractionByDefault
                s.copy(fractionOverride = !current)
            }
            KeyAction.Shift -> _ui.update { it.copy(shift = !it.shift, alpha = false, pending = null) }
            KeyAction.Alpha -> _ui.update { it.copy(alpha = !it.alpha, shift = false) }
            KeyAction.Hyp -> _ui.update { it.copy(hyp = !it.hyp) }
            KeyAction.ClearModifiers -> clearModifiers()
            is KeyAction.Move -> move(action.direction)
            is KeyAction.Template -> _ui.update { s ->
                s.withEditor(MathEditor.insertTemplate(s.editor, action.open, afterResult = s.committed != null)).copy(pending = null)
            }
            KeyAction.Store -> _ui.update { it.copy(pending = Pending.STORE, alpha = true, shift = false) }
            KeyAction.Recall -> _ui.update { it.copy(pending = Pending.RECALL, alpha = true, shift = false) }
            KeyAction.MemoryPlus -> memory(+1)
            KeyAction.MemoryMinus -> memory(-1)
            KeyAction.CycleNotation -> updateSettings {
                val order = listOf(NumberNotation.AUTO, NumberNotation.SCIENTIFIC, NumberNotation.ENGINEERING)
                val at = order.indexOf(NumberNotation.fromName(it.numberNotation))
                it.copy(numberNotation = order[(at + 1) % order.size].name)
            }
            KeyAction.CopyResult -> copyResult()
            is KeyAction.LoadExpression -> _ui.value = CalcUiState(expression = action.text.take(InputEditor.MAX_LENGTH))
            // These need the screen (dialogs, tabs); the app root handles them before they get here.
            KeyAction.Calc, KeyAction.OpenMode, KeyAction.OpenTape, is KeyAction.OpenTool -> Unit
        }
        // SHIFT, ALPHA and HYP apply to the next key only (STO / RCL keep ALPHA on for the letter that follows).
        if (action !is KeyAction.Shift && action !is KeyAction.Alpha && action !is KeyAction.Hyp &&
            action !is KeyAction.Store && action !is KeyAction.Recall
        ) clearModifiers()
    }

    /** Switches SHIFT, ALPHA and HYP off. */
    fun clearModifiers() {
        _ui.update { if (it.shift || it.alpha || it.hyp) it.copy(shift = false, alpha = false, hyp = false) else it }
    }

    private fun insertToken(token: String) {
        val pending = _ui.value.pending
        if (pending != null) {
            _ui.update { it.copy(pending = null) }
            if (token.length == 1 && token[0].isLetter() && pending == Pending.STORE) {
                storeInVariable(token)
                return
            }
            // RCL: the variable's name goes into the line, and the calculator reads its value when it calculates
        }
        _ui.update { s ->
            s.withEditor(MathEditor.insert(s.editor, token, afterResult = s.committed != null))
        }
    }

    // ------------------------------------------------------------------ cursor movement and replay

    private fun move(direction: Dir) {
        val s = _ui.value
        // after "=", the arrows re-open the line that was just calculated so it can be edited
        val c = s.committed
        val base = if (c != null) s.copy(expression = c.shown, cursor = -1, committed = null, fractionOverride = null, error = null) else s
        val editor = base.editor
        val next: EditorState? = when (direction) {
            Dir.LEFT -> MathEditor.moveLeft(editor)
            Dir.RIGHT -> MathEditor.moveRight(editor)
            Dir.UP -> MathEditor.moveVertical(editor, up = true)
            Dir.DOWN -> MathEditor.moveVertical(editor, up = false)
        }
        if (next != null) {
            _ui.value = base.copy(expression = next.text, cursor = next.cursor, error = null)
            return
        }
        // up / down with no box to go to: browse the earlier lines of the tape (like a Casio's replay)
        replay(base, older = direction == Dir.UP)
    }

    private fun replay(s: CalcUiState, older: Boolean) {
        val list = entries.value
        if (list.isEmpty()) {
            _ui.value = s.copy(cursor = if (older) 0 else -1)
            return
        }
        val current = s.replayIndex
        if (older) {
            val target = if (current == null) list.lastIndex else (current - 1).coerceAtLeast(0)
            _ui.value = CalcUiState(expression = list[target].expression, replayIndex = target)
        } else if (current == null) {
            _ui.value = s.copy(cursor = -1)
        } else if (current + 1 > list.lastIndex) {
            _ui.value = CalcUiState()
        } else {
            _ui.value = CalcUiState(expression = list[current + 1].expression, replayIndex = current + 1)
        }
    }

    // ------------------------------------------------------------------ STO, M+ and M-

    /** The text to calculate for the current line (or the shown result), or null after telling the screen why not. */
    private fun currentEngineText(): String? {
        val s = _ui.value
        s.committed?.let { return it.expression }
        val engine = MathEditor.engineText(s.editor)
        val text = Calculator.autoClose(engine.text).trim()
        if (engine.hasBlank) {
            _events.trySend(UiEvent.Message("Fill in the empty boxes first"))
            return null
        }
        if (text.isEmpty()) {
            _events.trySend(UiEvent.Message("Type or calculate a value first"))
            return null
        }
        return text
    }

    private fun storeInVariable(name: String) {
        val text = currentEngineText() ?: return
        viewModelScope.launch {
            val problem = addVariable("$name = ($text)")
            _events.send(UiEvent.Message(problem ?: "Stored in $name"))
        }
    }

    private fun memory(sign: Int) {
        val text = currentEngineText() ?: return
        viewModelScope.launch {
            val exists = variables.value.any { it.name == "M" }
            val operator = if (sign > 0) "+" else "−"
            val assignment = when {
                exists -> "M = M $operator ($text)"
                sign > 0 -> "M = ($text)"
                else -> "M = −($text)"
            }
            val problem = addVariable(assignment)
            _events.send(UiEvent.Message(problem ?: if (sign > 0) "Added to M" else "Subtracted from M"))
        }
    }

    // ------------------------------------------------------------------ CALC (ask for the unknown letters)

    private val freeLetter = Regex("(?<![A-Za-z0-9_])([A-Za-z])(?![A-Za-z0-9_(])")
    private val bindingFunction = Regex("(integral|sum|prod|deriv|solve)\\(")

    /** Single letters in the current line that have no stored value yet (so CALC has to ask for them). */
    fun missingVariables(): List<String> {
        val s = _ui.value
        val text = s.committed?.expression ?: MathEditor.engineText(s.editor).text
        val bound = bindingFunction.containsMatchIn(text)
        val known = variables.value.map { it.name }.toSet()
        return freeLetter.findAll(text).map { it.groupValues[1] }
            .filter { it != "e" && it !in known && !(bound && it == "x") }
            .distinct().toList()
    }

    /** CALC: runs the current line with the given values for its unknown letters. */
    fun calculateWith(values: Map<String, String>) {
        val s = _ui.value
        val text = s.committed?.expression ?: Calculator.autoClose(MathEditor.engineText(s.editor).text).trim()
        if (text.isEmpty()) return
        val assignments = values.entries.joinToString(" : ") { (k, v) -> "$k = ${v.ifBlank { "0" }}" }
        submitText(if (assignments.isEmpty()) text else "$assignments : $text")
    }

    /** Replaces the expression with typed text (for example "12 km to mi"). */
    fun setExpression(text: String) {
        _ui.value = CalcUiState(expression = text.take(InputEditor.MAX_LENGTH))
    }

    /**
     * Writes a Tools-tab result on the tape of the open workspace. It is a record only: it never becomes `ans` and is
     * not recalculated. [title] names the tool, [input] is what was entered, [summary] the result on one line and
     * [full] the result with one value per line.
     */
    fun saveToolResult(title: String, input: String, summary: String, full: String) {
        val wid = activeWorkspace.value?.id ?: return
        viewModelScope.launch { repo.addToolEntry(wid, title, input, summary, full) }
    }

    fun submitText(text: String) {
        setExpression(text)
        calculate()
    }

    fun onPaste(clipboardText: String?) {
        if (!pasteGuard.tryAcquire()) return
        val extracted = clipboardText?.let { SmartPaste.extract(it) }
        if (extracted == null) {
            _events.trySend(UiEvent.Message("Nothing to paste: no number or expression found on the clipboard."))
            return
        }
        val s = _ui.value
        val before = if (s.committed != null) EditorState() else s.editor
        val next = if (before.text.isEmpty()) EditorState(extracted.take(InputEditor.MAX_LENGTH)) else MathEditor.insert(before, extracted, false)
        if (before.text.isNotEmpty() && next == before) {
            _events.trySend(UiEvent.Message("That would make the expression too long."))
            return
        }
        _ui.value = s.withEditor(next)
    }

    private fun calculate() {
        val s = _ui.value
        if (s.committed != null) return
        val engine = MathEditor.engineText(s.editor)
        if (engine.hasBlank) {
            _ui.update { it.copy(error = "Fill in the empty boxes first") }
            return
        }
        val text = Calculator.autoClose(engine.text).trim()
        if (text.isEmpty()) return
        val wid = activeWorkspace.value?.id ?: return
        val angle = AngleMode.fromName(settings.value.angleMode)
        viewModelScope.launch {
            when (val r = repo.submit(wid, text, angle)) {
                is SubmitResult.Added -> _ui.update {
                    CalcUiState(expression = text, committed = CommittedResult(text, r.result, shown = s.expression))
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
        val text = _ui.value.committed?.expression ?: MathEditor.engineText(_ui.value.editor).text
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

    fun saveRawVariable(name: String, valueText: String, displayText: String) {
        viewModelScope.launch { repo.saveRawVariable(name, valueText, displayText) }
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

    /** Called when the first-run tutorial is finished or skipped. */
    fun completeOnboarding() = updateSettings { it.copy(onboardingDone = true) }
}

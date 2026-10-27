package com.tapecalc.core.data.repo

import com.tapecalc.core.data.db.HistoryDao
import com.tapecalc.core.data.db.VariableDao
import com.tapecalc.core.data.db.VariableEntity
import com.tapecalc.core.data.db.WorkspaceDao
import com.tapecalc.core.data.db.WorkspaceEntity
import com.tapecalc.core.data.db.toDomain
import com.tapecalc.core.data.db.toEntity
import com.tapecalc.core.data.domain.EntryStatus
import com.tapecalc.core.data.domain.ExportFormat
import com.tapecalc.core.data.domain.HistoryEntry
import com.tapecalc.core.data.domain.HistoryExporter
import com.tapecalc.core.data.domain.SubmitResult
import com.tapecalc.core.data.domain.TapeRecalculator
import com.tapecalc.core.data.domain.VariableItem
import com.tapecalc.core.data.domain.Workspace
import com.tapecalc.core.math.AngleMode
import com.tapecalc.core.math.CalcError
import com.tapecalc.core.math.CalcValue
import com.tapecalc.core.math.Calculator
import com.tapecalc.core.math.ErrorKind
import com.tapecalc.core.math.EvalContext
import com.tapecalc.core.math.EvalOutcome
import com.tapecalc.core.precision.NumberFormatter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Single source of truth for workspaces, the calculation tape and variables.
 * Everything is stored locally through Room; there is no network code in this app.
 */
class TapeRepository(
    private val workspaces: WorkspaceDao,
    private val history: HistoryDao,
    private val variables: VariableDao,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {

    companion object {
        val DEFAULT_WORKSPACES = listOf("Personal", "Work", "Study", "Engineering")
    }

    // ------------------------------------------------------------ observation

    fun observeWorkspaces(): Flow<List<Workspace>> = workspaces.observeAll().map { list -> list.map { it.toDomain() } }

    fun observeEntries(workspaceId: Long): Flow<List<HistoryEntry>> =
        history.observe(workspaceId).map { list -> list.map { it.toDomain() } }

    fun observeVariables(): Flow<List<VariableItem>> = variables.observeAll().map { list -> list.map { it.toDomain() } }

    // ------------------------------------------------------------ workspaces

    suspend fun ensureDefaults(): List<Workspace> {
        if (workspaces.count() == 0) {
            DEFAULT_WORKSPACES.forEachIndexed { index, name -> workspaces.insert(WorkspaceEntity(name = name, sortOrder = index)) }
        }
        return workspaces.getAll().map { it.toDomain() }
    }

    suspend fun addWorkspace(name: String): Long {
        val clean = name.trim().ifEmpty { "Untitled" }.take(40)
        val next = (workspaces.getAll().maxOfOrNull { it.sortOrder } ?: -1) + 1
        return workspaces.insert(WorkspaceEntity(name = clean, sortOrder = next))
    }

    suspend fun renameWorkspace(id: Long, name: String) {
        val current = workspaces.getAll().firstOrNull { it.id == id } ?: return
        workspaces.update(current.copy(name = name.trim().ifEmpty { current.name }.take(40)))
    }

    /** The last remaining workspace cannot be deleted. Returns true if something was deleted. */
    suspend fun deleteWorkspace(id: Long): Boolean {
        if (workspaces.count() <= 1) return false
        history.clear(id)
        workspaces.delete(id)
        return true
    }

    // ------------------------------------------------------------ variables

    suspend fun loadVariables(): Map<String, CalcValue> {
        val out = LinkedHashMap<String, CalcValue>()
        for (v in variables.getAll()) CalcValue.deserialize(v.valueText)?.let { out[v.name] = it }
        return out
    }

    private suspend fun saveVariable(name: String, value: CalcValue) {
        variables.upsert(VariableEntity(name, value.serialize(), NumberFormatter.format(value.decimal), null, clock()))
    }

    /** Accepts text such as `rate = 0.18` and stores the variable. */
    suspend fun setVariable(text: String, angleMode: AngleMode = AngleMode.DEG): EvalOutcome {
        val ctx = EvalContext(angleMode, loadVariables(), null)
        val outcome = Calculator.evaluate(text, ctx)
        if (outcome is EvalOutcome.Success) {
            val name = outcome.result.assignedName
                ?: return EvalOutcome.Failure(CalcError(ErrorKind.SYNTAX, "Use the form name = value, for example rate = 0.18"))
            saveVariable(name, outcome.result.value)
        }
        return outcome
    }

    suspend fun deleteVariable(name: String) = variables.delete(name)

    // ------------------------------------------------------------ the tape

    /** Evaluates [expression] and, on success, appends it to the tape of [workspaceId]. */
    suspend fun submit(workspaceId: Long, expression: String, angleMode: AngleMode): SubmitResult {
        val entries = history.getAll(workspaceId).map { it.toDomain() }
        val ans = TapeRecalculator.ansBefore(entries, entries.size)
        val ctx = EvalContext(angleMode, loadVariables(), ans)
        return when (val outcome = Calculator.evaluate(expression, ctx)) {
            is EvalOutcome.Failure -> SubmitResult.Failed(outcome.error)
            is EvalOutcome.Success -> {
                val r = outcome.result
                val now = clock()
                val draft = HistoryEntry(
                    workspaceId = workspaceId,
                    expression = expression.trim(),
                    resultText = r.display,
                    resultFull = r.full,
                    resultValue = r.value.serialize(),
                    fraction = r.fraction,
                    unit = r.unit,
                    angleMode = angleMode,
                    createdAt = now,
                    updatedAt = now
                )
                val id = history.insert(draft.toEntity())
                r.assignedName?.let { saveVariable(it, r.value) }
                SubmitResult.Added(draft.copy(id = id), r)
            }
        }
    }

    /**
     * Edits an entry and updates (autoRecalc) or flags (otherwise) later entries that depend on it.
     * Returns false if the entry does not exist.
     */
    suspend fun editEntry(entryId: Long, expression: String, autoRecalc: Boolean): Boolean {
        val target = history.get(entryId)?.toDomain() ?: return false
        val entries = history.getAll(target.workspaceId).map { it.toDomain() }
        val outcome = TapeRecalculator.edit(entries, entryId, expression, loadVariables(), autoRecalc, clock())
        persist(entries, outcome)
        return true
    }

    suspend fun recalculateStale(workspaceId: Long) {
        val entries = history.getAll(workspaceId).map { it.toDomain() }
        val outcome = TapeRecalculator.recalculateStale(entries, loadVariables(), clock())
        persist(entries, outcome)
    }

    private suspend fun persist(before: List<HistoryEntry>, outcome: TapeRecalculator.Outcome) {
        val changed = outcome.entries.filter { it != before.firstOrNull { b -> b.id == it.id } }
        if (changed.isNotEmpty()) history.updateAll(changed.map { it.toEntity() })
        for ((name, serialized) in outcome.changedVariables) {
            CalcValue.deserialize(serialized)?.let { saveVariable(name, it) }
        }
    }

    suspend fun updateMeta(entryId: Long, label: String?, note: String?, tags: List<String>) {
        val e = history.get(entryId)?.toDomain() ?: return
        history.update(e.copy(label = label?.trim(), note = note?.trim(), tags = tags, updatedAt = clock()).toEntity())
    }

    suspend fun togglePin(entryId: Long) {
        val e = history.get(entryId)?.toDomain() ?: return
        history.update(e.copy(pinned = !e.pinned, updatedAt = clock()).toEntity())
    }

    /** Appends a copy at the end of the tape (so `ans` still means "previous entry"). */
    suspend fun duplicate(entryId: Long): Long? {
        val e = history.get(entryId)?.toDomain() ?: return null
        val now = clock()
        return history.insert(e.copy(id = 0, pinned = false, status = EntryStatus.OK, createdAt = now, updatedAt = now).toEntity())
    }

    suspend fun delete(entryId: Long) = history.delete(entryId)

    suspend fun clearWorkspace(workspaceId: Long) = history.clear(workspaceId)

    suspend fun export(workspaceId: Long, format: ExportFormat): String {
        val name = workspaces.getAll().firstOrNull { it.id == workspaceId }?.name ?: "Tape"
        val entries = history.getAll(workspaceId).map { it.toDomain() }
        return HistoryExporter.export(format, name, entries)
    }
}

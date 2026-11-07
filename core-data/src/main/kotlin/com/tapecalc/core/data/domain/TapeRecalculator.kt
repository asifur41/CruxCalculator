package com.tapecalc.core.data.domain

import com.tapecalc.core.math.CalcValue
import com.tapecalc.core.math.Calculator
import com.tapecalc.core.math.EvalContext
import com.tapecalc.core.math.EvalOutcome

/**
 * Pure logic for the editable tape: re-evaluates an edited entry and decides which later entries
 * (those using `ans` or an edited variable) become UPDATED (recalculated) or STALE (not yet recalculated).
 */
object TapeRecalculator {

    /** [entries] in chronological order, plus the variables that changed (name -> serialized value). */
    data class Outcome(val entries: List<HistoryEntry>, val changedVariables: Map<String, String>)

    fun ansBefore(entries: List<HistoryEntry>, index: Int): CalcValue? {
        val i = ansIndex(entries, index)
        return if (i >= 0) CalcValue.deserialize(entries[i].resultValue) else null
    }

    /** Position of the entry that `ans` refers to for the entry at [index]: the nearest earlier one that has a result. */
    private fun ansIndex(entries: List<HistoryEntry>, index: Int): Int {
        for (i in index - 1 downTo 0) {
            val e = entries[i]
            if (!e.isError && !e.isTool && e.resultValue.isNotEmpty()) return i
        }
        return -1
    }

    /** Evaluates [expression] and returns a copy of [base] carrying the new result (or error). */
    fun evaluated(
        base: HistoryEntry,
        expression: String,
        ans: CalcValue?,
        variables: Map<String, CalcValue>,
        status: EntryStatus,
        now: Long
    ): Pair<HistoryEntry, Map<String, CalcValue>> {
        val ctx = EvalContext(base.angleMode, variables, ans)
        return when (val outcome = Calculator.evaluate(expression, ctx)) {
            is EvalOutcome.Success -> {
                val r = outcome.result
                base.copy(
                    expression = expression,
                    resultText = r.display,
                    resultFull = r.full,
                    resultValue = r.value.serialize(),
                    fraction = r.exactForm,
                    unit = r.unit,
                    isError = false,
                    errorMessage = null,
                    status = status,
                    updatedAt = now
                ) to assignedValues(r)
            }
            is EvalOutcome.Failure -> base.copy(
                expression = expression,
                resultText = "",
                resultFull = "",
                resultValue = "",
                fraction = null,
                unit = null,
                isError = true,
                errorMessage = outcome.error.message,
                status = status,
                updatedAt = now
            ) to emptyMap()
        }
    }

    /** Every variable the expression set: the main one, plus the rest of a colon chain or Pol/Rec. */
    private fun assignedValues(r: com.tapecalc.core.math.EvalResult): Map<String, CalcValue> {
        val out = LinkedHashMap<String, CalcValue>(r.assignments)
        r.assignedName?.let { if (it !in out) out[it] = r.value }
        return out
    }

    private fun resultChanged(before: HistoryEntry, after: HistoryEntry): Boolean =
        before.resultValue != after.resultValue || before.isError != after.isError || before.errorMessage != after.errorMessage

    /**
     * Edits the entry with [entryId]. When [autoRecalc] is true dependent entries are recomputed
     * (status UPDATED); otherwise they are only marked STALE.
     */
    fun edit(
        entries: List<HistoryEntry>,
        entryId: Long,
        newExpression: String,
        variables: Map<String, CalcValue>,
        autoRecalc: Boolean,
        now: Long
    ): Outcome {
        val index = entries.indexOfFirst { it.id == entryId }
        if (index < 0) return Outcome(entries, emptyMap())
        val vars = variables.toMutableMap()
        val changedVars = linkedMapOf<String, String>()
        val result = entries.toMutableList()
        val dirtyNames = mutableSetOf<String>()
        // Positions of entries whose result (the value `ans` hands on) changed or is now unreliable.
        val dirtyEntries = mutableSetOf<Int>()

        val old = entries[index]
        val expression = newExpression.trim()
        val (updated, assignedValues) = evaluated(old, expression, ansBefore(result, index), vars, EntryStatus.OK, now)
        result[index] = updated
        val editChanged = resultChanged(old, updated) || old.expression != expression
        if (editChanged) dirtyEntries += index
        for ((name, value) in assignedValues) {
            vars[name] = value
            changedVars[name] = value.serialize()
            if (editChanged) dirtyNames += name
        }

        for (j in index + 1 until result.size) {
            val entry = result[j]
            if (entry.isTool) continue
            val analysis = Calculator.analyze(entry.expression)
            // `ans` for this entry is the nearest earlier entry WITH a result. Entries that are errors do not
            // produce one, so they must not reset the flag (an error between the edit and an `ans` user used to).
            val ansOld = ansIndex(entries, j)
            val ansNew = ansIndex(result, j)
            val ansDirty = ansOld != ansNew || ansNew in dirtyEntries
            val dependent = (analysis.usesAns && ansDirty) || analysis.referencedNames.any { it in dirtyNames }
            if (!dependent) continue
            if (autoRecalc) {
                val (recomputed, values) = evaluated(entry, entry.expression, ansBefore(result, j), vars, EntryStatus.UPDATED, now)
                result[j] = recomputed
                val changed = resultChanged(entry, recomputed)
                if (changed) dirtyEntries += j
                for ((name, value) in values) {
                    vars[name] = value
                    changedVars[name] = value.serialize()
                    if (changed) dirtyNames += name
                }
            } else {
                result[j] = entry.copy(status = EntryStatus.STALE, updatedAt = now)
                dirtyEntries += j
                dirtyNames += analysis.assignedNames
            }
        }
        return Outcome(result, changedVars)
    }

    /** Recomputes every STALE entry in order. */
    fun recalculateStale(
        entries: List<HistoryEntry>,
        variables: Map<String, CalcValue>,
        now: Long
    ): Outcome {
        val vars = variables.toMutableMap()
        val changedVars = linkedMapOf<String, String>()
        val result = entries.toMutableList()
        for (j in result.indices) {
            val entry = result[j]
            if (entry.isTool || entry.status != EntryStatus.STALE) continue
            val (recomputed, values) = evaluated(entry, entry.expression, ansBefore(result, j), vars, EntryStatus.UPDATED, now)
            result[j] = recomputed
            for ((name, value) in values) {
                vars[name] = value
                changedVars[name] = value.serialize()
            }
        }
        return Outcome(result, changedVars)
    }
}

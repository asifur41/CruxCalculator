package com.tapecalc.core.data

import com.tapecalc.core.data.domain.EntryStatus
import com.tapecalc.core.data.domain.HistoryEntry
import com.tapecalc.core.data.domain.TapeRecalculator
import com.tapecalc.core.math.Calculator
import com.tapecalc.core.math.EvalContext
import com.tapecalc.core.math.EvalOutcome
import org.junit.Assert.assertEquals
import org.junit.Test

/** Editing a tape line must update, or flag, every later line that depends on it through `ans`. */
class TapeRecalculatorTest {

    /** Builds a tape the way the app does: each line is evaluated with `ans` = the last line that has a result. */
    private fun tape(vararg expressions: String): List<HistoryEntry> {
        val out = ArrayList<HistoryEntry>()
        expressions.forEachIndexed { i, expr ->
            val ans = TapeRecalculator.ansBefore(out, out.size)
            val base = HistoryEntry(id = (i + 1).toLong(), workspaceId = 1, expression = expr)
            val (entry, _) = TapeRecalculator.evaluated(base, expr, ans, emptyMap(), EntryStatus.OK, 0)
            out += entry
        }
        return out
    }

    private fun List<HistoryEntry>.shown() = map { it.displayResult }

    @Test
    fun errorLineBetweenTheEditAndAnsUserDoesNotHideTheDependency() {
        // line 3 is an error, so line 4's `ans` is line 2. Editing line 1 changes line 2, and so line 4.
        val before = tape("10", "ans*2", "1/0", "ans+1")
        assertEquals(listOf("10", "20", "Division by zero", "21"), before.shown())

        val after = TapeRecalculator.edit(before, 1, "100", emptyMap(), autoRecalc = true, now = 1).entries
        assertEquals(listOf("100", "200", "Division by zero", "201"), after.shown())
        assertEquals(EntryStatus.UPDATED, after[3].status)
    }

    @Test
    fun withAutoRecalcOffTheLineBehindAnErrorIsMarkedStale() {
        val before = tape("10", "ans*2", "1/0", "ans+1")
        val after = TapeRecalculator.edit(before, 1, "100", emptyMap(), autoRecalc = false, now = 1).entries
        assertEquals(EntryStatus.STALE, after[1].status)
        assertEquals(EntryStatus.OK, after[2].status)      // the error line does not use ans
        assertEquals(EntryStatus.STALE, after[3].status)   // but the line after it still depends on line 2

        val fixed = TapeRecalculator.recalculateStale(after, emptyMap(), now = 2).entries
        assertEquals(listOf("100", "200", "Division by zero", "201"), fixed.shown())
    }

    @Test
    fun aLineWithItsOwnResultStillStopsTheChain() {
        // line 3 has a result of its own, so line 4 uses line 3 and is NOT affected by the edit
        val before = tape("10", "ans*2", "5+5", "ans+1")
        val after = TapeRecalculator.edit(before, 1, "100", emptyMap(), autoRecalc = true, now = 1).entries
        assertEquals(listOf("100", "200", "10", "11"), after.shown())
        assertEquals(EntryStatus.OK, after[3].status)
    }

    @Test
    fun editingALineIntoAnErrorRedirectsLaterAnsToTheEarlierLine() {
        // line 2 becomes an error, so line 3's `ans` is now line 1 (10), not line 2 (20)
        val before = tape("10", "ans*2", "ans+1")
        assertEquals(listOf("10", "20", "21"), before.shown())
        val after = TapeRecalculator.edit(before, 2, "1/0", emptyMap(), autoRecalc = true, now = 1).entries
        assertEquals(listOf("10", "Division by zero", "11"), after.shown())
        assertEquals(EntryStatus.UPDATED, after[2].status)
    }

    @Test
    fun variablesStillPropagate() {
        val before = tape("rate = 2", "10 * rate")
        val out = TapeRecalculator.edit(before, 1, "rate = 3", mapOf("rate" to evalValue("2")), autoRecalc = true, now = 1)
        assertEquals(listOf("3", "30"), out.entries.shown())
        assertEquals(EntryStatus.UPDATED, out.entries[1].status)
    }

    private fun evalValue(expr: String) =
        ((Calculator.evaluate(expr, EvalContext()) as EvalOutcome.Success).result.value)
}

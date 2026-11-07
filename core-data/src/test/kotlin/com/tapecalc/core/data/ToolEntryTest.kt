package com.tapecalc.core.data

import com.tapecalc.core.data.domain.EntryStatus
import com.tapecalc.core.data.domain.ExportFormat
import com.tapecalc.core.data.domain.HistoryEntry
import com.tapecalc.core.data.domain.HistoryExporter
import com.tapecalc.core.data.domain.TOOL_RESULT_MARKER
import com.tapecalc.core.data.domain.TapeRecalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A result saved from the Tools tab is a record on the tape: it must never disturb `ans` or the recalculation. */
class ToolEntryTest {

    private fun calc(id: Long, expr: String, ans: com.tapecalc.core.math.CalcValue?): HistoryEntry {
        val base = HistoryEntry(id = id, workspaceId = 1, expression = expr)
        return TapeRecalculator.evaluated(base, expr, ans, emptyMap(), EntryStatus.OK, 0).first
    }

    private fun tool(id: Long) = HistoryEntry(
        id = id, workspaceId = 1,
        expression = "Polynomial roots: 1, -3, 2",
        resultText = "x₁ = 1; x₂ = 2",
        resultFull = "x₁ = 1\nx₂ = 2",
        resultValue = TOOL_RESULT_MARKER,
        label = "Polynomial roots"
    )

    @Test fun markerIdentifiesToolEntries() {
        assertTrue(tool(1).isTool)
        assertFalse(calc(1, "1+1", null).isTool)
    }

    @Test fun ansSkipsToolEntries() {
        val first = calc(1, "10", null)
        val tape = listOf(first, tool(2))
        val ans = TapeRecalculator.ansBefore(tape, tape.size)
        assertEquals("10", ans!!.decimal.toPlainString())
    }

    @Test fun editingAnEarlierLineLeavesToolEntryAlone() {
        val first = calc(1, "10", null)
        val tool = tool(2)
        val third = calc(3, "ans*2", TapeRecalculator.ansBefore(listOf(first, tool), 2))
        assertEquals("20", third.displayResult)
        val out = TapeRecalculator.edit(listOf(first, tool, third), 1, "100", emptyMap(), autoRecalc = true, now = 5).entries
        assertEquals(tool, out[1])
        assertEquals("200", out[2].displayResult)
        assertEquals(EntryStatus.UPDATED, out[2].status)
    }

    @Test fun staleRecalculationSkipsToolEntries() {
        val staleTool = tool(1).copy(status = EntryStatus.STALE)
        val out = TapeRecalculator.recalculateStale(listOf(staleTool), emptyMap(), 1).entries
        assertEquals(staleTool, out[0])
        assertFalse(out[0].isError)
    }

    @Test fun exportsReadNaturally() {
        val entries = listOf(calc(1, "1+1", null), tool(2))
        val text = HistoryExporter.export(ExportFormat.PLAIN_TEXT, "Personal", entries)
        assertTrue(text, text.contains("1+1 = 2"))
        assertTrue(text, text.contains("Polynomial roots: 1, -3, 2 -> x₁ = 1; x₂ = 2"))
        val csv = HistoryExporter.export(ExportFormat.CSV, "Personal", entries)
        assertTrue(csv, csv.contains("x₁ = 1; x₂ = 2"))
    }
}

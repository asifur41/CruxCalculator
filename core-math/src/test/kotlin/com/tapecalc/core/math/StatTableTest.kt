package com.tapecalc.core.math

import com.tapecalc.core.math.stats.StatColumn
import com.tapecalc.core.math.stats.StatRow
import com.tapecalc.core.math.stats.StatTableEngine
import com.tapecalc.core.math.stats.StatTableState
import com.tapecalc.core.math.tools.ToolResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StatTableTest {
    private val ctx = EvalContext()

    private fun fill(state: StatTableState, vararg rows: List<String>): StatTableState {
        var s = state
        rows.forEachIndexed { i, r ->
            r.forEachIndexed { c, text -> s = s.withCell(i, StatColumn.entries[c], text) }
        }
        return s
    }

    private fun lines(r: ToolResult): Map<String, String> {
        assertTrue("expected OK but was ${(r as? ToolResult.Err)?.message}", r is ToolResult.Ok)
        return (r as ToolResult.Ok).lines.associate { it.label to it.value }
    }

    @Test fun oneVariableTable() {
        val s = fill(StatTableState(), listOf("3"), listOf("5"), listOf("5"), listOf("8"), listOf("10"))
        val l = lines(StatTableEngine.run(s, ctx))
        assertEquals("5", l["n"])
        assertEquals("6.2", l["Mean x̄"])
        assertEquals("5", l["Median"])
    }

    @Test fun oneVariableTableWithFrequencies() {
        // 2 ×3, 4 ×1 is the data 2, 2, 2, 4
        val s = fill(StatTableState(useFreq = true), listOf("2", "", "3"), listOf("4", "", ""))
        val l = lines(StatTableEngine.run(s, ctx))
        assertEquals("4", l["n"])
        assertEquals("2.5", l["Mean x̄"])
        assertEquals("2", l["Mode"])
    }

    @Test fun twoVariableTableFitsALine() {
        val s = fill(StatTableState(twoVar = true), listOf("1", "2"), listOf("2", "4"), listOf("3", "6"))
        val l = lines(StatTableEngine.run(s, ctx))
        assertEquals("3", l["n"])
        assertEquals("1", l["r"])
        assertTrue(l["Equation"]!!.startsWith("y = 0 + 2x") || l["Equation"] == "y = 2x" || l["Equation"]!!.contains("2x"))
        assertEquals("28", l["Σxy"])
    }

    @Test fun twoVariableTablePredicts() {
        val s = fill(StatTableState(twoVar = true), listOf("1", "2"), listOf("2", "4"), listOf("3", "6"))
            .withPredictY("10").withEstimateX("8")
        val l = lines(StatTableEngine.run(s, ctx))
        assertEquals("20", l["ŷ at x = 10"])
        assertEquals("4", l["x̂ at y = 8"])
    }

    @Test fun aCurveThatCannotBeFittedKeepsTheStatistics() {
        // a log fit needs x > 0
        val s = fill(StatTableState(twoVar = true, regression = 2), listOf("-1", "2"), listOf("2", "4"), listOf("3", "5"))
        val l = lines(StatTableEngine.run(s, ctx))
        assertTrue(l.containsKey("Σxy"))
        assertTrue(l.containsKey("Fit"))
    }

    @Test fun cellsMayBeExpressions() {
        val s = fill(StatTableState(), listOf("1/2"), listOf("sqrt(4)"), listOf("3.5e0"))
        assertEquals("3", lines(StatTableEngine.run(s, ctx))["n"])
    }

    @Test fun problemsNameTheRow() {
        val noY = fill(StatTableState(twoVar = true), listOf("1", "2"), listOf("2", ""))
        assertEquals("Row 2: enter y", (StatTableEngine.run(noY, ctx) as ToolResult.Err).message)
        val noX = fill(StatTableState(twoVar = true), listOf("1", "2"), listOf("", "5"))
        assertEquals("Row 2: enter x", (StatTableEngine.run(noX, ctx) as ToolResult.Err).message)
        val bad = fill(StatTableState(), listOf("1"), listOf("2 +"))
        assertTrue((StatTableEngine.run(bad, ctx) as ToolResult.Err).message.startsWith("Row 2, x:"))
        val zeroFreq = fill(StatTableState(useFreq = true), listOf("1", "", "0"))
        assertTrue((StatTableEngine.run(zeroFreq, ctx) as ToolResult.Err).message.startsWith("Row 1:"))
        assertTrue(StatTableEngine.run(StatTableState(), ctx) is ToolResult.Err)
    }

    @Test fun hiddenColumnsAreIgnored() {
        // typed a Y earlier, then switched to the 1-variable table: the old Y must not matter
        val s = fill(StatTableState(twoVar = true), listOf("1", "2"), listOf("2", "4")).withMode(false)
        assertEquals("2", lines(StatTableEngine.run(s, ctx))["n"])
    }

    @Test fun blankRowsInTheMiddleAreSkipped() {
        val s = fill(StatTableState(), listOf("1"), listOf(""), listOf("3"))
        assertEquals("2", lines(StatTableEngine.run(s, ctx))["n"])
    }

    @Test fun lastRowIsAlwaysEmptyUpToTheLimit() {
        var s = StatTableState(rows = listOf(StatRow()))
        s = s.withCell(0, StatColumn.X, "1")
        assertEquals(2, s.rows.size)
        assertTrue(s.rows.last().x.isEmpty())
        for (i in 1 until 200) s = s.withCell(s.rows.lastIndex, StatColumn.X, "$i")
        assertEquals(StatTableState.MAX_ROWS, s.rows.size)
    }

    @Test fun removingRowsKeepsOneToTypeIn() {
        var s = fill(StatTableState(), listOf("1"), listOf("2"))
        s = s.withRowRemoved(0)
        assertEquals("2", s.rows[0].x)
        var one = StatTableState(rows = listOf(StatRow("5")))
        one = one.withRowRemoved(0)
        assertEquals(1, one.rows.size)
        assertTrue(one.rows[0].x.isEmpty())
        assertEquals(one, one.withRowRemoved(9))
    }

    @Test fun addingRowsStopsAtTheLimit() {
        var s = StatTableState()
        repeat(200) { s = s.withRowAdded() }
        assertEquals(StatTableState.MAX_ROWS, s.rows.size)
    }

    @Test fun clearedKeepsTheMode() {
        val s = fill(StatTableState(twoVar = true, useFreq = true), listOf("1", "2", "3")).cleared()
        assertTrue(s.twoVar && s.useFreq)
        assertEquals(0, s.filledRows)
    }

    @Test fun savingAndLoadingRoundTrips() {
        val s = fill(StatTableState(twoVar = true, useFreq = true, regression = 3), listOf("1", "2", "3"), listOf("4", "5", ""))
            .withPredictY("7").withEstimateX("9")
        val back = StatTableState.decode(s.encode())
        assertEquals(s, back)
    }

    @Test fun damagedSavedTextGivesAnEmptyTable() {
        assertEquals(StatTableState(), StatTableState.decode(null))
        assertEquals(StatTableState(), StatTableState.decode(""))
        assertEquals(StatTableState(), StatTableState.decode("junk"))
        assertEquals(StatTableState(), StatTableState.decode("v2\t1\t1\t0\t\t"))
        val odd = StatTableState.decode("v1\t1\t0\t99\t\t\n1\t2")
        assertEquals(6, odd.regression)
        assertEquals("1", odd.rows[0].x)
    }

    @Test fun cellTextIsKeptOnOneLine() {
        val s = StatTableState().withCell(0, StatColumn.X, "1\t2\n3")
        assertEquals("1 2 3", s.rows[0].x)
        assertEquals(s, StatTableState.decode(s.encode()))
        assertNotEquals(StatTableState(), s)
    }

    @Test fun tapeTextListsTheData() {
        val s = fill(StatTableState(twoVar = true, useFreq = true), listOf("1", "2", "3"), listOf("2", "4", ""))
        assertEquals("Statistics table: [2-Var] [FREQ] [Linear] (1, 2)×3 (2, 4)×1", StatTableEngine.inputText(s))
    }
}

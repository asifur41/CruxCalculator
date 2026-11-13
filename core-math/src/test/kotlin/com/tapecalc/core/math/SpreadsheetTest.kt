package com.tapecalc.core.math

import com.tapecalc.core.math.sheet.CellRange
import com.tapecalc.core.math.sheet.CellValue
import com.tapecalc.core.math.sheet.SheetEngine
import com.tapecalc.core.math.sheet.SheetRef
import com.tapecalc.core.math.sheet.SheetState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpreadsheetTest {
    private val ctx = EvalContext()

    private fun sheet(vararg cells: Pair<String, String>): SheetState {
        var s = SheetState()
        for ((ref, text) in cells) {
            val (c, r) = SheetRef.parse(ref)!!
            s = s.with(c, r, text)
        }
        return s
    }

    private fun show(s: SheetState, ref: String, angle: AngleMode = AngleMode.DEG): String {
        val (c, r) = SheetRef.parse(ref)!!
        return when (val v = SheetEngine.evaluate(s, EvalContext(angleMode = angle))[c, r]) {
            is CellValue.Num -> v.value.toString()
            is CellValue.Text -> "text:${v.text}"
            is CellValue.Error -> "${v.short}:${v.message}"
            CellValue.Empty -> "empty"
        }
    }

    @Test fun gridIsFiveByFortyFive() {
        assertEquals(5, SheetRef.COLS)
        assertEquals(45, SheetRef.ROWS)
        assertEquals("A1", SheetRef.name(0, 0))
        assertEquals("E45", SheetRef.name(4, 44))
        assertEquals(4 to 44, SheetRef.parse("e45"))
        assertNull(SheetRef.parse("F1"))
        assertNull(SheetRef.parse("A46"))
        assertNull(SheetRef.parse("A0"))
        assertNull(SheetRef.parse("A"))
        assertNull(SheetRef.parse("A1x"))
    }

    @Test fun plainCellsAreNumbersOrLabels() {
        val s = sheet("A1" to "12", "A2" to "1/4", "A3" to "Rent", "A4" to "3e2")
        assertEquals("12", show(s, "A1"))
        assertEquals("0.25", show(s, "A2"))
        assertEquals("text:Rent", show(s, "A3"))
        assertEquals("300", show(s, "A4"))
        assertEquals("empty", show(s, "B9"))
    }

    @Test fun formulasUseCellReferences() {
        val s = sheet("A1" to "2", "B1" to "3", "C1" to "=A1+B1", "D1" to "=c1*B1^2", "E1" to "=(a1+b1)/4")
        assertEquals("5", show(s, "C1"))
        assertEquals("45", show(s, "D1"))
        assertEquals("1.25", show(s, "E1"))
    }

    @Test fun formulasStayExact() {
        val s = sheet("A1" to "1", "B1" to "3", "C1" to "=A1/B1", "D1" to "=C1*3")
        assertEquals("1", show(s, "D1"))
    }

    @Test fun emptyCellsCountAsZeroInArithmetic() {
        assertEquals("7", show(sheet("A1" to "=B1+7"), "A1"))
    }

    @Test fun formulasCanReferToFormulas() {
        val s = sheet("A1" to "1", "A2" to "=A1+1", "A3" to "=A2*2", "A4" to "=A3+A2+A1")
        assertEquals("7", show(s, "A4"))
    }

    @Test fun sumAndMeanOverARange() {
        val s = sheet("A1" to "10", "A2" to "20", "A3" to "30", "A4" to "", "A5" to "label",
            "B1" to "=sum(A1:A5)", "B2" to "=mean(A1:A5)", "B3" to "=avg(A1:A3)", "B4" to "=count(A1:A5)",
            "B5" to "=min(A1:A5)", "C1" to "=max(A1:A5)")
        assertEquals("60", show(s, "B1"))
        assertEquals("20", show(s, "B2"))
        assertEquals("20", show(s, "B3"))
        assertEquals("3", show(s, "B4"))
        assertEquals("10", show(s, "B5"))
        assertEquals("30", show(s, "C1"))
    }

    @Test fun rangesWorkInTwoDimensionsAndMixedArguments() {
        val s = sheet("A1" to "1", "B1" to "2", "A2" to "3", "B2" to "4", "C1" to "=sum(A1:B2)", "C2" to "=mean(A1:B2, 10)",
            "C3" to "=SUM(A1, B2, 100)", "C4" to "=sum(A1:B2)/count(A1:B2)+1", "C5" to "=sum(B2:A1)")
        assertEquals("10", show(s, "C1"))
        assertEquals("4", show(s, "C2"))
        assertEquals("105", show(s, "C3"))
        assertEquals("3.5", show(s, "C4"))
        assertEquals("10", show(s, "C5"))
    }

    @Test fun meanKeepsExactFractions() {
        val s = sheet("A1" to "1", "A2" to "2", "A3" to "4", "B1" to "=mean(A1:A3)", "B2" to "=B1*3")
        assertEquals("7", show(s, "B2"))
    }

    @Test fun circularReferencesAreReported() {
        val s = sheet("A1" to "=B1", "B1" to "=A1", "C1" to "=C1+1", "D1" to "=sum(A1:A2)")
        assertTrue(show(s, "A1").startsWith("#CYCLE"))
        assertTrue(show(s, "B1").startsWith("#CYCLE"))
        assertTrue(show(s, "C1").startsWith("#CYCLE"))
        assertTrue(show(s, "D1").startsWith("#CYCLE"))
    }

    @Test fun errorsNameTheirCause() {
        assertTrue(show(sheet("A1" to "=1/0"), "A1").startsWith("#ERR"))
        assertTrue(show(sheet("A1" to "=1+"), "A1").startsWith("#ERR"))
        assertEquals("#ERR:A2 holds text, not a number", show(sheet("A1" to "=A2+1", "A2" to "hello"), "A1"))
        assertEquals("#ERR:There are no numbers to average", show(sheet("A1" to "=mean(B1:B3)"), "A1"))
        assertTrue(show(sheet("A1" to "=A2:A3"), "A1").startsWith("#ERR"))
        assertTrue(show(sheet("A1" to "="), "A1").startsWith("#ERR"))
        // an error in a cell that a formula uses is passed on, with its location
        val s = sheet("A1" to "=1/0", "B1" to "=A1+1", "B2" to "=sum(A1:A2)")
        assertTrue(show(s, "B1").startsWith("#ERR:A1:"))
        assertTrue(show(s, "B2").startsWith("#ERR:A1:"))
    }

    @Test fun rangeOutsideTheGridIsAnError() {
        // A1:F1 is not matched as a range, so the calculator reports it
        assertTrue(show(sheet("A1" to "=sum(A1:F1)"), "A1").startsWith("#"))
    }

    @Test fun angleUnitFollowsTheCalculator() {
        val s = sheet("A1" to "=sin(30)")
        assertEquals("0.5", show(s, "A1", AngleMode.DEG))
        assertEquals("-0.98803162409", show(s, "A1", AngleMode.RAD).take(14))
    }

    @Test fun selectionStats() {
        val s = sheet("A1" to "2", "A2" to "4", "A3" to "x", "A4" to "9")
        val v = SheetEngine.evaluate(s, ctx)
        val st = v.stats(CellRange(0, 0, 0, 3))
        assertEquals(3, st.count)
        assertEquals("15", st.sum.toString())
        assertEquals("5", st.mean.toString())
        assertEquals("2", st.min.toString())
        assertEquals("9", st.max.toString())
        assertNull(v.stats(CellRange(1, 0, 1, 3)).mean)
        val bad = SheetEngine.evaluate(sheet("A1" to "=1/0"), ctx).stats(CellRange(0, 0, 0, 1))
        assertTrue(bad.error!!.startsWith("A1:"))
    }

    @Test fun autoSumPlacement() {
        assertEquals(0 to 5, SheetEngine.autoSumTarget(CellRange(0, 0, 0, 4)))
        assertEquals(2 to 0, SheetEngine.autoSumTarget(CellRange(0, 0, 1, 0)))   // a row: to the right
        assertEquals(4 to 1, SheetEngine.autoSumTarget(CellRange(4, 0, 4, 0)))
        assertNull(SheetEngine.autoSumTarget(CellRange(0, 40, 0, 44)))
        // a row that already reaches column E has no room on the right: it goes below
        assertEquals(0 to 1, SheetEngine.autoSumTarget(CellRange(0, 0, 4, 0)))
        assertEquals("=sum(A1:A5)", SheetEngine.formula("sum", CellRange(0, 4, 0, 0)))
        assertEquals("=mean(B2)", SheetEngine.formula("mean", CellRange(1, 1, 1, 1)))
    }

    @Test fun editsAreImmutableAndTidy() {
        val a = SheetState()
        val b = a.with(0, 0, "5")
        assertEquals("", a.text(0, 0))
        assertEquals("5", b.text(0, 0))
        assertTrue(a === a.with(0, 0, ""))
        assertEquals("1 2", a.with(0, 0, "1\n2").text(0, 0))
        assertEquals(SheetState.MAX_CELL, a.with(0, 0, "x".repeat(500)).text(0, 0).length)
        assertTrue(a === a.with(9, 9, "x"))
        assertEquals("", b.withCleared(CellRange(0, 0, 0, 0)).text(0, 0))
        assertEquals(0, b.cleared().filledCells)
    }

    @Test fun savingAndLoadingRoundTrips() {
        val s = sheet("A1" to "5", "E45" to "=sum(A1:A44)", "C3" to "label with spaces")
        val back = SheetState.decode(s.encode())
        assertEquals(s, back)
        assertEquals(3, back.filledCells)
        assertEquals(SheetState(), SheetState.decode(null))
        assertEquals(SheetState(), SheetState.decode("garbage\nZ9\tnope\n\tno"))
    }

    @Test fun aFullGridOfFormulasIsFast() {
        var s = SheetState()
        for (r in 0 until 45) {
            s = s.with(0, r, "${r + 1}")
            if (r > 0) s = s.with(1, r, "=${SheetRef.name(1, r - 1)}+${SheetRef.name(0, r)}")
            s = s.with(2, r, "=sum(A1:A${r + 1})")
        }
        val t = System.nanoTime()
        val v = SheetEngine.evaluate(s, ctx)
        val ms = (System.nanoTime() - t) / 1_000_000
        assertTrue("took $ms ms", ms < 1500)
        assertEquals("1035", (v[2, 44] as CellValue.Num).value.toString())
    }
}

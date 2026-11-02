package com.tapecalc.core.math.stats

import com.tapecalc.core.math.CalcException
import com.tapecalc.core.math.CalcValue
import com.tapecalc.core.math.ErrorKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

internal fun v(s: String): CalcValue = CalcValue.parseLiteral(s)
internal fun vs(vararg s: String): List<CalcValue> = s.map { v(it) }
internal fun assertClose(expected: Double, actual: CalcValue, tol: Double = 1e-9) {
    val a = actual.decimal.toDouble()
    assertTrue("expected $expected but was $a", Math.abs(expected - a) <= tol * maxOf(1.0, Math.abs(expected)))
}

class StatsTest {

    @Test fun meanOfOneTwoFourIsExactlySevenThirds() {
        val s = Stats.oneVar(vs("1", "2", "4"))
        assertEquals("7/3", s.mean.exact.toString())
        assertEquals("3", s.n.toString())
        assertEquals("7", s.sum.toString())
        assertEquals("21", s.sumSq.toString())
    }

    @Test fun textbookDataSet() {
        val s = Stats.oneVar(vs("3", "5", "7", "7", "9", "10", "12", "15", "20"))
        assertClose(9.777777777777779, s.mean)
        assertClose(4.961580791503579, s.sdPop)
        assertClose(5.262551134615648, s.sdSample!!)
        assertEquals("9", s.median.toString())
        assertEquals("6", s.q1.toString())
        assertEquals("13.5", s.q3.toString())
        assertEquals(listOf("7"), s.modes.map { it.toString() })
        assertEquals("17", s.range.toString())
        assertEquals("3", s.min.toString())
        assertEquals("20", s.max.toString())
    }

    @Test fun evenCountMedianAndQuartiles() {
        val s = Stats.oneVar(vs("1", "2", "3", "4", "5", "6", "7", "8"))
        assertEquals("4.5", s.median.toString())
        assertEquals("2.5", s.q1.toString())
        assertEquals("6.5", s.q3.toString())
    }

    @Test fun unsortedInputIsSorted() {
        val s = Stats.oneVar(vs("9", "1", "5", "3", "7"))
        assertEquals("5", s.median.toString())
        assertEquals("2", s.q1.toString())
        assertEquals("8", s.q3.toString())
    }

    @Test fun noModeWhenNothingRepeats() {
        assertTrue(Stats.oneVar(vs("1", "2", "3")).modes.isEmpty())
    }

    @Test fun severalModes() {
        val s = Stats.oneVar(vs("1", "1", "2", "2", "3"))
        assertEquals(listOf("1", "2"), s.modes.map { it.toString() })
    }

    @Test fun singleValue() {
        val s = Stats.oneVar(vs("5"))
        assertEquals("5", s.mean.toString())
        assertEquals("0", s.sdPop.toString())
        assertNull(s.sdSample)
        assertEquals("5", s.median.toString())
        assertEquals("5", s.q1.toString())
    }

    @Test fun frequenciesBehaveLikeRepeatedValues() {
        val weighted = Stats.oneVar(vs("2", "4", "9"), vs("3", "1", "2"))
        val repeated = Stats.oneVar(vs("2", "2", "2", "4", "9", "9"))
        assertEquals(repeated.n.toString(), weighted.n.toString())
        assertEquals(repeated.mean.toString(), weighted.mean.toString())
        assertEquals(repeated.sdPop.toString(), weighted.sdPop.toString())
        assertEquals(repeated.sdSample.toString(), weighted.sdSample.toString())
        assertEquals(repeated.median.toString(), weighted.median.toString())
        assertEquals(repeated.q1.toString(), weighted.q1.toString())
        assertEquals(repeated.q3.toString(), weighted.q3.toString())
        assertEquals(repeated.modes.map { it.toString() }, weighted.modes.map { it.toString() })
    }

    @Test fun populationStandardDeviationIsExactWhenPerfectSquare() {
        val s = Stats.oneVar(vs("2", "4", "4", "4", "5", "5", "7", "9"))
        assertEquals("5", s.mean.toString())
        assertEquals("2", s.sdPop.toString())
    }

    @Test fun decimalsStayExact() {
        val s = Stats.oneVar(vs("0.1", "0.2", "0.3"))
        assertEquals("0.6", s.sum.toString())
        assertEquals("0.2", s.mean.toString())
    }

    @Test fun emptyDataIsAnError() {
        try { Stats.oneVar(emptyList()); fail() } catch (e: CalcException) { assertEquals(ErrorKind.EMPTY, e.error.kind) }
    }

    @Test fun frequencyMismatchAndNonPositive() {
        try { Stats.oneVar(vs("1", "2"), vs("1")); fail() } catch (e: CalcException) { assertTrue(e.error.message.contains("frequencies")) }
        try { Stats.oneVar(vs("1", "2"), vs("1", "0")); fail() } catch (e: CalcException) { assertTrue(e.error.message.contains("greater than 0")) }
    }

    @Test fun parseListHandlesSeparatorsAndExpressions() {
        val l = Stats.parseList("1, 2;3\n4/2, sqrt(9)\n5e1")
        assertEquals(listOf("1", "2", "3", "2", "3", "50"), l.map { it.toString() })
        assertEquals(listOf("4", "5", "6"), Stats.parseList("4 5   6").map { it.toString() })
    }

    @Test fun parseListReportsTheBadEntry() {
        try { Stats.parseList("1, 2, abc!"); fail() } catch (e: CalcException) {
            assertTrue(e.error.message, e.error.message.startsWith("Entry 3"))
        }
    }

    @Test fun twoVariableStatistics() {
        val xs = vs("1", "2", "3", "4", "5", "6", "7", "8")
        val ys = vs("2", "3", "5", "4", "7", "8", "8", "11")
        val t = Stats.twoVar(xs, ys)
        assertClose(0.9643959372630743, t.r!!)
        assertEquals("4.5", t.x.mean.toString())
        assertEquals("6", t.y.mean.toString())
        assertEquals("266", t.sumXY.toString())
        assertNotNull(t.covarianceSample)
    }

    @Test fun twoVariableLengthMismatch() {
        try { Stats.twoVar(vs("1", "2"), vs("1")); fail() } catch (e: CalcException) { assertTrue(e.error.message.contains("x values")) }
    }

    @Test fun constantXHasNoCorrelation() {
        assertNull(Stats.twoVar(vs("1", "1", "1"), vs("1", "2", "3")).r)
    }
}

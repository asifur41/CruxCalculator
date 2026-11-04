package com.tapecalc.core.math

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SolversTest {
    private fun v(s: String) = CalcValue.parseLiteral(s)
    private fun roots(vararg c: String) = Polynomial.solve(c.map { v(it) }).map { it.format() }
    private fun close(expected: Double, text: String, tol: Double = 1e-12) {
        val d = text.toDouble()
        assertTrue("expected $expected got $text", Math.abs(expected - d) <= tol * Math.max(1.0, Math.abs(expected)))
    }

    // ---------------------------------------------------------------- polynomials

    @Test fun linear() = assertEquals(listOf("2"), roots("3", "-6"))

    @Test fun quadraticRational() = assertEquals(listOf("2", "3"), roots("1", "-5", "6"))

    @Test fun quadraticFractionRoots() = assertEquals(listOf("0.5", "3"), roots("2", "-7", "3"))

    @Test fun quadraticIrrational() {
        val r = roots("1", "0", "-2")
        close(-1.41421356237309, r[0]); close(1.41421356237309, r[1])
    }

    @Test fun quadraticComplex() = assertEquals(listOf("-1 + 2i", "-1 - 2i"), roots("1", "2", "5"))

    @Test fun quadraticPureImaginary() = assertEquals(listOf("i", "-i"), roots("1", "0", "1"))

    @Test fun quadraticDoubleRoot() = assertEquals(listOf("1", "1"), roots("1", "-2", "1"))

    @Test fun cubicWithThreeRationalRoots() = assertEquals(listOf("1", "2", "3"), roots("1", "-6", "11", "-6"))

    @Test fun cubicWithZeroRoot() = assertEquals(listOf("-1", "0", "1"), roots("1", "0", "-1", "0"))

    @Test fun cubicWithOneRealRoot() {
        val r = roots("1", "0", "0", "-2")
        assertEquals(3, r.size)
        close(1.25992104989487, r[0])
        assertTrue(r[1].contains("i") && r[2].contains("i"))
        assertTrue(r[1].contains(" + ") && r[2].contains(" - "))
    }

    @Test fun cubicNeedingNumericRoots() {
        // x^3 - 3x + 1 has three real irrational roots.
        val r = roots("1", "0", "-3", "1")
        close(-1.87938524157182, r[0]); close(0.347296355333861, r[1]); close(1.53208888623796, r[2])
    }

    @Test fun cubicWithLeadingCoefficient() = assertEquals(listOf("-2", "0.5", "3"), roots("2", "-3", "-11", "6"))

    @Test fun quarticWithFourRationalRoots() = assertEquals(listOf("-2", "-1", "1", "2"), roots("1", "0", "-5", "0", "4"))

    @Test fun quarticWithRepeatedRoot() = assertEquals(listOf("1", "1", "1", "2"), roots("1", "-5", "9", "-7", "2"))

    @Test fun quarticComplexPairs() {
        val r = roots("1", "0", "0", "0", "1")   // x^4 = -1
        assertEquals(4, r.size)
        assertTrue(r.all { it.contains("i") })
        close(0.707106781186548, r[0].substringBefore(" ").trim().removePrefix("-"))
    }

    @Test fun quarticMixed() {
        // (x^2 - 2)(x^2 + 1)
        val r = roots("1", "0", "-1", "0", "-2")
        close(-1.4142135623731, r[0]); close(1.4142135623731, r[1])
        assertEquals(listOf("i", "-i"), r.drop(2))
    }

    @Test fun decimalCoefficients() {
        val r = roots("1", "-3.5", "3.5", "-1")   // (x-1)(x^2 - 2.5x + 1) -> 0.5, 1, 2
        assertEquals(listOf("0.5", "1", "2"), r)
    }

    @Test fun polynomialErrors() {
        fun msg(vararg c: String): String = try { Polynomial.solve(c.map { v(it) }); "none" } catch (e: CalcException) { e.error.message }
        assertTrue(msg("0", "0").contains("Every number"))
        assertTrue(msg("0", "0", "5").contains("No solution"))
        assertTrue(msg("1", "0", "0", "0", "0", "1").contains("degree 4"))
    }

    @Test fun leadingZerosAreIgnored() = assertEquals(listOf("2"), roots("0", "0", "1", "-2"))

    @Test fun coefficientsCanBeExpressions() {
        val c = Polynomial.parseCoefficients(listOf("1", "-1/2", "-1/2"))
        assertEquals(listOf("-0.5", "1"), Polynomial.solve(c).map { it.format() })
        try { Polynomial.parseCoefficients(listOf("1", "abc!")); fail() } catch (e: CalcException) {
            assertTrue(e.error.message.startsWith("Coefficient 2"))
        }
    }

    // ---------------------------------------------------------------- simultaneous equations

    private fun solveSystem(vararg rows: String): LinearSolution {
        val (a, b) = LinearSystem.parse(rows.map { it.split(" ") })
        return LinearSystem.solve(a, b)
    }

    @Test fun twoUnknowns() {
        val s = solveSystem("1 1 3", "1 -1 1") as LinearSolution.Unique
        assertEquals(listOf("2", "1"), s.values.map { it.toString() })
    }

    @Test fun threeUnknownsExactFractions() {
        val s = solveSystem("2 1 -1 8", "-3 -1 2 -11", "-2 1 2 -3") as LinearSolution.Unique
        assertEquals(listOf("2", "3", "-1"), s.values.map { it.toString() })
        val t = solveSystem("1 2 3 1", "2 3 4 2", "3 4 6 7") as LinearSolution.Unique
        assertTrue(t.values.all { it.exact != null })
    }

    @Test fun fourUnknowns() {
        val s = solveSystem("1 1 1 1 10", "1 -1 1 -1 -2", "1 2 3 4 30", "2 0 0 1 6") as LinearSolution.Unique
        assertEquals(listOf("1", "2", "3", "4"), s.values.map { it.toString() })
    }

    @Test fun fractionsInEquations() {
        val s = solveSystem("1/2 1/3 1", "1/4 -1/3 0") as LinearSolution.Unique
        assertEquals("4/3", s.values[0].exact.toString())
    }

    @Test fun inconsistentSystem() = assertTrue(solveSystem("1 1 2", "2 2 5") === LinearSolution.None)

    @Test fun dependentSystem() {
        val s = solveSystem("1 1 2", "2 2 4") as LinearSolution.Infinite
        assertEquals(1, s.rank); assertEquals(2, s.unknowns)
    }

    @Test fun badCellIsReported() {
        try { solveSystem("1 1 2", "1 x! 3"); fail() } catch (e: CalcException) {
            assertTrue(e.error.message, e.error.message.startsWith("Row 2, column 2"))
        }
    }

    @Test fun tooManyUnknowns() {
        try { solveSystem("1 1 1 1 1 5"); fail() } catch (e: CalcException) { assertTrue(e.error.message.contains("At most 4")) }
    }
}

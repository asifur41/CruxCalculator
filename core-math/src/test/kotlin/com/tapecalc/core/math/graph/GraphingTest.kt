package com.tapecalc.core.math.graph

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GraphingTest {
    private fun fn(t: String) = (Graphing.compile(t) as GraphCompile.Ok).function

    @Test fun evaluates() {
        assertEquals(9.0, fn("x^2").at(3.0), 1e-12)
        assertEquals(9.0, fn("y = x^2").at(-3.0), 1e-12)
        assertTrue(fn("ln(x)").at(-1.0).isNaN())
        assertTrue(fn("1/x").at(0.0).isNaN())
    }

    @Test fun badInputGivesError() {
        assertTrue(Graphing.compile("") is GraphCompile.Err)
        assertTrue(Graphing.compile("foo(x)") is GraphCompile.Err)
        assertTrue(Graphing.compile("x +") is GraphCompile.Err)
    }

    @Test fun segmentsBreakAtDomainEdgeAndAsymptote() {
        val s = Graphing.segments(fn("ln(x)"), -5.0, 5.0, -5.0, 5.0)
        assertEquals(1, s.size)
        assertTrue(s[0].xs.first() > 0.0)
        val t = Graphing.segments(fn("1/x"), -5.0, 5.0, -5.0, 5.0, 801)
        assertEquals(2, t.size)
        val rad = (Graphing.compile("tan(x)", com.tapecalc.core.math.EvalContext(angleMode = com.tapecalc.core.math.AngleMode.RAD)) as GraphCompile.Ok).function
        val tan = Graphing.segments(rad, -2.0 * Math.PI, 2.0 * Math.PI, -5.0, 5.0)
        assertTrue("tan breaks at asymptotes: ${tan.size}", tan.size >= 3)
    }

    @Test fun rootsAndExtremes() {
        val r = Graphing.roots(fn("x^2 - 4"), -10.0, 10.0)
        assertEquals(2, r.size)
        assertEquals(-2.0, r[0], 1e-9)
        assertEquals(2.0, r[1], 1e-9)
        assertEquals(0, Graphing.roots(fn("1/x"), -5.0, 5.0).size)
        val e = Graphing.extremes(fn("x^3 - 3x"), -3.0, 3.0)
        assertEquals(2, e.size)
        assertEquals(-1.0, e[0].x, 1e-6)
        assertTrue(e[0].isMax)
        assertEquals(2.0, e[0].y, 1e-9)
        assertEquals(1.0, e[1].x, 1e-6)
        assertTrue(!e[1].isMax)
    }

    @Test fun intersectionPoints() {
        val p = Graphing.intersections(fn("x^2"), fn("x + 2"), -5.0, 5.0)
        assertEquals(2, p.size)
        assertEquals(-1.0, p[0].first, 1e-9)
        assertEquals(1.0, p[0].second, 1e-9)
        assertEquals(2.0, p[1].first, 1e-9)
        assertEquals(4.0, p[1].second, 1e-9)
    }

    @Test fun slopeAndRange() {
        assertEquals(6.0, Graphing.slopeAt(fn("x^2"), 3.0), 1e-5)
        val (lo, hi) = Graphing.autoYRange(fn("1/x"), -5.0, 5.0)
        assertTrue(hi - lo < 100)
        assertEquals(1.0, Graphing.tickStep(6.0, 6), 0.0)
        assertEquals(5.0, Graphing.tickStep(30.0, 6), 0.0)
        assertEquals(0.2, Graphing.tickStep(1.2, 6), 1e-12)
    }
}

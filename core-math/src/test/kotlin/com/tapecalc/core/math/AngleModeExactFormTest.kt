package com.tapecalc.core.math

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The exact forms (√3/2, 1/2, π multiples) are derived from the value the engine calculated under the active angle
 * unit, so they must follow RAD / DEG / GRAD with the decimal result. The screen showed √3/2 for sin(60) in RAD
 * because a result calculated in DEG stayed on screen after the switch, not because the engine ignored the unit.
 */
class AngleModeExactFormTest {

    private fun ok(text: String, mode: AngleMode): EvalResult {
        val o = Calculator.evaluate(text, EvalContext(mode))
        return (o as? EvalOutcome.Success)?.result ?: throw AssertionError("'$text' failed: ${(o as EvalOutcome.Failure).error.message}")
    }

    @Test fun sin60InDegreesIsExactlyRootThreeOverTwo() {
        val r = ok("sin(60)", AngleMode.DEG)
        assertEquals("√3/2", r.exactForm)
        assertTrue(r.display.startsWith("0.8660254037844"))
    }

    @Test fun sin60InRadiansIsTheRadianValueWithNoRadical() {
        val r = ok("sin(60)", AngleMode.RAD)
        assertNull(r.exactForm)
        assertTrue(r.display, r.display.startsWith("-0.30481062110221"))
    }

    @Test fun cos90InRadiansIsNotZero() {
        val r = ok("cos(90)", AngleMode.RAD)
        assertNull(r.exactForm)
        assertTrue(r.display, r.display.startsWith("-0.44807361612917"))
        assertEquals("0", ok("cos(90)", AngleMode.DEG).display)
    }

    @Test fun gradiansUseTheirOwnUnit() {
        // 100 grad is a right angle
        assertEquals("1", ok("sin(100)", AngleMode.GRAD).display)
        assertNotEquals(ok("sin(100)", AngleMode.GRAD).display, ok("sin(100)", AngleMode.DEG).display)
    }

    @Test fun sameTextGivesDifferentExactFormsPerUnit() {
        val deg = ok("sin(60)", AngleMode.DEG)
        val rad = ok("sin(60)", AngleMode.RAD)
        assertNotEquals(deg.value.decimal, rad.value.decimal)
        assertNotEquals(deg.exactForm, rad.exactForm)
    }
}

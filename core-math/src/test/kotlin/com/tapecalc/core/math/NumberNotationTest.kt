package com.tapecalc.core.math

import org.junit.Assert.assertEquals
import org.junit.Test

class NumberNotationTest {

    private fun result(expr: String): EvalResult {
        val outcome = Calculator.evaluate(expr)
        if (outcome is EvalOutcome.Failure) throw AssertionError("'$expr' failed: ${outcome.error}")
        return (outcome as EvalOutcome.Success).result
    }

    @Test
    fun autoIsExactlyTheNormalDisplay() {
        for (expr in listOf("12345", "1/3", "2^0.5", "1e20", "0.0000001234", "-7.5")) {
            val r = result(expr)
            assertEquals(expr, r.display, r.formatted(NumberNotation.AUTO))
        }
    }

    @Test
    fun scientificView() {
        assertEquals("1.2345E4", result("12345").formatted(NumberNotation.SCIENTIFIC))
        assertEquals("1E6", result("1000*1000").formatted(NumberNotation.SCIENTIFIC))
        assertEquals("3.33333333333333E-1", result("1/3").formatted(NumberNotation.SCIENTIFIC))
        assertEquals("-7.5E0", result("-7.5").formatted(NumberNotation.SCIENTIFIC))
        assertEquals("0", result("0").formatted(NumberNotation.SCIENTIFIC))
    }

    @Test
    fun engineeringView() {
        assertEquals("12.345E3", result("12345").formatted(NumberNotation.ENGINEERING))
        assertEquals("1E6", result("1000*1000").formatted(NumberNotation.ENGINEERING))
        assertEquals("333.333333333333E-3", result("1/3").formatted(NumberNotation.ENGINEERING))
        assertEquals("120E-6", result("0.00012").formatted(NumberNotation.ENGINEERING))
    }

    @Test
    fun unitsAreNotPartOfTheFormattedNumber() {
        val r = result("5 km to m")
        assertEquals("5E3", r.formatted(NumberNotation.SCIENTIFIC))
        assertEquals("m", r.unit)
    }

    @Test
    fun fromNameFallsBackToAuto() {
        assertEquals(NumberNotation.AUTO, NumberNotation.fromName(null))
        assertEquals(NumberNotation.AUTO, NumberNotation.fromName("garbage"))
        assertEquals(NumberNotation.SCIENTIFIC, NumberNotation.fromName("SCIENTIFIC"))
        assertEquals(NumberNotation.ENGINEERING, NumberNotation.fromName("ENGINEERING"))
    }

    @Test
    fun everyNotationHasALabelForTheSettingsScreen() {
        assertEquals(listOf("Auto", "Scientific", "Engineering", "Fix", "Norm 1", "Norm 2"), NumberNotation.entries.map { it.label })
    }
}

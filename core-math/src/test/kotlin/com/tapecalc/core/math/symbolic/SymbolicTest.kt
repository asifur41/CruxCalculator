package com.tapecalc.core.math.symbolic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SymbolicTest {
    private fun ok(o: SymOutcome): String = when (o) {
        is SymOutcome.Ok -> o.text
        is SymOutcome.Err -> "ERR: ${o.error.message}"
    }

    @Test fun simplifyCombinesLikeTerms() {
        assertEquals("5x", ok(Symbolic.simplify("2x + 3x")))
        assertEquals("x^3", ok(Symbolic.simplify("x * x^2")))
        assertEquals("0", ok(Symbolic.simplify("x - x")))
        assertEquals("x^2 + 2x + 1", ok(Symbolic.simplify("1 + 2x + x^2")))
    }

    @Test fun expandProducts() {
        assertEquals("x^2 + 2x + 1", ok(Symbolic.expand("(x+1)^2")))
        assertEquals("x^2 - 1", ok(Symbolic.expand("(x-1)(x+1)")))
        assertEquals("x^3 + 3x^2 + 3x + 1", ok(Symbolic.expand("(x+1)^3")))
        assertEquals("a^2 + 2a*b + b^2", ok(Symbolic.expand("(a+b)^2")))
    }

    @Test fun derivatives() {
        assertEquals("3x^2", ok(Symbolic.derivative("x^3")))
        assertEquals("cos(x)", ok(Symbolic.derivative("sin(x)")))
        assertEquals("2x*cos(x^2)", ok(Symbolic.derivative("sin(x^2)")))
        assertEquals("exp(x)", ok(Symbolic.derivative("exp(x)")))
        assertEquals("1/x", ok(Symbolic.derivative("ln(x)")))
        assertEquals("2x + 3", ok(Symbolic.derivative("x^2 + 3x + 5")))
        assertEquals("x", ok(Symbolic.derivative("a*x", "a")))
    }

    @Test fun productAndQuotientRule() {
        assertEquals("x^2*exp(x) + 2x*exp(x)", ok(Symbolic.derivative("x^2*exp(x)")))
        assertEquals("1/x - 1/x^2".let { "1/x - 1/x^2" }, ok(Symbolic.simplify("1/x - x^-2")))
    }

    @Test fun integrals() {
        assertEquals("x^3/3 + C", ok(Symbolic.integral("x^2")))
        assertEquals("x^2 + x + C", ok(Symbolic.integral("2x + 1")))
        assertEquals("-cos(x) + C", ok(Symbolic.integral("sin(x)")))
        assertEquals("exp(x) + C", ok(Symbolic.integral("exp(x)")))
        assertEquals("ln(abs(x)) + C", ok(Symbolic.integral("1/x")))
        assertEquals("sin(2x)/2 + C", ok(Symbolic.integral("cos(2x)")))
    }

    @Test fun integralByParts() {
        val r = ok(Symbolic.integral("x*exp(x)"))
        assertTrue(r, r.contains("exp(x)") && r.endsWith("+ C"))
    }

    @Test fun factoring() {
        assertEquals("(x - 1)*(x + 1)", ok(Symbolic.factor("x^2 - 1")))
        assertEquals("(x + 1)*(x + 2)", ok(Symbolic.factor("x^2 + 3x + 2")))
        assertEquals("2*(x - 1)*(x + 3)", ok(Symbolic.factor("2x^2 + 4x - 6")))
        assertEquals("x*(x - 2)", ok(Symbolic.factor("x^2 - 2x")))
        assertEquals("2^3*3^2*5", ok(Symbolic.factor("360")))
    }

    @Test fun substitution() {
        assertEquals("x^2 + 2x + 2", ok(Symbolic.substitute("y^2 + 1", "y", "x + 1")))
    }

    @Test fun errorsAreFriendly() {
        assertTrue(ok(Symbolic.derivative("x!")).startsWith("ERR"))
        assertTrue(ok(Symbolic.integral("sin(x^2)")).startsWith("ERR"))
        assertTrue(ok(Symbolic.simplify("")).startsWith("ERR"))
    }

    @Test fun moreResults() {
        assertEquals("x^2*exp(x) - 2x*exp(x) + 2*exp(x) + C", ok(Symbolic.integral("x^2*exp(x)")))
        assertEquals("-x*cos(x) + sin(x) + C", ok(Symbolic.integral("x*sin(x)")))
        assertEquals("atan(x) + C", ok(Symbolic.integral("1/(1+x^2)")))
        assertEquals("2*x^(3/2)/3 + C", ok(Symbolic.integral("sqrt(x)")))
        assertEquals("(x - 1)*(x - 2)*(x - 3)", ok(Symbolic.factor("x^3 - 6x^2 + 11x - 6")))
        assertEquals("(x - 1)*(x + 1)*(x^2 + 1)", ok(Symbolic.factor("x^4 - 1")))
        assertEquals("(x - 1)*(x + 1)/2", ok(Symbolic.factor("x^2/2 - 1/2")))
        assertEquals("(2x + 1)*(3x + 1)", ok(Symbolic.factor("6x^2 + 5x + 1")))
        assertEquals("1/sqrt(x)", ok(Symbolic.simplify("sqrt(x)/x")))
    }

    /** d/dx of the antiderivative must give the integrand back (checked numerically at several points). */
    @Test fun integralsDifferentiateBack() {
        val cases = listOf("x*sin(x)", "x*cos(x)", "x^2*exp(x)", "x^2*sin(x)", "x*ln(x)", "x^3*exp(2x)", "3x^2 + 2x", "1/(3x+2)", "sin(3x+1)", "x*exp(-x)")
        for (c in cases) {
            val anti = ok(Symbolic.integral(c)).removeSuffix(" + C")
            assertTrue("$c -> $anti", !anti.startsWith("ERR"))
            val back = ok(Symbolic.derivative(anti))
            for (x in doubleArrayOf(0.7, 1.3, 2.1)) {
                val a = evalAt(back, x)
                val b = evalAt(c, x)
                assertEquals("$c via $anti -> $back at $x", b, a, 1e-9 * maxOf(1.0, Math.abs(b)))
            }
        }
    }

    private fun evalAt(text: String, x: Double): Double {
        val r = com.tapecalc.core.math.Calculator.evaluate("x = $x : $text")
        return (r as com.tapecalc.core.math.EvalOutcome.Success).result.value.decimal.toDouble()
    }
}

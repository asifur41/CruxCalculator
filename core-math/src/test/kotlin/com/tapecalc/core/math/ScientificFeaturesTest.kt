package com.tapecalc.core.math

import com.tapecalc.core.precision.NumberFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class ScientificFeaturesTest {

    private fun ok(text: String, mode: AngleMode = AngleMode.DEG, vars: Map<String, CalcValue> = emptyMap()): EvalResult {
        val o = Calculator.evaluate(text, EvalContext(mode, vars))
        return (o as? EvalOutcome.Success)?.result ?: throw AssertionError("'$text' failed: ${(o as EvalOutcome.Failure).error.message}")
    }

    private fun err(text: String): CalcError =
        (Calculator.evaluate(text) as? EvalOutcome.Failure)?.error ?: throw AssertionError("'$text' should fail")

    private fun d(text: String, mode: AngleMode = AngleMode.DEG) = ok(text, mode).display

    // ---------------------------------------------------------------- Fix / Norm

    @Test fun fixKeepsTrailingZeros() {
        assertEquals("3.50", NumberFormatter.formatFixed(BigDecimal("3.5"), 2))
        assertEquals("3.14", NumberFormatter.formatFixed(BigDecimal("3.14159"), 2))
        assertEquals("3", NumberFormatter.formatFixed(BigDecimal("2.5"), 0))
        assertEquals("0.00", NumberFormatter.formatFixed(BigDecimal("-0.001"), 2))
        assertEquals("-1.01", NumberFormatter.formatFixed(BigDecimal("-1.005"), 2))
    }

    @Test fun normSwitchPoints() {
        assertEquals("0.005", NumberFormatter.formatNorm(BigDecimal("0.005"), 2))
        assertEquals("5E-3", NumberFormatter.formatNorm(BigDecimal("0.005"), 1))
        assertEquals("0.0123", NumberFormatter.formatNorm(BigDecimal("0.0123"), 1))
        assertEquals("1234567890", NumberFormatter.formatNorm(BigDecimal("1234567890"), 1))
        assertEquals("1E10", NumberFormatter.formatNorm(BigDecimal("10000000000"), 2))
        assertEquals("5E-10", NumberFormatter.formatNorm(BigDecimal("0.0000000005"), 2))
    }

    @Test fun resultFormattedWithFixAndNorm() {
        val r = ok("22/7")
        assertEquals("3.14", r.formatted(NumberNotation.FIXED, 2))
        assertEquals("3.1429", r.formatted(NumberNotation.FIXED, 4))
        assertEquals("3.14285714285714", r.formatted(NumberNotation.NORM1))
        assertEquals("1E-3", ok("0.001").formatted(NumberNotation.NORM1))
    }

    // ---------------------------------------------------------------- DMS

    @Test fun dmsInput() {
        assertEquals("2.5", d("2°30'0\""))
        assertEquals("2.508333333333333".take(14), d("2°30'30\"").take(14))
        assertEquals("1/2", ok("30'").fraction)
        assertEquals("3.5", d("2°30' + 1°"))
        assertEquals("-2.5", d("-2°30'"))
        assertEquals("1.25", d("1°15'"))
        assertEquals("45", d("(30+15)°"))
    }

    @Test fun dmsInputFollowsTheAngleMode() {
        assertEquals("1", d("sin(90°)", AngleMode.RAD))           // 90 degrees is entered in radians mode too
        assertEquals("0.5", d("sin(30°)", AngleMode.GRAD))
        assertEquals("100", d("90°", AngleMode.GRAD))
    }

    @Test fun dmsOutput() {
        assertEquals("2°30′0″", ok("2.5").dms)
        assertEquals("-0°30′0″", ok("-0.5").dms)
        assertEquals("1°0′0″", ok("0.99999999").dms)           // carries instead of 59.9999″
        assertEquals("0°20′0″", ok("1/3").dms)
        assertEquals("12°34′56.789″", Dms.format(BigDecimal("12.582441388888889")))
    }

    // ---------------------------------------------------------------- exact forms

    @Test fun piForm() {
        assertEquals("π/3", ok("pi/3").piForm)
        assertEquals("2π/3", ok("2pi/3").piForm)
        assertEquals("-π/6", ok("-pi/6").piForm)
        assertEquals("3π", ok("3pi").piForm)
        assertEquals("π", ok("pi").piForm)
        assertNull(ok("ln(2)").piForm)
        assertNull(ok("1/3").piForm)
    }

    @Test fun radicalForm() {
        assertEquals("√2", ok("sqrt(2)").radicalForm)
        assertEquals("2√2", ok("sqrt(8)").radicalForm)
        assertEquals("√2/2", ok("sin(45)").radicalForm)
        assertEquals("√3/2", ok("sin(60)").radicalForm)
        assertEquals("√3/3", ok("tan(30)").radicalForm)
        assertEquals("-√3", ok("-sqrt(3)").radicalForm)
        assertEquals("3√5/2", ok("3*sqrt(5)/2").radicalForm)
        assertNull(ok("ln(2)").radicalForm)
        assertNull(ok("pi").radicalForm)
    }

    @Test fun exactFormFeedsTheToggle() {
        assertEquals("1/3", ok("1/3").exactForm)
        assertEquals("√2", ok("sqrt(2)").exactForm)
        assertEquals("π/4", ok("pi/4").exactForm)
        assertNull(ok("ln(2)").exactForm)
        assertTrue(ok("sqrt(2)").hasExactView)
    }

    @Test fun simpleDecimalsKeepTheirFractionButNoisyOnesDoNot() {
        assertEquals("3/4", ok("0.75").fraction)
        assertNull(ok("hP").fraction)
    }

    // ---------------------------------------------------------------- colon chains, memory variables

    @Test fun colonChain() {
        val r = ok("A = 5 : B = A * 2 : A + B")
        assertEquals("15", r.display)
        assertEquals(setOf("A", "B"), r.assignments.keys)
        assertEquals("10", r.assignments.getValue("B").toString())
        assertNull(r.assignedName)
    }

    @Test fun colonChainWithAnsAndLastAssignment() {
        val r = ok("4 : ans * 3 : total = ans + 1")
        assertEquals("13", r.display)
        assertEquals("total", r.assignedName)
        assertEquals(setOf("total"), r.assignments.keys)
    }

    @Test fun colonChainErrorsPointAtTheBadPart() {
        assertEquals(ErrorKind.DIVISION_BY_ZERO, err("A = 1 : 1/0").kind)
        assertEquals(ErrorKind.EMPTY, err(" : ").kind)
        assertEquals("5", d("5 :"))
    }

    @Test fun analyzeColonChain() {
        val a = Calculator.analyze("A = 5 : B = A * k : B")
        assertTrue(a.isValid)
        assertEquals(setOf("k"), a.referencedNames)
        assertNull(a.assignedName)
        assertEquals("z", Calculator.analyze("1 : z = 2").assignedName)
    }

    @Test fun memoryVariableWorksInExpressions() {
        assertEquals("14", ok("M * 2", vars = mapOf("M" to CalcValue.ofLong(7))).display)
        assertEquals("14", ok("2M", vars = mapOf("M" to CalcValue.ofLong(7))).display)
    }

    // ---------------------------------------------------------------- Pol / Rec

    @Test fun polarFromRectangular() {
        val r = ok("pol(3, 4)")
        assertEquals("5", r.display)
        assertEquals("θ", r.extras.single().first)
        assertEquals("53.130102354156", r.extras.single().second.toString().take(15))
        assertEquals("5", r.assignments.getValue("X").toString())
        assertEquals("45", ok("pol(1, 1)").extras.single().second.toString())
        assertEquals("180", ok("pol(-2, 0)").extras.single().second.toString())
        assertEquals("-90", ok("pol(0, -5)").extras.single().second.toString())
        assertEquals("-135", ok("pol(-1, -1)").extras.single().second.toString())
        assertEquals("135", ok("pol(-1, 1)").extras.single().second.toString())
    }

    @Test fun rectangularFromPolar() {
        val r = ok("rec(2, 60)")
        assertEquals("1", r.display)
        assertEquals("1.73205080756888", r.extras.single().second.toString())
        assertEquals("y", r.extras.single().first)
        assertEquals("-2", ok("rec(2, 180)").display)
        assertEquals("0", ok("rec(2, 90)").display)
    }

    @Test fun polOriginIsUndefined() {
        assertEquals(ErrorKind.UNDEFINED, err("pol(0, 0)").kind)
    }

    @Test fun polarInRadians() {
        assertEquals("0.785398163397448", ok("pol(1, 1)", AngleMode.RAD).extras.single().second.toString())
        assertEquals("100", ok("pol(0, 1)", AngleMode.GRAD).extras.single().second.toString())
    }

    @Test fun atan2AndHypot() {
        assertEquals("45", d("atan2(1, 1)"))
        assertEquals("135", d("atan2(1, -1)"))
        assertEquals("5", d("hypot(3, 4)"))
        assertEquals("6", d("gcd(12, 18)"))
        assertEquals("6", d("gcd(12, 18, 30)"))
        assertEquals("12", d("lcm(4, 6)"))
        assertEquals(ErrorKind.DOMAIN, err("gcd(1.5, 3)").kind)
    }

    // ---------------------------------------------------------------- constants

    @Test fun constantsLibrary() {
        assertEquals("299792458", d("c0"))
        assertEquals("6.62607015E-34", d("hP"))
        assertEquals("6.02214076E23", d("NA"))
        assertEquals("8.31446261815324", d("NA * kB"))
        assertTrue(d("hbar"), d("hbar").startsWith("1.05457181"))
        assertEquals("9.80665", d("g0"))
        assertEquals(48, Constants.all.size) // the 47 of the Casio fx-991EX list plus the Coulomb constant ke
        assertNotNull(Constants.find("kB"))
    }

    @Test fun userVariableBeatsConstant() {
        assertEquals("5", ok("c0", vars = mapOf("c0" to CalcValue.ofLong(5))).display)
    }

    @Test fun constantNamesAreSuggested() {
        val e = err("kb * 2")
        assertEquals("kB", e.suggestion)
    }

    // ---------------------------------------------------------------- Σ, Π

    @Test fun sumAndProduct() {
        assertEquals("385", d("sum(k^2, k, 1, 10)"))
        assertEquals("120", d("prod(k, k, 1, 5)"))
        assertEquals("55", d("sum(k, k, 1, 10)"))
        assertEquals("0", d("sum(k, k, 5, 1)"))
        assertEquals("1", d("prod(k, k, 5, 1)"))
        assertEquals("1.5", d("sum(1/2^k, k, 0, 1) "))
        assertEquals("0.9990234375".take(8), d("sum(1/2^k, k, 1, 10)").take(8))
        assertEquals("3025", d("sum(k^3, k, 1, 10)"))
    }

    @Test fun sumErrors() {
        assertTrue(err("sum(k, 5, 1, 10)").message.contains("variable name"))
        assertEquals(ErrorKind.TOO_COMPLEX, err("sum(k, k, 1, 100000)").kind)
        assertEquals(ErrorKind.INVALID_ARGUMENT, err("sum(k, k)").kind)
    }

    // ---------------------------------------------------------------- calculus

    @Test fun definiteIntegrals() {
        assertEquals("9", d("integral(x^2, x, 0, 3)"))
        assertEquals("1/3", ok("integral(x^2, x, 0, 1)").fraction)
        assertEquals("2", d("integral(sin(x), x, 0, pi)", AngleMode.RAD))
        assertEquals("1", d("integral(1/x, x, 1, e)"))
        assertEquals("-9", d("integral(x^2, x, 3, 0)"))
        assertEquals("0", d("integral(x, x, 2, 2)"))
        assertEquals("0.785398163397", d("integral(1/(1+x^2), x, 0, 1)").take(14))
    }

    @Test fun improperIntegrals() {
        assertEquals("1.772453850906", d("integral(e^(-x^2), x, -inf, inf)"))
        assertEquals("1", d("integral(e^(-x), x, 0, inf)"))
        assertEquals("2", d("integral(1/sqrt(x), x, 0, 1)"))
    }

    @Test fun integralErrors() {
        assertEquals(ErrorKind.UNDEFINED, err("integral(ln(x), x, -1, 1)").kind)
        assertEquals(ErrorKind.SYNTAX, err("integral(x, 3, 0, 1)").kind)
        assertTrue(err("integral(1/x, x, 0, 1)").message.isNotEmpty())
    }

    @Test fun derivatives() {
        assertEquals("12", d("deriv(x^3, x, 2)"))
        assertEquals("1", d("deriv(sin(x), x, 0)", AngleMode.RAD))
        assertEquals("1", d("deriv(e^x, x, 0)"))
        assertEquals("0.5", d("deriv(sqrt(x), x, 1)"))
        assertEquals("-0.25", d("deriv(1/x, x, 2)"))
        assertEquals("1", d("deriv(abs(x), x, 3)"))
    }

    @Test fun derivativeErrors() {
        assertEquals(ErrorKind.UNDEFINED, err("deriv(ln(x), x, -1)").kind)
    }

    // ---------------------------------------------------------------- SOLVE

    @Test fun solveFindsExactRoots() {
        assertEquals("2", d("solve(x^2 - 4, x, 1)"))
        assertEquals("-2", d("solve(x^2 - 4, x, -1)"))
        assertEquals("1/3", ok("solve(3x - 1, x, 0)").fraction)
        assertEquals("3", d("solve(2^x - 8, x, 1)"))
    }

    @Test fun solveFindsIrrationalRootsToManyDigits() {
        val r = ok("solve(x^2 - 2, x, 1)")
        assertEquals("1.4142135623731", r.display)
        assertEquals("√2", r.radicalForm)
        assertTrue(r.full.startsWith("1.41421356237309504880168872420"))
        assertEquals("0.739085133215161", d("solve(cos(x) - x, x, 0)", AngleMode.RAD))
    }

    @Test fun solveTranscendental() {
        assertEquals("1.74552800274", d("solve(x*e^x - 10, x, 1)").take(13))
        assertEquals("1", d("solve(ln(x), x, 2)"))
        assertEquals("0.5671432904097", d("solve(x - e^(-x), x, 1)").take(15))
    }

    @Test fun solveUsesAFallbackWhenNewtonFails() {
        // The derivative is zero at the guess.
        assertEquals("2", d("solve(x^2 - 4, x, 0)").let { it.removePrefix("-") })
    }

    @Test fun solveErrors() {
        assertTrue(err("solve(x^2 + 1, x, 1)").message.contains("No solution"))
        assertEquals(ErrorKind.SYNTAX, err("solve(x, 2, 1)").kind)
    }

    @Test fun solveUsesVariablesFromTheContext() {
        assertEquals("6", ok("solve(x - k, x, 0)", vars = mapOf("k" to CalcValue.ofLong(6))).display)
    }

    @Test fun numericToolsRespectTheAngleMode() {
        assertEquals("30", d("solve(sin(x) - 0.5, x, 20)"))
        assertEquals("0.01745329252", d("deriv(sin(x), x, 0)"))
    }

    // ---------------------------------------------------------------- misc

    @Test fun existingBehaviourIsUnchanged() {
        assertEquals("0.3", d("0.1 + 0.2"))
        assertEquals("110", d("100 + 10%"))
        assertEquals("0.5", d("sin(30)"))
        assertEquals("7.45645", d("12 km to mi").take(7))
        assertEquals("Division by zero", err("1/0").message)
    }

    @Test fun tokenizerStillRejectsUnknownSymbols() {
        assertEquals(ErrorKind.SYNTAX, err("3 @ 4").kind)
    }
}

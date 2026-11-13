package com.tapecalc.core.math

import com.tapecalc.core.math.exact.ExactNumber
import com.tapecalc.core.precision.Rational
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/** The symbolic engine: exact fractions, roots and π, in the active angle unit. */
class ExactEngineTest {

    private fun eval(text: String, mode: AngleMode = AngleMode.DEG): EvalResult {
        val o = Calculator.evaluate(text, EvalContext(mode))
        return (o as? EvalOutcome.Success)?.result ?: throw AssertionError("'$text' failed: ${(o as EvalOutcome.Failure).error.message}")
    }

    private fun exact(text: String, mode: AngleMode = AngleMode.DEG): String? = eval(text, mode).exactForm

    // ------------------------------------------------------------------ the number class

    private fun r(n: Long, d: Long = 1) = ExactNumber.of(Rational.of(n, d))
    private fun root(n: Long) = ExactNumber.sqrtOf(Rational.of(n))

    @Test fun squareRootsSimplify() {
        assertEquals("2√2", root(8).format())
        assertEquals("3", root(9).format())
        assertEquals("√2/2", ExactNumber.sqrtOf(Rational.of(1, 2)).format())
        assertEquals("√3/3", ExactNumber.sqrtOf(Rational.of(1, 3)).format())
        assertEquals("5√2", root(50).format())
        assertEquals("0", root(0).format())
    }

    @Test fun rootsMultiplyAndCancel() {
        assertEquals(r(2), root(2) * root(2))
        assertEquals("√6", (root(2) * root(3)).format())
        assertEquals("6", (root(12) * root(3)).format())          // 2√3 · √3
        assertEquals("√3", (r(3) / root(3)).format())             // 3/√3
        assertEquals("√2/2", (r(1) / root(2)).format())
    }

    @Test fun sumsKeepLikeTermsTogether() {
        assertEquals("3√2", (root(2) + root(8)).format())          // √2 + 2√2
        assertEquals("1+√2", (r(1) + root(2)).format())
        assertEquals("√2-1", (root(2) - r(1)).format())
        assertEquals("0", (root(2) - root(2)).format())
        assertEquals("(√6-√2)/4", ((root(6) - root(2)) * r(1, 4)).format())
    }

    @Test fun expandingProducts() {
        // (1 + √2)² = 3 + 2√2 and (√2 + √3)² = 5 + 2√6
        assertEquals("3+2√2", (r(1) + root(2)).pow(2).format())
        assertEquals("5+2√6", (root(2) + root(3)).pow(2).format())
        // (√3 - 1)(√3 + 1) = 2
        assertEquals(r(2), (root(3) - r(1)) * (root(3) + r(1)))
    }

    @Test fun rationalisingADenominator() {
        assertEquals("√2-1", (r(1) / (r(1) + root(2))).format())            // 1/(1+√2)
        assertEquals("(3+√3)/6", (ExactNumber.ONE / (r(3) - root(3))).format())      // 1/(3-√3)
        assertEquals("1+√2", (r(1) / (root(2) - r(1))).format())            // 1/(√2-1)
    }

    @Test fun piTerms() {
        assertEquals("2π/3", (ExactNumber.PI * r(2, 3)).format())
        assertEquals("-π", (-ExactNumber.PI).format())
        assertEquals("π²", ExactNumber.PI.pow(2).format())
        assertEquals("1/π", (r(1) / ExactNumber.PI).format())
        assertEquals("3/(2π)", (r(3, 2) / ExactNumber.PI).format())
        assertEquals("√2/π", (root(2) / ExactNumber.PI).format())
        assertEquals("π√3", (ExactNumber.PI * root(3)).format())
        assertEquals("1+π", (r(1) + ExactNumber.PI).format())
        assertEquals(r(1), ExactNumber.PI / ExactNumber.PI)
    }

    @Test fun squareRootOfPiSquared() {
        assertEquals("π", ExactNumber.PI.pow(2).sqrt().format())
        assertEquals("2π", (ExactNumber.PI.pow(2) * r(4)).sqrt().format())
    }

    @Test fun decimalValueOfAnExactNumber() {
        val v = (root(2) + root(3)).toBigDecimal()
        assertTrue(v.toString(), v.toString().startsWith("3.1462643699419723423"))
        assertTrue(ExactNumber.PI.toBigDecimal().toString().startsWith("3.14159265358979323846264338327950288"))
    }

    // ------------------------------------------------------------------ in the calculator

    @Test fun degreesGiveClosedFormsForMultiplesOf15() {
        assertEquals("√3/2", exact("sin(60)"))
        assertEquals("√2/2", exact("sin(45)"))
        assertEquals("(√6-√2)/4", exact("sin(15)"))
        assertEquals("(√2+√6)/4", exact("cos(15)"))
        assertEquals("2-√3", exact("tan(15)"))
        assertEquals("2+√3", exact("tan(75)"))
        assertEquals("√3", exact("tan(60)"))
        assertEquals("-√3/2", exact("cos(150)"))
        assertEquals("-√2/2", exact("sin(225)"))
        assertEquals("1/2", exact("sin(30)"))
    }

    @Test fun radiansReadMultiplesOfPiExactly() {
        assertEquals("√3/2", exact("sin(pi/3)", AngleMode.RAD))
        assertEquals("√2/2", exact("cos(pi/4)", AngleMode.RAD))
        assertEquals("√3", exact("tan(pi/3)", AngleMode.RAD))
        assertEquals("-√3/2", exact("sin(4pi/3)", AngleMode.RAD))
        assertEquals("(√6-√2)/4", exact("sin(pi/12)", AngleMode.RAD))
    }

    @Test fun radianResultsThatAreRationalBecomeExact() {
        // the decimal engine alone only had approximations of these
        for ((text, shown) in listOf("sin(pi/6)" to "0.5", "cos(pi/3)" to "0.5", "sin(pi)" to "0", "cos(pi/2)" to "0", "cos(pi)" to "-1", "tan(pi/4)" to "1")) {
            val x = eval(text, AngleMode.RAD)
            assertNotNull("$text should be exact", x.value.exact)
            assertEquals(text, shown, x.display)
        }
        assertEquals("1/2", eval("sin(pi/6)", AngleMode.RAD).fraction)
        // and stays exact when used again
        assertEquals("1", eval("2*sin(pi/6)", AngleMode.RAD).display)
        assertEquals(Rational.ONE, eval("2*sin(pi/6)", AngleMode.RAD).value.exact)
        assertEquals("0", eval("sin(pi)^2 + cos(pi/2)", AngleMode.RAD).display)
    }

    @Test fun theSameTextFollowsTheAngleUnit() {
        assertEquals("√3/2", exact("sin(60)", AngleMode.DEG))
        // 60 radians is not a multiple of π: no closed form, and the decimal is the radian one
        val rad = eval("sin(60)", AngleMode.RAD)
        assertNull(rad.symbolicForm)
        assertNull(rad.exactForm)
        assertTrue(rad.display, rad.display.startsWith("-0.30481062110221"))
        // 100 grad is a right angle, 50 grad is 45°
        assertEquals("1", eval("sin(100)", AngleMode.GRAD).display)
        assertEquals("√2/2", exact("sin(50)", AngleMode.GRAD))
        // π in degrees is just 3.14159 degrees
        assertNull(eval("sin(pi)", AngleMode.DEG).symbolicForm)
        assertEquals("0", eval("sin(pi)", AngleMode.RAD).display)
    }

    @Test fun inverseFunctionsReturnExactAnglesInTheActiveUnit() {
        assertEquals("60", eval("asin(sqrt(3)/2)", AngleMode.DEG).display)
        assertEquals("π/3", exact("asin(sqrt(3)/2)", AngleMode.RAD))
        assertEquals("π/4", exact("atan(1)", AngleMode.RAD))
        assertEquals("2π/3", exact("acos(-1/2)", AngleMode.RAD))
        assertEquals("-π/6", exact("asin(-1/2)", AngleMode.RAD))
        assertEquals("75", eval("atan(2+sqrt(3))", AngleMode.DEG).display)
        assertEquals("100", eval("asin(1)", AngleMode.GRAD).display)
    }

    @Test fun squareRootsAndPowersOfRoots() {
        assertEquals("2√2", exact("sqrt(8)"))
        assertEquals("1+√2", exact("1+sqrt(2)"))
        assertEquals("3+2√2", exact("(1+sqrt(2))^2"))
        assertEquals("√2", exact("2^(1/2)"))
        assertEquals("2√2", exact("2^(3/2)"))
        assertEquals("√2-1", exact("1/(1+sqrt(2))"))
        assertEquals("5", eval("sqrt(2)*sqrt(2)+3").display)
        assertEquals("√5", exact("hypot(1,2)"))
    }

    @Test fun piArithmetic() {
        assertEquals("2π", exact("2pi"))
        assertEquals("π²", exact("pi^2"))
        assertEquals("π/2", exact("pi/2"))
        assertEquals("1/π", exact("1/pi"))
        assertEquals("π√3", exact("pi*sqrt(3)"))
        assertEquals("-π/6", exact("-pi/6"))
        assertEquals("1", eval("pi/pi").display)
    }

    @Test fun phiIsExact() {
        assertEquals("(1+√5)/2", exact("phi"))
        assertEquals("1", eval("phi^2 - phi").display)
    }

    @Test fun symbolicFormsMatchAnIndependentDecimalCalculation() {
        val cases = listOf(
            "sin(15)" to Math.sin(Math.toRadians(15.0)),
            "cos(75)" to Math.cos(Math.toRadians(75.0)),
            "tan(15)" to Math.tan(Math.toRadians(15.0)),
            "(1+sqrt(2))^3" to Math.pow(1 + Math.sqrt(2.0), 3.0),
            "pi*sqrt(5)/7" to Math.PI * Math.sqrt(5.0) / 7,
            "1/(sqrt(3)-1)" to 1 / (Math.sqrt(3.0) - 1),
            "sqrt(12)+sqrt(27)" to Math.sqrt(12.0) + Math.sqrt(27.0)
        )
        for ((text, expected) in cases) {
            val x = eval(text)
            assertNotNull(text, x.symbolicForm)
            assertEquals(text, expected, x.value.decimal.toDouble(), 1e-12)
        }
    }

    @Test fun unsupportedThingsKeepTheirDecimalAnswer() {
        for (text in listOf("e^2", "ln(5)", "sin(7)", "2^pi", "sqrt(1+sqrt(2))", "sqrt(pi)", "tan(10)", "exp(1)", "log(3)")) {
            val x = eval(text)
            assertNull("$text should have no symbolic form", x.symbolicForm)
        }
        assertEquals("0.5", eval("1/2").display)
        assertEquals("1/2", eval("1/2").fraction)
    }

    @Test fun exactnessOfPlainArithmeticIsUntouched() {
        assertEquals("0.3", eval("0.1+0.2").display)
        assertEquals("3/10", eval("0.1+0.2").fraction)
        assertEquals("7/4", eval("7/4").fraction)
        assertEquals("1 3/4", eval("7/4").mixed)
        assertEquals("0.(3)", eval("1/3").repeating)
        assertEquals("120", eval("5!").display)
        assertEquals("110", eval("100+10%").display)
    }

    @Test fun variablesAndAnsStayExactWhenTheyAreFractions() {
        val ctx = EvalContext(AngleMode.RAD, mapOf("x" to CalcValue.ofRational(Rational.of(1, 6))))
        val o = Calculator.evaluate("sin(x*pi)", ctx)
        assertEquals("1/2", ((o as EvalOutcome.Success).result).fraction)
    }

    @Test fun errorsStillComeFromTheDecimalEngine() {
        val o = Calculator.evaluate("tan(90)", EvalContext(AngleMode.DEG))
        assertTrue(o is EvalOutcome.Failure)
        assertTrue(Calculator.evaluate("sqrt(-4)", EvalContext()) is EvalOutcome.Failure)
        assertTrue(Calculator.evaluate("1/0", EvalContext()) is EvalOutcome.Failure)
    }

    @Test fun displayedDecimalIsStillTheNumericOne() {
        assertTrue(eval("sin(15)").display.startsWith("0.25881904510252"))
        assertEquals(BigDecimal("0.5"), eval("sin(pi/6)", AngleMode.RAD).value.decimal)
    }
}

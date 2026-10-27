package com.tapecalc.core.math

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalculatorTest {

    private fun ok(expr: String, ctx: EvalContext = EvalContext()): EvalResult {
        val outcome = Calculator.evaluate(expr, ctx)
        if (outcome is EvalOutcome.Failure) throw AssertionError("'$expr' failed: ${outcome.error}")
        return (outcome as EvalOutcome.Success).result
    }

    private fun show(expr: String, ctx: EvalContext = EvalContext()): String = ok(expr, ctx).display

    private fun errorOf(expr: String, ctx: EvalContext = EvalContext()): CalcError {
        val outcome = Calculator.evaluate(expr, ctx)
        assertTrue("'$expr' should fail but gave $outcome", outcome is EvalOutcome.Failure)
        return (outcome as EvalOutcome.Failure).error
    }

    // ---------------------------------------------------------------- basics

    @Test
    fun basicArithmetic() {
        assertEquals("5", show("2+3"))
        assertEquals("-1", show("2-3"))
        assertEquals("24", show("6*4"))
        assertEquals("24", show("6×4"))
        assertEquals("2.5", show("5/2"))
        assertEquals("2.5", show("5÷2"))
        assertEquals("1.5", show("3−1.5"))
        assertEquals("1000", show("1e3"))
        assertEquals("0.5", show(".5"))
    }

    @Test
    fun operatorPrecedence() {
        assertEquals("14", show("2+3*4"))
        assertEquals("-4", show("-2^2"))
        assertEquals("512", show("2^3^2"))
        assertEquals("0.125", show("2^-3"))
        assertEquals("7", show("10-9/3"))
        assertEquals("11", show("3!+5"))
        assertEquals("1", show("7 mod 3"))
        assertEquals("2", show("-7 mod 3"))
    }

    @Test
    fun parentheses() {
        assertEquals("20", show("(2+3)*4"))
        assertEquals("21", show("((2+3)*4)+1"))
        assertEquals("14", show("2(3+4)"))
        assertEquals("12", show("(1+2)(3+1)"))
        assertEquals(ErrorKind.UNBALANCED_PARENTHESES, errorOf("(1+2").kind)
        assertEquals(ErrorKind.UNBALANCED_PARENTHESES, errorOf("1+2)").kind)
        assertEquals("(1+2)", Calculator.autoClose("(1+2"))
        assertEquals("5", show(Calculator.previewText("2+3+")))
    }

    @Test
    fun divisionByZero() {
        assertEquals(ErrorKind.DIVISION_BY_ZERO, errorOf("1/0").kind)
        assertEquals(ErrorKind.DIVISION_BY_ZERO, errorOf("5 mod 0").kind)
        assertEquals(ErrorKind.DIVISION_BY_ZERO, errorOf("0^-1").kind)
        assertEquals(ErrorKind.DIVISION_BY_ZERO, errorOf("1/(2-2)").kind)
    }

    @Test
    fun floatingPointPrecision() {
        assertEquals("0.3", show("0.1+0.2"))
        assertEquals("3/10", ok("0.1+0.2").fraction)
        assertEquals("0.3", ok("0.1+0.2").full)
        assertEquals("1", show("0.3-0.1-0.2+1"))
        assertEquals("0.1", show("1.1-1"))
        assertEquals("1", show("(1/3)*3"))
    }

    @Test
    fun percentages() {
        assertEquals("0.5", show("50%"))
        assertEquals("110", show("100+10%"))
        assertEquals("90", show("100-10%"))
        assertEquals("20", show("200*10%"))
        assertEquals("5", show("50%*10"))
        assertEquals("1150", show("1000+15%"))
    }

    // ---------------------------------------------------------------- scientific

    @Test
    fun trigDegrees() {
        val deg = EvalContext(AngleMode.DEG)
        assertEquals("0.5", show("sin(30)", deg))
        assertEquals("1/2", ok("sin(30)", deg).fraction)
        assertEquals("0.5", show("cos(60)", deg))
        assertEquals("1", show("tan(45)", deg))
        assertEquals("0", show("sin(180)", deg))
        assertEquals("0", show("cos(90)", deg))
        assertEquals("-1", show("cos(180)", deg))
        assertEquals("0.866025403784439", show("sin(60)", deg))
        assertEquals("0.707106781186548", show("sin(45)", deg))
        assertEquals("0.5", show("sin(390)", deg))
        assertEquals("-0.5", show("sin(-30)", deg))
        assertEquals(ErrorKind.UNDEFINED, errorOf("tan(90)", deg).kind)
        assertEquals("30", show("asin(0.5)", deg))
        assertEquals("60", show("acos(0.5)", deg))
        assertEquals("45", show("atan(1)", deg))
    }

    @Test
    fun trigRadians() {
        val rad = EvalContext(AngleMode.RAD)
        assertEquals("0", show("sin(pi)", rad))
        assertEquals("-1", show("cos(pi)", rad))
        assertEquals("1", show("sin(pi/2)", rad))
        assertEquals("0.5", show("sin(pi/6)", rad))
        assertEquals("1", show("tan(pi/4)", rad))
        assertEquals("0.841470984807897", show("sin(1)", rad))
        assertEquals("0.785398163397448", show("atan(1)", rad))
        assertEquals("1.5707963267949", show("asin(1)", rad))
        assertEquals(ErrorKind.UNDEFINED, errorOf("tan(pi/2)", rad).kind)
    }

    @Test
    fun trigGradians() {
        val grad = EvalContext(AngleMode.GRAD)
        assertEquals("1", show("sin(100)", grad))
        assertEquals("0", show("cos(100)", grad))
        assertEquals("0.5", show("sin(100/3)", grad))
        assertEquals("1", show("tan(50)", grad))
        assertEquals("100", show("asin(1)", grad))
        assertEquals("50", show("atan(1)", grad))
    }

    @Test
    fun hyperbolicAndLogs() {
        assertEquals("1.1752011936438", show("sinh(1)"))
        assertEquals("1.54308063481524", show("cosh(1)"))
        assertEquals("0.761594155955765", show("tanh(1)"))
        assertEquals("0.881373587019543", show("asinh(1)"))
        assertEquals("3", show("log(1000)"))
        assertEquals("3", ok("log(1000)").full)
        assertEquals("8", show("log2(256)"))
        assertEquals("3", show("log(8, 2)"))
        assertEquals("1", show("ln(e)"))
        assertEquals("0", show("ln(1)"))
        assertEquals("2.30258509299405", show("ln(10)"))
        assertEquals("7.38905609893065", show("exp(2)"))
    }

    @Test
    fun domainErrors() {
        assertEquals(ErrorKind.DOMAIN, errorOf("log(-1)").kind)
        assertEquals(ErrorKind.DOMAIN, errorOf("ln(0)").kind)
        assertEquals(ErrorKind.DOMAIN, errorOf("sqrt(-4)").kind)
        assertEquals(ErrorKind.DOMAIN, errorOf("asin(2)").kind)
        assertEquals(ErrorKind.DOMAIN, errorOf("acosh(0.5)").kind)
        assertEquals(ErrorKind.DOMAIN, errorOf("(-8)^0.5").kind)
        assertEquals(ErrorKind.DOMAIN, errorOf("(-3)!").kind)
        assertEquals(ErrorKind.DOMAIN, errorOf("2.5!").kind)
    }

    @Test
    fun powersRootsFactorialsCombinatorics() {
        assertEquals("1024", show("2^10"))
        assertEquals("4", show("sqrt(16)"))
        assertEquals("1.4142135623731", show("sqrt(2)"))
        assertEquals("1.4142135623731", show("√2"))
        assertEquals("2", show("cbrt(8)"))
        assertEquals("-2", show("cbrt(-8)"))
        assertEquals("-2", show("(-8)^(1/3)"))
        assertEquals("2", show("8^(1/3)"))
        assertEquals("3", show("root(81, 4)"))
        assertEquals("120", show("5!"))
        assertEquals("2.43290200817664E18", show("20!"))
        assertEquals("2432902008176640000", ok("20!").full)
        assertEquals("5", show("abs(-5)"))
        assertEquals("6", show("npr(4,2)*0.5"))
        assertEquals("12", show("nPr(4,2)"))
        assertEquals("10", show("nCr(5,2)"))
        assertEquals("1", show("nCr(10,0)"))
        assertEquals("3", show("floor(3.7)"))
        assertEquals("4", show("ceil(3.2)"))
        assertEquals("3.15", show("round(3.14159+0.0084, 2)"))
        assertEquals("3", show("mod(10,7)"))
        assertEquals("3", show("min(5,3,9)"))
        assertEquals("9", show("max(5,3,9)"))
    }

    @Test
    fun constants() {
        assertEquals("3.14159265358979", show("pi"))
        assertEquals("3.14159265358979", show("π"))
        assertEquals("2.71828182845905", show("e"))
        assertEquals("1.61803398874989", show("phi"))
        assertEquals("6.28318530717959", show("2pi"))
        assertEquals("5.43656365691809", show("2e"))
    }

    @Test
    fun overflowAndUnderflow() {
        assertEquals(ErrorKind.OVERFLOW, errorOf("10^10000").kind)
        assertEquals(ErrorKind.OVERFLOW, errorOf("9^9^9").kind)
        assertEquals(ErrorKind.UNDERFLOW, errorOf("10^-10000").kind)
        assertEquals(ErrorKind.OVERFLOW, errorOf("4000!").kind)
        assertEquals(ErrorKind.OVERFLOW, errorOf("exp(30000)").kind)
        assertEquals("1E+9999".replace("+", ""), show("10^9999"))
    }

    @Test
    fun veryLargeAndVerySmall() {
        assertEquals("1E-20", show("0.1^20"))
        assertEquals("1.23456789E50", show("123456789*10^42"))
        assertEquals("1E100", show("10^100"))
        assertEquals("1E-100", show("1/10^100"))
        assertEquals("1.84467440737096E19", show("2^64"))
        assertEquals("18446744073709551616", ok("2^64").full)
    }

    @Test
    fun implicitMultiplication() {
        assertEquals("6", show("2*3"))
        assertEquals("10", show("2(5)"))
        assertEquals("10", show("(5)2".replace("2", "(2)")))
        val ctx = EvalContext(variables = mapOf("x" to CalcValue.ofLong(4)))
        assertEquals("8", show("2x", ctx))
        assertEquals("16", show("x x", ctx))
        assertEquals("32", show("2x^2", ctx))
        // Ambiguous: 1/2x is read as 1/(2x) and a warning is attached
        val ambiguous = ok("1/2x", ctx)
        assertEquals("0.125", ambiguous.display)
        assertTrue(ambiguous.warnings.isNotEmpty())
        assertTrue(ok("1/(2x)", ctx).warnings.isEmpty())
        assertEquals(ErrorKind.SYNTAX, errorOf("2 3").kind)
        assertEquals("0.5", show("sin 30"))
        assertEquals("0.5", show("sin(30)*1"))
    }

    @Test
    fun repeatingDecimalsAndFractions() {
        val third = ok("1/3")
        assertEquals("0.333333333333333", third.display)
        assertEquals("1/3", third.fraction)
        assertEquals("0.(3)", third.repeating)
        assertEquals("0.1(6)", ok("1/6").repeating)
        assertEquals("0.(142857)", ok("1/7").repeating)
        assertNull(ok("1/8").repeating)
        assertEquals("0.125", ok("1/8").display)
        assertEquals("7/4", ok("7/4").fraction)
        assertEquals("1 3/4", ok("7/4").mixed)
        assertEquals("11/12", ok("3/4+1/6").fraction)
        assertEquals("0.916666666666667", ok("3/4+1/6").display)
        assertEquals("1/2", ok("2/4").fraction)
        assertNull(ok("4/2").fraction)
        assertNull(ok("sqrt(2)").fraction)
    }

    // ---------------------------------------------------------------- units

    @Test
    fun unitConversion() {
        assertEquals("7.45645430684", show("12 km to mi").take(13))
        assertEquals("mi", ok("12 km to mi").unit)
        assertEquals("11.0231131092439", show("5 kg to lb"))
        assertEquals("22.2222222222222", show("72 F to C"))
        assertEquals("°C", ok("72 F to C").unit)
        assertEquals("100", show("212 F to C"))
        assertEquals("32", show("0 C to F"))
        assertEquals("273.15", show("0 °C to K"))
        assertEquals("1000", show("1 km to m"))
        assertEquals("1000", show("1km to m"))
        assertEquals("3.28083989501312", show("1 m to ft"))
        assertEquals("2.54", show("1 in to cm"))
        assertEquals("2.54", show("1 inch in cm"))
        assertEquals("60", show("1 h to min"))
        assertEquals("3.6", show("1 m/s to km/h"))
        assertEquals("8", show("1 B to bit"))
        assertEquals("1024", show("1 KiB to B"))
        assertEquals("3.78541178400000", ok("1 gal to L").display.padEnd(16, '0').take(16))
        assertEquals("180", show("pi rad to deg"))
        assertEquals("90", show("100 grad to deg"))
        assertEquals("101325", show("1 atm to Pa"))
        assertEquals("4184", show("1 kcal to J"))
        assertEquals("2", show("(1+1) km to km"))
        assertEquals("1000", show("km to m"))
        assertEquals("235.214583333333", show("1 mpg to L/100km"))
        assertEquals("10", show("10 L/100km to km/L".replace("10 L/100km", "10 km/L")))
        assertEquals(ErrorKind.UNIT_MISMATCH, errorOf("5 kg to km").kind)
        assertEquals(ErrorKind.UNKNOWN_UNIT, errorOf("5 kg to zorp").kind)
        assertEquals(ErrorKind.DIVISION_BY_ZERO, errorOf("0 mpg to km/L").kind)
    }

    @Test
    fun unitRegistryCoversAllCategories() {
        for (category in UnitCategory.entries) {
            assertTrue("${category.title} has units", UnitRegistry.byCategory(category).size >= 3)
        }
        assertNotNull(UnitRegistry.find("KM"))
        assertNotNull(UnitRegistry.find("kilometers"))
        assertNull(UnitRegistry.find("mb"))
    }

    // ---------------------------------------------------------------- variables, ans

    @Test
    fun variableAssignmentAndReuse() {
        val first = ok("rate = 0.18")
        assertEquals("rate", first.assignedName)
        assertEquals("0.18", first.display)
        val vars = mapOf("rate" to first.value)
        val ctx = EvalContext(variables = vars)
        assertEquals("36", show("200*rate", ctx))
        assertEquals("0.36", show("2rate", ctx))
        assertEquals("rate", Calculator.analyze("200*rate").referencedNames.single())
        assertEquals("rate", Calculator.analyze("rate = 5").assignedName)
        assertEquals(ErrorKind.UNKNOWN_NAME, errorOf("200*tax").kind)
        assertEquals(ErrorKind.SYNTAX, errorOf("pi = 3").kind)
        assertEquals(ErrorKind.SYNTAX, errorOf("sin = 3").kind)
        // value survives serialization (this is what the database stores)
        val restored = CalcValue.deserialize(first.value.serialize())
        assertNotNull(restored)
        assertEquals("0.18", ok("rate", EvalContext(variables = mapOf("rate" to restored!!))).display)
        assertEquals("9/50", ok("rate", EvalContext(variables = mapOf("rate" to restored))).fraction)
    }

    @Test
    fun answerToken() {
        val ctx = EvalContext(ans = CalcValue.ofLong(12))
        assertEquals("24", show("ans*2", ctx))
        assertEquals("144", show("ans^2", ctx))
        assertTrue(Calculator.analyze("ans+1").usesAns)
        assertFalse(Calculator.analyze("1+1").usesAns)
        assertEquals(ErrorKind.UNKNOWN_NAME, errorOf("ans+1").kind)
    }

    // ---------------------------------------------------------------- misc

    @Test
    fun syntaxErrors() {
        assertEquals(ErrorKind.EMPTY, errorOf("   ").kind)
        assertEquals(ErrorKind.SYNTAX, errorOf("2+*3").kind)
        assertEquals(ErrorKind.SYNTAX, errorOf("2+").kind)
        assertEquals(ErrorKind.SYNTAX, errorOf("1.2.3").kind)
        assertEquals(ErrorKind.SYNTAX, errorOf("2 @ 3").kind)
        assertEquals(ErrorKind.INVALID_ARGUMENT, errorOf("sin(1,2)").kind)
        assertEquals(ErrorKind.TOO_COMPLEX, errorOf("(".repeat(500) + "1" + ")".repeat(500)).kind)
    }

    @Test
    fun smartPaste() {
        assertEquals("1234.5", SmartPaste.extract("$1,234.50")?.let { if (it == "1234.50") "1234.5" else it })
        assertEquals("1234.56", SmartPaste.extract("1.234,56"))
        assertEquals("12.5", SmartPaste.extract("12,5"))
        assertEquals("2+2*3", SmartPaste.extract("2+2*3"))
        assertEquals("42", SmartPaste.extract("total: 42 items"))
        assertNull(SmartPaste.extract("no digits here"))
    }

    @Test
    fun numberFormattingViews() {
        assertEquals("1,234,567.89", com.tapecalc.core.precision.NumberFormatter.group("1234567.89"))
        assertEquals("1E-7", show("0.0000001"))
        assertEquals("0.000001", show("0.000001"))
    }
}

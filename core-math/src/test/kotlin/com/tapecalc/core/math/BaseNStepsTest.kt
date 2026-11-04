package com.tapecalc.core.math

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BaseNStepsTest {
    private fun base(text: String, base: Int, bits: Int = 32): BaseNResult =
        (BaseNCalc.evaluate(text, base, bits) as? BaseNOutcome.Ok)?.result
            ?: throw AssertionError("'$text' failed: " + (BaseNCalc.evaluate(text, base, bits) as BaseNOutcome.Err).message)

    private fun baseErr(text: String, base: Int, bits: Int = 32): String =
        (BaseNCalc.evaluate(text, base, bits) as BaseNOutcome.Err).message

    @Test fun hexArithmetic() {
        val r = base("FF + 1", 16)
        assertEquals("256", r.dec); assertEquals("100", r.hex); assertEquals("400", r.oct); assertEquals("100000000", r.bin)
    }

    @Test fun binaryLogic() {
        assertEquals("1000", base("1100 and 1010", 2).bin)
        assertEquals("1110", base("1100 or 1010", 2).bin)
        assertEquals("110", base("1100 xor 1010", 2).bin)
    }

    @Test fun xnorAndNotInEightBits() {
        assertEquals("-7", base("1100 xnor 1010", 2, 8).dec)
        assertEquals("FF", base("not 0", 16, 8).hex)
        assertEquals("-1", base("not 0", 16, 8).dec)
        assertEquals("11111111", base("not 0", 2, 8).bin)
    }

    @Test fun negation() {
        val r = base("neg 5", 10, 8)
        assertEquals("-5", r.dec); assertEquals("FB", r.hex); assertEquals("11111011", r.bin)
        assertEquals("-5", base("-5", 10, 8).dec)
    }

    @Test fun twosComplementWrapAround() {
        assertEquals("-128", base("7F + 1", 16, 8).dec)
        assertEquals("-1", base("FFFFFFFF", 16).dec)
        assertEquals("0", base("FF + 1", 16, 8).dec)
    }

    @Test fun precedence() {
        assertEquals("14", base("2 + 3 * 4", 10).dec)
        assertEquals("20", base("(2 + 3) * 4", 10).dec)
        assertEquals("1", base("1 or 0 and 0", 2).bin)       // and binds tighter than or
        assertEquals("7", base("3 + 4 and 7", 10).dec)        // + binds tighter than and
    }

    @Test fun divisionAndModulo() {
        assertEquals("3", base("7 / 2", 10).dec)
        assertEquals("-3", base("-7 / 2", 10).dec)
        assertEquals("1", base("7 mod 2", 10).dec)
    }

    @Test fun shifts() {
        assertEquals("8", base("1 shl 3", 10).dec)
        assertEquals("2", base("8 shr 2", 10).dec)
        assertEquals("-2", base("-8 shr 2", 10).dec)
    }

    @Test fun prefixesAndLowerCase() {
        assertEquals("255", base("0xff", 16).dec)
        assertEquals("5", base("0b101", 2).dec)
        assertEquals("255", base("ff", 16).dec)
    }

    @Test fun groupedBinary() {
        assertEquals("1111 0000 1010 0101", base("F0A5", 16).binGrouped)
        assertEquals("1 0000", base("10", 16).binGrouped)
    }

    @Test fun sixtyFourBit() {
        assertEquals("-1", base("FFFFFFFFFFFFFFFF", 16, 64).dec)
        assertEquals("9223372036854775807", base("7FFFFFFFFFFFFFFF", 16, 64).dec)
    }

    @Test fun baseNErrors() {
        assertTrue(baseErr("12", 2).contains("not a valid binary"))
        assertTrue(baseErr("G", 16).contains("hexadecimal"))
        assertEquals("Division by zero", baseErr("5 / 0", 10))
        assertTrue(baseErr("1FFFFFFFFF", 16).contains("does not fit"))
        assertTrue(baseErr("5 +", 10).isNotEmpty())
        assertTrue(baseErr("(1", 10).contains("parenthesis"))
        assertTrue(baseErr("5 $ 3", 10).contains("Unexpected character"))
        assertTrue(baseErr("", 10).contains("Nothing"))
    }

    // ---------------------------------------------------------------- steps

    private fun steps(text: String, vars: Map<String, CalcValue> = emptyMap()): List<SolutionStep> =
        (StepSolver.solve(text, EvalContext(AngleMode.DEG, vars)) as StepsOutcome.Ok).steps

    @Test fun stepsFollowPrecedence() {
        val s = steps("2 + 3 * 4")
        assertEquals(listOf("Start", "Multiply: 3 × 4 = 12", "Add: 2 + 12 = 14", "Result"), s.map { it.description })
        assertEquals("2 + 12", s[1].expression)
    }

    @Test fun parenthesesFirstThenPowerThenMultiply() {
        val s = steps("(1+2)*(3+4)^2")
        assertEquals(listOf("Add: 1 + 2 = 3", "Add: 3 + 4 = 7", "Power: 7 ^ 2 = 49", "Multiply: 3 × 49 = 147"), s.drop(1).dropLast(1).map { it.description })
    }

    @Test fun rightAssociativePower() {
        assertEquals("Power: 3 ^ 2 = 9", steps("2^3^2")[1].description)
    }

    @Test fun negationFoldsIn() {
        val s = steps("-3^2")
        assertEquals(listOf("Start", "Power: 3 ^ 2 = 9", "Result"), s.map { it.description })
        assertEquals("-9", s.last().expression)
    }

    @Test fun percentKeepsItsMeaning() {
        val s = steps("100 + 10%")
        assertTrue(s[1].description.startsWith("Add the percentage"))
        assertEquals("110", s.last().expression)
    }

    @Test fun variablesAreSubstitutedFirst() {
        val s = steps("3x + 2", mapOf("x" to CalcValue.ofLong(5)))
        assertEquals("Substitute x = 5", s[1].description)
        assertEquals("3 × 5 + 2", s[1].expression)
    }

    @Test fun functionsAndFractions() {
        val s = steps("sin(30) + 1/3")
        assertEquals("Evaluate sin: sin(30) = 0.5", s[1].description)
        assertTrue(s.any { it.description.contains("1/3") })
        assertEquals("Result", s.last().description)
    }

    @Test fun leftToRightDivision() {
        assertEquals("Divide: 8 ÷ 4 = 2", steps("8/4/2")[1].description)
    }

    @Test fun lastStepMatchesTheAnswer() {
        for (e in listOf("2+3*4", "(7-2)^2/5", "sqrt(16)+2^3", "5!/(3!*2!)", "2(3+4)", "10 mod 3 + 1")) {
            val r = (Calculator.evaluate(e) as EvalOutcome.Success).result
            val out = StepSolver.solve(e) as StepsOutcome.Ok
            assertEquals(e, r.displayWithUnit, out.steps.last().expression)
        }
    }

    @Test fun plainNumberAndConversionAndChain() {
        assertEquals(listOf("Result"), steps("7").map { it.description })
        assertEquals("Result", steps("12 km to mi").last().description)
        val chain = steps("A=2 : A*3")
        assertEquals("Result", chain.last().description)
        assertEquals("6", chain.last().expression)
    }

    @Test fun errorsPassThrough() {
        val o = StepSolver.solve("1/0") as StepsOutcome.Err
        assertEquals(ErrorKind.DIVISION_BY_ZERO, o.error.kind)
    }
}

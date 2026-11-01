package com.tapecalc.core.math

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ErrorMessagesTest {

    private fun errorOf(expr: String, ctx: EvalContext = EvalContext()): CalcError {
        val outcome = Calculator.evaluate(expr, ctx)
        assertTrue("'$expr' should fail but gave $outcome", outcome is EvalOutcome.Failure)
        return (outcome as EvalOutcome.Failure).error
    }

    // ------------------------------------------------------------ unknown names

    @Test
    fun misspelledFunctionGetsASuggestion() {
        val e = errorOf("sinn(30)")
        assertEquals(ErrorKind.UNKNOWN_NAME, e.kind)
        assertEquals("sin", e.suggestion)
        assertEquals("Unknown name 'sinn'. Did you mean 'sin'?", e.message)
        assertEquals(0, e.position)
    }

    @Test
    fun misspelledConstantGetsASuggestion() {
        assertEquals("ans", errorOf("anss + 1").suggestion)
        assertEquals("phi", errorOf("phii * 2").suggestion)
    }

    @Test
    fun misspelledVariableGetsASuggestion() {
        val ctx = EvalContext(variables = mapOf("rate" to CalcValue.parseLiteral("0.18")))
        val e = errorOf("200 * rat", ctx)
        assertEquals(ErrorKind.UNKNOWN_NAME, e.kind)
        assertEquals("rate", e.suggestion)
        assertTrue(e.message.contains("Did you mean 'rate'?"))
    }

    @Test
    fun unrelatedUnknownNameHasNoSuggestion() {
        val e = errorOf("foo + 1")
        assertEquals(ErrorKind.UNKNOWN_NAME, e.kind)
        assertEquals("Unknown name 'foo'", e.message)
        assertNull(e.suggestion)
    }

    @Test
    fun singleLetterNamesNeverGetSuggestions() {
        assertNull(errorOf("x + 1").suggestion)
        assertNull(errorOf("2z").suggestion)
    }

    // ------------------------------------------------------------ units

    @Test
    fun misspelledUnitGetsASuggestion() {
        val e = errorOf("5 kg to pund")
        assertEquals(ErrorKind.UNKNOWN_UNIT, e.kind)
        assertEquals("pound", e.suggestion)
        assertEquals("Unknown unit 'pund'. Did you mean 'pound'?", e.message)
    }

    @Test
    fun unknownSourceUnitIsNamed() {
        val e = errorOf("5 kgg to lb")
        assertEquals(ErrorKind.UNKNOWN_UNIT, e.kind)
        assertEquals("kg", e.suggestion)
        assertTrue(e.message.startsWith("Unknown unit 'kgg'"))
    }

    @Test
    fun completelyUnknownUnitShowsAnExample() {
        val e = errorOf("5 kg to zorp")
        assertEquals(ErrorKind.UNKNOWN_UNIT, e.kind)
        assertEquals("Unknown unit 'zorp'. Example: 12 km to mi", e.message)
        assertNull(e.suggestion)
    }

    @Test
    fun missingUnitBeforeToStaysGeneric() {
        val e = errorOf("5 to km")
        assertEquals(ErrorKind.UNKNOWN_UNIT, e.kind)
        assertEquals("Unknown unit in conversion. Example: 12 km to mi", e.message)
    }

    // ------------------------------------------------------------ syntax

    @Test
    fun danglingOperatorNamesTheOperator() {
        assertEquals("'+' needs a number after it", errorOf("5 +").message)
        assertEquals("'*' needs a number after it", errorOf("5 ×").message)
        assertEquals("'/' needs a number after it", errorOf("8 ÷").message)
        assertEquals("'^' needs a number after it", errorOf("2^").message)
        assertEquals("'-' needs a number after it", errorOf("9-").message)
        assertEquals(3, errorOf("5 +").position)
    }

    @Test
    fun operatorWithoutLeftNumberIsExplained() {
        val e = errorOf("2+*3")
        assertEquals(ErrorKind.SYNTAX, e.kind)
        assertEquals("'*' needs a number before it", e.message)
        assertEquals(2, e.position)
        assertEquals("'/' needs a number before it", errorOf("/5").message)
        assertEquals("'^' needs a number before it", errorOf("^2").message)
    }

    @Test
    fun emptyParenthesesAreExplained() {
        assertEquals("Nothing inside the parentheses", errorOf("()").message)
        assertEquals("Nothing inside the parentheses", errorOf("3(").message)
        assertEquals("Nothing inside the parentheses", errorOf("sin(").message)
    }

    @Test
    fun missingValueAfterEqualsOrComma() {
        assertEquals("A value is missing after '='", errorOf("x=").message)
        assertEquals("A value is missing after the comma", errorOf("max(1,").message)
    }

    @Test
    fun parenthesisErrorsKeepTheirKinds() {
        assertEquals(ErrorKind.UNBALANCED_PARENTHESES, errorOf("(1+2").kind)
        assertEquals(ErrorKind.UNBALANCED_PARENTHESES, errorOf("1+2)").kind)
    }

    // ------------------------------------------------------------ arguments

    @Test
    fun wrongArgumentCountSaysHowManyWereNeeded() {
        assertEquals("sin needs 1 value but got 0", errorOf("sin()").message)
        assertEquals("sin needs 1 value but got 2", errorOf("sin(1,2)").message)
        assertEquals("root needs 2 values but got 1", errorOf("root(8)").message)
        assertEquals("log needs 1 to 2 values but got 3", errorOf("log(1,2,3)").message)
        assertEquals("min needs at least 1 value but got 0", errorOf("min()").message)
        assertEquals(ErrorKind.INVALID_ARGUMENT, errorOf("sin(1,2)").kind)
    }

    // ------------------------------------------------------------ unchanged behaviour

    @Test
    fun existingErrorKindsAreUnchanged() {
        assertEquals(ErrorKind.DIVISION_BY_ZERO, errorOf("1/0").kind)
        assertEquals(ErrorKind.DOMAIN, errorOf("log(-1)").kind)
        assertEquals(ErrorKind.EMPTY, errorOf("   ").kind)
        assertEquals(ErrorKind.SYNTAX, errorOf("2 3").kind)
        assertEquals(ErrorKind.UNIT_MISMATCH, errorOf("5 kg to km").kind)
    }
}

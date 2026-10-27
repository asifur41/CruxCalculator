package com.tapecalc.pro

import com.tapecalc.core.math.Calculator
import com.tapecalc.core.math.EvalContext
import com.tapecalc.core.math.EvalOutcome
import com.tapecalc.pro.presentation.InputEditor
import org.junit.Assert.assertEquals
import org.junit.Test

class InputEditorTest {

    private fun type(vararg tokens: String): String {
        var text = ""
        for (t in tokens) text = InputEditor.insert(text, t, afterResult = false)
        return text
    }

    private fun value(text: String): String {
        val outcome = Calculator.evaluate(Calculator.autoClose(text), EvalContext())
        return (outcome as EvalOutcome.Success).result.display
    }

    @Test
    fun typingDigitsAndOperators() {
        assertEquals("12+3", type("1", "2", "+", "3"))
        assertEquals("5", value(type("2", "+", "3")))
        assertEquals("14", value(type("2", "+", "3", "×", "4")))
    }

    @Test
    fun operatorReplacesPreviousOperator() {
        assertEquals("5×", type("5", "+", "×"))
        assertEquals("5×−", type("5", "×", "−"))
        assertEquals("", type("×"))
        assertEquals("−", type("−"))
        assertEquals("5+", type("5", "×", "+"))
        assertEquals("5−", type("5", "+", "−"))
    }

    @Test
    fun decimalPointRules() {
        assertEquals("0.", type("."))
        assertEquals("1.5", type("1", ".", "5"))
        assertEquals("1.5", type("1", ".", "5", "."))
        assertEquals("1+0.", type("1", "+", "."))
        assertEquals("1.5+2.", type("1", ".", "5", "+", "2", "."))
    }

    @Test
    fun parenthesesAreBalancedByTheCloseKey() {
        assertEquals("(1+2)", type("(", "1", "+", "2", ")"))
        assertEquals("1", type("1", ")"))
        assertEquals("(", type("(", ")"))
        assertEquals("3", value(type("(", "1", "+", "2")))
    }

    @Test
    fun implicitMultiplicationIsAvoidedForAdjacentLetters() {
        assertEquals("pi 2", type("pi", "2"))
        assertEquals("2 e", type("2", "e"))
        assertEquals("ans 5", type("ans", "5"))
        assertEquals("sin(30)", type("sin(", "3", "0", ")"))
    }

    @Test
    fun resultContinuationUsesAns() {
        assertEquals("ans+", InputEditor.insert("2+2", "+", afterResult = true))
        assertEquals("7", InputEditor.insert("2+2", "7", afterResult = true))
        assertEquals("sin(", InputEditor.insert("2+2", "sin(", afterResult = true))
        assertEquals("ans^2", InputEditor.insert("5", "^2", afterResult = true))
        assertEquals("ans%", InputEditor.insert("5", "%", afterResult = true))
    }

    @Test
    fun postfixOperatorsNeedAnOperand() {
        assertEquals("", type("%"))
        assertEquals("5%", type("5", "%"))
        assertEquals("5!", type("5", "!"))
        assertEquals("(", type("(", "!"))
        assertEquals("3^2", type("3", "^2"))
    }

    @Test
    fun commaOnlyInsideFunctionCalls() {
        assertEquals("", type(","))
        assertEquals("5", type("5", ","))
        assertEquals("log(8, 2)", type("log(", "8", ",", "2", ")"))
        assertEquals("3", value(type("log(", "8", ",", "2", ")")))
    }

    @Test
    fun moduloKeyword() {
        assertEquals("7 mod 3", type("7", " mod ", "3"))
        assertEquals("1", value("7 mod 3"))
        assertEquals("7 mod ", type("7", " mod "))
    }

    @Test
    fun backspaceRemovesWholeFunctionNames() {
        assertEquals("2+", InputEditor.backspace("2+sin("))
        assertEquals("2+", InputEditor.backspace("2+asin("))
        assertEquals("7", InputEditor.backspace("7 mod "))
        assertEquals("12", InputEditor.backspace("123"))
        assertEquals("", InputEditor.backspace(""))
        assertEquals("(1+", InputEditor.backspace("(1+2"))
    }

    @Test
    fun toggleSign() {
        assertEquals("−", InputEditor.toggleSign(""))
        assertEquals("−5", InputEditor.toggleSign("5"))
        assertEquals("5", InputEditor.toggleSign("−5"))
        assertEquals("2+−3", InputEditor.toggleSign("2+3"))
        assertEquals("2+3", InputEditor.toggleSign("2+−3"))
        assertEquals("−(2+3)", InputEditor.toggleSign("(2+3)"))
        assertEquals("(2+3)", InputEditor.toggleSign("−(2+3)"))
        assertEquals("−(pi)", InputEditor.toggleSign("pi"))
        assertEquals("(pi)", InputEditor.toggleSign("−(pi)"))
    }

    @Test
    fun lengthIsCapped() {
        var text = ""
        repeat(InputEditor.MAX_LENGTH + 50) { text = InputEditor.insert(text, "7", false) }
        assertEquals(InputEditor.MAX_LENGTH, text.length)
    }
}

package com.tapecalc.pro

import com.tapecalc.core.math.Calculator
import com.tapecalc.core.math.EvalOutcome
import com.tapecalc.core.ui.KbAction
import com.tapecalc.core.ui.KbRow
import com.tapecalc.core.ui.KbStyle
import com.tapecalc.core.ui.KeyboardPage
import com.tapecalc.core.ui.MathKeyboardKind
import com.tapecalc.core.ui.MathKeyboardLogic
import com.tapecalc.core.ui.ShiftState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The optional built-in keyboard: its key tables and the editing logic behind its keys. No Android code. */
class MathKeyboardLogicTest {

    private fun allRows(kind: MathKeyboardKind, shift: ShiftState = ShiftState.OFF, multiline: Boolean = false, compact: Boolean = false): List<KbRow> =
        MathKeyboardLogic.pages(kind).flatMap { MathKeyboardLogic.rows(it, kind, shift, multiline, compact) }

    private fun inserts(rows: List<KbRow>): List<String> =
        rows.flatMap { it.keys }.mapNotNull { (it.action as? KbAction.Insert)?.text }

    // ---------------------------------------------------------------- pages

    @Test fun expressionBoxesStartOnNumbersAndPlainTextStartsOnLetters() {
        assertEquals(KeyboardPage.NUMBERS, MathKeyboardLogic.defaultPage(MathKeyboardKind.EXPRESSION))
        assertEquals(KeyboardPage.NUMBERS, MathKeyboardLogic.defaultPage(MathKeyboardKind.FORMULA))
        assertEquals(KeyboardPage.LETTERS, MathKeyboardLogic.defaultPage(MathKeyboardKind.PLAIN_TEXT))
    }

    @Test fun plainTextHasNoFunctionsPage() {
        assertFalse(KeyboardPage.FUNCTIONS in MathKeyboardLogic.pages(MathKeyboardKind.PLAIN_TEXT))
        assertTrue(KeyboardPage.FUNCTIONS in MathKeyboardLogic.pages(MathKeyboardKind.EXPRESSION))
    }

    @Test fun everyPageOfEveryKindHasKeys() {
        for (kind in MathKeyboardKind.entries) for (page in MathKeyboardLogic.pages(kind)) {
            for (compact in listOf(false, true)) {
                val rows = MathKeyboardLogic.rows(page, kind, compact = compact)
                assertTrue("$kind $page is empty", rows.isNotEmpty() && rows.all { it.keys.isNotEmpty() })
            }
        }
    }

    @Test fun everyKeyIsLabelledForTalkBack() {
        for (kind in MathKeyboardKind.entries) {
            val keys = allRows(kind, multiline = true).flatMap { it.keys } + MathKeyboardLogic.stripKeys
            for (k in keys) if (k.style != KbStyle.SPACER) {
                assertTrue("key '${k.label}' has no description", k.description.isNotBlank())
                assertTrue("key '${k.label}' has no label", k.label.isNotBlank())
            }
        }
    }

    @Test fun everyKeyHasAWeightThatFits() {
        for (kind in MathKeyboardKind.entries) for (compact in listOf(false, true)) for (multi in listOf(false, true)) {
            for (row in allRows(kind, multiline = multi, compact = compact)) {
                assertTrue(row.keys.all { it.weight > 0f })
            }
        }
    }

    // ---------------------------------------------------------------- everything a calculator box needs is reachable

    @Test fun everyDigitAndTheBasicSignsAreOnTheNumbersPage() {
        val texts = inserts(MathKeyboardLogic.rows(KeyboardPage.NUMBERS, MathKeyboardKind.EXPRESSION))
        for (d in "0123456789") assertTrue("missing $d", d.toString() in texts)
        for (s in listOf("+", "-", "*", "/", "^", "%", "(", ")", ".", ",", "=", "pi", " ")) assertTrue("missing '$s'", s in texts)
    }

    @Test fun everyLetterIsOnTheLettersPageInBothLayouts() {
        for (compact in listOf(false, true)) {
            val lower = inserts(MathKeyboardLogic.rows(KeyboardPage.LETTERS, MathKeyboardKind.EXPRESSION, ShiftState.OFF, compact = compact))
            val upper = inserts(MathKeyboardLogic.rows(KeyboardPage.LETTERS, MathKeyboardKind.EXPRESSION, ShiftState.ONCE, compact = compact))
            for (c in 'a'..'z') {
                assertTrue("missing $c (compact=$compact)", c.toString() in lower)
                assertTrue("missing ${c.uppercaseChar()} (compact=$compact)", c.uppercaseChar().toString() in upper)
            }
            assertFalse(lower.any { it.length == 1 && it[0].isUpperCase() })
        }
    }

    @Test fun cellNamesCanBeTypedOnTheLettersPage() {
        // A1 needs a capital letter and a digit without leaving the page
        val keys = inserts(MathKeyboardLogic.rows(KeyboardPage.LETTERS, MathKeyboardKind.FORMULA, ShiftState.ONCE))
        assertTrue("A" in keys && "1" in keys)
    }

    @Test fun everyEngineFunctionAndConstantHasAKey() {
        val needed = listOf(
            "sin(", "cos(", "tan(", "asin(", "acos(", "atan(", "sinh(", "cosh(", "tanh(", "asinh(", "acosh(", "atanh(",
            "log(", "ln(", "log2(", "exp(", "sqrt(", "cbrt(", "root(", "abs(", "!", "npr(", "ncr(", "mod(", "gcd(", "lcm(",
            "floor(", "ceil(", "round(", "sign(", "hypot(", "atan2(", "pol(", "rec(", "ans", "e", "phi", "pi", "°"
        )
        val have = inserts(allRows(MathKeyboardKind.EXPRESSION)).toSet()
        for (n in needed) assertTrue("no key types '$n'", n in have)
    }

    @Test fun spreadsheetBoxesGetRangeKeys() {
        val formula = inserts(allRows(MathKeyboardKind.FORMULA)).toSet()
        for (n in listOf("sum(", "mean(", "min(", "max(", "count(", ":")) assertTrue("missing '$n'", n in formula)
        val expression = inserts(allRows(MathKeyboardKind.EXPRESSION)).toSet()
        assertFalse("sum( belongs to cells only", "sum(" in expression)
        assertFalse("count( belongs to cells only", "count(" in expression)
    }

    @Test fun plainTextHasPunctuationAndPaste() {
        val rows = MathKeyboardLogic.pages(MathKeyboardKind.PLAIN_TEXT).flatMap { MathKeyboardLogic.rows(it, MathKeyboardKind.PLAIN_TEXT) }
        val texts = inserts(rows)
        for (s in listOf(".", ",", "!", "?", "-", "_", "@", "#")) assertTrue("missing '$s'", s in texts)
        assertTrue(rows.flatMap { it.keys }.any { it.action == KbAction.Paste })
    }

    @Test fun pasteAndClearAreReachableInEveryKind() {
        for (kind in MathKeyboardKind.entries) {
            val keys = allRows(kind).flatMap { it.keys }
            assertTrue("$kind has no Paste", keys.any { it.action == KbAction.Paste })
            assertTrue("$kind has no Clear", keys.any { it.action == KbAction.Clear })
        }
    }

    @Test fun cursorDeleteAndHideStayOnEveryPage() {
        val actions = MathKeyboardLogic.stripKeys.map { it.action }
        assertTrue(KbAction.CursorLeft in actions && KbAction.CursorRight in actions)
        assertTrue(KbAction.Backspace in actions && KbAction.Hide in actions)
    }

    @Test fun newLineKeyOnlyInMultilineBoxes() {
        assertFalse("\n" in inserts(allRows(MathKeyboardKind.EXPRESSION, multiline = false)))
        assertTrue("\n" in inserts(allRows(MathKeyboardKind.EXPRESSION, multiline = true)))
    }

    // ---------------------------------------------------------------- the keys really work in the calculator

    private fun value(expr: String): String {
        val outcome = Calculator.evaluate(expr)
        if (outcome is EvalOutcome.Failure) throw AssertionError("'$expr' failed: ${outcome.error}")
        return (outcome as EvalOutcome.Success).result.display
    }

    /** Types keys one after another into an empty box, exactly as the keyboard would, and returns the text. */
    private fun typed(vararg parts: String): String {
        var text = ""
        var caret = 0
        for (p in parts) {
            val e = MathKeyboardLogic.insert(text, caret, caret, p)
            text = e.text
            caret = e.caret
        }
        return text
    }

    @Test fun textTypedFromTheNumbersPageCalculates() {
        assertEquals("14", value(typed("2", "+", "3", "*", "4")))
        assertEquals("2.5", value(typed("5", "/", "2")))
        assertEquals("8", value(typed("2", "^", "3")))
        assertEquals("10", value(typed("(", "2", "+", "3", ")", "*", "2")))
        assertEquals("1", value(typed("(", "2", "+", "3", ")", "-", "4")))
    }

    @Test fun everyFunctionKeyTextIsKnownToTheCalculator() {
        // each function key, with a sample argument, must be accepted by the engine (no "unknown name")
        val samples = mapOf(
            "asinh(" to "0.5", "acosh(" to "2", "atanh(" to "0.5", "log(" to "100", "ln(" to "10", "log2(" to "8",
            "sqrt(" to "16", "cbrt(" to "27", "abs(" to "-4", "floor(" to "2.5", "ceil(" to "2.5", "round(" to "2.5", "sign(" to "-2",
            "exp(" to "1", "sin(" to "0", "cos(" to "0", "tan(" to "0", "asin(" to "0", "acos(" to "1", "atan(" to "0",
            "sinh(" to "0", "cosh(" to "0", "tanh(" to "0"
        )
        for ((fn, arg) in samples) {
            assertTrue("no key for $fn", fn in MathKeyboardLogic.functionInsertTexts())
            assertTrue("$fn$arg) was rejected", Calculator.evaluate(typed(fn, arg, ")")) is EvalOutcome.Success)
        }
        // functions that need two numbers
        for ((fn, args) in mapOf("root(" to "27,3", "npr(" to "5,2", "ncr(" to "5,2", "mod(" to "7,3", "gcd(" to "12,18", "lcm(" to "4,6", "hypot(" to "3,4", "atan2(" to "1,1")) {
            assertTrue("$fn$args) was rejected", Calculator.evaluate(typed(fn, args, ")")) is EvalOutcome.Success)
        }
        assertEquals("120", value(typed("5", "!")))
        assertEquals("4", value(typed("sqrt(", "1", "6", ")")))
        assertEquals("25", value(typed("5", "^2")))
        assertEquals("0.2", value(typed("5", "^(-1)")))
    }

    @Test fun cellFunctionsAreKnownToTheCalculatorEngineToo() {
        // sum( and friends are spreadsheet functions; the engine's own sum needs four arguments, so only the others are checked here
        assertEquals("3", value(typed("min(", "3", ",", "9", ")")))
        assertEquals("9", value(typed("max(", "3", ",", "9", ")")))
    }

    @Test fun constantsTypedFromTheKeyboardCalculate() {
        assertTrue(value(typed("2", "*", "pi")).startsWith("6.28318"))
        assertTrue(value(typed("e")).startsWith("2.71828"))
        assertTrue(value(typed("phi")).startsWith("1.61803"))
    }

    // ---------------------------------------------------------------- inserting

    @Test fun insertGoesAtTheCursor() {
        assertEquals(MathKeyboardLogic.Edit("a+b", 2), MathKeyboardLogic.insert("ab", 1, 1, "+"))
        assertEquals(MathKeyboardLogic.Edit("sin(", 4), MathKeyboardLogic.insert("", 0, 0, "sin("))
    }

    @Test fun insertReplacesTheSelection() {
        assertEquals(MathKeyboardLogic.Edit("a+d", 2), MathKeyboardLogic.insert("abcd", 1, 3, "+"))
        assertEquals(MathKeyboardLogic.Edit("a+d", 2), MathKeyboardLogic.insert("abcd", 3, 1, "+"))
    }

    @Test fun insertWithTheCursorOutOfRangeDoesNotCrash() {
        val e = MathKeyboardLogic.insert("ab", 9, 9, "+")
        assertEquals("ab+", e.text)
        assertEquals(3, e.caret)
    }

    // ---------------------------------------------------------------- delete

    @Test fun deleteRemovesOneCharacter() {
        assertEquals(MathKeyboardLogic.Edit("12", 2), MathKeyboardLogic.backspace("123", 3, 3))
        assertEquals(MathKeyboardLogic.Edit("13", 1), MathKeyboardLogic.backspace("123", 2, 2))
    }

    @Test fun deleteAtTheStartDoesNothing() {
        assertEquals(MathKeyboardLogic.Edit("123", 0), MathKeyboardLogic.backspace("123", 0, 0))
        assertEquals(MathKeyboardLogic.Edit("", 0), MathKeyboardLogic.backspace("", 0, 0))
    }

    @Test fun deleteRemovesTheSelection() {
        assertEquals(MathKeyboardLogic.Edit("14", 1), MathKeyboardLogic.backspace("1234", 1, 3))
        assertEquals(MathKeyboardLogic.Edit("14", 1), MathKeyboardLogic.backspace("1234", 3, 1))
        assertEquals(MathKeyboardLogic.Edit("", 0), MathKeyboardLogic.backspace("1234", 0, 4))
    }

    @Test fun deleteRemovesAWholeFunction() {
        assertEquals(MathKeyboardLogic.Edit("2+", 2), MathKeyboardLogic.backspace("2+sin(", 6, 6))
        assertEquals(MathKeyboardLogic.Edit("2+", 2), MathKeyboardLogic.backspace("2+asin(", 7, 7))
        assertEquals(MathKeyboardLogic.Edit("", 0), MathKeyboardLogic.backspace("asinh(", 6, 6))
        assertEquals(MathKeyboardLogic.Edit("", 0), MathKeyboardLogic.backspace("sqrt(", 5, 5))
    }

    @Test fun deleteInsideAFunctionOnlyRemovesOneCharacter() {
        // the cursor is after the 5 of sin(5, not right after the bracket
        assertEquals(MathKeyboardLogic.Edit("sin(", 4), MathKeyboardLogic.backspace("sin(5", 5, 5))
        // and after a closing bracket
        assertEquals(MathKeyboardLogic.Edit("sin(5", 5), MathKeyboardLogic.backspace("sin(5)", 6, 6))
    }

    @Test fun deleteDoesNotEatTheTailOfALongerWord() {
        // "xsin(" is a variable followed by sin(, but "cosin(" ends in the letters sin( of a longer word, so one character goes
        assertEquals(MathKeyboardLogic.Edit("cosin", 5), MathKeyboardLogic.backspace("cosin(", 6, 6))
    }

    @Test fun deleteInTheMiddleOfTheTextWorks() {
        // the cursor sits right after "sin(" with a 3 still to its right: the function goes, the 3 stays
        assertEquals(MathKeyboardLogic.Edit("2+3", 2), MathKeyboardLogic.backspace("2+sin(3", 6, 6))
    }

    @Test fun deleteTakesAWholeEmojiNotHalfOfIt() {
        val text = "a😀"
        assertEquals(MathKeyboardLogic.Edit("a", 1), MathKeyboardLogic.backspace(text, 3, 3))
    }

    // ---------------------------------------------------------------- cursor

    @Test fun arrowsMoveTheCursorAndStayInsideTheText() {
        assertEquals(1, MathKeyboardLogic.caretLeft("abc", 2, 2))
        assertEquals(0, MathKeyboardLogic.caretLeft("abc", 0, 0))
        assertEquals(3, MathKeyboardLogic.caretRight("abc", 2, 2))
        assertEquals(3, MathKeyboardLogic.caretRight("abc", 3, 3))
    }

    @Test fun arrowsCollapseASelectionToItsEdge() {
        assertEquals(1, MathKeyboardLogic.caretLeft("abcd", 1, 3))
        assertEquals(3, MathKeyboardLogic.caretRight("abcd", 1, 3))
        assertEquals(1, MathKeyboardLogic.caretLeft("abcd", 3, 1))
        assertEquals(3, MathKeyboardLogic.caretRight("abcd", 3, 1))
    }

    // ---------------------------------------------------------------- shift

    @Test fun shiftCyclesOffOnceLock() {
        assertEquals(ShiftState.ONCE, ShiftState.OFF.next())
        assertEquals(ShiftState.LOCK, ShiftState.ONCE.next())
        assertEquals(ShiftState.OFF, ShiftState.LOCK.next())
    }

    @Test fun oneCapitalLetterUsesUpShiftButCapsLockStays() {
        val a = KbAction.Insert("A")
        assertEquals(ShiftState.OFF, MathKeyboardLogic.nextShift(ShiftState.ONCE, a, KeyboardPage.LETTERS))
        assertEquals(ShiftState.LOCK, MathKeyboardLogic.nextShift(ShiftState.LOCK, a, KeyboardPage.LETTERS))
        assertEquals(ShiftState.OFF, MathKeyboardLogic.nextShift(ShiftState.OFF, a, KeyboardPage.LETTERS))
    }

    @Test fun digitsAndSignsDoNotUseUpShift() {
        assertEquals(ShiftState.ONCE, MathKeyboardLogic.nextShift(ShiftState.ONCE, KbAction.Insert("1"), KeyboardPage.LETTERS))
        assertEquals(ShiftState.ONCE, MathKeyboardLogic.nextShift(ShiftState.ONCE, KbAction.Insert("."), KeyboardPage.LETTERS))
        assertEquals(ShiftState.ONCE, MathKeyboardLogic.nextShift(ShiftState.ONCE, KbAction.Backspace, KeyboardPage.LETTERS))
    }

    @Test fun theShiftKeyItselfCycles() {
        assertEquals(ShiftState.ONCE, MathKeyboardLogic.nextShift(ShiftState.OFF, KbAction.Shift, KeyboardPage.LETTERS))
        assertEquals(ShiftState.LOCK, MathKeyboardLogic.nextShift(ShiftState.ONCE, KbAction.Shift, KeyboardPage.LETTERS))
    }

    @Test fun shiftKeyShowsItsState() {
        fun shiftKey(s: ShiftState) = MathKeyboardLogic.rows(KeyboardPage.LETTERS, MathKeyboardKind.EXPRESSION, s)
            .flatMap { it.keys }.first { it.action == KbAction.Shift }
        assertFalse(shiftKey(ShiftState.OFF).active)
        assertTrue(shiftKey(ShiftState.ONCE).active)
        assertTrue(shiftKey(ShiftState.LOCK).active)
        assertTrue(shiftKey(ShiftState.LOCK).description.contains("Caps lock"))
    }

    // ---------------------------------------------------------------- paste

    @Test fun pasteInAOneLineBoxTurnsNewLinesIntoSpaces() {
        assertEquals("1 2 3", MathKeyboardLogic.cleanPaste("1\n2\r\n3", multiline = false))
        assertEquals("1 2", MathKeyboardLogic.cleanPaste("1\r2", multiline = false))
    }

    @Test fun pasteInAMultilineBoxKeepsLineBreaks() {
        assertEquals("1\n2\n3", MathKeyboardLogic.cleanPaste("1\r\n2\r3", multiline = true))
    }

    @Test fun pasteOfNothingIsIgnored() {
        assertNull(MathKeyboardLogic.cleanPaste(null, multiline = false))
        assertNull(MathKeyboardLogic.cleanPaste("", multiline = true))
    }
}

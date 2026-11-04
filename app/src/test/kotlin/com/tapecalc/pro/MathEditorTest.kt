package com.tapecalc.pro

import com.tapecalc.core.math.editor.Mk
import com.tapecalc.pro.presentation.EditorState
import com.tapecalc.pro.presentation.MathEditor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MathEditorTest {

    private val F = Mk.FRAC
    private val S = Mk.SEP
    private val E = Mk.END

    private fun type(vararg tokens: String): EditorState {
        var s = EditorState()
        for (t in tokens) s = MathEditor.insert(s, t, afterResult = false)
        return s
    }

    private fun shown(s: EditorState): String =
        (s.text.substring(0, s.cursor) + "|" + s.text.substring(s.cursor))
            .replace(F, '⟨').replace(S, '¦').replace(E, '⟩').replace(Mk.LOGB, '⟦').replace(Mk.INTEG, '∫')

    // ------------------------------------------------------------------ typing at the cursor

    @Test
    fun typingAtTheEndMatchesTheOldBehaviour() {
        val s = type("1", "2", "+", "3")
        assertEquals("12+3", s.text)
        assertEquals(4, s.cursor)
    }

    @Test
    fun typingInTheMiddleInsertsThere() {
        var s = type("1", "2", "3", "4")
        s = MathEditor.moveLeft(MathEditor.moveLeft(s))
        assertEquals("12|34", shown(s))
        s = MathEditor.insert(s, "5", false)
        assertEquals("125|34", shown(s))
        assertEquals("12534", s.text)
    }

    @Test
    fun editingAMistakeWithoutDeletingEverything() {
        // 2+3×4 typed as 2+9×4; fix the 9 into 3
        var s = type("2", "+", "9", "×", "4")
        s = MathEditor.moveLeft(MathEditor.moveLeft(s)) // before ×
        s = MathEditor.backspace(s)                      // deletes 9
        assertEquals("2+|×4", shown(s))
        s = MathEditor.insert(s, "3", false)
        assertEquals("2+3×4", s.text)
        assertEquals(3, s.cursor)
    }

    @Test
    fun backspaceRemovesWholeFunctionNames() {
        var s = type("sin(", "3", "0", ")")
        s = MathEditor.moveLeft(MathEditor.moveLeft(MathEditor.moveLeft(s))) // sin(|30)
        assertEquals("sin(|30)", shown(s))
        s = MathEditor.backspace(s)
        assertEquals("|30)", shown(s))
    }

    @Test
    fun movingStepsOverWholeFunctionNamesAndWords() {
        var s = type("sin(", "3", ")", "+", "ans")
        s = MathEditor.moveLeft(s)
        assertEquals("sin(3)+|ans", shown(s))
        s = MathEditor.moveLeft(s)
        assertEquals("sin(3)|+ans", shown(s))
        s = MathEditor.moveLeft(MathEditor.moveLeft(s))
        assertEquals("sin(|3)+ans", shown(s))
        s = MathEditor.moveLeft(s)
        assertEquals("|sin(3)+ans", shown(s))
        assertEquals(s, MathEditor.moveLeft(s)) // cannot go past the start
        s = MathEditor.moveRight(s)
        assertEquals("sin(|3)+ans", shown(s))
        s = MathEditor.moveEnd(s)
        assertEquals(s, MathEditor.moveRight(s)) // cannot go past the end
    }

    @Test
    fun movingOverModTreatsItAsOneStep() {
        var s = type("6", " mod ", "4")
        s = MathEditor.moveLeft(s)
        assertEquals("6 mod |4", shown(s))
        s = MathEditor.moveLeft(s)
        assertEquals(1, s.cursor)
        s = MathEditor.moveRight(s)
        assertEquals("6 mod |4", shown(s))
    }

    @Test
    fun decimalPointRuleStillAppliesAtTheCursor() {
        var s = type("1", ".", "5", "+", "2")
        s = MathEditor.moveHome(s)
        s = MathEditor.insert(s, ".", false)
        assertEquals("0.|1.5+2", shown(s))
    }

    @Test
    fun maxLengthIsRespected() {
        var s = EditorState("1".repeat(300))
        s = MathEditor.insert(s, "2", false)
        assertEquals(300, s.text.length)
    }

    @Test
    fun afterAResultAnOperatorContinuesFromAns() {
        val s = MathEditor.insert(EditorState("2+3"), "×", afterResult = true)
        assertEquals("ans×", s.text)
        assertEquals(4, s.cursor)
        val fresh = MathEditor.insert(EditorState("2+3"), "7", afterResult = true)
        assertEquals("7", fresh.text)
    }

    // ------------------------------------------------------------------ templates

    @Test
    fun fractionKeyBuildsABoxAboveAndBelow() {
        val s = MathEditor.insertTemplate(EditorState(), Mk.FRAC, afterResult = false)
        assertEquals("$F$S$E", s.text)
        assertEquals(1, s.cursor) // in the numerator
    }

    @Test
    fun fractionTakesThePrecedingNumberAsNumerator() {
        var s = type("2", "+", "3")
        s = MathEditor.insertTemplate(s, Mk.FRAC, false)
        assertEquals("2+⟨3¦|⟩", shown(s)) // cursor in the denominator
        s = MathEditor.insert(s, "4", false)
        assertEquals("2+$F" + "3" + S + "4" + E, s.text)
    }

    @Test
    fun fractionWithNothingBeforeItHasEmptyBoxes() {
        val s = MathEditor.insertTemplate(type("2", "+"), Mk.FRAC, false)
        assertEquals("2+⟨|¦⟩", shown(s))
    }

    @Test
    fun typingInsideBoxesAndWalkingOutWithTheArrows() {
        var s = MathEditor.insertTemplate(EditorState(), Mk.FRAC, false)
        s = MathEditor.insert(s, "1", false)
        s = MathEditor.moveRight(s) // over SEP into the denominator
        s = MathEditor.insert(s, "2", false)
        assertEquals("⟨1¦2|⟩", shown(s))
        s = MathEditor.moveRight(s) // over END, out of the fraction
        assertEquals("⟨1¦2⟩|", shown(s))
        s = MathEditor.insert(s, "+", false)
        s = MathEditor.insert(s, "3", false)
        assertEquals("$F" + "1" + S + "2" + E + "+3", s.text)
    }

    @Test
    fun operatorRulesAreLocalToTheBox() {
        // a leading "×" is not allowed at the start of a box, a leading "−" is
        var s = MathEditor.insertTemplate(EditorState(), Mk.FRAC, false)
        s = MathEditor.insert(s, "×", false)
        assertEquals("$F$S$E", s.text)
        s = MathEditor.insert(s, "−", false)
        assertEquals("$F−$S$E", s.text)
    }

    @Test
    fun closeParenCannotUseAnOpenParenFromAnotherBox() {
        var s = MathEditor.insertTemplate(EditorState(), Mk.FRAC, false)
        s = MathEditor.insert(s, "(", false)
        s = MathEditor.insert(s, "1", false)
        s = MathEditor.moveRight(s) // denominator
        s = MathEditor.insert(s, "2", false)
        s = MathEditor.insert(s, ")", false)
        assertEquals("$F(1${S}2$E", s.text) // the ")" was refused in the denominator
    }

    @Test
    fun backspaceOnAnEmptyTemplateRemovesItAllAtOnce() {
        var s = type("5", "+")
        s = MathEditor.insertTemplate(s, Mk.FRAC, false)
        s = MathEditor.backspace(s) // cursor sits right after the opening marker
        assertEquals("5+|", shown(s))
    }

    @Test
    fun backspaceStepsIntoAFilledTemplateBeforeDeleting() {
        var s = MathEditor.insertTemplate(EditorState(), Mk.FRAC, false)
        s = MathEditor.insert(s, "1", false)
        s = MathEditor.moveRight(s)
        s = MathEditor.insert(s, "2", false)
        s = MathEditor.moveRight(s)                   // ⟨1¦2⟩|
        s = MathEditor.backspace(s)                   // steps inside: ⟨1¦2|⟩
        assertEquals("⟨1¦2|⟩", shown(s))
        s = MathEditor.backspace(s)                   // deletes the 2
        assertEquals("⟨1¦|⟩", shown(s))
        s = MathEditor.backspace(s)                   // at the start of box 2: move to end of box 1
        assertEquals("⟨1|¦⟩", shown(s))
    }

    @Test
    fun upAndDownJumpBetweenNumeratorAndDenominator() {
        var s = MathEditor.insertTemplate(EditorState(), Mk.FRAC, false)
        s = MathEditor.insert(s, "1", false)
        s = MathEditor.insert(s, "2", false)
        val down = MathEditor.moveVertical(s, up = false)
        assertNotNull(down)
        assertEquals("⟨12¦|⟩", shown(down!!))
        val back = MathEditor.moveVertical(down, up = true)
        assertNotNull(back)
        assertEquals(1, back!!.cursor) // the denominator is empty, so the cursor goes to the start of the numerator
        // nothing above the numerator, nothing below the denominator
        assertNull(MathEditor.moveVertical(s, up = true))
        assertNull(MathEditor.moveVertical(down, up = false))
    }

    @Test
    fun verticalMovementOutsideTemplatesIsNotHandled() {
        assertNull(MathEditor.moveVertical(type("1", "+", "2"), up = true))
    }

    @Test
    fun integralLimitsAreAboveAndBelowTheBody() {
        var s = MathEditor.insertTemplate(EditorState(), Mk.INTEG, false)
        s = MathEditor.insert(s, "x", false) // in the body
        val toLower = MathEditor.moveVertical(s, up = false)!!
        val lowerSlotStart = toLower.cursor
        val toUpper = MathEditor.moveVertical(s, up = true)!!
        assertTrue(toUpper.cursor > lowerSlotStart) // slot order is body, lower, upper in the text
    }

    @Test
    fun afterAResultAFractionStartsFromTheAnswer() {
        val s = MathEditor.insertTemplate(EditorState("2+3"), Mk.FRAC, afterResult = true)
        assertEquals("${F}ans$S$E", s.text)
        assertEquals(F.toString().length + 3 + 1, s.cursor) // in the denominator
    }

    @Test
    fun toggleSignWorksOnTheBoxTheCursorIsIn() {
        var s = type("5")
        s = MathEditor.toggleSign(s)
        assertEquals("−5", s.text)
        s = MathEditor.toggleSign(s)
        assertEquals("5", s.text)
    }

    @Test
    fun engineTextOfAnEditedLine() {
        var s = MathEditor.insertTemplate(EditorState(), Mk.FRAC, false)
        s = MathEditor.insert(s, "1", false)
        s = MathEditor.moveRight(s)
        s = MathEditor.insert(s, "4", false)
        val r = MathEditor.engineText(s)
        assertEquals("(1÷4)", r.text)
        assertFalse(r.hasBlank)
        assertTrue(MathEditor.engineText(MathEditor.insertTemplate(EditorState(), Mk.LOGB, false)).hasBlank)
    }

    @Test
    fun expKeyTypesTimesTenToThePower() {
        assertEquals("10^(", type(MathEditor.EXP).text)
        assertEquals("5×10^(", type("5", MathEditor.EXP).text)
        assertEquals("5+10^(", type("5", "+", MathEditor.EXP).text)
        assertEquals(6, type("5", MathEditor.EXP).cursor)
    }
}

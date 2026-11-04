package com.tapecalc.core.math.editor

import com.tapecalc.core.math.Calculator
import com.tapecalc.core.math.EvalContext
import com.tapecalc.core.math.EvalOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkupTest {

    private val F = Mk.FRAC
    private val S = Mk.SEP
    private val E = Mk.END

    private fun value(text: String): String {
        val engine = MathEngineText.convert(text)
        assertFalse("blank box in $text", engine.hasBlank)
        val outcome = Calculator.evaluate(engine.text, EvalContext())
        return (outcome as EvalOutcome.Success).result.display
    }

    @Test
    fun plainTextIsUnchanged() {
        val r = MathEngineText.convert("2+3×sin(30)")
        assertEquals("2+3×sin(30)", r.text)
        assertFalse(r.hasBlank)
    }

    @Test
    fun fractionBecomesDivision() {
        assertEquals("(1÷2)", MathEngineText.convert("${F}1${S}2$E").text)
        assertEquals("0.5", value("${F}1${S}2$E"))
        assertEquals("1.5", value("1+${F}1${S}2$E"))
    }

    @Test
    fun complexSlotsAreBracketed() {
        assertEquals("((1+2)÷(3×4))", MathEngineText.convert("${F}1+2${S}3×4$E").text)
        assertEquals("0.25", value("${F}1+2${S}3×4$E"))
    }

    @Test
    fun nestedTemplates() {
        // 1 / (2 / 4)
        assertEquals("2", value("$F" + "1" + S + F + "2" + S + "4" + E + E))
    }

    @Test
    fun implicitMultiplicationAroundTemplates() {
        assertEquals("(1÷2)×3", MathEngineText.convert("${F}1${S}2${E}3").text)
        assertEquals("1.5", value("${F}1${S}2${E}3"))
        assertEquals("2×(1÷2)", MathEngineText.convert("2${F}1${S}2$E").text)
        assertEquals("1", value("2${F}1${S}2$E"))
    }

    @Test
    fun logWithBase() {
        // log base 2 of 8 = 3
        assertEquals("log(8, 2)", MathEngineText.convert("${Mk.LOGB}2${S}8$E").text)
        assertEquals("3", value("${Mk.LOGB}2${S}8$E"))
    }

    @Test
    fun nthRoot() {
        // 3rd root of 27
        assertEquals("3", value("${Mk.NROOT}3${S}27$E"))
    }

    @Test
    fun integralSumProductDerivative() {
        // integral of x^2 from 0 to 3 = 9
        assertEquals("9", value("${Mk.INTEG}x^2${S}0${S}3$E"))
        // sum of x from 1 to 10 = 55
        assertEquals("55", value("${Mk.SUM}x${S}1${S}10$E"))
        // product of x from 1 to 5 = 120
        assertEquals("120", value("${Mk.PROD}x${S}1${S}5$E"))
        // derivative of x^2 at 3 = 6
        assertEquals("6", value("${Mk.DERIV}x^2${S}3$E"))
    }

    @Test
    fun mixedNumber() {
        // 1 and 3/4 = 1.75
        assertEquals("1.75", value("${Mk.MIXED}1${S}3${S}4$E"))
    }

    @Test
    fun emptyBoxIsReported() {
        assertTrue(MathEngineText.convert("${F}1${S}$E").hasBlank)
        assertTrue(MathEngineText.convert(Mk.emptyTemplate(Mk.INTEG)).hasBlank)
        assertFalse(MathEngineText.convert("${F}1${S}2$E").hasBlank)
    }

    @Test
    fun parseAlternatesRunsAndTemplates() {
        val nodes = MathTree.parse("1+${F}2${S}3${E}+4")
        assertEquals(3, nodes.size)
        assertEquals(RunNode("1+", 0), nodes[0])
        val t = nodes[1] as TemplateNode
        assertEquals(F, t.open)
        assertEquals(2, t.slots.size)
        assertEquals(2, t.openIndex)
        assertEquals(6, t.endIndex)
        assertEquals(RunNode("+4", 7), nodes[2])
    }

    @Test
    fun parseEmptyBoxesStillHaveARun() {
        val t = MathTree.parse(Mk.emptyTemplate(Mk.FRAC))[1] as TemplateNode
        assertTrue(t.slots[0].isEmpty)
        assertTrue(t.slots[1].isEmpty)
        assertEquals(1, t.slots[0].nodes.size)
    }

    @Test
    fun parseSurvivesBrokenMarkers() {
        // unterminated template and stray markers must never throw
        MathTree.parse("${F}1${S}2")
        MathTree.parse("1${E}2${S}3")
        MathTree.parse("$F$F$F")
        assertEquals("1+2", MathEngineText.convert("1+2").text)
    }

    @Test
    fun slotAtFindsInnermostBox() {
        val text = F + "1" + S + F + "2" + S + "3" + E + E // 1 / (2 / 3)
        val nodes = MathTree.parse(text)
        // cursor after the "2" of the inner numerator
        val (inner, slot) = MathTree.slotAt(nodes, 4)!!
        assertEquals(0, slot)
        assertEquals(3, inner.openIndex)
        // cursor after the "1": outer numerator
        val (outer, outerSlot) = MathTree.slotAt(nodes, 2)!!
        assertEquals(0, outerSlot)
        assertEquals(0, outer.openIndex)
        // cursor at the very start / end: outside
        assertNull(MathTree.slotAt(nodes, 0))
        assertNull(MathTree.slotAt(nodes, text.length))
    }
}

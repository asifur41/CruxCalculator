package com.tapecalc.core.math

import com.tapecalc.core.math.tools.ToolHistory
import com.tapecalc.core.math.tools.ToolInput
import com.tapecalc.core.math.tools.ToolResult
import com.tapecalc.core.math.tools.Tools
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What a Tools-tab calculation looks like when it is written on the tape. */
class ToolHistoryTest {

    private fun defaults(id: String): Triple<com.tapecalc.core.math.tools.ToolSpec, Map<String, String>, Map<String, Int>> {
        val spec = Tools.byId(id)!!
        return Triple(
            spec,
            spec.fields.associate { it.id to it.default },
            spec.options.associate { it.id to it.default }
        )
    }

    @Test fun singleBoxToolShowsTitleAndValue() {
        val (spec, texts, choices) = defaults("poly")
        assertEquals("Polynomial roots: 1, -3, 2", ToolHistory.inputText(spec, texts, choices))
    }

    @Test fun severalBoxesAreLabelled() {
        val (spec, texts, choices) = defaults("solve")
        val text = ToolHistory.inputText(spec, texts, choices)
        assertTrue(text, text.startsWith("Solve f(x) = 0: f(x): x^3 - 2x - 5; Starting guess: 1"))
    }

    @Test fun multiLineInputIsOneLine() {
        val (spec, texts, choices) = defaults("linear")
        val text = ToolHistory.inputText(spec, texts, choices)
        assertTrue(text, !text.contains('\n'))
        assertTrue(text, text.contains("2, 1, 5 / 1, -1, 1"))
    }

    @Test fun emptyBoxesAreLeftOut() {
        val (spec, _, choices) = defaults("solve")
        val text = ToolHistory.inputText(spec, mapOf("f" to "x-1", "guess" to ""), choices)
        assertEquals("Solve f(x) = 0: f(x): x-1", text)
    }

    @Test fun chosenChipAppearsInBrackets() {
        val spec = Tools.byId("calculus")!!
        val option = spec.options.first()
        val picked = option.choices.last()
        val text = ToolHistory.inputText(spec, emptyMap(), mapOf(option.id to option.choices.lastIndex))
        assertTrue(text, text.startsWith("${spec.title}: [$picked]"))
    }

    @Test fun successfulResultBecomesARecord() {
        val (spec, texts, choices) = defaults("poly")
        val result = spec.run(ToolInput(texts, choices, EvalContext()))
        val record = ToolHistory.record(spec, texts, choices, result)
        assertNotNull(record)
        record!!
        assertEquals("Polynomial roots: 1, -3, 2", record.input)
        assertEquals(2, record.summary.split("; ").size)
        assertEquals(2, record.full.lines().size)
        assertTrue(record.summary, record.summary.startsWith("x₁ = "))
    }

    @Test fun errorsAreNotSaved() {
        val (spec, texts, choices) = defaults("poly")
        assertNull(ToolHistory.record(spec, texts, choices, ToolResult.Err("nope")))
    }

    @Test fun everyToolHasARecordWithItsDefaults() {
        for (spec in Tools.all) {
            val (_, texts, choices) = defaults(spec.id)
            val result = spec.run(ToolInput(texts, choices, EvalContext()))
            val record = ToolHistory.record(spec, texts, choices, result)
            assertNotNull(spec.id, record)
            assertTrue(spec.id, record!!.summary.isNotBlank())
            assertTrue(spec.id, !record.summary.contains('\n'))
            assertTrue(spec.id, !record.input.contains('\n'))
        }
    }
}

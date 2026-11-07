package com.tapecalc.pro

import com.tapecalc.core.ui.InMemoryToolStateStore
import com.tapecalc.core.ui.SymbolInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The logic behind the row of signs that shows up under an expression box while a letter is being typed. */
class SymbolInputTest {

    @Test fun typingALetterShowsTheRow() {
        assertEquals(true, SymbolInput.reaction("", "x", 1))
        assertEquals(true, SymbolInput.reaction("2", "2x", 2))
        assertEquals(true, SymbolInput.reaction("sin(", "sin(y", 5))
    }

    @Test fun typingAnythingElseHidesTheRow() {
        assertEquals(false, SymbolInput.reaction("x", "x2", 2))
        assertEquals(false, SymbolInput.reaction("x", "x+", 2))
        assertEquals(false, SymbolInput.reaction("x", "x ", 2))
        assertEquals(false, SymbolInput.reaction("x", "x(", 2))
    }

    @Test fun deletingOrMovingTheCursorLeavesTheRowAsItIs() {
        assertNull(SymbolInput.reaction("xy", "x", 1))
        assertNull(SymbolInput.reaction("xy", "xy", 1))
        assertNull(SymbolInput.reaction("xy", "xy", 0))
    }

    @Test fun lettersInTheMiddleCountToo() {
        // the new character sits just before the cursor, not at the end of the text
        assertEquals(true, SymbolInput.reaction("2+3", "2+x3", 3))
        assertEquals(false, SymbolInput.reaction("2x", "2*x", 2))
    }

    @Test fun symbolIsInsertedAtTheCursor() {
        assertEquals("x^" to 2, SymbolInput.insert("x", 1, 1, "^"))
        assertEquals("x+y" to 2, SymbolInput.insert("xy", 1, 1, "+"))
        assertEquals("+" to 1, SymbolInput.insert("", 0, 0, "+"))
    }

    @Test fun symbolReplacesASelection() {
        assertEquals("a*c" to 2, SymbolInput.insert("abc", 1, 2, "*"))
        // a backwards selection works the same way
        assertEquals("a*c" to 2, SymbolInput.insert("abc", 2, 1, "*"))
    }

    @Test fun cursorLandsAfterAMultiCharacterSymbol() {
        assertEquals("2sqrt(" to 6, SymbolInput.insert("2", 1, 1, "sqrt("))
    }

    @Test fun outOfRangeSelectionIsClamped() {
        assertEquals("ab+" to 3, SymbolInput.insert("ab", 9, 9, "+"))
        assertEquals("+ab" to 1, SymbolInput.insert("ab", -3, -3, "+"))
    }

    @Test fun everySymbolHasLabelInsertAndDescription() {
        val labels = SymbolInput.symbols.map { it.label }
        assertEquals(labels.size, labels.toSet().size)
        for (s in SymbolInput.symbols) {
            assertTrue(s.label.isNotEmpty())
            assertTrue(s.insert.isNotEmpty())
            assertTrue(s.description.isNotEmpty())
        }
        // the signs the person asked for
        for (needed in listOf("+", "−", "×", "÷", "^", "(", ")")) assertTrue(needed, needed in labels)
    }

    @Test fun storeKeepsAndRemovesValues() {
        val store = InMemoryToolStateStore()
        assertNull(store.get("tool.poly.t.coef"))
        store.put("tool.poly.t.coef", "1, -3, 2")
        assertEquals("1, -3, 2", store.get("tool.poly.t.coef"))
        store.put("tool.poly.t.coef", "")
        assertEquals("", store.get("tool.poly.t.coef"))
        store.put("tool.poly.t.coef", null)
        assertNull(store.get("tool.poly.t.coef"))
    }
}

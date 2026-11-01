package com.tapecalc.core.math

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SuggestionsTest {

    private val names = listOf("sin", "cos", "tan", "sqrt", "log", "ln", "pi", "phi", "ans")

    @Test
    fun findsOneLetterMistakes() {
        assertEquals("sin", Suggestions.closest("sinn", names))
        assertEquals("sqrt", Suggestions.closest("sqr", names))
        assertEquals("cos", Suggestions.closest("coss", names))
    }

    @Test
    fun swappedNeighbourLettersCountAsOneMistake() {
        assertEquals("sin", Suggestions.closest("sni", names))
        assertEquals("sqrt", Suggestions.closest("sqtr", names))
    }

    @Test
    fun ignoresCaseAndKeepsOriginalSpelling() {
        assertEquals("sin", Suggestions.closest("SINN", names))
        assertEquals("Rate", Suggestions.closest("rat", listOf("Rate", "tax")))
    }

    @Test
    fun noSuggestionForTinyFarOrExactWords() {
        assertNull(Suggestions.closest("x", names))
        assertNull(Suggestions.closest("pe", names))
        assertNull(Suggestions.closest("banana", names))
        assertNull(Suggestions.closest("sin", names))
        assertNull(Suggestions.closest("SIN", names))
        assertNull(Suggestions.closest("", names))
    }

    @Test
    fun longerWordsAllowTwoMistakes() {
        assertEquals("sqrt", Suggestions.closest("sqroot", listOf("sqrt", "cbrt")))
        assertNull(Suggestions.closest("sqrtxyz", listOf("sqrt")))
    }

    @Test
    fun theFirstListedWordWinsATie() {
        // "cat" is one edit from both, so the caller's order decides.
        assertEquals("hat", Suggestions.closest("cat", listOf("hat", "bat")))
        assertEquals("bat", Suggestions.closest("cat", listOf("bat", "hat")))
    }

    @Test
    fun aCloserWordBeatsAnEarlierOne() {
        assertEquals("sqrt", Suggestions.closest("sqrtt", listOf("sqrt1xx", "sqrt")))
    }

    @Test
    fun withHintFormatsTheMessage() {
        assertEquals("Unknown name 'sinn'. Did you mean 'sin'?", Suggestions.withHint("Unknown name 'sinn'", "sin"))
        assertEquals("Unknown name 'foo'", Suggestions.withHint("Unknown name 'foo'", null))
    }
}

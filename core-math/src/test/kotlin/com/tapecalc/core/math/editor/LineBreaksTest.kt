package com.tapecalc.core.math.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LineBreaksTest {

    private fun joined(text: String, digitRun: Int = 4, wordRun: Int = 10) =
        LineBreaks.chunks(text, digitRun, wordRun).joinToString("")

    @Test
    fun emptyTextHasNoChunks() {
        assertEquals(emptyList<String>(), LineBreaks.chunks(""))
    }

    @Test
    fun shortNumberStaysOneChunk() {
        assertEquals(listOf("1234"), LineBreaks.chunks("1234"))
        assertEquals(listOf("42"), LineBreaks.chunks("42"))
    }

    @Test
    fun longDigitRunIsCutIntoGroups() {
        // the case from the bug report: 22 digits used to be one unbreakable piece
        val digits = "7899999999999999999999"
        val chunks = LineBreaks.chunks(digits)
        assertTrue("more than one chunk", chunks.size > 1)
        assertTrue("no chunk longer than 4 digits", chunks.all { it.length <= 4 })
        assertEquals(digits, chunks.joinToString(""))
    }

    @Test
    fun decimalPointStaysWithTheDigits() {
        assertEquals(listOf("3.14", "1592"), LineBreaks.chunks("3.141592"))
    }

    @Test
    fun ansChainCanWrapBetweenTerms() {
        // the second bug report: ans+ans+ans+ans+ans
        val chunks = LineBreaks.chunks("ans+ans+ans+ans+ans")
        assertEquals(listOf("ans", "+", "ans", "+", "ans", "+", "ans", "+", "ans"), chunks)
    }

    @Test
    fun openingBracketStaysWithTheFunctionName() {
        assertEquals(listOf("sin(", "30)"), LineBreaks.chunks("sin(30)"))
    }

    @Test
    fun closingBracketNeverStartsARow() {
        val chunks = LineBreaks.chunks("(12345)+(6)")
        assertTrue(chunks.none { it.startsWith(")") })
        assertEquals("(12345)+(6)", chunks.joinToString(""))
    }

    @Test
    fun spacesAndPostfixMarksStickToWhatComesBefore() {
        val chunks = LineBreaks.chunks("5! mod 3%")
        assertTrue(chunks.none { it.startsWith("!") || it.startsWith("%") || it.startsWith(" ") })
        assertEquals("5! mod 3%", chunks.joinToString(""))
    }

    @Test
    fun functionNamesAreNotCutInTheMiddle() {
        assertEquals(listOf("floor(", "2.5)"), LineBreaks.chunks("floor(2.5)"))
        // a name longer than the limit is the only case that is cut
        assertEquals(listOf("abcd", "efgh"), LineBreaks.chunks("abcdefgh", digitRun = 4, wordRun = 4))
    }

    @Test
    fun anyTextJoinsBackToItself() {
        val samples = listOf(
            "789999999999999999999999",
            "ans+ans+ans+ans+ans+ans+ans",
            "2(3+4)×sin(30)−√(16)",
            "12.5%+7!÷(1,2)",
            "πr²+e^(−1)",
            "((((((((((1))))))))))",
            "x=5 : x+3",
            "1+",
            "+"
        )
        for (s in samples) {
            assertEquals(s, joined(s))
            assertEquals(s, joined(s, digitRun = 1, wordRun = 1))
        }
    }

    @Test
    fun runLengthsMustBePositive() {
        try {
            LineBreaks.chunks("12", digitRun = 0)
            throw AssertionError("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }
}

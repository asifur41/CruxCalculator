package com.tapecalc.pro

import com.tapecalc.feature.basic.CaretHit
import com.tapecalc.feature.basic.CaretTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Turning a finger position on the input line into a cursor index. */
class CaretHitTest {

    /** A piece of text [text] starting at index [start], drawn at x = [left], 10 px per character, one row at [top]. */
    private fun piece(start: Int, text: String, left: Float, top: Float = 0f): CaretTarget = CaretTarget().apply {
        this.start = start
        length = text.length
        boundaries = FloatArray(text.length + 1) { it * 10f }
        place(left, top, left + text.length * 10f, top + 40f)
    }

    @Test
    fun nothingDrawnGivesNull() {
        assertNull(CaretHit.pick(emptyList(), 5f, 5f))
    }

    @Test
    fun leftHalfOfACharacterPutsTheCursorBeforeItAndTheRightHalfAfter() {
        val line = listOf(piece(0, "1234", 100f))
        assertEquals(1, CaretHit.pick(line, 100f + 12f, 10f)) // 12 px in: nearer the boundary at 10
        assertEquals(2, CaretHit.pick(line, 100f + 16f, 10f)) // 16 px in: nearer the boundary at 20
    }

    @Test
    fun touchingLeftOfTheLineGivesItsStartAndRightOfItGivesItsEnd() {
        val line = listOf(piece(0, "1234", 100f))
        assertEquals(0, CaretHit.pick(line, 5f, 10f))
        assertEquals(4, CaretHit.pick(line, 400f, 10f))
    }

    @Test
    fun theBoundaryBetweenTwoPiecesBelongsToTheNearerOne() {
        // "12" + "+" + "34" drawn side by side: indexes 0-2, 2-3, 3-5
        val line = listOf(piece(0, "12", 0f), piece(2, "+", 20f), piece(3, "34", 30f))
        assertEquals(2, CaretHit.pick(line, 19f, 10f))
        assertEquals(3, CaretHit.pick(line, 31f, 10f))
        assertEquals(5, CaretHit.pick(line, 52f, 10f))
    }

    @Test
    fun theRowTouchedDecidesOnAWrappedLine() {
        // two rows: "1234" (indexes 0-4) above "5678" (4-8)
        val line = listOf(piece(0, "1234", 0f, top = 0f), piece(4, "5678", 0f, top = 50f))
        assertEquals(1, CaretHit.pick(line, 10f, 10f))
        assertEquals(5, CaretHit.pick(line, 10f, 60f))
    }

    @Test
    fun anEmptyBoxGivesItsOwnPosition() {
        // numerator box (empty, index 7) above a denominator "9" (index 9)
        val box = CaretTarget().apply { start = 7; length = 0; place(0f, 0f, 20f, 20f) }
        val low = piece(9, "9", 0f, top = 30f)
        assertEquals(7, CaretHit.pick(listOf(box, low), 10f, 8f))
        assertEquals(10, CaretHit.pick(listOf(box, low), 10f, 45f)) // right half of the "9": after it
    }

    @Test
    fun withoutMeasuredCharactersEqualWidthsAreAssumed() {
        val t = CaretTarget().apply { start = 3; length = 4; place(0f, 0f, 40f, 40f) }
        assertEquals(5, CaretHit.pick(listOf(t), 20f, 10f)) // half way across 4 characters -> after 2
    }

    @Test
    fun aPieceThatWasNeverPlacedIsIgnored() {
        val unplaced = CaretTarget().apply { start = 0; length = 3 }
        assertNull(CaretHit.pick(listOf(unplaced), 5f, 5f))
    }
}

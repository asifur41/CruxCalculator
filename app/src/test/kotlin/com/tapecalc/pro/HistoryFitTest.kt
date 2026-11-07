package com.tapecalc.pro

import com.tapecalc.feature.basic.HistoryFit
import org.junit.Assert.assertEquals
import org.junit.Test

/** Which earlier lines are drawn above the working line: whole rows only, newest kept, oldest dropped. */
class HistoryFitTest {

    private val row = 60
    private val gap = 8

    @Test
    fun everyRowIsKeptWhenTheyAllFit() {
        val heights = listOf(row, row, row)
        // 3 rows + 2 gaps = 196
        assertEquals(0, HistoryFit.firstVisible(heights, gap, 196))
        assertEquals(196, HistoryFit.usedHeight(heights, gap, 0))
    }

    @Test
    fun theOldestRowIsDroppedInsteadOfBeingCutInHalf() {
        val heights = listOf(row, row, row, row)
        // room for 3.5 rows: the fourth (oldest) row would be cut, so it is not drawn
        assertEquals(1, HistoryFit.firstVisible(heights, gap, 3 * row + 2 * gap + row / 2))
    }

    @Test
    fun theGapCountsTowardsTheLimit() {
        val heights = listOf(row, row)
        // two rows need 128; 127 leaves room for one only
        assertEquals(0, HistoryFit.firstVisible(heights, gap, 128))
        assertEquals(1, HistoryFit.firstVisible(heights, gap, 127))
    }

    @Test
    fun nothingIsShownWhenEvenTheNewestRowDoesNotFit() {
        val heights = listOf(row, row)
        assertEquals(2, HistoryFit.firstVisible(heights, gap, row - 1))
        assertEquals(0, HistoryFit.usedHeight(heights, gap, 2))
    }

    @Test
    fun rowsOfDifferentHeightsAreMeasuredNotAssumed() {
        // a tall row in the middle pushes the oldest one out
        val heights = listOf(40, 100, 40)
        assertEquals(1, HistoryFit.firstVisible(heights, gap, 40 + gap + 100))
        assertEquals(2, HistoryFit.firstVisible(heights, gap, 100))
    }

    @Test
    fun noRowsAndUnboundedHeight() {
        assertEquals(0, HistoryFit.firstVisible(emptyList(), gap, 100))
        assertEquals(0, HistoryFit.firstVisible(listOf(row, row, row), gap, Int.MAX_VALUE))
        assertEquals(0, HistoryFit.usedHeight(emptyList(), gap, 0))
    }
}

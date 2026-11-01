package com.tapecalc.pro

import com.tapecalc.core.math.SmartPaste
import com.tapecalc.pro.presentation.ActionDebouncer
import com.tapecalc.pro.presentation.InputEditor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionDebouncerTest {

    @Test
    fun secondActionTooSoonIsRejected() {
        var now = 1_000L
        val d = ActionDebouncer(400) { now }
        assertTrue(d.tryAcquire())
        now += 100
        assertFalse(d.tryAcquire())
        now += 100
        assertFalse(d.tryAcquire())
    }

    @Test
    fun actionAfterTheGapIsAccepted() {
        var now = 0L
        val d = ActionDebouncer(400) { now }
        assertTrue(d.tryAcquire())
        now += 400
        assertTrue(d.tryAcquire())
    }

    @Test
    fun rejectedActionDoesNotExtendTheWindow() {
        var now = 0L
        val d = ActionDebouncer(400) { now }
        assertTrue(d.tryAcquire())
        now += 300
        assertFalse(d.tryAcquire())
        now += 150 // 450 after the first accepted action
        assertTrue(d.tryAcquire())
    }

    @Test
    fun clipboardNumbersPasteWhole() {
        assertEquals("4141", SmartPaste.extract("4141"))
        assertEquals("1234.50", SmartPaste.extract("\$1,234.50"))
        assertEquals("42", SmartPaste.extract("total: 42 items"))
    }

    @Test
    fun pasteIntoAnExpressionAppendsOnce() {
        assertEquals("4141", InputEditor.insert("", "4141", afterResult = false))
        assertEquals("5+4141", InputEditor.insert("5+", "4141", afterResult = false))
    }
}

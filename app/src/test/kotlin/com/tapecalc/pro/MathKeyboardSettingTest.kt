package com.tapecalc.pro

import com.tapecalc.core.data.settings.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The built-in keyboard is an opt-in: it must be off for new installs and for settings saved before it existed. */
class MathKeyboardSettingTest {

    @Test fun theKeyboardIsOffByDefault() {
        assertFalse(AppSettings().mathKeyboard)
    }

    @Test fun switchingItOnLeavesTheOtherSettingsAlone() {
        val before = AppSettings()
        val after = before.copy(mathKeyboard = true)
        assertTrue(after.mathKeyboard)
        assertEquals(before, after.copy(mathKeyboard = false))
    }
}

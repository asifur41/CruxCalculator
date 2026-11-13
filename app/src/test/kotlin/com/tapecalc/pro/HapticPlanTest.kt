package com.tapecalc.pro

import com.tapecalc.pro.ui.HapticPlan
import com.tapecalc.pro.ui.HapticTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HapticPlanTest {

    @Test fun ordinaryKeysTapAndStateKeysClick() {
        assertEquals(HapticTier.TAP, HapticPlan.tier(firm = false, strong = false))
        assertEquals(HapticTier.KEY, HapticPlan.tier(firm = true, strong = false))
    }

    @Test fun strongMovesEveryKeyUpOneTierAndKeepsTheDifference() {
        assertEquals(HapticTier.KEY, HapticPlan.tier(firm = false, strong = true))
        assertEquals(HapticTier.HEAVY, HapticPlan.tier(firm = true, strong = true))
        // a state key is always at least one step above an ordinary key
        for (strong in listOf(false, true)) {
            assertTrue(HapticPlan.tier(true, strong).ordinal > HapticPlan.tier(false, strong).ordinal)
        }
    }

    @Test fun masterSwitch() {
        assertFalse(HapticPlan.isOn(0))
        assertTrue(HapticPlan.isOn(1))
        assertEquals(0, HapticPlan.withOn(3, false))
        assertEquals(HapticPlan.NORMAL, HapticPlan.withOn(0, true))
        // turning it on while already on keeps the level (a strong setting is not lost)
        assertEquals(3, HapticPlan.withOn(3, true))
        assertEquals(1, HapticPlan.withOn(1, true))
    }

    @Test fun strongSubOption() {
        assertEquals(3, HapticPlan.withStrong(2, true))
        assertEquals(2, HapticPlan.withStrong(3, false))
        assertTrue(HapticPlan.isStrong(3))
        assertFalse(HapticPlan.isStrong(2))
        // no effect while haptics are off
        assertEquals(0, HapticPlan.withStrong(0, true))
    }

    @Test fun settingsSavedByEarlierVersionsStillMakeSense() {
        // old slider: 0 off, 1 light, 2 medium, 3 strong
        assertEquals(listOf(false, true, true, true), (0..3).map { HapticPlan.isOn(it) })
        assertEquals(listOf(false, false, false, true), (0..3).map { HapticPlan.isStrong(it) })
    }
}

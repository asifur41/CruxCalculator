package com.tapecalc.pro.ui

/** How hard a key press feels. Each tier maps to one of Android's own haptic constants (see [Feedback]). */
enum class HapticTier {
    /** KEYBOARD_TAP: the lightest tap, for digits, operators and ordinary function keys. */
    TAP,

    /** VIRTUAL_KEY: a firmer click. */
    KEY,

    /** LONG_PRESS: the heaviest one. */
    HEAVY
}

/**
 * The haptic rules, free of any Android class so they can be tested.
 *
 * The saved setting is still the single number `hapticLevel` (0 = off, 1 and 2 = normal, 3 = strong), so settings saved by
 * earlier versions keep working. The Settings screen shows it as a master switch with one sub-option, "Haptic Strong".
 */
object HapticPlan {
    const val OFF = 0
    const val NORMAL = 2
    const val STRONG = 3

    fun isOn(level: Int) = level > 0
    fun isStrong(level: Int) = level >= STRONG

    /** Master switch. Turning it on gives normal strength; turning it off silences the sound option too. */
    fun withOn(level: Int, on: Boolean): Int = if (!on) OFF else if (level > 0) level else NORMAL

    /** "Haptic Strong". Only meaningful while haptics are on. */
    fun withStrong(level: Int, strong: Boolean): Int = when {
        level <= 0 -> OFF
        strong -> STRONG
        else -> NORMAL
    }

    /**
     * A light tap for ordinary keys, a firmer one for the keys that change state ("=", SHIFT, ALPHA, HYP, CALC) so the
     * person can feel the difference. Strong mode moves every key up one tier.
     */
    fun tier(firm: Boolean, strong: Boolean): HapticTier = when {
        firm && strong -> HapticTier.HEAVY
        firm || strong -> HapticTier.KEY
        else -> HapticTier.TAP
    }
}

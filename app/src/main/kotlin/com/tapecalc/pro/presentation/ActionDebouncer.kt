package com.tapecalc.pro.presentation

/**
 * Lets an action through at most once per [minGapMillis]. Used for "paste" so that one gesture can
 * never paste the clipboard several times, even if the system delivers the same tap or long-press twice.
 * Pure Kotlin (the clock is injectable) so it is unit-tested without Android.
 */
class ActionDebouncer(
    private val minGapMillis: Long,
    private val clock: () -> Long = { System.nanoTime() / 1_000_000L }
) {
    private var hasLast = false
    private var lastMillis = 0L

    /** True when the action may run now; false when it is too soon after the previous accepted one. */
    fun tryAcquire(): Boolean {
        val now = clock()
        if (hasLast && now - lastMillis < minGapMillis) return false
        hasLast = true
        lastMillis = now
        return true
    }
}

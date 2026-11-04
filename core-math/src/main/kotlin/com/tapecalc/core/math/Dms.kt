package com.tapecalc.core.math

import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

/** Degrees-minutes-seconds text for an angle given in decimal degrees: 2.5 -> "2°30′0″". */
object Dms {
    private val SIXTY = BigDecimal(60)
    private val MAX = BigDecimal("1E9")

    /** Returns null when [degrees] is too large for a readable angle. */
    fun format(degrees: BigDecimal): String? {
        if (degrees.abs() >= MAX) return null
        val negative = degrees.signum() < 0
        // Work in whole milli-arc-seconds so rounding carries cleanly (59.9996″ becomes the next minute).
        val total = degrees.abs().multiply(BigDecimal(3_600_000)).setScale(0, RoundingMode.HALF_UP).toBigInteger()
        val perDegree = BigInteger.valueOf(3_600_000)
        val perMinute = BigInteger.valueOf(60_000)
        val d = total.divide(perDegree)
        val rest = total.mod(perDegree)
        val m = rest.divide(perMinute)
        val milli = rest.mod(perMinute)
        val seconds = BigDecimal(milli).movePointLeft(3).stripTrailingZeros().toPlainString()
        val sign = if (negative && total.signum() != 0) "-" else ""
        return "$sign$d°$m′$seconds″"
    }

    fun format(value: CalcValue): String? = format(value.decimal)
}

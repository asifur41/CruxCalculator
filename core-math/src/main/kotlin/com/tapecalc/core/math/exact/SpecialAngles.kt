package com.tapecalc.core.math.exact

import com.tapecalc.core.math.AngleMode
import com.tapecalc.core.precision.Rational
import java.math.BigInteger

/**
 * Exact sin, cos and tan of the angles that are multiples of 15°, and the inverse functions on those values, in the
 * angle unit that is active. Because the angle is first turned into degrees *in that unit* (a radian angle must be a
 * rational multiple of π, a gradian angle is scaled by 9/10), the same text gives different exact answers in RAD, DEG
 * and GRAD, exactly as the decimal engine does: sin(60) is √3/2 in degrees but has no closed form in radians, while
 * sin(π/3) is √3/2 in radians.
 */
internal object SpecialAngles {
    private const val STEP = 15
    private const val FULL = 24 // 24 steps of 15° make a turn

    private val half = ExactNumber.of(Rational.of(1, 2))
    private val quarter = Rational.of(1, 4)
    private val sqrt2 = ExactNumber.sqrtOf(Rational.of(2))
    private val sqrt3 = ExactNumber.sqrtOf(Rational.of(3))
    private val sqrt6 = ExactNumber.sqrtOf(Rational.of(6))

    /** sin of 0°, 15°, 30°, 45°, 60°, 75°, 90°. */
    private val firstQuadrant: List<ExactNumber> = listOf(
        ExactNumber.ZERO,
        (sqrt6 - sqrt2) * ExactNumber.of(quarter),
        half,
        sqrt2 * half,
        sqrt3 * half,
        (sqrt6 + sqrt2) * ExactNumber.of(quarter),
        ExactNumber.ONE
    )

    private fun sinStep(step: Int): ExactNumber {
        val i = Math.floorMod(step, FULL)
        return when {
            i <= 6 -> firstQuadrant[i]
            i <= 12 -> firstQuadrant[12 - i]
            i <= 18 -> -firstQuadrant[i - 12]
            else -> -firstQuadrant[FULL - i]
        }
    }

    private fun cosStep(step: Int) = sinStep(step + 6)

    private fun tanStep(step: Int): ExactNumber? {
        val c = cosStep(step)
        return if (c.isZero) null else sinStep(step) / c
    }

    /** The angle in degrees if it is a whole number of 15° steps, else null. */
    private fun stepOf(deg: Rational): Int? {
        val steps = deg / Rational.of(STEP.toLong())
        if (!steps.isInteger) return null
        return steps.num.mod(BigInteger.valueOf(FULL.toLong())).toInt()
    }

    /** [x] (an angle in the active unit) as exact degrees, or null when it is not a rational number of degrees. */
    fun toDegrees(x: ExactNumber, mode: AngleMode): Rational? = when (mode) {
        AngleMode.DEG -> x.asRational()
        AngleMode.GRAD -> x.asRational()?.let { it * Rational.of(9, 10) }
        AngleMode.RAD -> x.asPiMultiple()?.let { it * Rational.of(180) }
    }

    /** [deg] degrees written in the active unit. */
    fun fromDegrees(deg: Rational, mode: AngleMode): ExactNumber = when (mode) {
        AngleMode.DEG -> ExactNumber.of(deg)
        AngleMode.GRAD -> ExactNumber.of(deg * Rational.of(10, 9))
        AngleMode.RAD -> ExactNumber.piTimes(deg / Rational.of(180))
    }

    fun sin(angle: ExactNumber, mode: AngleMode): ExactNumber? =
        toDegrees(angle, mode)?.let(::stepOf)?.let(::sinStep)

    fun cos(angle: ExactNumber, mode: AngleMode): ExactNumber? =
        toDegrees(angle, mode)?.let(::stepOf)?.let(::cosStep)

    fun tan(angle: ExactNumber, mode: AngleMode): ExactNumber? =
        toDegrees(angle, mode)?.let(::stepOf)?.let(::tanStep)

    /** asin on the principal range -90°..90°. */
    fun asin(x: ExactNumber, mode: AngleMode): ExactNumber? =
        (-6..6).firstOrNull { sinStep(it) == x }?.let { fromDegrees(Rational.of((it * STEP).toLong()), mode) }

    /** acos on the principal range 0°..180°. */
    fun acos(x: ExactNumber, mode: AngleMode): ExactNumber? =
        (0..12).firstOrNull { cosStep(it) == x }?.let { fromDegrees(Rational.of((it * STEP).toLong()), mode) }

    /** atan on the principal range -90°..90° (exclusive). */
    fun atan(x: ExactNumber, mode: AngleMode): ExactNumber? =
        (-5..5).firstOrNull { tanStep(it) == x }?.let { fromDegrees(Rational.of((it * STEP).toLong()), mode) }
}

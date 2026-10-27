package com.tapecalc.core.math

import com.tapecalc.core.precision.BigMath
import com.tapecalc.core.precision.DomainException
import com.tapecalc.core.precision.Precision
import com.tapecalc.core.precision.Rational
import java.math.BigDecimal
import java.math.MathContext

/**
 * Trigonometry in degrees / radians / gradians.
 * Degree and gradian inputs are reduced exactly, so sin(30) is exactly 1/2 and tan(45) exactly 1.
 */
internal object Trig {
    private val FULL = BigDecimal(360)
    private val WORK = Precision.WORK
    private val HALF = Rational.of(1, 2)
    private val CLEAN = MathContext(34)

    private fun toDegrees(x: BigDecimal, mode: AngleMode): BigDecimal? = when (mode) {
        AngleMode.DEG -> x
        AngleMode.GRAD -> x.multiply(BigDecimal("0.9"))
        AngleMode.RAD -> null
    }

    private fun normalize(deg: BigDecimal): BigDecimal {
        var r = deg.remainder(FULL)
        if (r.signum() < 0) r = r.add(FULL)
        return r
    }

    private fun toRadians(deg: BigDecimal): BigDecimal =
        deg.multiply(BigMath.pi, WORK).divide(BigDecimal(180), WORK)

    private fun at(r: BigDecimal, v: Int): Boolean = r.compareTo(BigDecimal(v)) == 0

    private fun exactSin(r: BigDecimal): Rational? = when {
        at(r, 0) || at(r, 180) -> Rational.ZERO
        at(r, 90) -> Rational.ONE
        at(r, 270) -> -Rational.ONE
        at(r, 30) || at(r, 150) -> HALF
        at(r, 210) || at(r, 330) -> -HALF
        else -> null
    }

    private fun exactCos(r: BigDecimal): Rational? = when {
        at(r, 0) -> Rational.ONE
        at(r, 90) || at(r, 270) -> Rational.ZERO
        at(r, 180) -> -Rational.ONE
        at(r, 60) || at(r, 300) -> HALF
        at(r, 120) || at(r, 240) -> -HALF
        else -> null
    }

    fun sin(x: BigDecimal, mode: AngleMode): CalcValue {
        val deg = toDegrees(x, mode) ?: return CalcValue.ofDecimal(BigMath.sin(x))
        val r = normalize(deg)
        exactSin(r)?.let { return CalcValue.ofRational(it) }
        return CalcValue.ofDecimal(BigMath.sin(toRadians(r)))
    }

    fun cos(x: BigDecimal, mode: AngleMode): CalcValue {
        val deg = toDegrees(x, mode) ?: return CalcValue.ofDecimal(BigMath.cos(x))
        val r = normalize(deg)
        exactCos(r)?.let { return CalcValue.ofRational(it) }
        return CalcValue.ofDecimal(BigMath.cos(toRadians(r)))
    }

    fun tan(x: BigDecimal, mode: AngleMode): CalcValue {
        val deg = toDegrees(x, mode)
        if (deg == null) {
            try {
                return CalcValue.ofDecimal(BigMath.tan(x))
            } catch (e: DomainException) {
                throw CalcException(ErrorKind.UNDEFINED, "tan is undefined at this angle")
            }
        }
        val r = normalize(deg)
        return when {
            at(r, 90) || at(r, 270) -> throw CalcException(ErrorKind.UNDEFINED, "tan is undefined at this angle")
            at(r, 0) || at(r, 180) -> CalcValue.ZERO
            at(r, 45) || at(r, 225) -> CalcValue.ONE
            at(r, 135) || at(r, 315) -> CalcValue.ofRational(-Rational.ONE)
            else -> CalcValue.ofDecimal(BigMath.tan(toRadians(r)))
        }
    }

    private fun fromRadians(rad: BigDecimal, mode: AngleMode): CalcValue = when (mode) {
        AngleMode.RAD -> CalcValue.ofDecimal(rad)
        AngleMode.DEG -> CalcValue.ofDecimal(rad.multiply(BigDecimal(180), WORK).divide(BigMath.pi, WORK).round(CLEAN))
        AngleMode.GRAD -> CalcValue.ofDecimal(rad.multiply(BigDecimal(200), WORK).divide(BigMath.pi, WORK).round(CLEAN))
    }

    fun asin(x: BigDecimal, mode: AngleMode): CalcValue = fromRadians(BigMath.asin(x), mode)
    fun acos(x: BigDecimal, mode: AngleMode): CalcValue = fromRadians(BigMath.acos(x), mode)
    fun atan(x: BigDecimal, mode: AngleMode): CalcValue = fromRadians(BigMath.atan(x), mode)
}

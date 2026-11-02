package com.tapecalc.core.math

import com.tapecalc.core.precision.BigMath
import com.tapecalc.core.precision.Precision
import com.tapecalc.core.precision.Rational
import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext

/**
 * Recognises results such as 2π/3 or √2/2 so the S⇔D toggle can show them exactly.
 * A form is only reported when it reproduces the value to about 25 significant digits.
 */
object ExactForms {
    private val TOLERANCE = BigDecimal("1E-24")
    private val MC = MathContext(45)

    /** Best rational p/q with q ≤ [maxDen] and |p| ≤ [maxNum] within a relative tolerance, else null. */
    internal fun rationalize(x: BigDecimal, maxDen: Long, maxNum: Long, tolerance: BigDecimal = TOLERANCE): Rational? {
        if (x.signum() == 0) return Rational.ZERO
        var h0 = BigInteger.ZERO; var h1 = BigInteger.ONE
        var k0 = BigInteger.ONE; var k1 = BigInteger.ZERO
        var rest = x
        repeat(40) {
            val a = rest.setScale(0, java.math.RoundingMode.FLOOR).toBigInteger()
            val h2 = a.multiply(h1).add(h0)
            val k2 = a.multiply(k1).add(k0)
            if (k2 > BigInteger.valueOf(maxDen) || h2.abs() > BigInteger.valueOf(maxNum)) return null
            h0 = h1; h1 = h2; k0 = k1; k1 = k2
            val approx = BigDecimal(h1).divide(BigDecimal(k1), MC)
            val err = approx.subtract(x).abs()
            if (err <= tolerance.multiply(x.abs().max(BigDecimal.ONE))) return Rational.of(h1, k1)
            val frac = rest.subtract(BigDecimal(a))
            if (frac.signum() == 0) return null
            rest = BigDecimal.ONE.divide(frac, MC)
        }
        return null
    }

    /** "2π/3", "-π/6", "3π", "π" ... or null when [v] is not a small rational multiple of π. */
    fun piForm(v: CalcValue): String? {
        if (v.exact != null || v.isZero) return null
        val r = rationalize(v.decimal.divide(BigMath.pi, MC), 720, 100_000) ?: return null
        if (r.isZero) return null
        val num = r.num.abs()
        val numText = if (num == BigInteger.ONE) "π" else "${num}π"
        val sign = if (r.signum < 0) "-" else ""
        return if (r.isInteger) "$sign$numText" else "$sign$numText/${r.den}"
    }

    /** "√2/2", "3√5", "-√3/3" ... or null when [v] is not a rational multiple of a square root. */
    fun radicalForm(v: CalcValue): String? {
        if (v.exact != null || v.isZero) return null
        val square = v.decimal.multiply(v.decimal, MC)
        val r = rationalize(square, 2000, 2_000_000_000L) ?: return null
        // v = sign * sqrt(p/q) = sign * sqrt(p*q) / q
        val n = r.num.multiply(r.den)
        val (outside, inside) = squareFree(n) ?: return null
        if (inside == BigInteger.ONE) return null
        var coefficient = outside
        var denominator = r.den
        val g = coefficient.gcd(denominator)
        coefficient /= g
        denominator /= g
        val sign = if (v.signum < 0) "-" else ""
        val lead = if (coefficient == BigInteger.ONE) "" else coefficient.toString()
        val top = "$lead√$inside"
        return if (denominator == BigInteger.ONE) "$sign$top" else "$sign$top/$denominator"
    }

    /** n = outside² · inside with `inside` square-free, or null when n is too big to factor quickly. */
    private fun squareFree(n: BigInteger): Pair<BigInteger, BigInteger>? {
        if (n.signum() <= 0 || n.bitLength() > 62) return null
        var rest = n.toLong()
        var outside = 1L
        var inside = 1L
        var p = 2L
        while (p * p <= rest) {
            var count = 0
            while (rest % p == 0L) {
                rest /= p
                count++
            }
            repeat(count / 2) { outside *= p }
            if (count % 2 == 1) inside *= p
            p += if (p == 2L) 1 else 2
        }
        inside *= rest
        return BigInteger.valueOf(outside) to BigInteger.valueOf(inside)
    }
}

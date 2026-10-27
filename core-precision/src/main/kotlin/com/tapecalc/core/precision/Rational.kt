package com.tapecalc.core.precision

import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext

/**
 * Exact rational number (always stored in lowest terms, denominator > 0).
 * Used so that 0.1 + 0.2 is exactly 3/10 and 1/3 stays 1/3 until display time.
 */
class Rational private constructor(val num: BigInteger, val den: BigInteger) : Comparable<Rational> {

    val isInteger: Boolean get() = den == BigInteger.ONE
    val signum: Int get() = num.signum()
    val isZero: Boolean get() = num.signum() == 0

    /** Largest bit length of numerator/denominator; used to stop runaway exact arithmetic. */
    val bitSize: Int get() = maxOf(num.bitLength(), den.bitLength())

    operator fun plus(o: Rational): Rational = of(num * o.den + o.num * den, den * o.den)
    operator fun minus(o: Rational): Rational = of(num * o.den - o.num * den, den * o.den)
    operator fun times(o: Rational): Rational = of(num * o.num, den * o.den)
    operator fun div(o: Rational): Rational {
        if (o.isZero) throw ArithmeticException("Division by zero")
        return of(num * o.den, den * o.num)
    }

    operator fun unaryMinus(): Rational = Rational(num.negate(), den)

    fun abs(): Rational = if (num.signum() < 0) Rational(num.negate(), den) else this

    fun pow(exp: Int): Rational {
        if (exp == 0) return ONE
        if (exp < 0) {
            if (isZero) throw ArithmeticException("Division by zero")
            return of(den.pow(-exp), num.pow(-exp))
        }
        return of(num.pow(exp), den.pow(exp))
    }

    /** Floor as BigInteger (rounds toward negative infinity). */
    fun floor(): BigInteger {
        val qr = num.divideAndRemainder(den)
        return if (qr[1].signum() < 0) qr[0] - BigInteger.ONE else qr[0]
    }

    fun ceil(): BigInteger {
        val f = floor()
        return if (isInteger) f else f + BigInteger.ONE
    }

    fun toBigDecimal(mc: MathContext): BigDecimal =
        if (isInteger) BigDecimal(num).round(mc) else BigDecimal(num).divide(BigDecimal(den), mc)

    override fun compareTo(other: Rational): Int = (num * other.den).compareTo(other.num * den)

    override fun equals(other: Any?): Boolean = other is Rational && num == other.num && den == other.den
    override fun hashCode(): Int = num.hashCode() * 31 + den.hashCode()
    override fun toString(): String = if (isInteger) num.toString() else "$num/$den"

    /** Mixed-number form such as "1 3/4" (null for integers). */
    fun toMixedString(): String? {
        if (isInteger) return null
        val qr = num.abs().divideAndRemainder(den)
        val sign = if (num.signum() < 0) "-" else ""
        return if (qr[0].signum() == 0) "$sign${qr[1]}/$den" else "$sign${qr[0]} ${qr[1]}/$den"
    }

    /** True when the decimal expansion terminates (denominator has only factors 2 and 5). */
    fun isTerminating(): Boolean {
        var d = den
        val two = BigInteger.valueOf(2)
        val five = BigInteger.valueOf(5)
        while (d.mod(two).signum() == 0) d /= two
        while (d.mod(five).signum() == 0) d /= five
        return d == BigInteger.ONE
    }

    /**
     * Decimal expansion with the repeating block in parentheses, e.g. 1/3 -> "0.(3)", 1/6 -> "0.1(6)".
     * Returns a truncated expansion ending in "…" if the period is longer than [maxDigits].
     */
    fun toRepeatingDecimal(maxDigits: Int = 60): String {
        val negative = num.signum() < 0
        val n = num.abs()
        val intPart = n / den
        var rem = n.mod(den)
        val sb = StringBuilder()
        if (negative) sb.append('-')
        sb.append(intPart.toString())
        if (rem.signum() == 0) return sb.toString()
        sb.append('.')
        val seen = HashMap<BigInteger, Int>()
        val digits = StringBuilder()
        while (rem.signum() != 0) {
            val at = seen[rem]
            if (at != null) {
                val head = digits.substring(0, at)
                val period = digits.substring(at)
                return sb.append(head).append('(').append(period).append(')').toString()
            }
            if (digits.length >= maxDigits) {
                return sb.append(digits).append('…').toString()
            }
            seen[rem] = digits.length
            rem *= BigInteger.TEN
            digits.append((rem / den).toString())
            rem = rem.mod(den)
        }
        return sb.append(digits).toString()
    }

    companion object {
        val ZERO = Rational(BigInteger.ZERO, BigInteger.ONE)
        val ONE = Rational(BigInteger.ONE, BigInteger.ONE)

        fun of(num: BigInteger, den: BigInteger): Rational {
            if (den.signum() == 0) throw ArithmeticException("Division by zero")
            if (num.signum() == 0) return ZERO
            var n = num
            var d = den
            if (d.signum() < 0) {
                n = n.negate()
                d = d.negate()
            }
            val g = n.gcd(d)
            if (g != BigInteger.ONE) {
                n /= g
                d /= g
            }
            return Rational(n, d)
        }

        fun of(num: Long, den: Long = 1L): Rational = of(BigInteger.valueOf(num), BigInteger.valueOf(den))

        fun of(value: BigInteger): Rational = Rational(value, BigInteger.ONE)

        fun fromBigDecimal(value: BigDecimal): Rational {
            val unscaled = value.unscaledValue()
            val scale = value.scale()
            return if (scale >= 0) of(unscaled, BigInteger.TEN.pow(scale))
            else of(unscaled * BigInteger.TEN.pow(-scale), BigInteger.ONE)
        }

        /** Parses a plain decimal string such as "12.5" exactly. */
        fun parse(text: String): Rational = fromBigDecimal(BigDecimal(text))
    }
}

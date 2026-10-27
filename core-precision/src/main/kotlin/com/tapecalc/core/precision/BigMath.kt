package com.tapecalc.core.precision

import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import java.math.RoundingMode

/**
 * Arbitrary-precision elementary functions built on BigDecimal series.
 * All functions work at [Precision.WORK] (60 digits) and return values at that precision;
 * callers round to the precision they need.
 */
object BigMath {
    private val WORK = Precision.WORK
    private val TWO = BigDecimal(2)
    private val HALF = BigDecimal("0.5")
    private val MAX_EXP_ARG = BigDecimal(23_100)

    private val HI = MathContext(110)

    /** pi to ~100 digits (Machin formula). */
    val pi: BigDecimal by lazy {
        val a = atanSeries(BigDecimal.ONE.divide(BigDecimal(5), HI), HI)
        val b = atanSeries(BigDecimal.ONE.divide(BigDecimal(239), HI), HI)
        a.multiply(BigDecimal(4), HI).subtract(b, HI).multiply(BigDecimal(4), HI)
    }

    private val twoPi: BigDecimal by lazy { pi.multiply(TWO, HI) }
    private val halfPi: BigDecimal by lazy { pi.divide(TWO, HI) }

    private val ln2: BigDecimal by lazy { lnNearOne(TWO, HI) }
    private val ln10: BigDecimal by lazy {
        ln2.multiply(BigDecimal(3), HI).add(lnNearOne(BigDecimal("1.25"), HI), HI)
    }

    val e: BigDecimal by lazy { exp(BigDecimal.ONE) }
    val phi: BigDecimal by lazy { BigDecimal.ONE.add(sqrt(BigDecimal(5))).divide(TWO, WORK) }

    private fun epsFor(mc: MathContext, ref: BigDecimal): BigDecimal =
        if (ref.signum() == 0) BigDecimal.ONE.movePointLeft(mc.precision + 5)
        else ref.abs().movePointLeft(mc.precision + 3)

    // ---------------------------------------------------------------- sqrt / pow

    fun sqrt(x: BigDecimal): BigDecimal {
        if (x.signum() < 0) throw DomainException("Square root of a negative number")
        if (x.signum() == 0) return BigDecimal.ZERO
        val exp10 = x.precision() - x.scale()
        val q = exp10 / 2
        val m = x.movePointLeft(2 * q)
        var g = BigDecimal(Math.sqrt(m.toDouble()))
        if (g.signum() == 0) g = BigDecimal.ONE
        repeat(8) {
            g = g.add(m.divide(g, WORK), WORK).divide(TWO, WORK)
        }
        return g.movePointRight(q).round(WORK)
    }

    /** x^n for integer n. Throws Overflow/Underflow for results outside the supported range. */
    fun powInt(x: BigDecimal, n: Int): BigDecimal {
        if (n == 0) return BigDecimal.ONE
        if (x.signum() == 0) {
            if (n < 0) throw ArithmeticException("Division by zero")
            return BigDecimal.ZERO
        }
        val mag = (x.precision() - x.scale() - 1).toLong()
        val absN = Math.abs(n.toLong())
        val estimate = mag * absN * (if (n < 0) -1 else 1)
        if (mag != 0L && Math.abs(mag * absN) > 20_000L) {
            if (estimate > 0) throw OverflowException() else throw UnderflowException()
        }
        val p = x.pow(absN.toInt(), WORK)
        return if (n < 0) BigDecimal.ONE.divide(p, WORK) else p
    }

    /** x^y for x > 0 and arbitrary real y. */
    fun powReal(x: BigDecimal, y: BigDecimal): BigDecimal {
        if (x.signum() <= 0) throw DomainException("Base must be positive")
        val t = y.multiply(ln(x), WORK)
        if (t > MAX_EXP_ARG) throw OverflowException()
        if (t < MAX_EXP_ARG.negate()) throw UnderflowException()
        return exp(t)
    }

    // ---------------------------------------------------------------- exp / ln

    fun exp(x: BigDecimal): BigDecimal {
        if (x.signum() == 0) return BigDecimal.ONE
        if (x > MAX_EXP_ARG) throw OverflowException()
        if (x < MAX_EXP_ARG.negate()) throw UnderflowException()
        val mc = WORK
        var r = x
        var halvings = 0
        while (r.abs() > HALF) {
            r = r.divide(TWO, mc)
            halvings++
        }
        var term = BigDecimal.ONE
        var sum = BigDecimal.ONE
        val eps = BigDecimal.ONE.movePointLeft(mc.precision + 5)
        var k = 1
        while (k < 10_000) {
            term = term.multiply(r, mc).divide(BigDecimal(k), mc)
            sum = sum.add(term, mc)
            if (term.abs() < eps) break
            k++
        }
        repeat(halvings) { sum = sum.multiply(sum, mc) }
        return sum.round(mc)
    }

    private fun lnNearOne(m: BigDecimal, mc: MathContext): BigDecimal {
        val z = m.subtract(BigDecimal.ONE).divide(m.add(BigDecimal.ONE), mc)
        if (z.signum() == 0) return BigDecimal.ZERO
        val z2 = z.multiply(z, mc)
        var term = z
        var sum = z
        var j = 1
        val eps = epsFor(mc, z)
        while (j < 100_000) {
            term = term.multiply(z2, mc)
            val t = term.divide(BigDecimal(2 * j + 1), mc)
            sum = sum.add(t, mc)
            if (t.abs() < eps) break
            j++
        }
        return sum.multiply(TWO, mc)
    }

    fun ln(x: BigDecimal): BigDecimal {
        if (x.signum() <= 0) throw DomainException("Logarithm of a non-positive number")
        if (x.compareTo(BigDecimal.ONE) == 0) return BigDecimal.ZERO
        val d = x.precision() - x.scale() - 1
        var m = x.movePointLeft(d)
        var k = 0
        val limit = BigDecimal("1.5")
        while (m > limit) {
            m = m.divide(TWO, HI)
            k++
        }
        val lm = lnNearOne(m, WORK)
        val result = lm
            .add(ln2.multiply(BigDecimal(k), HI), HI)
            .add(ln10.multiply(BigDecimal(d), HI), HI)
        return result.round(WORK)
    }

    fun log10(x: BigDecimal): BigDecimal {
        // Exact powers of ten give exact answers.
        if (x.signum() > 0) {
            val stripped = x.stripTrailingZeros()
            if (stripped.unscaledValue() == BigInteger.ONE) return BigDecimal(-stripped.scale())
        }
        return ln(x).divide(ln10, WORK)
    }

    fun log2(x: BigDecimal): BigDecimal {
        if (x.signum() > 0) {
            val bi = x.stripTrailingZeros()
            if (bi.scale() <= 0) {
                val n = bi.toBigIntegerExact()
                if (n.bitCount() == 1) return BigDecimal(n.bitLength() - 1)
            }
        }
        return ln(x).divide(ln2, WORK)
    }

    // ---------------------------------------------------------------- trigonometry (radians)

    private fun atanSeries(x: BigDecimal, mc: MathContext): BigDecimal {
        if (x.signum() == 0) return BigDecimal.ZERO
        val x2 = x.multiply(x, mc)
        var term = x
        var sum = x
        val eps = epsFor(mc, x)
        var k = 1
        while (k < 100_000) {
            term = term.multiply(x2, mc).negate()
            val t = term.divide(BigDecimal(2 * k + 1), mc)
            sum = sum.add(t, mc)
            if (t.abs() < eps) break
            k++
        }
        return sum
    }

    private fun reduceTwoPi(x: BigDecimal): BigDecimal {
        val mag = x.precision() - x.scale()
        if (mag > 40) throw DomainException("Angle too large")
        if (x.abs() <= BigDecimal(3)) return x.round(WORK)
        val q = x.divide(twoPi, 0, RoundingMode.HALF_EVEN)
        return x.subtract(q.multiply(twoPi)).round(WORK)
    }

    private fun snap(result: BigDecimal, x: BigDecimal): BigDecimal {
        if (x.abs() >= BigDecimal.ONE) {
            val threshold = x.abs().max(BigDecimal.ONE).movePointLeft(38)
            if (result.abs() < threshold) return BigDecimal.ZERO
        }
        return result
    }

    fun sin(x: BigDecimal): BigDecimal {
        if (x.signum() == 0) return BigDecimal.ZERO
        val r = reduceTwoPi(x)
        if (r.signum() == 0) return BigDecimal.ZERO
        val mc = WORK
        val r2 = r.multiply(r, mc)
        var term = r
        var sum = r
        val eps = epsFor(mc, r)
        var k = 1
        while (k < 10_000) {
            term = term.multiply(r2, mc).divide(BigDecimal((2 * k) * (2 * k + 1)), mc).negate()
            sum = sum.add(term, mc)
            if (term.abs() < eps) break
            k++
        }
        return snap(sum.round(mc), x)
    }

    fun cos(x: BigDecimal): BigDecimal {
        if (x.signum() == 0) return BigDecimal.ONE
        val r = reduceTwoPi(x)
        val mc = WORK
        val r2 = r.multiply(r, mc)
        var term = BigDecimal.ONE
        var sum = BigDecimal.ONE
        val eps = BigDecimal.ONE.movePointLeft(mc.precision + 5)
        var k = 1
        while (k < 10_000) {
            term = term.multiply(r2, mc).divide(BigDecimal((2 * k - 1) * (2 * k)), mc).negate()
            sum = sum.add(term, mc)
            if (term.abs() < eps) break
            k++
        }
        return snap(sum.round(mc), x)
    }

    /** Throws DomainException when cos(x) is zero (tan undefined). */
    fun tan(x: BigDecimal): BigDecimal {
        val c = cos(x)
        if (c.signum() == 0) throw DomainException("tan is undefined at this angle")
        return sin(x).divide(c, WORK)
    }

    fun atan(x: BigDecimal): BigDecimal {
        if (x.signum() == 0) return BigDecimal.ZERO
        if (x.signum() < 0) return atan(x.negate()).negate()
        if (x > BigDecimal.ONE) {
            return halfPi.round(WORK).subtract(atan(BigDecimal.ONE.divide(x, WORK)), WORK)
        }
        var r = x
        var n = 0
        val tenth = BigDecimal("0.1")
        while (r > tenth) {
            val s = sqrt(BigDecimal.ONE.add(r.multiply(r, WORK), WORK))
            r = r.divide(BigDecimal.ONE.add(s, WORK), WORK)
            n++
        }
        var result = atanSeries(r, WORK)
        repeat(n) { result = result.multiply(TWO, WORK) }
        return result.round(WORK)
    }

    fun asin(x: BigDecimal): BigDecimal {
        val one = BigDecimal.ONE
        val c = x.abs().compareTo(one)
        if (c > 0) throw DomainException("asin is only defined for values between -1 and 1")
        if (c == 0) return if (x.signum() > 0) halfPi.round(WORK) else halfPi.negate().round(WORK)
        val denom = sqrt(one.subtract(x.multiply(x, WORK), WORK))
        return atan(x.divide(denom, WORK))
    }

    fun acos(x: BigDecimal): BigDecimal = halfPi.round(WORK).subtract(asin(x), WORK)

    // ---------------------------------------------------------------- hyperbolic

    private fun sinhSeries(x: BigDecimal): BigDecimal {
        val mc = WORK
        val x2 = x.multiply(x, mc)
        var term = x
        var sum = x
        val eps = epsFor(mc, x)
        var k = 1
        while (k < 10_000) {
            term = term.multiply(x2, mc).divide(BigDecimal((2 * k) * (2 * k + 1)), mc)
            sum = sum.add(term, mc)
            if (term.abs() < eps) break
            k++
        }
        return sum
    }

    fun sinh(x: BigDecimal): BigDecimal {
        if (x.signum() == 0) return BigDecimal.ZERO
        if (x.abs() < BigDecimal.ONE) return sinhSeries(x)
        val ex = exp(x)
        return ex.subtract(BigDecimal.ONE.divide(ex, WORK), WORK).divide(TWO, WORK)
    }

    fun cosh(x: BigDecimal): BigDecimal {
        val ex = exp(x)
        return ex.add(BigDecimal.ONE.divide(ex, WORK), WORK).divide(TWO, WORK)
    }

    fun tanh(x: BigDecimal): BigDecimal {
        if (x.signum() == 0) return BigDecimal.ZERO
        if (x.abs() > BigDecimal(200)) return if (x.signum() > 0) BigDecimal.ONE else BigDecimal.ONE.negate()
        if (x.abs() < HALF) return sinh(x).divide(cosh(x), WORK)
        val ax = x.abs()
        val e = exp(ax.multiply(TWO).negate())
        val t = BigDecimal.ONE.subtract(e, WORK).divide(BigDecimal.ONE.add(e, WORK), WORK)
        return if (x.signum() < 0) t.negate() else t
    }

    private val TINY = BigDecimal("1E-20")

    fun asinh(x: BigDecimal): BigDecimal {
        if (x.signum() == 0) return BigDecimal.ZERO
        if (x.abs() < TINY) return x
        val ax = x.abs()
        val r = ln(ax.add(sqrt(ax.multiply(ax, WORK).add(BigDecimal.ONE, WORK)), WORK))
        return if (x.signum() < 0) r.negate() else r
    }

    fun acosh(x: BigDecimal): BigDecimal {
        if (x < BigDecimal.ONE) throw DomainException("acosh is only defined for values >= 1")
        return ln(x.add(sqrt(x.multiply(x, WORK).subtract(BigDecimal.ONE, WORK)), WORK))
    }

    fun atanh(x: BigDecimal): BigDecimal {
        if (x.abs() >= BigDecimal.ONE) throw DomainException("atanh is only defined for values between -1 and 1")
        if (x.signum() == 0) return BigDecimal.ZERO
        if (x.abs() < TINY) return x
        val ratio = BigDecimal.ONE.add(x).divide(BigDecimal.ONE.subtract(x), WORK)
        return ln(ratio).divide(TWO, WORK)
    }

    // ---------------------------------------------------------------- integer helpers

    fun factorial(n: Int): BigInteger {
        var r = BigInteger.ONE
        for (i in 2..n) r = r.multiply(BigInteger.valueOf(i.toLong()))
        return r
    }

    /** Integer k-th root floor via binary search. */
    fun integerRoot(n: BigInteger, k: Int): BigInteger {
        if (n.signum() == 0 || k == 1) return n
        var lo = BigInteger.ZERO
        var hi = BigInteger.ONE.shiftLeft(n.bitLength() / k + 1)
        while (lo < hi) {
            val mid = lo.add(hi).add(BigInteger.ONE).shiftRight(1)
            if (mid.pow(k) <= n) lo = mid else hi = mid.subtract(BigInteger.ONE)
        }
        return lo
    }
}

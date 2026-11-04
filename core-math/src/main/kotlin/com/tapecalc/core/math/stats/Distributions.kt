package com.tapecalc.core.math.stats

import com.tapecalc.core.math.Arith
import com.tapecalc.core.math.CalcException
import com.tapecalc.core.math.CalcValue
import com.tapecalc.core.math.ErrorKind
import com.tapecalc.core.precision.BigMath
import com.tapecalc.core.precision.Precision
import java.math.BigDecimal
import java.math.MathContext

/**
 * Normal, binomial and Poisson distributions.
 *
 * Binomial and Poisson use 40-digit decimal recurrences (or exact fractions for small cases), so
 * `B(10, 0.5)` at 5 is exactly 0.24609375. The normal distribution uses double precision (about 15 digits)
 * and rounds its answers to 12 significant digits so no floating-point noise is shown.
 */
object Distributions {
    private val ZERO = CalcValue.ZERO
    private val ONE = CalcValue.ONE
    private val MC = Precision.WORK
    private const val MAX_BINOMIAL_N = 1_000_000
    private const val EXACT_BINOMIAL_N = 1000
    private const val MAX_POISSON_K = 200_000
    private const val MAX_POISSON_LAMBDA = 100_000

    private fun domain(message: String): Nothing = throw CalcException(ErrorKind.DOMAIN, message)

    // ------------------------------------------------------------------ normal

    private fun fromDouble(d: Double): CalcValue {
        if (d.isNaN() || d.isInfinite()) throw CalcException(ErrorKind.OVERFLOW, "The result is out of range")
        if (d == 0.0) return ZERO
        return CalcValue.ofDecimal(BigDecimal(d).round(MathContext(12)))
    }

    private fun checkSd(sd: CalcValue) {
        if (sd.signum <= 0) domain("The standard deviation must be greater than 0")
    }

    fun normalPdf(x: CalcValue, mean: CalcValue = ZERO, sd: CalcValue = ONE): CalcValue {
        checkSd(sd)
        val s = sd.decimal.toDouble()
        val z = (x.decimal.toDouble() - mean.decimal.toDouble()) / s
        return fromDouble(Math.exp(-0.5 * z * z) / (s * Math.sqrt(2.0 * Math.PI)))
    }

    /** P(lower ≤ X ≤ upper); a null bound means minus or plus infinity. */
    fun normalCdf(lower: CalcValue?, upper: CalcValue?, mean: CalcValue = ZERO, sd: CalcValue = ONE): CalcValue {
        checkSd(sd)
        val m = mean.decimal.toDouble()
        val s = sd.decimal.toDouble()
        val zl = lower?.let { (it.decimal.toDouble() - m) / s }
        val zu = upper?.let { (it.decimal.toDouble() - m) / s }
        if (zl != null && zu != null && zl > zu) domain("The lower bound must not be above the upper bound")
        // Use the upper tail when both bounds are right of the mean, so small areas keep their digits.
        val result = if ((zl ?: Double.NEGATIVE_INFINITY) > 0) tail(zl!!) - (zu?.let { tail(it) } ?: 0.0)
        else (zu?.let { lowerTail(it) } ?: 1.0) - (zl?.let { lowerTail(it) } ?: 0.0)
        return fromDouble(maxOf(0.0, result))
    }

    private fun lowerTail(z: Double): Double = 0.5 * erfc(-z / Math.sqrt(2.0))
    private fun tail(z: Double): Double = 0.5 * erfc(z / Math.sqrt(2.0))

    /** x with P(X ≤ x) = [p]. */
    fun invNormal(p: CalcValue, mean: CalcValue = ZERO, sd: CalcValue = ONE): CalcValue {
        checkSd(sd)
        if (p.signum <= 0 || Arith.compare(p, ONE) >= 0) domain("The area must be between 0 and 1")
        val pd = p.decimal.toDouble()
        var x = acklam(pd)
        repeat(3) {
            val e = lowerTail(x) - pd
            val u = e * Math.sqrt(2.0 * Math.PI) * Math.exp(x * x / 2.0)
            x -= u / (1.0 + x * u / 2.0)
        }
        val m = mean.decimal.toDouble()
        val s = sd.decimal.toDouble()
        return fromDouble(m + s * x)
    }

    private fun acklam(p: Double): Double {
        val a = doubleArrayOf(-3.969683028665376e+01, 2.209460984245205e+02, -2.759285104469687e+02, 1.383577518672690e+02, -3.066479806614716e+01, 2.506628277459239e+00)
        val b = doubleArrayOf(-5.447609879822406e+01, 1.615858368580409e+02, -1.556989798598866e+02, 6.680131188771972e+01, -1.328068155288572e+01)
        val c = doubleArrayOf(-7.784894002430293e-03, -3.223964580411365e-01, -2.400758277161838e+00, -2.549732539343734e+00, 4.374664141464968e+00, 2.938163982698783e+00)
        val d = doubleArrayOf(7.784695709041462e-03, 3.224671290700398e-01, 2.445134137142996e+00, 3.754408661907416e+00)
        val low = 0.02425
        return when {
            p < low -> {
                val q = Math.sqrt(-2.0 * Math.log(p))
                (((((c[0] * q + c[1]) * q + c[2]) * q + c[3]) * q + c[4]) * q + c[5]) /
                    ((((d[0] * q + d[1]) * q + d[2]) * q + d[3]) * q + 1.0)
            }
            p > 1.0 - low -> {
                val q = Math.sqrt(-2.0 * Math.log(1.0 - p))
                -(((((c[0] * q + c[1]) * q + c[2]) * q + c[3]) * q + c[4]) * q + c[5]) /
                    ((((d[0] * q + d[1]) * q + d[2]) * q + d[3]) * q + 1.0)
            }
            else -> {
                val q = p - 0.5
                val r = q * q
                (((((a[0] * r + a[1]) * r + a[2]) * r + a[3]) * r + a[4]) * r + a[5]) * q /
                    (((((b[0] * r + b[1]) * r + b[2]) * r + b[3]) * r + b[4]) * r + 1.0)
            }
        }
    }

    /** Complementary error function, accurate to about 1e-15 relative. */
    internal fun erfc(x: Double): Double {
        if (x.isNaN()) return Double.NaN
        if (x < 0) return 2.0 - erfc(-x)
        if (x < 0.5) return 1.0 - erfSeries(x)
        if (x > 27.0) return 0.0
        // Continued fraction  erfc(x) = e^(-x²)/√π · 1/(x + (1/2)/(x + (2/2)/(x + (3/2)/(x + …)))), evaluated from the tail.
        var f = x
        for (k in 1500 downTo 1) f = x + (k / 2.0) / f
        return Math.exp(-x * x) / (Math.sqrt(Math.PI) * f)
    }

    private fun erfSeries(x: Double): Double {
        var term = x
        var sum = x
        var n = 0
        while (Math.abs(term) > 1e-17 * Math.abs(sum) && n < 200) {
            n++
            term *= -x * x / n
            sum += term / (2 * n + 1)
        }
        return 2.0 / Math.sqrt(Math.PI) * sum
    }

    // ------------------------------------------------------------------ binomial

    private fun checkBinomial(n: Int, p: CalcValue) {
        if (n < 0) domain("n must be 0 or more")
        if (n > MAX_BINOMIAL_N) throw CalcException(ErrorKind.OVERFLOW, "n is too large (maximum $MAX_BINOMIAL_N)")
        if (p.signum < 0 || Arith.compare(p, ONE) > 0) domain("The probability p must be between 0 and 1")
    }

    private fun bd(v: CalcValue): BigDecimal = v.decimal

    /** Tiny probabilities below the number range are reported as 0 instead of failing. */
    private fun fromBig(d: BigDecimal): CalcValue =
        try { CalcValue.ofDecimal(d) } catch (e: CalcException) { if (e.error.kind == ErrorKind.UNDERFLOW) ZERO else throw e }

    fun binomialPdf(n: Int, p: CalcValue, k: Int): CalcValue {
        checkBinomial(n, p)
        if (k < 0 || k > n) return ZERO
        if (n <= EXACT_BINOMIAL_N) return exactBinomialTerm(n, p, k)
        return fromBig(binomialTerms(n, p, k).last())
    }

    fun binomialCdf(n: Int, p: CalcValue, k: Int): CalcValue {
        checkBinomial(n, p)
        if (k < 0) return ZERO
        if (k >= n) return ONE
        if (n <= EXACT_BINOMIAL_N) {
            var sum = ZERO
            for (j in 0..k) sum = Arith.add(sum, exactBinomialTerm(n, p, j))
            return sum
        }
        return fromBig(binomialTerms(n, p, k).fold(BigDecimal.ZERO) { acc, t -> acc.add(t, MC) })
    }

    fun invBinomial(n: Int, p: CalcValue, area: CalcValue): Int {
        checkBinomial(n, p)
        if (area.signum < 0 || Arith.compare(area, ONE) > 0) domain("The area must be between 0 and 1")
        val target = bd(area).subtract(TOLERANCE)
        var sum = BigDecimal.ZERO
        var k = 0
        for (term in binomialSequence(n, p)) {
            sum = sum.add(term, MC)
            if (sum.compareTo(target) >= 0 || k == n) return k
            k++
        }
        return n
    }

    private val TOLERANCE = BigDecimal("1E-30")

    private fun exactBinomialTerm(n: Int, p: CalcValue, k: Int): CalcValue {
        val q = Arith.sub(ONE, p)
        return Arith.mul(Arith.combinations(CalcValue.ofLong(n.toLong()), CalcValue.ofLong(k.toLong())),
            Arith.mul(Arith.pow(p, CalcValue.ofLong(k.toLong())), Arith.pow(q, CalcValue.ofLong((n - k).toLong()))))
    }

    /** pmf(0..k) as 60-digit decimals. */
    private fun binomialTerms(n: Int, p: CalcValue, k: Int): List<BigDecimal> {
        val out = ArrayList<BigDecimal>(k + 1)
        for (t in binomialSequence(n, p)) {
            out.add(t)
            if (out.size > k) break
        }
        return out
    }

    private fun binomialSequence(n: Int, p: CalcValue): Sequence<BigDecimal> = sequence {
        val pd = bd(p)
        val qd = BigDecimal.ONE.subtract(pd)
        if (pd.signum() == 0) { yield(BigDecimal.ONE); for (i in 1..n) yield(BigDecimal.ZERO); return@sequence }
        if (qd.signum() == 0) { for (i in 0 until n) yield(BigDecimal.ZERO); yield(BigDecimal.ONE); return@sequence }
        // Start from the log scale to avoid underflow of (1-p)^n for large n, then use the ratio recurrence.
        val logStart = BigMath.ln(qd).multiply(BigDecimal(n), MC)
        var term = BigMath.exp(logStart)
        val ratio = pd.divide(qd, MC)
        yield(term)
        for (k in 1..n) {
            term = term.multiply(BigDecimal(n - k + 1), MC).divide(BigDecimal(k), MC).multiply(ratio, MC)
            yield(term)
        }
    }

    // ------------------------------------------------------------------ Poisson

    private fun checkPoisson(lambda: CalcValue) {
        if (lambda.signum <= 0) domain("λ must be greater than 0")
        if (Arith.compare(lambda, CalcValue.ofLong(MAX_POISSON_LAMBDA.toLong())) > 0) {
            throw CalcException(ErrorKind.OVERFLOW, "λ is too large (maximum $MAX_POISSON_LAMBDA)")
        }
    }

    private fun poissonSequence(lambda: CalcValue): Sequence<BigDecimal> = sequence {
        val l = bd(lambda)
        var term = BigMath.exp(l.negate())
        yield(term)
        var k = 1
        while (true) {
            term = term.multiply(l, MC).divide(BigDecimal(k), MC)
            yield(term)
            k++
        }
    }

    fun poissonPdf(lambda: CalcValue, k: Int): CalcValue {
        checkPoisson(lambda)
        if (k < 0) return ZERO
        if (k > MAX_POISSON_K) throw CalcException(ErrorKind.OVERFLOW, "k is too large (maximum $MAX_POISSON_K)")
        return fromBig(poissonSequence(lambda).drop(k).first())
    }

    fun poissonCdf(lambda: CalcValue, k: Int): CalcValue {
        checkPoisson(lambda)
        if (k < 0) return ZERO
        if (k > MAX_POISSON_K) throw CalcException(ErrorKind.OVERFLOW, "k is too large (maximum $MAX_POISSON_K)")
        var sum = BigDecimal.ZERO
        for (t in poissonSequence(lambda).take(k + 1)) sum = sum.add(t, MC)
        return fromBig(if (sum.compareTo(BigDecimal.ONE) > 0) BigDecimal.ONE else sum)
    }

    fun invPoisson(lambda: CalcValue, area: CalcValue): Int {
        checkPoisson(lambda)
        if (area.signum < 0 || Arith.compare(area, ONE) >= 0) domain("The area must be from 0 up to (but not including) 1")
        val target = bd(area).subtract(TOLERANCE)
        var sum = BigDecimal.ZERO
        var k = 0
        for (t in poissonSequence(lambda)) {
            sum = sum.add(t, MC)
            if (sum.compareTo(target) >= 0) return k
            k++
            if (k > MAX_POISSON_K) throw CalcException(ErrorKind.OVERFLOW, "The answer is too large to search for")
        }
        return k
    }
}

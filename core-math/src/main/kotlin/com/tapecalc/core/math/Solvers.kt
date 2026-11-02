package com.tapecalc.core.math

import com.tapecalc.core.precision.Rational
import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext

/** One solution of a polynomial: real when [im] is zero. */
class PolyRoot(val re: CalcValue, val im: CalcValue) {
    val isReal: Boolean get() = im.isZero

    /** "2", "-1.5", "1 + 2i", "0.5 - 0.866025403784439i" */
    fun format(): String {
        if (isReal) return re.toString()
        val imAbs = Arith.abs(im)
        val sign = if (im.signum < 0) "-" else "+"
        val imText = if (Arith.compare(imAbs, CalcValue.ONE) == 0) "i" else "${imAbs}i"
        return if (re.isZero) (if (im.signum < 0) "-$imText" else imText) else "$re $sign $imText"
    }
}

/**
 * Roots of polynomials up to degree 4 (quadratic, cubic, quartic).
 * Rational roots are found exactly first (x³ - 6x² + 11x - 6 gives exactly 1, 2, 3); what remains is solved
 * with the quadratic formula or, for an irreducible cubic or quartic, with the Durand-Kerner method at 50 digits.
 */
object Polynomial {
    const val MAX_DEGREE = 4
    private val MC = MathContext(50)

    private fun domain(message: String): Nothing = throw CalcException(ErrorKind.DOMAIN, message)

    /** Evaluates each text (so `1/2` and `sqrt(2)` work) into coefficients, highest power first. */
    fun parseCoefficients(texts: List<String>, ctx: EvalContext = EvalContext()): List<CalcValue> =
        texts.mapIndexed { i, t ->
            val text = t.trim().ifEmpty { "0" }
            when (val o = Calculator.evaluate(text, ctx)) {
                is EvalOutcome.Success -> o.result.value
                is EvalOutcome.Failure -> throw CalcException(ErrorKind.SYNTAX, "Coefficient ${i + 1} ('$text'): ${o.error.message}")
            }
        }

    /** Solves c[0]·x^n + c[1]·x^(n-1) + … + c[n] = 0. */
    fun solve(coefficients: List<CalcValue>): List<PolyRoot> {
        var c = coefficients.dropWhile { it.isZero }
        if (c.isEmpty()) domain("Every number is a solution (all coefficients are 0)")
        val degree = c.size - 1
        if (degree == 0) domain("No solution: the equation reduces to ${c[0]} = 0")
        if (degree > MAX_DEGREE) domain("Only equations up to degree $MAX_DEGREE can be solved (this one has degree $degree)")

        val roots = ArrayList<PolyRoot>()
        // Zero roots: trailing zero coefficients.
        while (c.size > 1 && c.last().isZero) {
            roots.add(PolyRoot(CalcValue.ZERO, CalcValue.ZERO))
            c = c.dropLast(1)
        }
        // Exact rational roots.
        if (c.size - 1 >= 3 && c.all { it.exact != null }) {
            val found = rationalRoots(c.map { it.exact!! })
            for (r in found.first) roots.add(PolyRoot(CalcValue.ofRational(r), CalcValue.ZERO))
            c = found.second.map { CalcValue.ofRational(it) }
        }
        when (c.size - 1) {
            0 -> Unit
            1 -> roots.add(PolyRoot(Arith.div(Arith.neg(c[1]), c[0]), CalcValue.ZERO))
            2 -> roots.addAll(quadratic(c[0], c[1], c[2]))
            else -> roots.addAll(durandKerner(c))
        }
        return sortRoots(roots)
    }

    private fun sortRoots(r: List<PolyRoot>): List<PolyRoot> {
        val real = r.filter { it.isReal }.sortedWith { a, b -> Arith.compare(a.re, b.re) }
        val cx = r.filter { !it.isReal }.sortedWith { a, b ->
            val c = Arith.compare(a.re, b.re)
            if (c != 0) c else Arith.compare(b.im, a.im)
        }
        return real + cx
    }

    private fun quadratic(a: CalcValue, b: CalcValue, c: CalcValue): List<PolyRoot> {
        val two = CalcValue.ofLong(2)
        val disc = Arith.sub(Arith.mul(b, b), Arith.mul(CalcValue.ofLong(4), Arith.mul(a, c)))
        val twoA = Arith.mul(two, a)
        return if (disc.signum >= 0) {
            val s = Arith.sqrt(disc)
            listOf(
                PolyRoot(Arith.div(Arith.sub(Arith.neg(b), s), twoA), CalcValue.ZERO),
                PolyRoot(Arith.div(Arith.add(Arith.neg(b), s), twoA), CalcValue.ZERO)
            )
        } else {
            val re = Arith.div(Arith.neg(b), twoA)
            val im = Arith.abs(Arith.div(Arith.sqrt(Arith.neg(disc)), twoA))
            listOf(PolyRoot(re, im), PolyRoot(re, Arith.neg(im)))
        }
    }

    // ---------------------------------------------------------------- exact rational roots

    /** Returns the rational roots found (with multiplicity) and the polynomial left after dividing them out. */
    private fun rationalRoots(c: List<Rational>): Pair<List<Rational>, List<Rational>> {
        var poly = c
        val found = ArrayList<Rational>()
        // Scale to integer coefficients.
        var lcm = BigInteger.ONE
        for (r in poly) lcm = lcm.multiply(r.den).divide(lcm.gcd(r.den))
        val ints = poly.map { it.num.multiply(lcm.divide(it.den)) }
        val a0 = ints.last().abs()
        val an = ints.first().abs()
        if (a0.bitLength() > 40 || an.bitLength() > 40) return emptyList<Rational>() to c
        val ps = divisors(a0.toLong())
        val qs = divisors(an.toLong())
        val candidates = LinkedHashSet<Rational>()
        for (p in ps) for (q in qs) {
            candidates.add(Rational.of(BigInteger.valueOf(p), BigInteger.valueOf(q)))
            candidates.add(Rational.of(BigInteger.valueOf(-p), BigInteger.valueOf(q)))
        }
        for (cand in candidates) {
            while (poly.size > 3 && evalPoly(poly, cand).isZero) {
                found.add(cand)
                poly = deflate(poly, cand)
            }
        }
        return found to poly
    }

    private fun divisors(n: Long): List<Long> {
        val out = ArrayList<Long>()
        var i = 1L
        while (i * i <= n) {
            if (n % i == 0L) {
                out.add(i)
                if (i != n / i) out.add(n / i)
            }
            i++
        }
        return out
    }

    private fun evalPoly(c: List<Rational>, x: Rational): Rational {
        var acc = Rational.ZERO
        for (k in c) acc = acc * x + k
        return acc
    }

    /** Synthetic division by (x - root); the remainder is zero. */
    private fun deflate(c: List<Rational>, root: Rational): List<Rational> {
        val out = ArrayList<Rational>()
        var acc = Rational.ZERO
        for (i in 0 until c.size - 1) {
            acc = acc * root + c[i]
            out.add(acc)
        }
        return out
    }

    // ---------------------------------------------------------------- numeric (cubic / quartic)

    private class C(val re: BigDecimal, val im: BigDecimal) {
        operator fun plus(o: C) = C(re.add(o.re, MC), im.add(o.im, MC))
        operator fun minus(o: C) = C(re.subtract(o.re, MC), im.subtract(o.im, MC))
        operator fun times(o: C) = C(re.multiply(o.re, MC).subtract(im.multiply(o.im, MC), MC), re.multiply(o.im, MC).add(im.multiply(o.re, MC), MC))
        operator fun div(o: C): C {
            val d = o.re.multiply(o.re, MC).add(o.im.multiply(o.im, MC), MC)
            return C(
                re.multiply(o.re, MC).add(im.multiply(o.im, MC), MC).divide(d, MC),
                im.multiply(o.re, MC).subtract(re.multiply(o.im, MC), MC).divide(d, MC)
            )
        }
        fun size(): BigDecimal = re.abs().add(im.abs())
    }

    private fun durandKerner(c: List<CalcValue>): List<PolyRoot> {
        val n = c.size - 1
        val lead = c[0].decimal
        val coeff = c.map { C(it.decimal.divide(lead, MC), BigDecimal.ZERO) }   // monic
        var radius = BigDecimal.ONE
        for (k in 1..n) radius = radius.max(coeff[k].re.abs().add(BigDecimal.ONE))
        val seed = C(BigDecimal("0.4"), BigDecimal("0.9"))
        val z = ArrayList<C>()
        var p = C(radius, BigDecimal.ZERO)
        for (k in 0 until n) {
            z.add(p)
            p = p * seed
        }
        fun poly(x: C): C {
            var acc = C(BigDecimal.ONE, BigDecimal.ZERO)
            for (k in 1..n) acc = acc * x + coeff[k]
            return acc
        }
        val tol = BigDecimal("1E-40")
        repeat(2000) {
            var biggest = BigDecimal.ZERO
            for (k in 0 until n) {
                var denom = C(BigDecimal.ONE, BigDecimal.ZERO)
                for (j in 0 until n) if (j != k) denom = denom * (z[k] - z[j])
                if (denom.size().signum() == 0) { z[k] = z[k] + C(BigDecimal("1E-20"), BigDecimal("1E-20")); continue }
                val delta = poly(z[k]) / denom
                z[k] = z[k] - delta
                biggest = biggest.max(delta.size())
            }
            if (biggest < tol) return finish(z)
        }
        return finish(z)
    }

    private fun finish(z: List<C>): List<PolyRoot> {
        val clean = z.map { r ->
            val limit = BigDecimal("1E-15").multiply(BigDecimal.ONE.max(r.re.abs()))
            if (r.im.abs() <= limit) C(r.re, BigDecimal.ZERO) else r
        }.toMutableList()
        // Make complex roots exact conjugate pairs.
        for (i in clean.indices) {
            if (clean[i].im.signum() <= 0) continue
            var best = -1
            var bestDist = BigDecimal.TEN
            for (j in clean.indices) {
                if (j == i || clean[j].im.signum() >= 0) continue
                val d = (clean[i] - C(clean[j].re, clean[j].im.negate())).size()
                if (d < bestDist) { bestDist = d; best = j }
            }
            if (best >= 0) {
                val re = clean[i].re.add(clean[best].re).divide(BigDecimal(2), MC)
                val im = clean[i].im.subtract(clean[best].im).divide(BigDecimal(2), MC)
                clean[i] = C(re, im)
                clean[best] = C(re, im.negate())
            }
        }
        return clean.map { PolyRoot(Numeric.snap(it.re.round(MathContext(40))), Numeric.snap(it.im.round(MathContext(40)))) }
    }
}

/** Result of solving simultaneous linear equations. */
sealed interface LinearSolution {
    /** One answer: the value of each unknown, in order. */
    class Unique(val values: List<CalcValue>) : LinearSolution

    /** Infinitely many answers (the equations do not pin every unknown down). */
    class Infinite(val rank: Int, val unknowns: Int) : LinearSolution

    /** The equations contradict each other. */
    object None : LinearSolution
}

/** Simultaneous linear equations with up to [MAX_UNKNOWNS] unknowns, solved with exact fractions. */
object LinearSystem {
    const val MAX_UNKNOWNS = 4

    /** [rows] holds the text of each coefficient, then the constant: `a x + b y = c` is `[a, b, c]`. */
    fun parse(rows: List<List<String>>, ctx: EvalContext = EvalContext()): Pair<List<List<CalcValue>>, List<CalcValue>> {
        val a = ArrayList<List<CalcValue>>()
        val b = ArrayList<CalcValue>()
        rows.forEachIndexed { r, row ->
            val values = row.mapIndexed { c, t ->
                val text = t.trim().ifEmpty { "0" }
                when (val o = Calculator.evaluate(text, ctx)) {
                    is EvalOutcome.Success -> o.result.value
                    is EvalOutcome.Failure -> throw CalcException(ErrorKind.SYNTAX, "Row ${r + 1}, column ${c + 1} ('$text'): ${o.error.message}")
                }
            }
            a.add(values.dropLast(1))
            b.add(values.last())
        }
        return a to b
    }

    fun solve(a: List<List<CalcValue>>, b: List<CalcValue>): LinearSolution {
        val m = a.size
        if (m == 0) throw CalcException(ErrorKind.EMPTY, "Enter at least one equation")
        val n = a[0].size
        if (n > MAX_UNKNOWNS) throw CalcException(ErrorKind.DOMAIN, "At most $MAX_UNKNOWNS unknowns are supported")
        val g = Array(m) { r -> Array(n + 1) { c -> if (c < n) a[r][c] else b[r] } }
        var row = 0
        val pivotCols = ArrayList<Int>()
        for (col in 0 until n) {
            if (row >= m) break
            var pivot = -1
            for (r in row until m) {
                if (!g[r][col].isZero && (pivot < 0 || Arith.compare(Arith.abs(g[r][col]), Arith.abs(g[pivot][col])) > 0)) pivot = r
            }
            if (pivot < 0) continue
            val t = g[row]; g[row] = g[pivot]; g[pivot] = t
            for (r in 0 until m) {
                if (r == row || g[r][col].isZero) continue
                val f = Arith.div(g[r][col], g[row][col])
                for (c in col..n) g[r][c] = Arith.sub(g[r][c], Arith.mul(f, g[row][c]))
            }
            pivotCols.add(col)
            row++
        }
        for (r in row until m) if (!g[r][n].isZero) return LinearSolution.None
        if (pivotCols.size < n) return LinearSolution.Infinite(pivotCols.size, n)
        return LinearSolution.Unique(List(n) { Arith.div(g[it][n], g[it][it]) })
    }
}

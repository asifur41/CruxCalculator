package com.tapecalc.core.math.stats

import com.tapecalc.core.math.Arith
import com.tapecalc.core.math.CalcException
import com.tapecalc.core.math.CalcValue
import com.tapecalc.core.math.ErrorKind
import com.tapecalc.core.math.EvalContext
import com.tapecalc.core.math.Functions

/** The curve shapes that can be fitted to paired data (the same set as a Casio fx-991 plus y = a·b^x). */
enum class RegressionType(val label: String) {
    LINEAR("Linear  y = a + bx"),
    QUADRATIC("Quadratic  y = a + bx + cx²"),
    LOGARITHMIC("Logarithmic  y = a + b·ln x"),
    EXPONENTIAL("Exponential  y = a·e^(bx)"),
    AB_EXPONENTIAL("Exponential  y = a·b^x"),
    POWER("Power  y = a·x^b"),
    INVERSE("Inverse  y = a + b/x")
}

/**
 * A fitted curve.
 *
 * @property coefficients a, b (and c for quadratic), in the order of the equation shown in [RegressionType.label]
 * @property r correlation coefficient of the (transformed) data; null for quadratic, or when y does not vary
 * @property r2 coefficient of determination
 */
class Regression(
    val type: RegressionType,
    val coefficients: List<CalcValue>,
    val r: CalcValue?,
    val r2: CalcValue,
    val n: Int
) {
    private val a get() = coefficients[0]
    private val b get() = coefficients[1]
    private val c get() = coefficients.getOrNull(2)

    /** The fitted equation as text, for example `y = 1.5 + 2x`. */
    val equation: String
        get() = when (type) {
            RegressionType.LINEAR -> "y = ${num(a)}${signed(b, "x")}"
            RegressionType.QUADRATIC -> "y = ${num(a)}${signed(b, "x")}${signed(c!!, "x²")}"
            RegressionType.LOGARITHMIC -> "y = ${num(a)}${signed(b, "·ln x")}"
            RegressionType.EXPONENTIAL -> "y = ${num(a)}·e^(${num(b)}x)"
            RegressionType.AB_EXPONENTIAL -> "y = ${num(a)}·${num(b).let { if (b.signum < 0) "($it)" else it }}^x"
            RegressionType.POWER -> "y = ${num(a)}·x^${num(b).let { if (b.signum < 0) "($it)" else it }}"
            RegressionType.INVERSE -> "y = ${num(a)}${signed(b, "/x")}"
        }

    /** Predicted y (ŷ) for [x]. */
    fun predictY(x: CalcValue): CalcValue = when (type) {
        RegressionType.LINEAR -> Arith.add(a, Arith.mul(b, x))
        RegressionType.QUADRATIC -> Arith.add(Arith.add(a, Arith.mul(b, x)), Arith.mul(c!!, Arith.mul(x, x)))
        RegressionType.LOGARITHMIC -> Arith.add(a, Arith.mul(b, ln(positive(x, "x"))))
        RegressionType.EXPONENTIAL -> Arith.mul(a, exp(Arith.mul(b, x)))
        RegressionType.AB_EXPONENTIAL -> Arith.mul(a, Arith.pow(b, x))
        RegressionType.POWER -> Arith.mul(a, Arith.pow(positive(x, "x"), b))
        RegressionType.INVERSE -> {
            if (x.isZero) throw CalcException(ErrorKind.DIVISION_BY_ZERO, "x cannot be 0 for an inverse fit")
            Arith.add(a, Arith.div(b, x))
        }
    }

    /** Estimated x (x̂) for a given [y]. Quadratic fits may have 0, 1 or 2 answers; the others at most one. */
    fun estimateX(y: CalcValue): List<CalcValue> {
        fun noSlope(): Nothing = throw CalcException(ErrorKind.DOMAIN, "The fitted curve is flat, so x cannot be estimated")
        return when (type) {
            RegressionType.LINEAR -> {
                if (b.isZero) noSlope()
                listOf(Arith.div(Arith.sub(y, a), b))
            }
            RegressionType.QUADRATIC -> {
                val cc = c!!
                val k = Arith.sub(a, y)
                if (cc.isZero) {
                    if (b.isZero) noSlope()
                    return listOf(Arith.div(Arith.neg(k), b))
                }
                val disc = Arith.sub(Arith.mul(b, b), Arith.mul(CalcValue.ofLong(4), Arith.mul(cc, k)))
                if (disc.signum < 0) return emptyList()
                val root = Arith.sqrt(disc)
                val twoC = Arith.mul(CalcValue.ofLong(2), cc)
                val x1 = Arith.div(Arith.sub(Arith.neg(b), root), twoC)
                val x2 = Arith.div(Arith.add(Arith.neg(b), root), twoC)
                if (disc.isZero) listOf(x1) else listOf(x1, x2).sortedWith { p, q -> Arith.compare(p, q) }
            }
            RegressionType.LOGARITHMIC -> {
                if (b.isZero) noSlope()
                listOf(exp(Arith.div(Arith.sub(y, a), b)))
            }
            RegressionType.EXPONENTIAL -> {
                if (b.isZero) noSlope()
                listOf(Arith.div(ln(positive(Arith.div(y, a), "y/a")), b))
            }
            RegressionType.AB_EXPONENTIAL -> {
                val lb = ln(positive(b, "b"))
                if (lb.isZero) noSlope()
                listOf(Arith.div(ln(positive(Arith.div(y, a), "y/a")), lb))
            }
            RegressionType.POWER -> {
                if (b.isZero) noSlope()
                listOf(Arith.pow(positive(Arith.div(y, a), "y/a"), Arith.div(CalcValue.ONE, b)))
            }
            RegressionType.INVERSE -> {
                val d = Arith.sub(y, a)
                if (d.isZero) throw CalcException(ErrorKind.DOMAIN, "No x gives y = a for an inverse fit")
                listOf(Arith.div(b, d))
            }
        }
    }

    private fun num(v: CalcValue): String = v.toString()

    private fun signed(v: CalcValue, suffix: String): String =
        if (v.signum < 0) " - ${Arith.neg(v)}$suffix" else " + $v$suffix"

    internal companion object {
        fun positive(v: CalcValue, what: String): CalcValue {
            if (v.signum <= 0) throw CalcException(ErrorKind.DOMAIN, "$what must be greater than 0 for this fit")
            return v
        }

        fun ln(v: CalcValue): CalcValue = Functions.call("ln", listOf(v), EvalContext())
        fun exp(v: CalcValue): CalcValue = Functions.call("exp", listOf(v), EvalContext())
    }
}

/** The fitting code: weighted least squares done with exact arithmetic where the data allows it. */
internal object RegressionEngine {

    private fun fail(message: String): Nothing = throw CalcException(ErrorKind.DOMAIN, message)

    fun fit(type: RegressionType, xs: List<CalcValue>, ys: List<CalcValue>, w: List<CalcValue>): Regression {
        val n = xs.size
        return when (type) {
            RegressionType.QUADRATIC -> quadratic(xs, ys, w, n)
            else -> {
                val tx: List<CalcValue> = when (type) {
                    RegressionType.LOGARITHMIC, RegressionType.POWER ->
                        xs.map { Regression.ln(Regression.positive(it, "Every x")) }
                    RegressionType.INVERSE -> xs.map {
                        if (it.isZero) fail("x cannot be 0 for an inverse fit")
                        Arith.div(CalcValue.ONE, it)
                    }
                    else -> xs
                }
                val ty: List<CalcValue> = when (type) {
                    RegressionType.EXPONENTIAL, RegressionType.AB_EXPONENTIAL, RegressionType.POWER ->
                        ys.map { Regression.ln(Regression.positive(it, "Every y")) }
                    else -> ys
                }
                val line = line(tx, ty, w)
                val a = when (type) {
                    RegressionType.EXPONENTIAL, RegressionType.AB_EXPONENTIAL, RegressionType.POWER -> Regression.exp(line.a)
                    else -> line.a
                }
                val b = if (type == RegressionType.AB_EXPONENTIAL) Regression.exp(line.b) else line.b
                Regression(type, listOf(a, b), line.r, line.r2, n)
            }
        }
    }

    private class Line(val a: CalcValue, val b: CalcValue, val r: CalcValue?, val r2: CalcValue)

    private fun line(xs: List<CalcValue>, ys: List<CalcValue>, w: List<CalcValue>): Line {
        var n = CalcValue.ZERO
        var sx = CalcValue.ZERO
        var sy = CalcValue.ZERO
        var sxx = CalcValue.ZERO
        var sxy = CalcValue.ZERO
        var syy = CalcValue.ZERO
        for (i in xs.indices) {
            val wx = Arith.mul(w[i], xs[i])
            n = Arith.add(n, w[i])
            sx = Arith.add(sx, wx)
            sy = Arith.add(sy, Arith.mul(w[i], ys[i]))
            sxx = Arith.add(sxx, Arith.mul(wx, xs[i]))
            sxy = Arith.add(sxy, Arith.mul(wx, ys[i]))
            syy = Arith.add(syy, Arith.mul(Arith.mul(w[i], ys[i]), ys[i]))
        }
        val denX = Arith.sub(Arith.mul(n, sxx), Arith.mul(sx, sx))
        if (denX.isZero) {
            fail(if (xs.size < 2) "At least two data points are needed to fit a curve" else "All x values are the same, so no curve can be fitted")
        }
        val cross = Arith.sub(Arith.mul(n, sxy), Arith.mul(sx, sy))
        val b = Arith.div(cross, denX)
        val a = Arith.div(Arith.sub(sy, Arith.mul(b, sx)), n)
        val denY = Arith.sub(Arith.mul(n, syy), Arith.mul(sy, sy))
        if (denY.isZero) return Line(a, b, null, CalcValue.ONE)
        val r = Arith.div(cross, Arith.sqrt(Arith.mul(denX, denY)))
        return Line(a, b, r, Arith.mul(r, r))
    }

    private fun quadratic(xs: List<CalcValue>, ys: List<CalcValue>, w: List<CalcValue>, count: Int): Regression {
        // Normal equations for y = a + bx + cx²:  [S0 S1 S2; S1 S2 S3; S2 S3 S4] (a b c) = (T0 T1 T2)
        val s = Array(5) { CalcValue.ZERO }
        val t = Array(3) { CalcValue.ZERO }
        for (i in xs.indices) {
            var p = w[i]
            for (k in 0..4) {
                s[k] = Arith.add(s[k], p)
                if (k <= 2) t[k] = Arith.add(t[k], Arith.mul(p, ys[i]))
                p = Arith.mul(p, xs[i])
            }
        }
        val m = Array(3) { r -> Array(4) { c -> if (c < 3) s[r + c] else t[r] } }
        val sol = gauss(m) ?: fail(
            if (xs.distinctBy { it.decimal.stripTrailingZeros() }.size < 3) "A quadratic fit needs at least three different x values"
            else "These points cannot be fitted by a quadratic"
        )
        val coeffs = listOf(sol[0], sol[1], sol[2])
        // R² = 1 - SSres / SStot
        var n = CalcValue.ZERO
        var sy = CalcValue.ZERO
        for (i in xs.indices) {
            n = Arith.add(n, w[i])
            sy = Arith.add(sy, Arith.mul(w[i], ys[i]))
        }
        val mean = Arith.div(sy, n)
        var ssRes = CalcValue.ZERO
        var ssTot = CalcValue.ZERO
        val reg = Regression(RegressionType.QUADRATIC, coeffs, null, CalcValue.ONE, count)
        for (i in xs.indices) {
            val e = Arith.sub(ys[i], reg.predictY(xs[i]))
            val d = Arith.sub(ys[i], mean)
            ssRes = Arith.add(ssRes, Arith.mul(w[i], Arith.mul(e, e)))
            ssTot = Arith.add(ssTot, Arith.mul(w[i], Arith.mul(d, d)))
        }
        val r2 = if (ssTot.isZero) CalcValue.ONE else Arith.sub(CalcValue.ONE, Arith.div(ssRes, ssTot))
        return Regression(RegressionType.QUADRATIC, coeffs, null, r2, count)
    }

    /** Gauss-Jordan elimination with partial pivoting on an augmented matrix; null when it is singular. */
    private fun gauss(m: Array<Array<CalcValue>>): List<CalcValue>? {
        val n = m.size
        for (col in 0 until n) {
            var pivot = col
            for (r in col + 1 until n) if (Arith.compare(Arith.abs(m[r][col]), Arith.abs(m[pivot][col])) > 0) pivot = r
            if (m[pivot][col].isZero) return null
            val tmp = m[col]; m[col] = m[pivot]; m[pivot] = tmp
            for (r in 0 until n) {
                if (r == col || m[r][col].isZero) continue
                val f = Arith.div(m[r][col], m[col][col])
                for (c in col until n + 1) m[r][c] = Arith.sub(m[r][c], Arith.mul(f, m[col][c]))
            }
        }
        return List(n) { Arith.div(m[it][n], m[it][it]) }
    }
}

package com.tapecalc.core.math

import com.tapecalc.core.precision.BigMath
import com.tapecalc.core.precision.Precision
import com.tapecalc.core.precision.Rational
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

/** Arithmetic on [CalcValue]: exact rational math while possible, 40-digit decimals otherwise. */
internal object Arith {
    private val MC = Precision.INTERNAL
    const val MAX_FACTORIAL = 3200
    private const val MAX_COMBINATORIC = 5000

    private fun domain(message: String): Nothing = throw CalcException(ErrorKind.DOMAIN, message)
    private fun divZero(message: String = "Division by zero"): Nothing =
        throw CalcException(ErrorKind.DIVISION_BY_ZERO, message)

    fun add(a: CalcValue, b: CalcValue): CalcValue {
        val x = a.exact
        val y = b.exact
        return if (x != null && y != null) CalcValue.ofRational(x + y)
        else CalcValue.ofDecimal(a.decimal.add(b.decimal, MC))
    }

    fun sub(a: CalcValue, b: CalcValue): CalcValue {
        val x = a.exact
        val y = b.exact
        return if (x != null && y != null) CalcValue.ofRational(x - y)
        else CalcValue.ofDecimal(a.decimal.subtract(b.decimal, MC))
    }

    fun mul(a: CalcValue, b: CalcValue): CalcValue {
        val x = a.exact
        val y = b.exact
        return if (x != null && y != null) CalcValue.ofRational(x * y)
        else CalcValue.ofDecimal(a.decimal.multiply(b.decimal, MC))
    }

    fun div(a: CalcValue, b: CalcValue): CalcValue {
        if (b.isZero) divZero()
        val x = a.exact
        val y = b.exact
        return if (x != null && y != null) CalcValue.ofRational(x / y)
        else CalcValue.ofDecimal(a.decimal.divide(b.decimal, MC))
    }

    fun neg(a: CalcValue): CalcValue {
        val x = a.exact
        return if (x != null) CalcValue.ofRational(-x) else CalcValue.ofDecimal(a.decimal.negate())
    }

    fun abs(a: CalcValue): CalcValue = if (a.signum < 0) neg(a) else a

    fun mod(a: CalcValue, b: CalcValue): CalcValue {
        if (b.isZero) divZero("Modulo by zero")
        val x = a.exact
        val y = b.exact
        if (x != null && y != null) {
            val q = (x / y).floor()
            return CalcValue.ofRational(x - y * Rational.of(q))
        }
        var r = a.decimal.remainder(b.decimal)
        if (r.signum() != 0 && r.signum() != b.decimal.signum()) r = r.add(b.decimal)
        return CalcValue.ofDecimal(r)
    }

    fun exactRoot(r: Rational, k: Int): Rational? {
        if (r.signum < 0 || r.bitSize > 4000) return null
        val n = BigMath.integerRoot(r.num, k)
        if (n.pow(k) != r.num) return null
        val d = BigMath.integerRoot(r.den, k)
        if (d.pow(k) != r.den) return null
        return Rational.of(n, d)
    }

    fun pow(a: CalcValue, b: CalcValue): CalcValue {
        if (b.isZero) return CalcValue.ONE
        if (a.isZero) {
            if (b.signum < 0) divZero("Division by zero (zero to a negative power)")
            return CalcValue.ZERO
        }
        val ax = a.exact
        val bx = b.exact
        if (bx != null && bx.isInteger && bx.num.bitLength() <= 31) {
            val n = bx.num.toInt()
            val absN = Math.abs(n.toLong())
            if (ax != null && ax.bitSize.toLong() * absN <= Precision.MAX_EXACT_BITS) {
                return CalcValue.ofRational(ax.pow(n))
            }
            if (absN <= 999_999_999L) return CalcValue.ofDecimal(BigMath.powInt(a.decimal, n))
        }
        if (bx != null && !bx.isInteger) {
            val p = bx.num
            val q = bx.den
            if (a.signum < 0) {
                if (q.testBit(0)) {
                    val magnitude = pow(neg(a), b)
                    return if (p.testBit(0)) neg(magnitude) else magnitude
                }
                domain("Even root of a negative number is not real")
            }
            if (ax != null && q.bitLength() <= 7 && p.bitLength() <= 15) {
                val root = exactRoot(ax, q.toInt())
                if (root != null && root.bitSize.toLong() * Math.abs(p.toLong()) <= Precision.MAX_EXACT_BITS) {
                    return CalcValue.ofRational(root.pow(p.toInt()))
                }
            }
        }
        if (a.signum < 0) {
            if (bx != null && bx.isInteger) {
                val magnitude = CalcValue.ofDecimal(BigMath.powReal(a.decimal.negate(), b.decimal))
                return if (bx.num.testBit(0)) neg(magnitude) else magnitude
            }
            domain("A negative base with a non-integer exponent is not real")
        }
        return CalcValue.ofDecimal(BigMath.powReal(a.decimal, b.decimal))
    }

    fun sqrt(a: CalcValue): CalcValue {
        if (a.signum < 0) domain("Square root of a negative number is not real")
        if (a.isZero) return CalcValue.ZERO
        val x = a.exact
        if (x != null) {
            val root = exactRoot(x, 2)
            if (root != null) return CalcValue.ofRational(root)
        }
        return CalcValue.ofDecimal(BigMath.sqrt(a.decimal))
    }

    fun cbrt(a: CalcValue): CalcValue = root(a, CalcValue.ofLong(3))

    /** n-th root; odd roots of negative numbers are real. */
    fun root(a: CalcValue, n: CalcValue): CalcValue {
        val nx = n.exact
        if (nx == null || !nx.isInteger || nx.isZero || nx.num.bitLength() > 31) {
            domain("The root index must be a non-zero integer")
        }
        if (a.isZero) return CalcValue.ZERO
        val odd = nx.num.testBit(0)
        if (a.signum < 0) {
            if (!odd) domain("Even root of a negative number is not real")
            return neg(pow(neg(a), div(CalcValue.ONE, n)))
        }
        return pow(a, div(CalcValue.ONE, n))
    }

    private fun intArg(a: CalcValue, what: String): BigInteger {
        val x = a.exact
        if (x == null || !x.isInteger) domain("$what requires a whole number")
        return x.num
    }

    fun factorial(a: CalcValue): CalcValue {
        val n = intArg(a, "Factorial")
        if (n.signum() < 0) domain("Factorial is not defined for negative numbers")
        if (n > BigInteger.valueOf(MAX_FACTORIAL.toLong())) {
            throw CalcException(ErrorKind.OVERFLOW, "Overflow: factorial is too large (maximum $MAX_FACTORIAL!)")
        }
        return CalcValue.ofInteger(BigMath.factorial(n.toInt()))
    }

    fun permutations(nv: CalcValue, rv: CalcValue): CalcValue {
        val n = intArg(nv, "nPr")
        val r = intArg(rv, "nPr")
        if (n.signum() < 0 || r.signum() < 0 || r > n) domain("nPr requires 0 ≤ r ≤ n")
        if (r > BigInteger.valueOf(MAX_COMBINATORIC.toLong())) {
            throw CalcException(ErrorKind.OVERFLOW, "Overflow: nPr is too large")
        }
        var result = BigInteger.ONE
        val rr = r.toInt()
        for (i in 0 until rr) result = result.multiply(n.subtract(BigInteger.valueOf(i.toLong())))
        return CalcValue.ofInteger(result)
    }

    fun combinations(nv: CalcValue, rv: CalcValue): CalcValue {
        val n = intArg(nv, "nCr")
        var r = intArg(rv, "nCr")
        if (n.signum() < 0 || r.signum() < 0 || r > n) domain("nCr requires 0 ≤ r ≤ n")
        if (r > n.subtract(r)) r = n.subtract(r)
        if (r > BigInteger.valueOf(MAX_COMBINATORIC.toLong())) {
            throw CalcException(ErrorKind.OVERFLOW, "Overflow: nCr is too large")
        }
        var result = BigInteger.ONE
        val rr = r.toInt()
        for (i in 1..rr) {
            result = result.multiply(n.subtract(r).add(BigInteger.valueOf(i.toLong()))).divide(BigInteger.valueOf(i.toLong()))
        }
        return CalcValue.ofInteger(result)
    }

    fun floor(a: CalcValue): CalcValue {
        val x = a.exact
        return if (x != null) CalcValue.ofInteger(x.floor())
        else CalcValue.ofDecimal(a.decimal.setScale(0, RoundingMode.FLOOR))
    }

    fun ceil(a: CalcValue): CalcValue {
        val x = a.exact
        return if (x != null) CalcValue.ofInteger(x.ceil())
        else CalcValue.ofDecimal(a.decimal.setScale(0, RoundingMode.CEILING))
    }

    /** Rounds half away from zero to [digits] decimal places (negative digits round to tens, hundreds…). */
    fun round(a: CalcValue, digits: Int): CalcValue {
        if (digits < -30 || digits > 30) domain("round supports -30 to 30 digits")
        val x = a.exact
        if (x != null) {
            val ten = Rational.of(BigInteger.TEN.pow(Math.abs(digits)))
            val factor = if (digits >= 0) ten else Rational.ONE / ten
            val scaled = x.abs() * factor + Rational.of(1, 2)
            val rounded = Rational.of(scaled.floor()) / factor
            return CalcValue.ofRational(if (x.signum < 0) -rounded else rounded)
        }
        return CalcValue.ofDecimal(a.decimal.setScale(digits, RoundingMode.HALF_UP))
    }

    fun compare(a: CalcValue, b: CalcValue): Int {
        val x = a.exact
        val y = b.exact
        return if (x != null && y != null) x.compareTo(y) else a.decimal.compareTo(b.decimal)
    }

    fun toInt(a: CalcValue, what: String): Int {
        val x = a.exact
        if (x == null || !x.isInteger || x.num.bitLength() > 31) domain("$what must be a small whole number")
        return x.num.toInt()
    }

    fun decimalOf(a: CalcValue): BigDecimal = a.decimal
}

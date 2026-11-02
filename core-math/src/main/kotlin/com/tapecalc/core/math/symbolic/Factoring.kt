package com.tapecalc.core.math.symbolic

import com.tapecalc.core.precision.Rational
import java.math.BigInteger

internal object Factoring {

    /** Coefficients c[0..n] of [e] as a polynomial in [v], or null if it is not one. */
    fun polyCoefficients(e: E, v: String): List<Rational>? {
        val x = Simplifier.expand(e)
        val terms = if (x is Add) x.t else listOf(x)
        val map = HashMap<Int, Rational>()
        for (t in terms) {
            val (c, rest) = Simplifier.split(t)
            val deg = when {
                rest is Num -> 0
                rest is Sym && rest.name == v -> 1
                rest is Pw && rest.b is Sym && rest.b.name == v && rest.x is Num && rest.x.v.isInteger && rest.x.v.signum > 0 -> rest.x.v.num.toInt()
                else -> return null
            }
            if (deg > 40) return null
            map[deg] = (map[deg] ?: Rational.ZERO) + c
        }
        val n = map.keys.maxOrNull() ?: 0
        return (0..n).map { map[it] ?: Rational.ZERO }
    }

    private fun lcm(a: BigInteger, b: BigInteger) = a.multiply(b).divide(a.gcd(b))

    private fun divisors(n: BigInteger): List<BigInteger> {
        val out = ArrayList<BigInteger>()
        val limit = n.toLong()
        var i = 1L
        while (i * i <= limit) {
            if (limit % i == 0L) { out.add(BigInteger.valueOf(i)); if (i != limit / i) out.add(BigInteger.valueOf(limit / i)) }
            i++
        }
        return out
    }

    private fun evalAt(c: List<BigInteger>, num: BigInteger, den: BigInteger): BigInteger {
        // sum c[i] * num^i * den^(n-i)
        val n = c.size - 1
        var acc = BigInteger.ZERO
        for (i in 0..n) acc += c[i] * num.pow(i) * den.pow(n - i)
        return acc
    }

    /** Divides integer polynomial by (p x − q) exactly; returns quotient. */
    private fun divideLinear(c: List<BigInteger>, p: BigInteger, q: BigInteger): List<BigInteger> {
        // c ascending; do synthetic division on descending
        val desc = c.reversed()
        val out = ArrayList<BigInteger>()
        var carry = BigInteger.ZERO
        // (p x - q) * (b_{n-1} x^{n-1} + ...) ; b = a/p style
        var rem = desc.toMutableList()
        for (i in 0 until desc.size - 1) {
            val b = rem[i].divide(p)
            out.add(b)
            rem[i + 1] = rem[i + 1] + b * q
        }
        return out.reversed()
    }

    fun factor(e: E, v: String): String {
        val coef = polyCoefficients(e, v) ?: throw NoClosedForm("factor needs a polynomial in a single variable")
        if (coef.size <= 1) throw NoClosedForm("Nothing to factor")
        var lcmDen = BigInteger.ONE
        coef.forEach { lcmDen = lcm(lcmDen, it.den) }
        var ints = coef.map { (it * Rational.of(lcmDen)).num }
        var content = ints.fold(BigInteger.ZERO) { a, b -> a.gcd(b) }
        if (ints.last().signum() < 0) content = content.negate()
        ints = ints.map { it.divide(content) }
        var scalar = Rational.of(content, lcmDen)

        val factors = ArrayList<Pair<E, Int>>() // base, multiplicity
        // Factor out x^k
        var shift = 0
        while (ints.size > 1 && ints[0].signum() == 0) { ints = ints.drop(1); shift++ }
        if (shift > 0) factors.add(Sym(v) to shift)

        // Rational roots
        var progress = true
        while (ints.size > 1 && progress) {
            progress = false
            val a0 = ints[0].abs()
            val an = ints.last().abs()
            if (a0.bitLength() > 40 || an.bitLength() > 40) break
            loop@ for (qd in divisors(an)) for (pd in divisors(a0)) for (sgn in intArrayOf(1, -1)) {
                val p = qd
                val q = pd.multiply(BigInteger.valueOf(sgn.toLong()))
                if (p.gcd(q.abs()) != BigInteger.ONE) continue
                if (evalAt(ints, q, p).signum() == 0) {
                    ints = divideLinear(ints, p, q)
                    val lin = Simplifier.simp(Add(listOf(Mul(listOf(Num(Rational.of(p)), Sym(v))), Num(Rational.of(q.negate())))))
                    val idx = factors.indexOfFirst { Printer.key(it.first) == Printer.key(lin) }
                    if (idx >= 0) factors[idx] = factors[idx].first to (factors[idx].second + 1) else factors.add(lin to 1)
                    progress = true
                    break@loop
                }
            }
        }
        if (ints.size > 1) {
            // Remaining polynomial; keep as is (primitive part)
            val rest = ints.mapIndexed { i, c -> Mul(listOf(Num(Rational.of(c)), Pw(Sym(v), num(i.toLong())))) }
            val r = Simplifier.simp(Add(rest))
            val cont = ints.fold(BigInteger.ZERO) { a, b -> a.gcd(b) }
            factors.add(r to 1)
        } else if (ints.size == 1) {
            scalar = scalar * Rational.of(ints[0])
        }

        val sc = scalar
        val single = factors.size == 1 && factors[0].second == 1 && sc == Rational.ONE
        val parts = factors.map { (b, m) ->
            val text = if (b is Sym || single) Printer.fmt(b) else "(${Printer.fmt(b)})"
            if (m > 1) "$text^$m" else text
        }
        val body = parts.joinToString("*")
        if (parts.isEmpty()) return Printer.fmt(Num(sc))
        return when {
            sc == Rational.ONE -> body
            sc == -Rational.ONE -> "-$body"
            sc.isInteger -> "${sc.num}*$body"
            sc.num == BigInteger.ONE -> "$body/${sc.den}"
            sc.num == BigInteger.ONE.negate() -> "-$body/${sc.den}"
            else -> "${sc.num}/${sc.den}*$body"
        }
    }

    /** Prime factorisation of an integer (up to about 10^15). */
    fun factorInteger(n: BigInteger): String {
        if (n.abs().bitLength() > 53) throw NoClosedForm("That number is too large to factor here")
        var m = n.abs().toLong()
        if (m < 2) throw NoClosedForm("Nothing to factor")
        val parts = ArrayList<String>()
        var p = 2L
        while (p * p <= m) {
            if (m % p == 0L) {
                var k = 0
                while (m % p == 0L) { m /= p; k++ }
                parts.add(if (k > 1) "$p^$k" else "$p")
            }
            p += if (p == 2L) 1 else 2
        }
        if (m > 1) parts.add("$m")
        return (if (n.signum() < 0) "-" else "") + parts.joinToString("*")
    }
}

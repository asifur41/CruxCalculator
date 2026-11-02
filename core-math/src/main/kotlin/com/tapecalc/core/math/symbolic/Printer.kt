package com.tapecalc.core.math.symbolic

import com.tapecalc.core.precision.Precision
import com.tapecalc.core.precision.Rational

/**
 * Writes an expression as text the calculator can read back: `3x^2 + 2x - 5`, `x/2`, `sin(x)/x`, `sqrt(x)`.
 * Fractions are written as `3x/2` or `(x + 1)/(x - 1)`, never `1/2x` (the calculator reads that as 1/(2x)).
 */
internal object Printer {
    fun key(e: E): String = fmt(e)

    fun fmt(e: E): String = when (e) {
        is Num -> number(e.v)
        is Sym -> e.name
        is Add -> add(e)
        is Mul -> mul(e)
        is Pw -> pw(e)
        is Fn -> "${e.name}(${fmt(e.a)})"
    }

    private fun number(r: Rational): String {
        if (r.isInteger) return r.num.toString()
        if (r.isTerminating() && r.den.bitLength() <= 40) {
            return r.toBigDecimal(Precision.INTERNAL).stripTrailingZeros().toPlainString()
        }
        return r.toString()
    }

    private fun isAtom(e: E): Boolean = e is Sym || e is Fn || (e is Num && e.v.isInteger && e.v.signum >= 0)

    /** A denominator needs brackets unless it is a single symbol, function, number or plain power of one. */
    private fun denText(e: E): String {
        val simple = isAtom(e) ||
            (e is Pw && e.x is Num && e.b.let { isAtom(it) } && (e.x.v.isInteger && e.x.v.signum > 0 || e.x.v == Rational.of(1, 2) || e.x.v == Rational.of(1, 3)))
        return if (simple) fmt(e) else "(${fmt(e)})"
    }

    private fun group(e: E): String = if (isAtom(e)) fmt(e) else "(${fmt(e)})"

    private fun negate(t: E): E? {
        val (c, rest) = Simplifier.split(t)
        return if (c.signum < 0) Simplifier.withCoef(-c, rest) else null
    }

    private fun add(e: Add): String {
        val sb = StringBuilder()
        e.t.forEachIndexed { i, t ->
            val neg = negate(t)
            if (i == 0) sb.append(fmt(t))
            else if (neg != null) sb.append(" - ").append(fmt(neg))
            else sb.append(" + ").append(fmt(t))
        }
        return sb.toString()
    }

    private fun pw(e: Pw): String {
        val x = e.x
        if (x is Num) {
            val v = x.v
            if (v.signum < 0) return "1/" + denText(Simplifier.pow(e.b, Num(-v)))
            if (v == Rational.of(1, 2)) return "sqrt(${fmt(e.b)})"
            if (v == Rational.of(1, 3)) return "cbrt(${fmt(e.b)})"
            if (v.isInteger) return "${group(e.b)}^${v.num}"
            return "${group(e.b)}^(${v})"
        }
        return "${group(e.b)}^${group(x)}"
    }

    private fun mul(e: Mul): String {
        val (c, _) = Simplifier.split(e)
        val numer = ArrayList<E>()
        val denom = ArrayList<E>()
        for (f in e.f) {
            if (f is Num) continue
            if (f is Pw && f.x is Num && f.x.v.signum < 0) denom.add(Simplifier.pow(f.b, Num(-f.x.v)))
            else numer.add(f)
        }
        val p = c.num
        val q = c.den
        val sign = if (p.signum() < 0) "-" else ""
        val absP = p.abs()
        val numText = StringBuilder()
        numer.forEachIndexed { i, f ->
            val text = if (f is Add) "(${fmt(f)})" else fmt(f)
            if (i > 0 && !(text.startsWith("(") && numer[i - 1] is Add)) numText.append("*")
            numText.append(text)
        }
        val numerator = when {
            numer.isEmpty() -> "$sign$absP"
            absP == java.math.BigInteger.ONE -> "$sign$numText"
            else -> {
                val first = numer[0]
                val plain = first is Sym || (first is Pw && first.b is Sym && first.x is Num && first.x.v.isInteger)
                "$sign$absP${if (numText.startsWith("(") || plain) "" else "*"}$numText"
            }
        }
        if (q != java.math.BigInteger.ONE && denom.isEmpty()) {
            return "$numerator/$q"
        }
        if (denom.isEmpty()) return numerator
        val dn = denom.map { if (it is Add) "(${fmt(it)})" else denText(it) }
        val all = (if (q != java.math.BigInteger.ONE) listOf(q.toString()) else emptyList()) + dn
        val den = if (all.size == 1) all[0] else "(" + all.joinToString("*") + ")"
        return "${if (numer.isEmpty()) "$sign$absP" else numerator}/$den"
    }
}

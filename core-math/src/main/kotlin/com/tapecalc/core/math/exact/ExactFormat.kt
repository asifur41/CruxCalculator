package com.tapecalc.core.math.exact

import com.tapecalc.core.precision.Rational
import java.math.BigInteger

/** Writes an [ExactNumber] on one line: "√3/2", "2π/3", "1/π", "3√5", "1+√2", "(√6-√2)/4". */
internal object ExactFormat {

    fun format(x: ExactNumber): String? {
        val terms = x.terms
        if (terms.isEmpty()) return "0"
        if (terms.size == 1) return monomial(terms.entries.single().key, terms.entries.single().value)
        // a sum: a negative power of π inside a sum has no tidy one-line form
        if (terms.keys.any { it.piPow < 0 }) return null

        var common = BigInteger.ONE
        for (c in terms.values) common = common / common.gcd(c.den) * c.den
        val ascending = terms.entries.sortedBy { it.key }
        // lead with a positive term when there is one: (√6-√2)/4 reads better than (-√2+√6)/4
        val ordered = if (ascending.first().value.signum < 0 && ascending.any { it.value.signum > 0 }) ascending.reversed() else ascending
        val body = StringBuilder()
        ordered.forEachIndexed { i, (basis, c) ->
            val n = c.num * (common / c.den)
            body.append(if (n.signum() < 0) "-" else if (i > 0) "+" else "")
            body.append(numeratorPiece(n.abs(), basis))
        }
        return if (common == BigInteger.ONE) body.toString() else "($body)/$common"
    }

    private fun monomial(basis: Basis, c: Rational): String {
        val sign = if (c.signum < 0) "-" else ""
        val top = numeratorPiece(c.num.abs(), basis)
        val bottomParts = StringBuilder()
        if (c.den != BigInteger.ONE) bottomParts.append(c.den)
        if (basis.piPow < 0) bottomParts.append(piText(-basis.piPow))
        if (bottomParts.isEmpty()) return "$sign$top"
        val bottom = if (c.den != BigInteger.ONE && basis.piPow < 0) "($bottomParts)" else bottomParts.toString()
        return "$sign$top/$bottom"
    }

    /** `m·π^k·√n` as written in a numerator: the coefficient (left out when it is 1 and a symbol follows), π, then the root. */
    private fun numeratorPiece(m: BigInteger, basis: Basis): String {
        val hasPi = basis.piPow > 0
        val hasRoot = basis.root != BigInteger.ONE
        if (!hasPi && !hasRoot) return m.toString()
        val sb = StringBuilder()
        if (m != BigInteger.ONE) sb.append(m)
        if (hasPi) sb.append(piText(basis.piPow))
        if (hasRoot) sb.append("√").append(basis.root)
        return sb.toString()
    }

    private fun piText(power: Int): String = when (power) {
        1 -> "π"
        2 -> "π²"
        3 -> "π³"
        else -> "π^$power"
    }
}

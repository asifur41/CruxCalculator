package com.tapecalc.core.math.exact

import com.tapecalc.core.precision.BigMath
import com.tapecalc.core.precision.Precision
import com.tapecalc.core.precision.Rational
import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext

/** Thrown inside the exact engine when a value cannot be kept in closed form; the caller falls back to the decimal. */
internal class Unsupported(message: String = "not representable exactly") : RuntimeException(message, null, false, false)

/**
 * One kind of term: √[root] · π^[piPow]. [root] is square-free and at least 1, so √8 is always stored as 2·√2 and
 * every number has exactly one representation.
 */
internal data class Basis(val root: BigInteger, val piPow: Int) : Comparable<Basis> {
    override fun compareTo(other: Basis): Int {
        if (piPow != other.piPow) return piPow.compareTo(other.piPow)
        return root.compareTo(other.root)
    }

    companion object {
        val ONE = Basis(BigInteger.ONE, 0)
        val PI = Basis(BigInteger.ONE, 1)
    }
}

/**
 * An exact real number: a sum of terms `coefficient · √root · π^k` with rational coefficients, such as 1/2, √3/2,
 * (√6 − √2)/4, 2π/3 or 1 + √2. It is closed under + − × and under division by a single term or by a two-term sum
 * (the denominator is rationalised), and it simplifies as it goes (√8 → 2√2, √2·√2 → 2, 3/√3 → √3).
 *
 * Anything outside that family (e^x, ln, √ of a sum, huge expressions) throws [Unsupported] inside the engine, and the
 * calculator then simply keeps the decimal value. An [ExactNumber] is turned into a decimal only at the very end, with
 * [toBigDecimal], so no rounding happens along the way.
 */
class ExactNumber private constructor(internal val terms: Map<Basis, Rational>) {

    val isZero: Boolean get() = terms.isEmpty()
    val isRational: Boolean get() = terms.isEmpty() || (terms.size == 1 && terms.containsKey(Basis.ONE))

    /** The value as a rational when it has no root and no π, else null. */
    fun asRational(): Rational? = when {
        terms.isEmpty() -> Rational.ZERO
        terms.size == 1 && terms.containsKey(Basis.ONE) -> terms.getValue(Basis.ONE)
        else -> null
    }

    /** `r · π` for a rational r, else null. Angles in radians are read through this. */
    internal fun asPiMultiple(): Rational? = when {
        terms.isEmpty() -> Rational.ZERO
        terms.size == 1 && terms.containsKey(Basis.PI) -> terms.getValue(Basis.PI)
        else -> null
    }

    operator fun unaryMinus(): ExactNumber = ExactNumber(terms.mapValues { -it.value })

    operator fun plus(o: ExactNumber): ExactNumber {
        val sum = LinkedHashMap(terms)
        for ((b, c) in o.terms) {
            val s = (sum[b] ?: Rational.ZERO) + c
            if (s.isZero) sum.remove(b) else sum[b] = s
        }
        return make(sum)
    }

    operator fun minus(o: ExactNumber): ExactNumber = this + (-o)

    operator fun times(o: ExactNumber): ExactNumber {
        val product = HashMap<Basis, Rational>()
        for ((b1, c1) in terms) for ((b2, c2) in o.terms) {
            val g = b1.root.gcd(b2.root)
            val root = (b1.root / g) * (b2.root / g)
            val basis = Basis(root, b1.piPow + b2.piPow)
            val coefficient = c1 * c2 * Rational.of(g)
            val s = (product[basis] ?: Rational.ZERO) + coefficient
            if (s.isZero) product.remove(basis) else product[basis] = s
        }
        return make(product)
    }

    /** `this / o`, with the denominator rationalised, or throws [Unsupported] (also for division by zero). */
    operator fun div(o: ExactNumber): ExactNumber {
        if (o.isZero) throw Unsupported("division by zero")
        return when (o.terms.size) {
            1 -> this * o.reciprocalOfTerm()
            2 -> {
                // n / (t1 + t2) = n (t1 - t2) / (t1² - t2²); the denominator is a single term when both have the same π power
                val (first, second) = o.terms.entries.toList()
                if (first.key.piPow != second.key.piPow) throw Unsupported()
                val conjugate = ExactNumber(mapOf(first.key to first.value, second.key to -second.value))
                val denominator = o * conjugate
                if (denominator.terms.size > 1) throw Unsupported()
                (this * conjugate).div(denominator)
            }
            else -> throw Unsupported()
        }
    }

    private fun reciprocalOfTerm(): ExactNumber {
        val (basis, c) = terms.entries.single()
        // 1 / (c √n π^k) = √n / (c n π^k)
        val coefficient = Rational.of(BigInteger.ONE) / (c * Rational.of(basis.root))
        return ExactNumber(mapOf(Basis(basis.root, -basis.piPow) to coefficient))
    }

    /** Integer power (negative powers divide). Larger exponents than [MAX_POWER] are not attempted. */
    fun pow(n: Int): ExactNumber {
        if (n > MAX_POWER || n < -MAX_POWER) throw Unsupported("exponent too large")
        if (n == 0) return ONE
        var result = ONE
        var base = this
        var e = Math.abs(n)
        while (e > 0) {
            if (e and 1 == 1) result = result * base
            e = e shr 1
            if (e > 0) base = base * base
        }
        return if (n < 0) ONE / result else result
    }

    /** Square root: of a non-negative rational, or of `c · π^(2k)` with a rational c ≥ 0. Anything else is unsupported. */
    fun sqrt(): ExactNumber {
        if (terms.isEmpty()) return ZERO
        val (basis, c) = terms.entries.singleOrNull() ?: throw Unsupported()
        if (basis.root != BigInteger.ONE || basis.piPow % 2 != 0 || c.signum < 0) throw Unsupported()
        val root = sqrtOfRational(c)
        return root * ExactNumber(mapOf(Basis(BigInteger.ONE, basis.piPow / 2) to Rational.ONE))
    }

    /** The value at working precision. */
    fun toBigDecimal(mc: MathContext = Precision.WORK): BigDecimal {
        var total = BigDecimal.ZERO
        for ((basis, c) in terms) {
            var x = c.toBigDecimal(mc)
            if (basis.root != BigInteger.ONE) x = x.multiply(BigMath.sqrt(BigDecimal(basis.root)), mc)
            if (basis.piPow > 0) repeat(basis.piPow) { x = x.multiply(BigMath.pi, mc) }
            if (basis.piPow < 0) repeat(-basis.piPow) { x = x.divide(BigMath.pi, mc) }
            total = total.add(x, mc)
        }
        return total
    }

    /** -1, 0 or 1. */
    fun signum(): Int = toBigDecimal().signum()

    /** Text such as "√3/2", "2π/3", "(√6-√2)/4", "1+√2"; null when the value has no tidy one-line form. */
    fun format(): String? = ExactFormat.format(this)

    override fun equals(other: Any?) = other is ExactNumber && terms == other.terms
    override fun hashCode() = terms.hashCode()
    override fun toString() = format() ?: "ExactNumber$terms"

    companion object {
        private const val MAX_POWER = 64
        private const val MAX_TERMS = 16
        private const val MAX_BITS = 640
        /** Largest radicand that is factored (trial division up to its square root). */
        private const val MAX_RADICAND_BITS = 44

        val ZERO = ExactNumber(emptyMap())
        val ONE = ExactNumber(mapOf(Basis.ONE to Rational.ONE))
        val PI = ExactNumber(mapOf(Basis.PI to Rational.ONE))

        fun of(r: Rational): ExactNumber = if (r.isZero) ZERO else make(mapOf(Basis.ONE to r))
        fun of(n: Long): ExactNumber = of(Rational.of(n))

        /** `r · π`. */
        internal fun piTimes(r: Rational): ExactNumber = if (r.isZero) ZERO else make(mapOf(Basis.PI to r))

        /** √r for a rational r ≥ 0, simplified: √(8) = 2√2, √(1/2) = √2/2, √(9/4) = 3/2. */
        fun sqrtOf(r: Rational): ExactNumber {
            if (r.signum < 0) throw Unsupported("square root of a negative number")
            return sqrtOfRational(r)
        }

        private fun sqrtOfRational(r: Rational): ExactNumber {
            if (r.isZero) return ZERO
            // √(p/q) = √(p·q) / q
            val n = r.num * r.den
            val (outside, inside) = squareFreeParts(n)
            return make(mapOf(Basis(inside, 0) to Rational.of(outside, r.den)))
        }

        /** n = outside² · inside with `inside` square-free. */
        internal fun squareFreeParts(n: BigInteger): Pair<BigInteger, BigInteger> {
            if (n.signum() <= 0 || n.bitLength() > MAX_RADICAND_BITS) throw Unsupported("radicand too large")
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

        private fun make(terms: Map<Basis, Rational>): ExactNumber {
            if (terms.size > MAX_TERMS) throw Unsupported("too many terms")
            for (c in terms.values) if (c.bitSize > MAX_BITS) throw Unsupported("number too large")
            return ExactNumber(terms.filterValues { !it.isZero })
        }
    }
}

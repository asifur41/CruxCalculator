package com.tapecalc.core.math.symbolic

import com.tapecalc.core.precision.Rational
import java.math.BigInteger

/** Turns expressions into a canonical, simplified form: like terms combined, powers merged, constants folded. */
internal object Simplifier {

    fun simp(e: E): E = when (e) {
        is Num, is Sym -> e
        is Add -> add(e.t.map { simp(it) })
        is Mul -> mul(e.f.map { simp(it) })
        is Pw -> pow(simp(e.b), simp(e.x))
        is Fn -> fn(e.name, simp(e.a))
    }

    /** The numeric coefficient of a term and the rest: 3x -> (3, x), x -> (1, x). */
    fun split(t: E): Pair<Rational, E> {
        if (t is Num) return t.v to ONE
        if (t is Mul && t.f.isNotEmpty() && t.f[0] is Num) {
            val rest = t.f.drop(1)
            return (t.f[0] as Num).v to (if (rest.size == 1) rest[0] else Mul(rest))
        }
        return Rational.ONE to t
    }

    fun withCoef(c: Rational, rest: E): E = when {
        c.isZero -> ZERO
        rest is Num -> Num(c * rest.v)
        c == Rational.ONE -> rest
        rest is Mul -> Mul(listOf<E>(Num(c)) + rest.f)
        else -> Mul(listOf(Num(c), rest))
    }

    fun add(terms: List<E>): E {
        var constant = Rational.ZERO
        val groups = LinkedHashMap<String, Pair<Rational, E>>()
        fun take(t: E) {
            when (t) {
                is Add -> t.t.forEach { take(it) }
                is Num -> constant += t.v
                else -> {
                    val (c, rest) = split(t)
                    val key = Printer.key(rest)
                    val old = groups[key]
                    groups[key] = if (old == null) c to rest else (old.first + c) to rest
                }
            }
        }
        terms.forEach { take(it) }
        val out = ArrayList<E>()
        for ((_, g) in groups) {
            if (!g.first.isZero) out.add(withCoef(g.first, g.second))
        }
        out.sortWith(Ordering.termOrder)
        if (!constant.isZero) out.add(Num(constant))
        return when (out.size) {
            0 -> ZERO
            1 -> out[0]
            else -> Add(out)
        }
    }

    fun mul(factors: List<E>): E {
        var coef = Rational.ONE
        val flat = ArrayList<E>()
        fun take(f: E) {
            when (f) {
                is Mul -> f.f.forEach { take(it) }
                is Num -> coef *= f.v
                else -> flat.add(f)
            }
        }
        factors.forEach { take(it) }
        if (coef.isZero) return ZERO
        // Merge equal bases: x * x^2 = x^3.
        val groups = LinkedHashMap<String, Pair<E, Rational>>()
        val others = ArrayList<E>()
        for (f in flat) {
            val (b, n) = if (f is Pw && f.x is Num) f.b to f.x.v else f to Rational.ONE
            val key = Printer.key(b)
            val old = groups[key]
            groups[key] = if (old == null) b to n else b to (old.second + n)
        }
        for ((_, g) in groups) {
            if (g.second.isZero) continue
            val piece = if (g.second == Rational.ONE) g.first else pow(g.first, Num(g.second))
            if (piece is Num) coef *= piece.v else if (piece is Mul) {
                piece.f.forEach { if (it is Num) coef *= it.v else others.add(it) }
            } else others.add(piece)
        }
        if (coef.isZero) return ZERO
        others.sortWith(Ordering.factorOrder)
        // A lone sum times a number stays a product; a number times nothing is just the number.
        if (others.isEmpty()) return Num(coef)
        if (coef == Rational.ONE) return if (others.size == 1) others[0] else Mul(others)
        return Mul(listOf<E>(Num(coef)) + others)
    }

    fun pow(b: E, x: E): E {
        if (x is Num) {
            if (x.v.isZero) return ONE
            if (x.v == Rational.ONE) return b
            if (b is Num) {
                if (b.v == Rational.ONE) return ONE
                if (b.v.isZero) return if (x.v.signum > 0) ZERO else Pw(b, x)
                if (x.v.isInteger && x.v.num.bitLength() <= 10 && b.v.bitSize * Math.abs(x.v.num.toInt()) <= 4000) {
                    return Num(b.v.pow(x.v.num.toInt()))
                }
                if (!x.v.isInteger && x.v.den.bitLength() <= 6 && b.v.signum > 0) {
                    val root = exactRoot(b.v, x.v.den.toInt())
                    if (root != null && x.v.num.bitLength() <= 10 && root.bitSize * Math.abs(x.v.num.toInt()) <= 4000) {
                        return Num(root.pow(x.v.num.toInt()))
                    }
                }
            }
            if (b is Pw && b.x is Num && x.v.isInteger) return pow(b.b, Num(b.x.v * x.v))
            if (b is Mul && x.v.isInteger) return mul(b.f.map { pow(it, x) })
        }
        if (b is Num && b.v == Rational.ONE) return ONE
        if (b is Fn && b.name == "exp" && x is Num && x.v.isInteger) return fn("exp", mul(listOf(b.a, x)))
        return Pw(b, x)
    }

    fun fn(name: String, a: E): E {
        when (name) {
            "ln" -> {
                if (isNum(a, 1)) return ZERO
                if (a is Sym && a.name == "e") return ONE
                if (a is Fn && a.name == "exp") return a.a
            }
            "exp" -> {
                if (isNum(a, 0)) return ONE
                if (a is Fn && a.name == "ln") return a.a
            }
            "sin", "tan", "sinh", "tanh", "asin", "atan" -> if (isNum(a, 0)) return ZERO
            "cos", "cosh" -> if (isNum(a, 0)) return ONE
            "abs" -> {
                if (a is Num) return Num(a.v.abs())
                if (a is Fn && a.name == "abs") return a
            }
        }
        return Fn(name, a)
    }

    // ---------------------------------------------------------------- expansion

    fun expand(e: E): E = simp(expandRaw(simp(e)))

    private fun termsOf(e: E): List<E> = if (e is Add) e.t else listOf(e)

    private fun expandRaw(e: E): E = when (e) {
        is Num, is Sym -> e
        is Add -> Add(e.t.map { expandRaw(it) })
        is Fn -> Fn(e.name, expandRaw(e.a))
        is Mul -> {
            var acc: List<E> = listOf(ONE)
            for (f in e.f) {
                val ft = termsOf(simp(expandRaw(f)))
                val next = ArrayList<E>()
                for (a in acc) for (b in ft) next.add(mul(listOf(a, b)))
                if (next.size > 600) throw SymbolicLimit("The expression is too large to expand")
                acc = next
            }
            Add(acc)
        }
        is Pw -> {
            val base = simp(expandRaw(e.b))
            val x = e.x
            if (x is Num && x.v.isInteger && x.v.signum > 0 && base is Add) {
                val n = x.v.num.toInt()
                if (n > 24) throw SymbolicLimit("The power is too large to expand")
                var acc: List<E> = listOf(ONE)
                repeat(n) {
                    val next = ArrayList<E>()
                    for (a in acc) for (b in base.t) next.add(mul(listOf(a, b)))
                    if (next.size > 600) throw SymbolicLimit("The expression is too large to expand")
                    acc = next.map { simp(it) }.let { add(it) }.let { termsOf(it) }
                }
                Add(acc)
            } else Pw(base, simp(expandRaw(x)))
        }
    }
}

internal class SymbolicLimit(message: String) : RuntimeException(message)

/** The order terms and factors are written in. */
internal object Ordering {
    private fun degree(e: E): Double = when (e) {
        is Num -> -1.0
        is Sym -> if (e.name in CONSTANTS) 0.0 else 1.0
        is Pw -> if (e.x is Num) degree(e.b) * e.x.v.toBigDecimal(com.tapecalc.core.precision.Precision.INTERNAL).toDouble() else 0.5
        is Mul -> e.f.filter { it !is Num }.sumOf { degree(it) }
        is Add -> e.t.maxOf { degree(it) }
        is Fn -> 0.5
    }

    val termOrder = Comparator<E> { a, b ->
        val da = degree(split(a).second)
        val db = degree(split(b).second)
        if (da != db) db.compareTo(da) else Printer.key(split(a).second).replace("*", "").compareTo(Printer.key(split(b).second).replace("*", ""))
    }

    val factorOrder = Comparator<E> { a, b ->
        val ra = rank(a)
        val rb = rank(b)
        if (ra != rb) ra.compareTo(rb) else Printer.key(a).compareTo(Printer.key(b))
    }

    private fun rank(e: E): Int = when (e) {
        is Sym -> 0
        is Pw -> if (e.x is Num && e.x.v.signum > 0) 1 else 4
        is Fn -> 2
        is Add -> 3
        else -> 5
    }

    private fun split(t: E) = Simplifier.split(t)
}

package com.tapecalc.core.math.symbolic

import com.tapecalc.core.precision.Rational
import java.math.BigInteger

internal class NoClosedForm(message: String) : RuntimeException(message)

internal object Calculus {
    private fun add(vararg t: E) = Simplifier.add(t.toList())
    private fun mul(vararg f: E) = Simplifier.mul(f.toList())
    private fun pow(b: E, x: E) = Simplifier.pow(b, x)
    private fun neg(e: E) = mul(MINUS_ONE, e)
    private fun inv(e: E) = pow(e, MINUS_ONE)
    private fun fn(n: String, a: E) = Simplifier.fn(n, a)

    // ------------------------------------------------------------ derivative

    fun derivative(e: E, v: String): E = Simplifier.simp(d(e, v))

    private fun d(e: E, v: String): E = when (e) {
        is Num -> ZERO
        is Sym -> if (e.name == v) ONE else ZERO
        is Add -> Add(e.t.map { d(it, v) })
        is Mul -> Add(e.f.indices.map { i ->
            Mul(e.f.mapIndexed { j, f -> if (i == j) d(f, v) else f })
        })
        is Pw -> {
            val bDep = dependsOn(e.b, v)
            val xDep = dependsOn(e.x, v)
            when {
                !bDep && !xDep -> ZERO
                !xDep -> Mul(listOf(e.x, Pw(e.b, add(e.x, MINUS_ONE)), d(e.b, v)))
                !bDep -> Mul(listOf(e, Fn("ln", e.b), d(e.x, v)))
                else -> Mul(listOf(e, Add(listOf(
                    Mul(listOf(d(e.x, v), Fn("ln", e.b))),
                    Mul(listOf(e.x, d(e.b, v), Pw(e.b, MINUS_ONE)))
                ))))
            }
        }
        is Fn -> {
            val a = e.a
            val outer: E = when (e.name) {
                "sin" -> Fn("cos", a)
                "cos" -> Mul(listOf(MINUS_ONE, Fn("sin", a)))
                "tan" -> Pw(Fn("cos", a), num(-2))
                "sinh" -> Fn("cosh", a)
                "cosh" -> Fn("sinh", a)
                "tanh" -> Pw(Fn("cosh", a), num(-2))
                "exp" -> e
                "ln" -> Pw(a, MINUS_ONE)
                "asin" -> Pw(Add(listOf(ONE, Mul(listOf(MINUS_ONE, Pw(a, num(2)))))), num(-1, 2))
                "acos" -> Mul(listOf(MINUS_ONE, Pw(Add(listOf(ONE, Mul(listOf(MINUS_ONE, Pw(a, num(2)))))), num(-1, 2))))
                "atan" -> Pw(Add(listOf(ONE, Pw(a, num(2)))), MINUS_ONE)
                "abs" -> Mul(listOf(a, Pw(Fn("abs", a), MINUS_ONE)))
                else -> throw NoClosedForm("I cannot differentiate ${e.name}")
            }
            Mul(listOf(outer, d(a, v)))
        }
    }

    // ------------------------------------------------------------ integral

    fun integral(e: E, v: String): E {
        val expanded = Simplifier.expand(e)
        val terms = if (expanded is Add) expanded.t else listOf(expanded)
        return Simplifier.expand(Add(terms.map { term(it, v) }))
    }

    /** a and b with expression = a*v + b, or null when it is not linear in v. */
    private fun linear(e: E, v: String): Pair<E, E>? {
        val x = Simplifier.expand(e)
        val ts = if (x is Add) x.t else listOf(x)
        var a: E = ZERO
        var b: E = ZERO
        for (t in ts) {
            if (!dependsOn(t, v)) { b = add(b, t); continue }
            val (c, rest) = Simplifier.split(t)
            if (rest is Sym && rest.name == v) { a = add(a, Num(c)); continue }
            if (rest is Mul) {
                val others = rest.f.filter { !dependsOn(it, v) }
                val vs = rest.f.filter { dependsOn(it, v) }
                if (vs.size == 1 && vs[0] is Sym && (vs[0] as Sym).name == v) {
                    a = add(a, mul(Num(c), Mul(others))); continue
                }
            }
            return null
        }
        return if (isNum(a, 0)) null else a to b
    }

    private fun term(t: E, v: String): E {
        if (!dependsOn(t, v)) return mul(t, Sym(v))
        // Pull out factors that do not depend on v.
        val factors = if (t is Mul) t.f else listOf(t)
        val constant = factors.filter { !dependsOn(it, v) }
        val varying = factors.filter { dependsOn(it, v) }
        if (constant.isNotEmpty()) return mul(Mul(constant), term(Simplifier.mul(varying), v))
        if (varying.size == 1) return basic(varying[0], v)
        return byParts(varying, v) ?: throw NoClosedForm("I could not find a closed form for this integral")
    }

    private fun basic(f: E, v: String): E {
        when (f) {
            is Sym -> return mul(num(1, 2), pow(f, num(2)))
            is Pw -> {
                val lin = linear(f.b, v)
                val n = f.x
                if (lin != null && n is Num && !dependsOn(n, v)) {
                    val (a, _) = lin
                    return if (n.v == -Rational.ONE) mul(inv(a), fn("ln", fn("abs", f.b)))
                    else mul(inv(mul(a, Num(n.v + Rational.ONE))), pow(f.b, Num(n.v + Rational.ONE)))
                }
                // 1/(a + b x^2) with b/a a perfect square: arctangent
                if (n is Num && n.v == -Rational.ONE && f.b is Add && f.b.t.size == 2) {
                    val (cx, rest) = f.b.t.map { Simplifier.split(it) }.let { p ->
                        val sq = p.firstOrNull { it.second is Pw && (it.second as Pw).b.let { b -> b is Sym && b.name == v } && ((it.second as Pw).x as? Num)?.v == Rational.of(2) }
                        val k = p.firstOrNull { it.second is Num || (it.second == ONE) }
                        if (sq != null && k != null) sq.first to k.first else null to null
                    }
                    if (cx != null && rest != null && !rest.isZero) {
                        val ratio = cx / rest
                        val r = exactRoot(ratio, 2)
                        if (r != null) return mul(Num(Rational.ONE / (rest * r)), fn("atan", mul(Num(r), Sym(v))))
                    }
                }
                if (!dependsOn(n, v) && n is Num) {
                    // a^x with constant base is not Pw here; fallthrough
                }
                if (!dependsOn(f.b, v) && linear(f.x, v) != null) {
                    val (a, _) = linear(f.x, v)!!
                    return mul(inv(mul(a, fn("ln", f.b))), f)
                }
            }
            is Fn -> {
                val lin = linear(f.a, v) ?: throw NoClosedForm("I could not find a closed form for this integral")
                val a = lin.first
                val ia = inv(a)
                return when (f.name) {
                    "sin" -> mul(MINUS_ONE, ia, fn("cos", f.a))
                    "cos" -> mul(ia, fn("sin", f.a))
                    "sinh" -> mul(ia, fn("cosh", f.a))
                    "cosh" -> mul(ia, fn("sinh", f.a))
                    "exp" -> mul(ia, f)
                    "tan" -> mul(MINUS_ONE, ia, fn("ln", fn("abs", fn("cos", f.a))))
                    "tanh" -> mul(ia, fn("ln", fn("cosh", f.a)))
                    "ln" -> mul(ia, add(mul(f.a, f), mul(MINUS_ONE, f.a)))
                    else -> throw NoClosedForm("I could not find a closed form for ${f.name}")
                }
            }
            else -> Unit
        }
        throw NoClosedForm("I could not find a closed form for this integral")
    }

    /** Integration by parts for x^n * (exp | sin | cos | ln) products. */
    private fun byParts(factors: List<E>, v: String): E? {
        if (factors.size != 2) return null
        val polyIdx = factors.indexOfFirst { isPolyPower(it, v) }
        if (polyIdx < 0) return null
        val p = factors[polyIdx]
        val g = factors[1 - polyIdx]
        val n = degreeOf(p, v)
        if (g is Fn && g.name == "ln") {
            val lin = linear(g.a, v)
            if (lin != null && isNum(lin.second, 0)) {
                if (n == -1) throw NoClosedForm("I could not find a closed form for this integral")
                val p1 = Num(Rational.of(n.toLong() + 1))
                val up = pow(Sym(v), p1)
                return add(mul(inv(p1), up, g), mul(MINUS_ONE, inv(mul(p1, p1)), up))
            }
            return null
        }
        if (g is Fn && g.name in setOf("exp", "sin", "cos", "sinh", "cosh") && linear(g.a, v) != null && n >= 1) {
            val big = basic(g, v)
            // ∫ x^n g = x^n G − n ∫ x^(n-1) g
            val rest = if (n == 1) term(big, v) else term(Simplifier.expand(mul(pow(Sym(v), num(n.toLong() - 1)), big)), v)
            return add(mul(p, big), mul(num(-n.toLong()), rest))
        }
        return null
    }

    private fun isPolyPower(e: E, v: String): Boolean =
        (e is Sym && e.name == v) || (e is Pw && e.b is Sym && e.b.name == v && e.x is Num && e.x.v.isInteger)

    private fun degreeOf(e: E, v: String): Int = if (e is Sym) 1 else ((e as Pw).x as Num).v.num.toInt()
}

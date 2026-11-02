package com.tapecalc.core.math.symbolic

import com.tapecalc.core.math.Arith
import com.tapecalc.core.precision.Rational
import java.math.BigInteger

/** Symbolic expression tree. Instances are immutable; [Simplifier] keeps them in a canonical form. */
internal sealed class E
internal class Num(val v: Rational) : E()
internal class Sym(val name: String) : E()
internal class Add(val t: List<E>) : E()
internal class Mul(val f: List<E>) : E()
internal class Pw(val b: E, val x: E) : E()
internal class Fn(val name: String, val a: E) : E()

internal val ZERO: E = Num(Rational.ZERO)
internal val ONE: E = Num(Rational.ONE)
internal val MINUS_ONE: E = Num(-Rational.ONE)
internal fun num(n: Long): E = Num(Rational.of(n))
internal fun num(n: Long, d: Long): E = Num(Rational.of(n, d))

internal val CONSTANTS = setOf("pi", "e", "phi")

internal fun isNum(e: E, n: Long): Boolean = e is Num && e.v == Rational.of(n)

internal fun dependsOn(e: E, v: String): Boolean = when (e) {
    is Num -> false
    is Sym -> e.name == v
    is Add -> e.t.any { dependsOn(it, v) }
    is Mul -> e.f.any { dependsOn(it, v) }
    is Pw -> dependsOn(e.b, v) || dependsOn(e.x, v)
    is Fn -> dependsOn(e.a, v)
}

internal fun symbols(e: E, out: MutableSet<String> = linkedSetOf()): Set<String> {
    when (e) {
        is Num -> Unit
        is Sym -> if (e.name !in CONSTANTS) out.add(e.name)
        is Add -> e.t.forEach { symbols(it, out) }
        is Mul -> e.f.forEach { symbols(it, out) }
        is Pw -> { symbols(e.b, out); symbols(e.x, out) }
        is Fn -> symbols(e.a, out)
    }
    return out
}

internal fun size(e: E): Int = when (e) {
    is Num, is Sym -> 1
    is Add -> 1 + e.t.sumOf { size(it) }
    is Mul -> 1 + e.f.sumOf { size(it) }
    is Pw -> 1 + size(e.b) + size(e.x)
    is Fn -> 1 + size(e.a)
}

/** Replaces every symbol named [name] by [with]. */
internal fun substitute(e: E, name: String, with: E): E = when (e) {
    is Num -> e
    is Sym -> if (e.name == name) with else e
    is Add -> Add(e.t.map { substitute(it, name, with) })
    is Mul -> Mul(e.f.map { substitute(it, name, with) })
    is Pw -> Pw(substitute(e.b, name, with), substitute(e.x, name, with))
    is Fn -> Fn(e.name, substitute(e.a, name, with))
}

internal fun bigDiv(n: BigInteger, d: BigInteger): Rational = Rational.of(n, d)

internal fun exactRoot(r: Rational, k: Int): Rational? = Arith.exactRoot(r, k)

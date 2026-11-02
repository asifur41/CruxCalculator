package com.tapecalc.core.math

import com.tapecalc.core.precision.BigMath
import java.math.BigDecimal
import java.math.BigInteger

/** Function table: names, aliases, arity and implementations. */
internal object Functions {

    private val UNLIMITED = Int.MAX_VALUE

    private val ARITY: Map<String, IntRange> = mapOf(
        "sin" to 1..1, "cos" to 1..1, "tan" to 1..1,
        "asin" to 1..1, "acos" to 1..1, "atan" to 1..1,
        "sinh" to 1..1, "cosh" to 1..1, "tanh" to 1..1,
        "asinh" to 1..1, "acosh" to 1..1, "atanh" to 1..1,
        "log" to 1..2, "ln" to 1..1, "log2" to 1..1, "exp" to 1..1,
        "sqrt" to 1..1, "cbrt" to 1..1, "root" to 2..2,
        "abs" to 1..1, "floor" to 1..1, "ceil" to 1..1, "round" to 1..2, "sign" to 1..1,
        "fact" to 1..1, "mod" to 2..2, "npr" to 2..2, "ncr" to 2..2,
        "min" to 1..UNLIMITED, "max" to 1..UNLIMITED, "pow" to 2..2,
        "atan2" to 2..2, "hypot" to 2..2, "gcd" to 2..UNLIMITED, "lcm" to 2..UNLIMITED,
        // Degrees-minutes-seconds pieces written by the tokenizer for 2°30'15" (not meant to be typed).
        "_deg" to 1..1, "_min" to 1..1, "_sec" to 1..1,
        // Forms that need their arguments unevaluated or return more than a number (handled by the evaluator).
        "sum" to 4..4, "prod" to 4..4, "integral" to 4..4, "deriv" to 3..3, "solve" to 2..3,
        "pol" to 2..2, "rec" to 2..2
    )

    private val ALIASES: Map<String, String> = mapOf(
        "arcsin" to "asin", "arccos" to "acos", "arctan" to "atan",
        "arsinh" to "asinh", "arcsinh" to "asinh", "arcosh" to "acosh", "arccosh" to "acosh",
        "artanh" to "atanh", "arctanh" to "atanh",
        "lg" to "log", "log10" to "log",
        "perm" to "npr", "comb" to "ncr", "choose" to "ncr",
        "factorial" to "fact", "sgn" to "sign", "ceiling" to "ceil", "nthroot" to "root",
        "arctan2" to "atan2", "integrate" to "integral", "diff" to "deriv", "derivative" to "deriv",
        "product" to "prod"
    )

    /** Functions whose arguments are not all plain numbers; [Evaluator] handles them itself. */
    val SPECIAL: Set<String> = setOf("sum", "prod", "integral", "deriv", "solve", "pol", "rec")

    private val CONSTANTS = setOf("pi", "e", "phi", "ans")
    private val OTHER_RESERVED = setOf("to", "in", "mod")

    fun canonicalName(text: String): String? {
        val lower = text.lowercase()
        ALIASES[lower]?.let { return it }
        return if (ARITY.containsKey(lower)) lower else null
    }

    /** Names a mistyped word can be compared with: every function, alias and constant. */
    fun suggestionNames(): Set<String> =
        (ARITY.keys + ALIASES.keys).filter { !it.startsWith("_") }.toSet() + CONSTANTS + Constants.names()

    fun isReserved(name: String): Boolean {
        val lower = name.lowercase()
        return canonicalName(name) != null || lower in CONSTANTS || lower in OTHER_RESERVED
    }

    private fun domain(message: String): Nothing = throw CalcException(ErrorKind.DOMAIN, message)

    private fun d(v: BigDecimal): CalcValue = CalcValue.ofDecimal(v)

    private fun isPowerOfTen(x: BigDecimal): Boolean =
        x.signum() > 0 && x.stripTrailingZeros().unscaledValue() == BigInteger.ONE

    private fun isPowerOfTwo(x: BigDecimal): Boolean {
        if (x.signum() <= 0) return false
        val s = x.stripTrailingZeros()
        return s.scale() <= 0 && s.toBigIntegerExact().bitCount() == 1
    }

    private fun log10(x: CalcValue): CalcValue {
        if (x.signum <= 0) domain("log is only defined for positive numbers")
        val r = BigMath.log10(x.decimal)
        return if (isPowerOfTen(x.decimal)) CalcValue.ofExactDecimal(r) else d(r)
    }

    private fun log2(x: CalcValue): CalcValue {
        if (x.signum <= 0) domain("log is only defined for positive numbers")
        val r = BigMath.log2(x.decimal)
        return if (isPowerOfTwo(x.decimal)) CalcValue.ofExactDecimal(r) else d(r)
    }

    private fun ln(x: CalcValue): CalcValue {
        if (x.signum <= 0) domain("ln is only defined for positive numbers")
        if (x.exact != null && x.exact == com.tapecalc.core.precision.Rational.ONE) return CalcValue.ZERO
        return d(BigMath.ln(x.decimal))
    }

    private fun logBase(x: CalcValue, base: CalcValue): CalcValue {
        if (x.signum <= 0 || base.signum <= 0) domain("log is only defined for positive numbers")
        if (Arith.compare(base, CalcValue.ONE) == 0) domain("log base cannot be 1")
        if (Arith.compare(base, CalcValue.ofLong(10)) == 0) return log10(x)
        if (Arith.compare(base, CalcValue.ofLong(2)) == 0) return log2(x)
        return Arith.div(ln(x), ln(base))
    }

    /** Throws "sin needs 1 value but got 0" style errors when [count] arguments do not fit [name]. */
    fun checkArity(name: String, count: Int) {
        val range = ARITY.getValue(name)
        if (count !in range) {
            fun values(n: Int) = if (n == 1) "1 value" else "$n values"
            val expected = when {
                range.last == UNLIMITED -> "at least ${values(range.first)}"
                range.first == range.last -> values(range.first)
                else -> "${range.first} to ${range.last} values"
            }
            throw CalcException(ErrorKind.INVALID_ARGUMENT, "$name needs $expected but got $count")
        }
    }

    private fun integerArg(v: CalcValue, what: String): BigInteger {
        val x = v.exact
        if (x == null || !x.isInteger) domain("$what needs whole numbers")
        return x.num
    }

    fun call(name: String, args: List<CalcValue>, ctx: EvalContext): CalcValue {
        checkArity(name, args.size)
        val x = args[0]
        val mode = ctx.angleMode
        return when (name) {
            "sin" -> Trig.sin(x.decimal, mode)
            "cos" -> Trig.cos(x.decimal, mode)
            "tan" -> Trig.tan(x.decimal, mode)
            "asin" -> Trig.asin(x.decimal, mode)
            "acos" -> Trig.acos(x.decimal, mode)
            "atan" -> Trig.atan(x.decimal, mode)
            "sinh" -> d(BigMath.sinh(x.decimal))
            "cosh" -> d(BigMath.cosh(x.decimal))
            "tanh" -> d(BigMath.tanh(x.decimal))
            "asinh" -> d(BigMath.asinh(x.decimal))
            "acosh" -> d(BigMath.acosh(x.decimal))
            "atanh" -> d(BigMath.atanh(x.decimal))
            "log" -> if (args.size == 1) log10(x) else logBase(x, args[1])
            "ln" -> ln(x)
            "log2" -> log2(x)
            "exp" -> if (x.isZero) CalcValue.ONE else d(BigMath.exp(x.decimal))
            "sqrt" -> Arith.sqrt(x)
            "cbrt" -> Arith.cbrt(x)
            "root" -> Arith.root(x, args[1])
            "abs" -> Arith.abs(x)
            "floor" -> Arith.floor(x)
            "ceil" -> Arith.ceil(x)
            "round" -> Arith.round(x, if (args.size == 2) Arith.toInt(args[1], "round digits") else 0)
            "sign" -> CalcValue.ofLong(x.signum.toLong())
            "fact" -> Arith.factorial(x)
            "mod" -> Arith.mod(x, args[1])
            "npr" -> Arith.permutations(x, args[1])
            "ncr" -> Arith.combinations(x, args[1])
            "min" -> args.reduce { a, b -> if (Arith.compare(b, a) < 0) b else a }
            "max" -> args.reduce { a, b -> if (Arith.compare(b, a) > 0) b else a }
            "pow" -> Arith.pow(x, args[1])
            "atan2" -> Trig.atan2(x, args[1], mode)
            "hypot" -> Arith.sqrt(Arith.add(Arith.mul(x, x), Arith.mul(args[1], args[1])))
            "gcd" -> CalcValue.ofInteger(args.map { integerArg(it, "gcd") }.reduce { a, b -> a.gcd(b) })
            "lcm" -> CalcValue.ofInteger(args.map { integerArg(it, "lcm") }.reduce { a, b ->
                if (a.signum() == 0 || b.signum() == 0) BigInteger.ZERO else a.multiply(b).abs().divide(a.gcd(b))
            })
            "_deg" -> Trig.fromDegrees(x, mode)
            "_min" -> Trig.fromDegrees(Arith.div(x, CalcValue.ofLong(60)), mode)
            "_sec" -> Trig.fromDegrees(Arith.div(x, CalcValue.ofLong(3600)), mode)
            else -> throw CalcException(ErrorKind.UNKNOWN_NAME, "Unknown function '$name'")
        }
    }
}

package com.tapecalc.core.math.algebra

import com.tapecalc.core.math.AngleMode
import com.tapecalc.core.math.Arith
import com.tapecalc.core.math.BinOp
import com.tapecalc.core.math.BinaryNode
import com.tapecalc.core.math.CalcError
import com.tapecalc.core.math.CalcException
import com.tapecalc.core.math.CalcValue
import com.tapecalc.core.math.CallNode
import com.tapecalc.core.math.ErrorKind
import com.tapecalc.core.math.EvalContext
import com.tapecalc.core.math.FactorialNode
import com.tapecalc.core.math.Functions
import com.tapecalc.core.math.NameNode
import com.tapecalc.core.math.Node
import com.tapecalc.core.math.NumNode
import com.tapecalc.core.math.ParenNode
import com.tapecalc.core.math.Parser
import com.tapecalc.core.math.PercentNode
import com.tapecalc.core.math.Statement
import com.tapecalc.core.math.Tokenizer
import com.tapecalc.core.math.Trig
import com.tapecalc.core.math.UnaryNode
import com.tapecalc.core.math.ValNode
import com.tapecalc.core.precision.BigMath
import com.tapecalc.core.precision.Rational
import java.math.BigDecimal

/** A complex number a + bi with exact parts while they are exact (so (1+2i)(3−i) is exactly 5+5i). */
class Complex(val re: CalcValue, val im: CalcValue) {

    val isReal: Boolean get() = im.isZero

    operator fun plus(o: Complex) = Complex(Arith.add(re, o.re), Arith.add(im, o.im))
    operator fun minus(o: Complex) = Complex(Arith.sub(re, o.re), Arith.sub(im, o.im))
    operator fun unaryMinus() = Complex(Arith.neg(re), Arith.neg(im))
    operator fun times(o: Complex) = Complex(
        Arith.sub(Arith.mul(re, o.re), Arith.mul(im, o.im)),
        Arith.add(Arith.mul(re, o.im), Arith.mul(im, o.re))
    )

    operator fun div(o: Complex): Complex {
        val d = Arith.add(Arith.mul(o.re, o.re), Arith.mul(o.im, o.im))
        if (d.isZero) throw CalcException(ErrorKind.DIVISION_BY_ZERO, "Division by zero")
        val n = this * o.conj()
        return Complex(Arith.div(n.re, d), Arith.div(n.im, d))
    }

    fun conj() = Complex(re, Arith.neg(im))

    /** Modulus |z|. */
    fun abs(): CalcValue = if (isReal) Arith.abs(re) else Arith.sqrt(Arith.add(Arith.mul(re, re), Arith.mul(im, im)))

    /** Argument (angle) in the chosen unit; undefined for 0. */
    fun arg(mode: AngleMode): CalcValue = Trig.atan2(im, re, mode)

    fun powInt(n: Int): Complex {
        if (Math.abs(n) > 100_000) throw CalcException(ErrorKind.DOMAIN, "The power is too large")
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

    fun sqrt(): Complex {
        if (isReal) {
            return if (re.signum >= 0) Complex(Arith.sqrt(re), CalcValue.ZERO) else Complex(CalcValue.ZERO, Arith.sqrt(Arith.neg(re)))
        }
        val two = CalcValue.ofLong(2)
        val m = abs()
        val a = Arith.sqrt(Arith.div(Arith.add(m, re), two))
        val b = Arith.sqrt(Arith.div(Arith.sub(m, re), two))
        return Complex(a, if (im.signum < 0) Arith.neg(b) else b)
    }

    fun exp(): Complex {
        val ea = if (re.isZero) CalcValue.ONE else CalcValue.ofDecimal(BigMath.exp(re.decimal))
        if (im.isZero) return Complex(ea, CalcValue.ZERO)
        return cleaned(Complex(Arith.mul(ea, CalcValue.ofDecimal(BigMath.cos(im.decimal))), Arith.mul(ea, CalcValue.ofDecimal(BigMath.sin(im.decimal)))))
    }

    fun ln(): Complex {
        if (re.isZero && im.isZero) throw CalcException(ErrorKind.DOMAIN, "ln(0) is undefined")
        val modulus = abs()
        val lnAbs = if (Arith.compare(modulus, CalcValue.ONE) == 0) CalcValue.ZERO else CalcValue.ofDecimal(BigMath.ln(modulus.decimal))
        return Complex(lnAbs, Trig.atan2(im, re, AngleMode.RAD))
    }

    /** z^w: whole powers are exact; the rest use e^(w·ln z). */
    fun pow(w: Complex): Complex {
        if (w.isReal && w.re.isExactInteger && w.re.exact!!.num.bitLength() <= 31) return cleaned(powInt(w.re.exact!!.num.toInt()))
        if (re.isZero && im.isZero) {
            if (w.re.signum > 0) return ZERO
            throw CalcException(ErrorKind.DIVISION_BY_ZERO, "Zero to a non-positive power is undefined")
        }
        if (w.isReal && w.re.exact?.let { it.den == java.math.BigInteger.valueOf(2) } == true) {
            // z^(p/2) = (sqrt z)^p keeps square roots exact.
            return cleaned(sqrt().powInt(w.re.exact!!.num.toInt()))
        }
        return cleaned((w * ln()).exp())
    }

    /** sin and cos of a complex number whose real part is in the current angle unit. */
    fun sin(mode: AngleMode): Complex {
        val x = toRadians(re, mode)
        val s = Trig.sin(x, AngleMode.RAD)
        val c = Trig.cos(x, AngleMode.RAD)
        if (im.isZero) return Complex(s, CalcValue.ZERO)
        val ch = CalcValue.ofDecimal(BigMath.cosh(im.decimal))
        val sh = CalcValue.ofDecimal(BigMath.sinh(im.decimal))
        return Complex(Arith.mul(s, ch), Arith.mul(c, sh))
    }

    fun cos(mode: AngleMode): Complex {
        val x = toRadians(re, mode)
        val s = Trig.sin(x, AngleMode.RAD)
        val c = Trig.cos(x, AngleMode.RAD)
        if (im.isZero) return Complex(c, CalcValue.ZERO)
        val ch = CalcValue.ofDecimal(BigMath.cosh(im.decimal))
        val sh = CalcValue.ofDecimal(BigMath.sinh(im.decimal))
        return Complex(Arith.mul(c, ch), Arith.neg(Arith.mul(s, sh)))
    }

    /** "3 + 4i", "-2i", "1/2 + 3/4i", "2". */
    fun format(): String {
        if (im.isZero) return cellText(re)
        val imAbs = Arith.abs(im)
        val coef = if (Arith.compare(imAbs, CalcValue.ONE) == 0) "" else cellText(imAbs)
        val imText = "${coef}i"
        if (re.isZero) return if (im.signum < 0) "-$imText" else imText
        return "${cellText(re)} ${if (im.signum < 0) "-" else "+"} $imText"
    }

    /** "5∠53.130102354156°" (degrees), "5∠0.927295218001612 rad" or "5∠59.0034 g". */
    fun formatPolar(mode: AngleMode): String {
        val r = abs()
        if (r.isZero) return "0"
        val a = arg(mode)
        val unit = when (mode) { AngleMode.DEG -> "°"; AngleMode.RAD -> " rad"; AngleMode.GRAD -> " g" }
        return "${cellText(r)}∠${cellText(a)}$unit"
    }

    override fun toString(): String = format()

    companion object {
        val ZERO = Complex(CalcValue.ZERO, CalcValue.ZERO)
        val ONE = Complex(CalcValue.ONE, CalcValue.ZERO)
        val I = Complex(CalcValue.ZERO, CalcValue.ONE)

        fun ofReal(v: CalcValue) = Complex(v, CalcValue.ZERO)

        /** r∠θ with θ in the given unit. */
        fun polar(r: CalcValue, theta: CalcValue, mode: AngleMode): Complex =
            Complex(Arith.mul(r, Trig.cos(theta.decimal, mode)), Arith.mul(r, Trig.sin(theta.decimal, mode)))

        private fun toRadians(v: CalcValue, mode: AngleMode): BigDecimal = when (mode) {
            AngleMode.RAD -> v.decimal
            AngleMode.DEG -> v.decimal.multiply(BigMath.pi, com.tapecalc.core.precision.Precision.WORK).divide(BigDecimal(180), com.tapecalc.core.precision.Precision.WORK)
            AngleMode.GRAD -> v.decimal.multiply(BigMath.pi, com.tapecalc.core.precision.Precision.WORK).divide(BigDecimal(200), com.tapecalc.core.precision.Precision.WORK)
        }

        /** Removes floating-point dust such as the 1E-60 imaginary part of e^(iπ). */
        internal fun cleaned(z: Complex): Complex {
            val big = Arith.abs(z.re).let { a -> val b = Arith.abs(z.im); if (Arith.compare(a, b) >= 0) a else b }
            if (big.isZero) return z
            val limit = Arith.mul(big, CalcValue.ofDecimal(BigDecimal("1E-30")))
            val re = if (Arith.compare(Arith.abs(z.re), limit) < 0) CalcValue.ZERO else z.re
            val im = if (Arith.compare(Arith.abs(z.im), limit) < 0) CalcValue.ZERO else z.im
            return Complex(re, im)
        }
    }
}

sealed interface ComplexOutcome {
    data class Ok(val value: Complex, val rect: String, val polar: String) : ComplexOutcome
    data class Err(val error: CalcError) : ComplexOutcome
}

/**
 * Calculator for complex numbers: `(3+4i)*(1-2i)`, `sqrt(-4)`, `abs(3+4i)`, `arg(1+i)`, `conj(2+3i)`,
 * `re(z)`, `im(z)`, `exp(i*pi)`, `(1+i)^8`, `ans`. `i` is the imaginary unit.
 */
object ComplexCalc {
    private val HELPERS = setOf("re", "im", "conj", "arg", "polar")

    fun evaluate(text: String, ctx: EvalContext = EvalContext(), ans: Complex? = null): ComplexOutcome = try {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) throw CalcException(ErrorKind.EMPTY, "Nothing to calculate")
        val (statement, _) = Parser.parse(Tokenizer.tokenize(trimmed))
        val node = when (statement) {
            is Statement.Expr -> statement.node
            is Statement.Assign -> throw CalcException(ErrorKind.SYNTAX, "Assignments are not available in complex mode")
        }
        val z = Eval(ctx, ans).eval(node)
        ComplexOutcome.Ok(z, z.format(), z.formatPolar(ctx.angleMode))
    } catch (e: CalcException) {
        ComplexOutcome.Err(e.error)
    } catch (e: ArithmeticException) {
        ComplexOutcome.Err(CalcError(ErrorKind.DOMAIN, e.message ?: "Math error"))
    }

    private class Eval(val ctx: EvalContext, val ans: Complex?) {
        fun unsupported(what: String): Nothing =
            throw CalcException(ErrorKind.DOMAIN, "$what is not available for complex numbers")

        fun eval(n: Node): Complex = when (n) {
            is NumNode -> Complex.ofReal(CalcValue.ofRational(n.value))
            is ValNode -> Complex.ofReal(n.value)
            is ParenNode -> eval(n.inner)
            is UnaryNode -> if (n.negate) -eval(n.operand) else eval(n.operand)
            is PercentNode -> eval(n.operand) / Complex.ofReal(CalcValue.ofLong(100))
            is FactorialNode -> {
                val z = eval(n.operand)
                if (!z.isReal) unsupported("Factorial")
                Complex.ofReal(Functions.call("fact", listOf(z.re), ctx))
            }
            is NameNode -> name(n)
            is BinaryNode -> binary(n)
            is CallNode -> call(n.name, n.args.map { eval(it) }, n.pos)
        }

        fun name(n: NameNode): Complex {
            val key = n.name
            if (key == "i" || key == "j") return Complex.I
            ctx.variables[key]?.let { return Complex.ofReal(it) }
            when (key.lowercase()) {
                "pi" -> return Complex.ofReal(CalcValue.ofDecimal(BigMath.pi))
                "e" -> return Complex.ofReal(CalcValue.ofDecimal(BigMath.e))
                "phi" -> return Complex.ofReal(CalcValue.ofDecimal(BigMath.phi))
                "ans" -> return ans ?: ctx.ans?.let { Complex.ofReal(it) }
                    ?: throw CalcException(ErrorKind.UNKNOWN_NAME, "There is no previous answer yet", n.pos)
            }
            com.tapecalc.core.math.Constants.valueOf(key)?.let { return Complex.ofReal(it) }
            throw CalcException(ErrorKind.UNKNOWN_NAME, "Unknown name '$key'", n.pos)
        }

        fun binary(n: BinaryNode): Complex {
            // re(z), im(z), conj(z), arg(z): the parser reads these as name × (…).
            val l = n.left
            val r = n.right
            if (n.implicit && n.op == BinOp.MUL && l is NameNode && l.name.lowercase() in HELPERS && r is ParenNode) {
                return helper(l.name.lowercase(), eval(r.inner))
            }
            if (n.implicit && n.op == BinOp.MUL && l is NameNode && l.name.lowercase() in HELPERS && r is BinaryNode &&
                r.op == BinOp.POW && r.left is ParenNode
            ) {
                val inner = helper(l.name.lowercase(), eval((r.left as ParenNode).inner))
                return inner.pow(eval(r.right))
            }
            val a = eval(l)
            val b = eval(r)
            return when (n.op) {
                BinOp.ADD -> a + b
                BinOp.SUB -> a - b
                BinOp.MUL -> a * b
                BinOp.DIV -> a / b
                BinOp.POW -> a.pow(b)
                BinOp.MOD -> unsupported("mod")
            }
        }

        fun helper(name: String, z: Complex): Complex = when (name) {
            "re" -> Complex.ofReal(z.re)
            "im" -> Complex.ofReal(z.im)
            "conj" -> z.conj()
            "arg" -> Complex.ofReal(z.arg(ctx.angleMode))
            else -> z
        }

        fun call(name: String, a: List<Complex>, pos: Int): Complex {
            Functions.checkArity(name, a.size)
            val z = a[0]
            return when (name) {
                "abs" -> Complex.ofReal(z.abs())
                "sqrt" -> z.sqrt()
                "exp" -> z.exp()
                "ln" -> z.ln()
                "log" -> if (a.size == 1) z.ln() / Complex.ofReal(CalcValue.ofDecimal(BigMath.ln(BigDecimal.TEN)))
                else z.ln() / a[1].ln()
                "sin" -> z.sin(ctx.angleMode)
                "cos" -> z.cos(ctx.angleMode)
                "pow" -> z.pow(a[1])
                "root" -> {
                    if (!a[1].isReal) unsupported("root")
                    z.pow(Complex.ONE / a[1])
                }
                else -> {
                    // Everything else works on real numbers only.
                    if (a.any { !it.isReal }) unsupported(name)
                    Complex.ofReal(Functions.call(name, a.map { it.re }, ctx))
                }
            }
        }
    }
}

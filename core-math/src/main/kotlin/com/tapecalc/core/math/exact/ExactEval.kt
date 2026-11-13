package com.tapecalc.core.math.exact

import com.tapecalc.core.math.AngleMode
import com.tapecalc.core.math.Arith
import com.tapecalc.core.math.BinOp
import com.tapecalc.core.math.BinaryNode
import com.tapecalc.core.math.CalcException
import com.tapecalc.core.math.CalcValue
import com.tapecalc.core.math.CallNode
import com.tapecalc.core.math.Constants
import com.tapecalc.core.math.EvalContext
import com.tapecalc.core.math.FactorialNode
import com.tapecalc.core.math.NameNode
import com.tapecalc.core.math.Node
import com.tapecalc.core.math.NumNode
import com.tapecalc.core.math.ParenNode
import com.tapecalc.core.math.PercentNode
import com.tapecalc.core.math.UnaryNode
import com.tapecalc.core.math.ValNode
import com.tapecalc.core.precision.Rational
import java.math.BigDecimal

/**
 * The symbolic side of the calculator. It walks the same syntax tree as the decimal evaluator and keeps the result as
 * an [ExactNumber] (fractions, square roots and π) for as long as that is possible, under the same [EvalContext], so
 * the angle unit, variables and `ans` are the ones the decimal engine uses.
 *
 * It is an add-on, never a replacement: [evaluate] returns null for anything it cannot keep exact, and the caller
 * then shows the decimal engine's value as before. [agrees] lets the caller check an exact result against the
 * decimal value before trusting it.
 */
object ExactEval {

    private val HUNDRED = ExactNumber.of(100)
    private val HALF = Rational.of(1, 2)
    private const val MAX_FACTORIAL = 170

    /** The exact value of [node], or null when it cannot be written with fractions, roots and π. */
    fun evaluate(node: Node, ctx: EvalContext): ExactNumber? = try {
        Walker(ctx).eval(node)
    } catch (e: Unsupported) {
        null
    } catch (e: CalcException) {
        null
    } catch (e: ArithmeticException) {
        null
    } catch (e: StackOverflowError) {
        null
    }

    /** True when [exact], evaluated to a decimal, matches [value] to far more digits than are ever shown. */
    fun agrees(exact: ExactNumber, value: CalcValue): Boolean {
        val a = exact.toBigDecimal()
        val b = value.decimal
        val tolerance = TOLERANCE.multiply(b.abs().max(BigDecimal.ONE))
        return a.subtract(b).abs() <= tolerance
    }

    private val TOLERANCE = BigDecimal("1E-28")

    private class Walker(private val ctx: EvalContext) {
        private val mode: AngleMode = ctx.angleMode

        fun eval(node: Node): ExactNumber = when (node) {
            is NumNode -> ExactNumber.of(node.value)
            is NameNode -> resolve(node.name)
            is ParenNode -> eval(node.inner)
            is UnaryNode -> if (node.negate) -eval(node.operand) else eval(node.operand)
            is PercentNode -> eval(node.operand) / HUNDRED
            is FactorialNode -> factorial(eval(node.operand))
            is ValNode -> fromValue(node.value)
            is CallNode -> call(node)
            is BinaryNode -> binary(node)
        }

        private fun fromValue(v: CalcValue): ExactNumber = ExactNumber.of(v.exact ?: throw Unsupported())

        private fun resolve(name: String): ExactNumber {
            ctx.variables[name]?.let { return fromValue(it) }
            when (name.lowercase()) {
                "pi" -> return ExactNumber.PI
                // phi = (1 + √5) / 2
                "phi" -> return (ExactNumber.ONE + ExactNumber.sqrtOf(Rational.of(5))) * ExactNumber.of(HALF)
                "ans" -> return fromValue(ctx.ans ?: throw Unsupported())
            }
            Constants.valueOf(name)?.let { return fromValue(it) }
            ctx.variables.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.let { return fromValue(it.value) }
            throw Unsupported("unknown name")
        }

        private fun factorial(x: ExactNumber): ExactNumber {
            val r = x.asRational() ?: throw Unsupported()
            if (!r.isInteger || r.signum < 0 || r.num > java.math.BigInteger.valueOf(MAX_FACTORIAL.toLong())) throw Unsupported()
            return fromValue(Arith.factorial(CalcValue.ofRational(r)))
        }

        private fun binary(node: BinaryNode): ExactNumber {
            val left = eval(node.left)
            val rightNode = node.right
            // "100 + 10%" means 100 + (10% of 100), as in the decimal evaluator
            if ((node.op == BinOp.ADD || node.op == BinOp.SUB) && rightNode is PercentNode) {
                val delta = left * (eval(rightNode.operand) / HUNDRED)
                return if (node.op == BinOp.ADD) left + delta else left - delta
            }
            val right = eval(rightNode)
            return when (node.op) {
                BinOp.ADD -> left + right
                BinOp.SUB -> left - right
                BinOp.MUL -> left * right
                BinOp.DIV -> left / right
                BinOp.POW -> power(left, right)
                BinOp.MOD -> {
                    val a = left.asRational() ?: throw Unsupported()
                    val b = right.asRational() ?: throw Unsupported()
                    fromValue(Arith.mod(CalcValue.ofRational(a), CalcValue.ofRational(b)))
                }
            }
        }

        /** Whole-number powers of anything, and halves (x^(1/2), x^(3/2)) of what has a square root. */
        private fun power(base: ExactNumber, exponent: ExactNumber): ExactNumber {
            val e = exponent.asRational() ?: throw Unsupported()
            if (e.isInteger) {
                if (e.num.bitLength() > 16) throw Unsupported()
                return base.pow(e.num.toInt())
            }
            if (e.den == java.math.BigInteger.valueOf(2) && e.num.bitLength() <= 16) return base.sqrt().pow(e.num.toInt())
            // a rational base and exponent whose result is itself rational, e.g. 8^(1/3) = 2
            val b = base.asRational() ?: throw Unsupported()
            return fromValue(Arith.pow(CalcValue.ofRational(b), CalcValue.ofRational(e)))
        }

        private fun call(node: CallNode): ExactNumber {
            val args = node.args
            return when (node.name) {
                "sqrt" -> one(args).sqrt()
                "abs" -> one(args).let { if (it.signum() < 0) -it else it }
                "pow" -> two(args).let { (a, b) -> power(a, b) }
                "hypot" -> two(args).let { (a, b) -> (a * a + b * b).sqrt() }
                "fact" -> factorial(one(args))
                "sin" -> SpecialAngles.sin(one(args), mode) ?: throw Unsupported()
                "cos" -> SpecialAngles.cos(one(args), mode) ?: throw Unsupported()
                "tan" -> SpecialAngles.tan(one(args), mode) ?: throw Unsupported()
                "asin" -> SpecialAngles.asin(one(args), mode) ?: throw Unsupported()
                "acos" -> SpecialAngles.acos(one(args), mode) ?: throw Unsupported()
                "atan" -> SpecialAngles.atan(one(args), mode) ?: throw Unsupported()
                // the degrees / minutes / seconds pieces of 2°30'15"
                "_deg" -> SpecialAngles.fromDegrees(one(args).asRational() ?: throw Unsupported(), mode)
                "_min" -> SpecialAngles.fromDegrees((one(args).asRational() ?: throw Unsupported()) / Rational.of(60), mode)
                "_sec" -> SpecialAngles.fromDegrees((one(args).asRational() ?: throw Unsupported()) / Rational.of(3600), mode)
                else -> throw Unsupported("function not supported")
            }
        }

        private fun one(args: List<Node>): ExactNumber {
            if (args.size != 1) throw Unsupported()
            return eval(args[0])
        }

        private fun two(args: List<Node>): Pair<ExactNumber, ExactNumber> {
            if (args.size != 2) throw Unsupported()
            return eval(args[0]) to eval(args[1])
        }
    }
}

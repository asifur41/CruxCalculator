package com.tapecalc.core.math.algebra

import com.tapecalc.core.math.Arith
import com.tapecalc.core.math.BinOp
import com.tapecalc.core.math.BinaryNode
import com.tapecalc.core.math.CalcError
import com.tapecalc.core.math.CalcException
import com.tapecalc.core.math.CalcValue
import com.tapecalc.core.math.CallNode
import com.tapecalc.core.math.ErrorKind
import com.tapecalc.core.math.EvalContext
import com.tapecalc.core.math.Evaluator
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
import com.tapecalc.core.math.UnaryNode
import com.tapecalc.core.math.ValNode

/** The answer of a matrix expression: a number or a matrix. */
sealed interface MatrixValue {
    class Scalar(val value: CalcValue) : MatrixValue
    class Mat(val matrix: Matrix) : MatrixValue
}

sealed interface MatrixOutcome {
    data class Ok(val value: MatrixValue) : MatrixOutcome
    data class Err(val error: CalcError) : MatrixOutcome
}

/**
 * Evaluates expressions such as `A*B + 2C`, `det(A)`, `inv(A)`, `A^-1`, `A^3`, `A^T`, `trans(A)`, `tr(A)`,
 * `rank(A)`, `rref(A)`, `eigen`-free. Matrices are named by a capital letter (A to F); numbers work as usual.
 * Write `det(A)^2` as `(det(A))^2` if you need a power of a function result.
 */
object MatrixExpr {
    private val FUNCTIONS = setOf("det", "inv", "trans", "transpose", "tr", "trace", "rank", "rref")

    fun evaluate(text: String, matrices: Map<String, Matrix>, ctx: EvalContext = EvalContext()): MatrixOutcome = try {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) throw CalcException(ErrorKind.EMPTY, "Nothing to calculate")
        val (statement, _) = Parser.parse(Tokenizer.tokenize(trimmed))
        val node = (statement as? Statement.Expr)?.node
            ?: throw CalcException(ErrorKind.SYNTAX, "Assignments are not available in matrix mode")
        MatrixOutcome.Ok(Eval(matrices, ctx).eval(node))
    } catch (e: CalcException) {
        MatrixOutcome.Err(e.error)
    } catch (e: IllegalArgumentException) {
        MatrixOutcome.Err(CalcError(ErrorKind.DOMAIN, e.message ?: "Invalid matrix"))
    } catch (e: ArithmeticException) {
        MatrixOutcome.Err(CalcError(ErrorKind.DOMAIN, e.message ?: "Math error"))
    }

    private class Eval(val mats: Map<String, Matrix>, val ctx: EvalContext) {
        fun fail(message: String): Nothing = throw CalcException(ErrorKind.DOMAIN, message)

        fun eval(n: Node): MatrixValue = when (n) {
            is ParenNode -> eval(n.inner)
            is NumNode, is ValNode, is PercentNode, is FactorialNode -> scalar(n)
            is NameNode -> name(n)
            is UnaryNode -> {
                val v = eval(n.operand)
                if (!n.negate) v else when (v) {
                    is MatrixValue.Scalar -> MatrixValue.Scalar(Arith.neg(v.value))
                    is MatrixValue.Mat -> MatrixValue.Mat(MatrixOps.scale(v.matrix, CalcValue.ofLong(-1)))
                }
            }
            is BinaryNode -> binary(n)
            is CallNode -> call(n)
        }

        fun scalar(n: Node): MatrixValue = MatrixValue.Scalar(Evaluator(ctx).eval(n))

        fun name(n: NameNode): MatrixValue {
            mats[n.name]?.let { return MatrixValue.Mat(it) }
            if (n.name.length == 1 && n.name[0] in 'A'..'Z' && n.name !in ctx.variables) {
                fail("Matrix ${n.name} is not defined. Enter it first.")
            }
            return scalar(n)
        }

        fun mat(v: MatrixValue, what: String): Matrix =
            (v as? MatrixValue.Mat)?.matrix ?: fail("$what needs a matrix")

        fun binary(n: BinaryNode): MatrixValue {
            val l = n.left
            val r = n.right
            // det(A) etc. arrive as name × (…).
            if (n.implicit && n.op == BinOp.MUL && l is NameNode && l.name.lowercase() in FUNCTIONS) {
                if (r is ParenNode) return function(l.name.lowercase(), eval(r.inner))
                if (r is BinaryNode && r.op == BinOp.POW && r.left is ParenNode) {
                    return power(function(l.name.lowercase(), eval((r.left as ParenNode).inner)), r.right)
                }
            }
            if (n.op == BinOp.POW) return power(eval(l), r)
            val a = eval(l)
            val b = eval(r)
            return when (n.op) {
                BinOp.ADD, BinOp.SUB -> when {
                    a is MatrixValue.Scalar && b is MatrixValue.Scalar ->
                        MatrixValue.Scalar(if (n.op == BinOp.ADD) Arith.add(a.value, b.value) else Arith.sub(a.value, b.value))
                    a is MatrixValue.Mat && b is MatrixValue.Mat ->
                        MatrixValue.Mat(if (n.op == BinOp.ADD) MatrixOps.add(a.matrix, b.matrix) else MatrixOps.sub(a.matrix, b.matrix))
                    else -> fail("Cannot add or subtract a number and a matrix")
                }
                BinOp.MUL -> when {
                    a is MatrixValue.Scalar && b is MatrixValue.Scalar -> MatrixValue.Scalar(Arith.mul(a.value, b.value))
                    a is MatrixValue.Scalar && b is MatrixValue.Mat -> MatrixValue.Mat(MatrixOps.scale(b.matrix, a.value))
                    a is MatrixValue.Mat && b is MatrixValue.Scalar -> MatrixValue.Mat(MatrixOps.scale(a.matrix, b.value))
                    else -> MatrixValue.Mat(MatrixOps.mul((a as MatrixValue.Mat).matrix, (b as MatrixValue.Mat).matrix))
                }
                BinOp.DIV -> when {
                    a is MatrixValue.Scalar && b is MatrixValue.Scalar -> MatrixValue.Scalar(Arith.div(a.value, b.value))
                    a is MatrixValue.Mat && b is MatrixValue.Scalar -> {
                        if (b.value.isZero) throw CalcException(ErrorKind.DIVISION_BY_ZERO, "Division by zero")
                        MatrixValue.Mat(MatrixOps.scale(a.matrix, Arith.div(CalcValue.ONE, b.value)))
                    }
                    else -> fail("Cannot divide by a matrix. Multiply by its inverse instead, for example A*inv(B)")
                }
                BinOp.MOD -> fail("mod is not available for matrices")
                BinOp.POW -> fail("unreachable")
            }
        }

        fun power(base: MatrixValue, exponent: Node): MatrixValue {
            if (base is MatrixValue.Mat) {
                if (exponent is NameNode && exponent.name == "T") return MatrixValue.Mat(MatrixOps.transpose(base.matrix))
                val e = eval(exponent)
                val k = (e as? MatrixValue.Scalar)?.value ?: fail("A matrix power must be a whole number")
                return MatrixValue.Mat(MatrixOps.power(base.matrix, Arith.toInt(k, "A matrix power")))
            }
            val e = eval(exponent)
            if (e !is MatrixValue.Scalar) fail("An exponent cannot be a matrix")
            return MatrixValue.Scalar(Arith.pow((base as MatrixValue.Scalar).value, e.value))
        }

        fun function(name: String, arg: MatrixValue): MatrixValue {
            val m = mat(arg, name)
            return when (name) {
                "det" -> MatrixValue.Scalar(MatrixOps.determinant(m))
                "inv" -> MatrixValue.Mat(MatrixOps.inverse(m))
                "trans", "transpose" -> MatrixValue.Mat(MatrixOps.transpose(m))
                "tr", "trace" -> MatrixValue.Scalar(MatrixOps.trace(m))
                "rank" -> MatrixValue.Scalar(CalcValue.ofLong(MatrixOps.rank(m).toLong()))
                else -> MatrixValue.Mat(MatrixOps.rref(m))
            }
        }

        fun call(n: CallNode): MatrixValue {
            Functions.checkArity(n.name, n.args.size)
            val args = n.args.map { eval(it) }
            val values = args.map { (it as? MatrixValue.Scalar)?.value ?: fail("${n.name} works on numbers, not matrices") }
            return MatrixValue.Scalar(Functions.call(n.name, values, ctx))
        }
    }
}

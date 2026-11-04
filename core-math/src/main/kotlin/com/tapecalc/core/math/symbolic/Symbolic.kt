package com.tapecalc.core.math.symbolic

import com.tapecalc.core.math.BinOp
import com.tapecalc.core.math.BinaryNode
import com.tapecalc.core.math.CalcError
import com.tapecalc.core.math.CalcException
import com.tapecalc.core.math.CallNode
import com.tapecalc.core.math.ErrorKind
import com.tapecalc.core.math.FactorialNode
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
import com.tapecalc.core.precision.Rational

sealed class SymOutcome {
    /** [text] can be pasted straight back into the calculator. */
    class Ok(val text: String) : SymOutcome()
    class Err(val error: CalcError) : SymOutcome()
}

/**
 * Symbolic algebra on one expression at a time: simplify, expand, factor, differentiate, integrate (indefinite),
 * substitute. Results are written in the calculator's own syntax. Unknown cases return a clear message rather
 * than a wrong answer.
 */
object Symbolic {

    fun simplify(text: String): SymOutcome = run(text) { Printer.fmt(Simplifier.simp(parse(it))) }

    fun expand(text: String): SymOutcome = run(text) { Printer.fmt(Simplifier.expand(parse(it))) }

    fun factor(text: String, variable: String? = null): SymOutcome = run(text) {
        val trimmed = text.trim()
        val asInt = trimmed.toBigIntegerOrNull()
        if (asInt != null) return@run Factoring.factorInteger(asInt)
        val e = parse(it)
        val v = variable ?: symbols(e).sorted().firstOrNull() ?: throw NoClosedForm("There is nothing to factor")
        Factoring.factor(e, v)
    }

    fun derivative(text: String, variable: String = "x"): SymOutcome = run(text) {
        Printer.fmt(Calculus.derivative(parse(it), variable))
    }

    fun integral(text: String, variable: String = "x"): SymOutcome = run(text) {
        Printer.fmt(Calculus.integral(parse(it), variable)) + " + C"
    }

    /** Replaces [variable] by another expression (`x^2 + 1` with x = `a + 1`). */
    fun substitute(text: String, variable: String, replacement: String): SymOutcome = run(text) {
        val with = parse(replacement)
        Printer.fmt(Simplifier.expand(substitute(parse(it), variable, with)))
    }

    private fun run(text: String, block: (String) -> String): SymOutcome = try {
        if (text.isBlank()) throw CalcException(ErrorKind.EMPTY, "Nothing to calculate")
        SymOutcome.Ok(block(text))
    } catch (e: CalcException) {
        SymOutcome.Err(e.error)
    } catch (e: NoClosedForm) {
        SymOutcome.Err(CalcError(ErrorKind.TOO_COMPLEX, e.message ?: "No closed form found"))
    } catch (e: SymbolicLimit) {
        SymOutcome.Err(CalcError(ErrorKind.TOO_COMPLEX, e.message ?: "Too large"))
    } catch (e: ArithmeticException) {
        SymOutcome.Err(CalcError(ErrorKind.DOMAIN, e.message ?: "Math error"))
    } catch (e: StackOverflowError) {
        SymOutcome.Err(CalcError(ErrorKind.TOO_COMPLEX, "The expression is too deeply nested"))
    }

    private fun parse(text: String): E {
        val (statement, _) = Parser.parse(Tokenizer.tokenize(text.trim()))
        val node = when (statement) {
            is Statement.Expr -> statement.node
            is Statement.Assign -> throw CalcException(ErrorKind.SYNTAX, "Assignments are not used in symbolic mode")
        }
        return Simplifier.simp(convert(node))
    }

    private fun fail(what: String): Nothing = throw CalcException(ErrorKind.DOMAIN, "$what is not supported in symbolic mode")

    private fun convert(n: Node): E = when (n) {
        is NumNode -> Num(n.value)
        is ValNode -> Num(n.value.exact ?: fail("A decimal-only value"))
        is ParenNode -> convert(n.inner)
        is NameNode -> Sym(n.name)
        is UnaryNode -> if (n.negate) Mul(listOf(MINUS_ONE, convert(n.operand))) else convert(n.operand)
        is BinaryNode -> {
            val l = convert(n.left)
            val r = convert(n.right)
            when (n.op) {
                BinOp.ADD -> Add(listOf(l, r))
                BinOp.SUB -> Add(listOf(l, Mul(listOf(MINUS_ONE, r))))
                BinOp.MUL -> Mul(listOf(l, r))
                BinOp.DIV -> {
                    if (r is Num && r.v.isZero) throw CalcException(ErrorKind.DIVISION_BY_ZERO, "Division by zero")
                    Mul(listOf(l, Pw(r, MINUS_ONE)))
                }
                BinOp.POW -> if (l is Sym && l.name == "e") Fn("exp", r) else Pw(l, r)
                BinOp.MOD -> fail("Remainder")
            }
        }
        is PercentNode -> Mul(listOf(convert(n.operand), num(1, 100)))
        is FactorialNode -> fail("Factorial")
        is CallNode -> call(n)
        else -> fail("This expression")
    }

    private fun call(n: CallNode): E {
        val args = n.args.map { convert(it) }
        fun one(): E { if (args.size != 1) throw CalcException(ErrorKind.INVALID_ARGUMENT, "${n.name} needs one value"); return args[0] }
        return when (n.name.lowercase()) {
            "sqrt" -> Pw(one(), num(1, 2))
            "cbrt" -> Pw(one(), num(1, 3))
            "sin", "cos", "tan", "sinh", "cosh", "tanh", "exp", "ln", "abs" -> Fn(n.name.lowercase(), one())
            "asin", "arcsin" -> Fn("asin", one())
            "acos", "arccos" -> Fn("acos", one())
            "atan", "arctan" -> Fn("atan", one())
            "log" -> if (args.size == 1) Mul(listOf(Fn("ln", args[0]), Pw(Fn("ln", num(10)), MINUS_ONE)))
                     else Mul(listOf(Fn("ln", args[1]), Pw(Fn("ln", args[0]), MINUS_ONE)))
            else -> fail("The function ${n.name}")
        }
    }
}

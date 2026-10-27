package com.tapecalc.core.math

import com.tapecalc.core.precision.BigMath

/** Walks the AST and computes a [CalcValue]. */
internal class Evaluator(private val ctx: EvalContext) {

    private val hundred = CalcValue.ofLong(100)

    fun eval(node: Node): CalcValue = when (node) {
        is NumNode -> CalcValue.ofRational(node.value)
        is NameNode -> resolve(node)
        is ParenNode -> eval(node.inner)
        is UnaryNode -> if (node.negate) Arith.neg(eval(node.operand)) else eval(node.operand)
        is PercentNode -> Arith.div(eval(node.operand), hundred)
        is FactorialNode -> Arith.factorial(eval(node.operand))
        is CallNode -> Functions.call(node.name, node.args.map { eval(it) }, ctx)
        is BinaryNode -> binary(node)
    }

    private fun resolve(node: NameNode): CalcValue {
        val name = node.name
        ctx.variables[name]?.let { return it }
        when (name.lowercase()) {
            "pi" -> return CalcValue.ofDecimal(BigMath.pi)
            "e" -> return CalcValue.ofDecimal(BigMath.e)
            "phi" -> return CalcValue.ofDecimal(BigMath.phi)
            "ans" -> return ctx.ans ?: throw CalcException(ErrorKind.UNKNOWN_NAME, "There is no previous answer yet", node.pos)
        }
        ctx.variables.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.let { return it.value }
        throw CalcException(ErrorKind.UNKNOWN_NAME, "Unknown name '$name'", node.pos)
    }

    private fun binary(node: BinaryNode): CalcValue {
        val left = eval(node.left)
        val rightNode = node.right
        // "100 + 10%" means 100 + (10% of 100); "100 - 10%" means 100 - (10% of 100).
        if ((node.op == BinOp.ADD || node.op == BinOp.SUB) && rightNode is PercentNode) {
            val pct = Arith.div(eval(rightNode.operand), hundred)
            val delta = Arith.mul(left, pct)
            return if (node.op == BinOp.ADD) Arith.add(left, delta) else Arith.sub(left, delta)
        }
        val right = eval(rightNode)
        return when (node.op) {
            BinOp.ADD -> Arith.add(left, right)
            BinOp.SUB -> Arith.sub(left, right)
            BinOp.MUL -> Arith.mul(left, right)
            BinOp.DIV -> Arith.div(left, right)
            BinOp.POW -> Arith.pow(left, right)
            BinOp.MOD -> Arith.mod(left, right)
        }
    }
}

package com.tapecalc.core.math

import com.tapecalc.core.precision.BigMath

/** Walks the AST and computes a [CalcValue]. */
internal class Evaluator(private val ctx: EvalContext) {

    private val hundred = CalcValue.ofLong(100)

    /** Extra outputs collected while evaluating (the angle of Pol, the y of Rec). */
    val extras = ArrayList<Pair<String, CalcValue>>()

    /** Variables set as a side effect (Pol and Rec store their two results in X and Y, like a Casio). */
    val assignments = LinkedHashMap<String, CalcValue>()

    fun eval(node: Node): CalcValue = when (node) {
        is NumNode -> CalcValue.ofRational(node.value)
        is NameNode -> resolve(node)
        is ParenNode -> eval(node.inner)
        is UnaryNode -> if (node.negate) Arith.neg(eval(node.operand)) else eval(node.operand)
        is PercentNode -> Arith.div(eval(node.operand), hundred)
        is FactorialNode -> Arith.factorial(eval(node.operand))
        is CallNode -> call(node)
        is ValNode -> node.value
        is BinaryNode -> binary(node)
    }

    private fun call(node: CallNode): CalcValue {
        if (node.name !in Functions.SPECIAL) return Functions.call(node.name, node.args.map { eval(it) }, ctx)
        Functions.checkArity(node.name, node.args.size)
        return when (node.name) {
            "sum", "prod" -> series(node)
            "integral" -> integral(node)
            "deriv" -> derivative(node)
            "solve" -> solve(node)
            "pol" -> pol(eval(node.args[0]), eval(node.args[1]))
            "rec" -> rec(eval(node.args[0]), eval(node.args[1]))
            else -> throw CalcException(ErrorKind.UNKNOWN_NAME, "Unknown function '${node.name}'")
        }
    }

    private fun variableName(node: CallNode, index: Int, example: String): String =
        (node.args[index] as? NameNode)?.name
            ?: throw CalcException(ErrorKind.SYNTAX, "The ${ordinal(index)} value of ${node.name} must be a variable name, for example $example")

    private fun ordinal(i: Int) = when (i) { 0 -> "first"; 1 -> "second"; 2 -> "third"; else -> "fourth" }

    private fun withVariable(name: String, value: CalcValue): Evaluator {
        val vars = HashMap(ctx.variables)
        vars[name] = value
        return Evaluator(EvalContext(ctx.angleMode, vars, ctx.ans))
    }

    /** sum(expression, k, from, to) and prod(...): both limits must be whole numbers. */
    private fun series(node: CallNode): CalcValue {
        val v = variableName(node, 1, "${node.name}(k^2, k, 1, 10)")
        val from = Arith.toInt(eval(node.args[2]), "The first limit")
        val to = Arith.toInt(eval(node.args[3]), "The last limit")
        if (to.toLong() - from > Numeric.MAX_TERMS) {
            throw CalcException(ErrorKind.TOO_COMPLEX, "Too many terms (maximum ${Numeric.MAX_TERMS})")
        }
        var acc = if (node.name == "sum") CalcValue.ZERO else CalcValue.ONE
        for (k in from..to) {
            val t = withVariable(v, CalcValue.ofLong(k.toLong())).eval(node.args[0])
            acc = if (node.name == "sum") Arith.add(acc, t) else Arith.mul(acc, t)
        }
        return acc
    }

    private fun numeric(): NumericEval = NumericEval(ctx)

    /** integral(f(x), x, a, b): limits may be inf / -inf. */
    private fun integral(node: CallNode): CalcValue {
        val v = variableName(node, 1, "integral(x^2, x, 0, 3)")
        val ne = numeric()
        val a = ne.eval(node.args[2], emptyMap())
        val b = ne.eval(node.args[3], emptyMap())
        if (a.isNaN() || b.isNaN()) throw CalcException(ErrorKind.UNDEFINED, "The limits of the integral are not valid numbers")
        val body = node.args[0]
        val result = Numeric.integrate({ x -> ne.eval(body, mapOf(v to x)) }, a, b)
        return Numeric.fromDouble(result, 13)
    }

    /** deriv(f(x), x, point). */
    private fun derivative(node: CallNode): CalcValue {
        val v = variableName(node, 1, "deriv(x^2, x, 3)")
        val ne = numeric()
        val at = ne.eval(node.args[2], emptyMap())
        val body = node.args[0]
        return Numeric.fromDouble(Numeric.derivative({ x -> ne.eval(body, mapOf(v to x)) }, at), 11)
    }

    /** solve(f(x), x, guess): a root of f near the guess (the equation f(x) = 0). */
    private fun solve(node: CallNode): CalcValue {
        val v = variableName(node, 1, "solve(x^2 - 2, x, 1)")
        val ne = numeric()
        val guess = if (node.args.size == 3) ne.eval(node.args[2], emptyMap()) else 1.0
        val body = node.args[0]
        val root = Numeric.findRoot({ x -> ne.eval(body, mapOf(v to x)) }, guess)
        return Numeric.polish(body, v, root, ctx) ?: Numeric.fromDouble(root, 14)
    }

    /** Pol(x, y): r is the result, the angle goes to extras; X = r and Y = θ like a Casio. */
    private fun pol(x: CalcValue, y: CalcValue): CalcValue {
        val r = Arith.sqrt(Arith.add(Arith.mul(x, x), Arith.mul(y, y)))
        val theta = Trig.atan2(y, x, ctx.angleMode)
        extras.add("θ" to theta)
        assignments["X"] = r
        assignments["Y"] = theta
        return r
    }

    /** Rec(r, θ): x is the result, y goes to extras; X = x and Y = y. */
    private fun rec(r: CalcValue, theta: CalcValue): CalcValue {
        val x = Arith.mul(r, Trig.cos(theta.decimal, ctx.angleMode))
        val y = Arith.mul(r, Trig.sin(theta.decimal, ctx.angleMode))
        extras.add("y" to y)
        assignments["X"] = x
        assignments["Y"] = y
        return x
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
        Constants.valueOf(name)?.let { return it }
        ctx.variables.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.let { return it.value }
        val known = ctx.variables.keys + Functions.suggestionNames()
        val hint = Constants.names().firstOrNull { it.equals(name, ignoreCase = true) } ?: Suggestions.closest(name, known)
        throw CalcException(
            CalcError(ErrorKind.UNKNOWN_NAME, Suggestions.withHint("Unknown name '$name'", hint), node.pos, hint)
        )
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

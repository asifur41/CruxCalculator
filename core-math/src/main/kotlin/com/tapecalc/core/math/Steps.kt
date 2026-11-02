package com.tapecalc.core.math

import com.tapecalc.core.precision.NumberFormatter
import com.tapecalc.core.precision.Rational

/** One line of a worked solution: what is done, and what the expression looks like afterwards. */
data class SolutionStep(val description: String, val expression: String)

sealed interface StepsOutcome {
    data class Ok(val steps: List<SolutionStep>, val result: EvalResult) : StepsOutcome
    data class Err(val error: CalcError) : StepsOutcome
}

/**
 * Shows how an expression is worked out, one operation at a time, in the order a person would do it by hand:
 * parentheses first, then functions and factorials, powers, negation, multiplication and division, and last
 * addition and subtraction. The values come from the real evaluator, so the last line always equals the answer.
 */
object StepSolver {
    private const val MAX_STEPS = 60

    fun solve(input: String, ctx: EvalContext = EvalContext()): StepsOutcome {
        val outcome = Calculator.evaluate(input, ctx)
        if (outcome is EvalOutcome.Failure) return StepsOutcome.Err(outcome.error)
        val result = (outcome as EvalOutcome.Success).result
        return try {
            StepsOutcome.Ok(buildSteps(input.trim(), ctx, result), result)
        } catch (e: CalcException) {
            StepsOutcome.Err(e.error)
        }
    }

    private fun buildSteps(text: String, ctx: EvalContext, result: EvalResult): List<SolutionStep> {
        if (text.indexOf(':') >= 0) {
            val steps = ArrayList<SolutionStep>()
            val vars = LinkedHashMap(ctx.variables)
            var ans = ctx.ans
            for (part in text.split(':').map { it.trim() }.filter { it.isNotEmpty() }) {
                val c = EvalContext(ctx.angleMode, vars, ans)
                val r = (Calculator.evaluate(part, c) as EvalOutcome.Success).result
                steps.add(SolutionStep("Statement", part))
                steps.addAll(buildSteps(part, c, r).drop(1))
                vars.putAll(r.assignments)
                ans = r.value
            }
            return steps
        }
        val parsed = try {
            val tokens = Tokenizer.tokenize(text)
            Parser.parse(tokens).first
        } catch (e: CalcException) {
            null
        }
        val isConversion = parsed == null || text.contains(Regex("""\s(to|in)\s|->|→""", RegexOption.IGNORE_CASE))
        if (isConversion) {
            return listOf(SolutionStep("Expression", text), SolutionStep("Result", result.displayWithUnit))
        }
        val rootNode = when (parsed) {
            is Statement.Assign -> parsed.node
            is Statement.Expr -> parsed.node
            else -> return listOf(SolutionStep("Result", result.display))
        }
        val steps = ArrayList<SolutionStep>()
        steps.add(SolutionStep("Start", text))
        var tree = rootNode
        val evaluator = Evaluator(ctx)

        // Replace variables and ans by their values first.
        val substituted = ArrayList<String>()
        tree = substitute(tree, ctx, substituted)
        if (substituted.isNotEmpty()) steps.add(SolutionStep("Substitute " + substituted.joinToString(", "), Printer.print(tree)))

        var guard = 0
        while (guard++ < MAX_STEPS) {
            val target = findReady(tree) ?: break
            val value = evaluator.eval(target.node)
            if (target.node is UnaryNode) {
                // A sign in front of a value is folded in without a step of its own.
                tree = unwrap(replace(tree, target.node, ValNode(value)))
                if (tree is ValNode) break
                continue
            }
            val before = Printer.print(target.node)
            val description = describe(target.node, before, value)
            tree = replace(tree, target.node, ValNode(value))
            tree = unwrap(tree)
            steps.add(SolutionStep(description, Printer.print(tree)))
            if (tree is ValNode) break
        }
        if (tree !is ValNode && tree !is NumNode) {
            // Too long to show every step, or a form the stepper does not split: give the final value.
            steps.add(SolutionStep("Evaluate the rest", Printer.value(result.value)))
        }
        val last = steps.last()
        if (!last.description.startsWith("Result")) steps.add(SolutionStep("Result", result.displayWithUnit))
        return dedupe(steps, rootNode, result)
    }

    private fun dedupe(steps: List<SolutionStep>, root: Node, result: EvalResult): List<SolutionStep> {
        // A plain number needs no working.
        if (root is NumNode) return listOf(SolutionStep("Result", result.display))
        return steps
    }

    // ------------------------------------------------------------------ tree walking

    private class Target(val node: Node, val depth: Int, val rank: Int, val order: Int)

    private fun isLeaf(n: Node): Boolean = n is NumNode || n is ValNode || n is NameNode

    private fun inner(n: Node): Node = if (n is ParenNode) n.inner else n

    /** The next operation to carry out: deepest parentheses first, then by precedence, then left to right. */
    private fun findReady(root: Node): Target? {
        var best: Target? = null
        var counter = 0
        fun consider(node: Node, depth: Int, rank: Int) {
            val t = Target(node, depth, rank, counter++)
            val b = best
            if (b == null || t.depth > b.depth || (t.depth == b.depth && t.rank < b.rank)) best = t
        }
        fun walk(n: Node, depth: Int, skipPercent: Boolean) {
            when (n) {
                is ParenNode -> walk(n.inner, depth + 1, false)
                is UnaryNode -> {
                    walk(n.operand, depth, false)
                    if (isLeaf(inner(n.operand)) && n.operand !is ParenNode) consider(n, depth, 3)
                }
                is PercentNode -> {
                    walk(n.operand, depth, false)
                    if (!skipPercent && isLeaf(n.operand)) consider(n, depth, 1)
                }
                is FactorialNode -> {
                    walk(n.operand, depth, false)
                    if (isLeaf(n.operand)) consider(n, depth, 1)
                }
                is CallNode -> {
                    if (n.name in Functions.SPECIAL) { consider(n, depth, 1); return }
                    n.args.forEach { walk(it, depth + 1, false) }
                    if (n.args.all { isLeaf(it) }) consider(n, depth, 1)
                }
                is BinaryNode -> {
                    val percentRight = (n.op == BinOp.ADD || n.op == BinOp.SUB) && n.right is PercentNode
                    walk(n.left, depth, false)
                    walk(n.right, depth, percentRight)
                    val rightReady = if (percentRight) isLeaf((n.right as PercentNode).operand) else isLeaf(n.right)
                    if (isLeaf(n.left) && rightReady) {
                        val rank = when (n.op) {
                            BinOp.POW -> 2
                            BinOp.MUL, BinOp.DIV, BinOp.MOD -> 4
                            else -> 5
                        }
                        consider(n, depth, rank)
                    }
                }
                else -> Unit
            }
        }
        walk(root, 0, false)
        return best
    }

    private fun replace(root: Node, target: Node, with: Node): Node {
        if (root === target) return with
        return when (root) {
            is ParenNode -> ParenNode(replace(root.inner, target, with))
            is UnaryNode -> UnaryNode(root.negate, replace(root.operand, target, with))
            is PercentNode -> PercentNode(replace(root.operand, target, with))
            is FactorialNode -> FactorialNode(replace(root.operand, target, with))
            is CallNode -> CallNode(root.name, root.args.map { replace(it, target, with) }, root.pos)
            is BinaryNode -> BinaryNode(root.op, replace(root.left, target, with), replace(root.right, target, with), root.implicit)
            else -> root
        }
    }

    /** Removes parentheses that now only surround a single value. */
    private fun unwrap(n: Node): Node = when (n) {
        is ParenNode -> {
            val i = unwrap(n.inner)
            if (i is ValNode || i is NumNode) i else ParenNode(i)
        }
        is UnaryNode -> UnaryNode(n.negate, unwrap(n.operand))
        is PercentNode -> PercentNode(unwrap(n.operand))
        is FactorialNode -> FactorialNode(unwrap(n.operand))
        is CallNode -> CallNode(n.name, n.args.map { unwrap(it) }, n.pos)
        is BinaryNode -> BinaryNode(n.op, unwrap(n.left), unwrap(n.right), n.implicit)
        else -> n
    }

    private val CONSTANT_NAMES = setOf("pi", "e", "phi")

    private fun substitute(n: Node, ctx: EvalContext, notes: MutableList<String>): Node = when (n) {
        is NameNode -> {
            val lower = n.name.lowercase()
            val v = ctx.variables[n.name]
                ?: if (lower == "ans") ctx.ans else if (lower in CONSTANT_NAMES || Constants.valueOf(n.name) != null) null
                else ctx.variables.entries.firstOrNull { it.key.equals(n.name, true) }?.value
            if (v != null) {
                val label = "${n.name} = ${Printer.value(v)}"
                if (label !in notes) notes.add(label)
                ValNode(v)
            } else n
        }
        is ParenNode -> ParenNode(substitute(n.inner, ctx, notes))
        is UnaryNode -> UnaryNode(n.negate, substitute(n.operand, ctx, notes))
        is PercentNode -> PercentNode(substitute(n.operand, ctx, notes))
        is FactorialNode -> FactorialNode(substitute(n.operand, ctx, notes))
        // Variables inside sum/integral/... are bound by that form, so they are left alone.
        is CallNode -> if (n.name in Functions.SPECIAL) n else CallNode(n.name, n.args.map { substitute(it, ctx, notes) }, n.pos)
        is BinaryNode -> BinaryNode(n.op, substitute(n.left, ctx, notes), substitute(n.right, ctx, notes), n.implicit)
        else -> n
    }

    private fun describe(node: Node, shown: String, value: CalcValue): String {
        val result = Printer.value(value)
        return when (node) {
            is BinaryNode -> when (node.op) {
                BinOp.ADD -> if (node.right is PercentNode) "Add the percentage: $shown = $result" else "Add: $shown = $result"
                BinOp.SUB -> if (node.right is PercentNode) "Subtract the percentage: $shown = $result" else "Subtract: $shown = $result"
                BinOp.MUL -> "Multiply: $shown = $result"
                BinOp.DIV -> "Divide: $shown = $result"
                BinOp.POW -> "Power: $shown = $result"
                BinOp.MOD -> "Remainder: $shown = $result"
            }
            is UnaryNode -> "Negate: $shown = $result"
            is PercentNode -> "Percent: $shown = $result"
            is FactorialNode -> "Factorial: $shown = $result"
            is CallNode -> when (node.name) {
                "_deg", "_min", "_sec" -> "Convert the angle: $shown = $result"
                "sum", "prod", "integral", "deriv", "solve", "pol", "rec" -> "Evaluate $shown = $result"
                else -> "Evaluate ${node.name}: $shown = $result"
            }
            else -> "Evaluate: $shown = $result"
        }
    }

    // ------------------------------------------------------------------ printing

    internal object Printer {
        /** A number for a worked solution: a decimal, or a fraction when the decimal never ends (1/3). */
        fun value(v: CalcValue): String {
            val ex = v.exact
            if (ex != null && !ex.isInteger && !ex.isTerminating() && ex.bitSize <= 64) return ex.toString()
            return NumberFormatter.format(v.decimal)
        }

        private fun literal(r: Rational): String {
            if (r.isInteger || r.isTerminating()) return NumberFormatter.format(r.toBigDecimal(com.tapecalc.core.precision.Precision.INTERNAL))
            return r.toString()
        }

        fun print(n: Node, needsGroup: Boolean = false): String = when (n) {
            is NumNode -> literal(n.value)
            is ValNode -> {
                val s = value(n.value)
                if (needsGroup && (n.value.signum < 0 || s.contains('/'))) "($s)" else s
            }
            is NameNode -> n.name
            is ParenNode -> "(" + print(n.inner) + ")"
            is UnaryNode -> (if (n.negate) "-" else "+") + print(n.operand, true)
            is PercentNode -> print(n.operand, true) + "%"
            is FactorialNode -> print(n.operand, true) + "!"
            is CallNode -> when (n.name) {
                "_deg" -> print(n.args[0], true) + "°"
                "_min" -> print(n.args[0], true) + "′"
                "_sec" -> print(n.args[0], true) + "″"
                else -> n.name + "(" + n.args.joinToString(", ") { print(it) } + ")"
            }
            is BinaryNode -> {
                val op = when (n.op) {
                    BinOp.ADD -> "+"; BinOp.SUB -> "−"; BinOp.MUL -> "×"; BinOp.DIV -> "÷"; BinOp.POW -> "^"; BinOp.MOD -> "mod"
                }
                print(n.left, n.op == BinOp.POW || n.op == BinOp.MOD) + " $op " + print(n.right, true)
            }
        }
    }
}

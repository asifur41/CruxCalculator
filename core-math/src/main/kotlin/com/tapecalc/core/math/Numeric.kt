package com.tapecalc.core.math

import com.tapecalc.core.precision.BigMath
import com.tapecalc.core.precision.Precision
import java.math.BigDecimal
import java.math.MathContext

/**
 * Fast double-precision evaluation of an expression tree for one free variable.
 * Used by the numeric tools (integral, derivative, solve) and by graphing, where thousands of evaluations are
 * needed. Undefined points (log of a negative number, 1/0 ...) come back as NaN instead of throwing.
 */
internal class NumericEval(private val ctx: EvalContext) {

    private val ctxValues = HashMap<String, Double>()

    private fun variable(name: String): Double? =
        ctxValues.getOrPut(name) {
            (ctx.variables[name] ?: ctx.variables.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value)
                ?.decimal?.toDouble() ?: return null
        }

    fun eval(node: Node, vars: Map<String, Double>): Double = when (node) {
        is NumNode -> node.value.toBigDecimal(Precision.INTERNAL).toDouble()
        is ValNode -> node.value.decimal.toDouble()
        is ParenNode -> eval(node.inner, vars)
        is UnaryNode -> if (node.negate) -eval(node.operand, vars) else eval(node.operand, vars)
        is PercentNode -> eval(node.operand, vars) / 100.0
        is FactorialNode -> factorial(eval(node.operand, vars))
        is NameNode -> name(node, vars)
        is BinaryNode -> binary(node, vars)
        is CallNode -> call(node, vars)
    }

    private fun name(node: NameNode, vars: Map<String, Double>): Double {
        vars[node.name]?.let { return it }
        variable(node.name)?.let { return it }
        return when (node.name.lowercase()) {
            "pi" -> Math.PI
            "e" -> Math.E
            "phi" -> (1.0 + Math.sqrt(5.0)) / 2.0
            "inf", "infinity" -> Double.POSITIVE_INFINITY
            "ans" -> ctx.ans?.decimal?.toDouble() ?: throw CalcException(ErrorKind.UNKNOWN_NAME, "There is no previous answer yet", node.pos)
            else -> Constants.valueOf(node.name)?.decimal?.toDouble()
                ?: throw CalcException(ErrorKind.UNKNOWN_NAME, "Unknown name '${node.name}'", node.pos)
        }
    }

    private fun binary(node: BinaryNode, vars: Map<String, Double>): Double {
        val left = eval(node.left, vars)
        val rn = node.right
        if ((node.op == BinOp.ADD || node.op == BinOp.SUB) && rn is PercentNode) {
            val delta = left * (eval(rn.operand, vars) / 100.0)
            return if (node.op == BinOp.ADD) left + delta else left - delta
        }
        val right = eval(rn, vars)
        return when (node.op) {
            BinOp.ADD -> left + right
            BinOp.SUB -> left - right
            BinOp.MUL -> left * right
            BinOp.DIV -> if (right == 0.0) Double.NaN else left / right
            BinOp.POW -> pow(left, right)
            BinOp.MOD -> if (right == 0.0) Double.NaN else left - right * Math.floor(left / right)
        }
    }

    private fun pow(b: Double, e: Double): Double {
        if (b >= 0.0 || e == Math.rint(e)) return Math.pow(b, e)
        val inv = 1.0 / e   // a negative base has a real root only for odd root indexes: (-8)^(1/3)
        val n = Math.rint(inv)
        if (Math.abs(inv - n) < 1e-12 && n % 2.0 != 0.0) return -Math.pow(-b, e)
        return Double.NaN
    }

    private fun factorial(x: Double): Double {
        if (x < 0 || x != Math.rint(x) || x > 170) return Double.NaN
        var r = 1.0
        for (i in 2..x.toInt()) r *= i
        return r
    }

    private fun toRad(x: Double): Double = when (ctx.angleMode) {
        AngleMode.DEG -> Math.toRadians(x)
        AngleMode.GRAD -> x * Math.PI / 200.0
        AngleMode.RAD -> x
    }

    private fun fromRad(x: Double): Double = when (ctx.angleMode) {
        AngleMode.DEG -> Math.toDegrees(x)
        AngleMode.GRAD -> x * 200.0 / Math.PI
        AngleMode.RAD -> x
    }

    private fun call(node: CallNode, vars: Map<String, Double>): Double {
        val name = node.name
        if (name == "sum" || name == "prod") return series(node, vars)
        if (name in Functions.SPECIAL) {
            throw CalcException(ErrorKind.TOO_COMPLEX, "'$name' cannot be used inside this calculation")
        }
        Functions.checkArity(name, node.args.size)
        val a = node.args.map { eval(it, vars) }
        val x = a[0]
        return when (name) {
            "sin" -> sinExact(x)
            "cos" -> cosExact(x)
            "tan" -> tanExact(x)
            "asin" -> fromRad(Math.asin(x))
            "acos" -> fromRad(Math.acos(x))
            "atan" -> fromRad(Math.atan(x))
            "sinh" -> Math.sinh(x)
            "cosh" -> Math.cosh(x)
            "tanh" -> Math.tanh(x)
            "asinh" -> Math.log(x + Math.sqrt(x * x + 1.0))
            "acosh" -> if (x < 1.0) Double.NaN else Math.log(x + Math.sqrt(x * x - 1.0))
            "atanh" -> if (Math.abs(x) >= 1.0) Double.NaN else 0.5 * Math.log((1.0 + x) / (1.0 - x))
            "log" -> if (a.size == 1) (if (x > 0) Math.log10(x) else Double.NaN)
            else if (x > 0 && a[1] > 0 && a[1] != 1.0) Math.log(x) / Math.log(a[1]) else Double.NaN
            "ln" -> if (x > 0) Math.log(x) else Double.NaN
            "log2" -> if (x > 0) Math.log(x) / Math.log(2.0) else Double.NaN
            "exp" -> Math.exp(x)
            "sqrt" -> if (x >= 0) Math.sqrt(x) else Double.NaN
            "cbrt" -> Math.cbrt(x)
            "root" -> {
                val n = a[1]
                if (n == 0.0 || n != Math.rint(n)) Double.NaN else if (x < 0 && n % 2.0 != 0.0) -Math.pow(-x, 1.0 / n)
                else if (x < 0) Double.NaN else Math.pow(x, 1.0 / n)
            }
            "abs" -> Math.abs(x)
            "floor" -> Math.floor(x)
            "ceil" -> Math.ceil(x)
            "round" -> if (a.size == 2) {
                val f = Math.pow(10.0, a[1])
                Math.signum(x) * Math.floor(Math.abs(x) * f + 0.5) / f
            } else Math.signum(x) * Math.floor(Math.abs(x) + 0.5)
            "sign" -> Math.signum(x)
            "fact" -> factorial(x)
            "mod" -> if (a[1] == 0.0) Double.NaN else x - a[1] * Math.floor(x / a[1])
            "npr" -> if (x < 0 || a[1] < 0 || a[1] > x) Double.NaN else {
                var r = 1.0
                for (i in 0 until a[1].toInt()) r *= (x - i)
                r
            }
            "ncr" -> if (x < 0 || a[1] < 0 || a[1] > x) Double.NaN else {
                val k = minOf(a[1], x - a[1]).toInt()
                var r = 1.0
                for (i in 1..k) r = r * (x - k + i) / i
                Math.rint(r)
            }
            "min" -> a.min()
            "max" -> a.max()
            "pow" -> pow(x, a[1])
            "atan2" -> fromRad(Math.atan2(x, a[1]))
            "hypot" -> Math.hypot(x, a[1])
            "gcd" -> a.reduce { p, q -> gcd(p, q) }
            "lcm" -> a.reduce { p, q -> if (p == 0.0 || q == 0.0) 0.0 else Math.abs(p * q) / gcd(p, q) }
            "_deg" -> degreesToUnit(x)
            "_min" -> degreesToUnit(x / 60.0)
            "_sec" -> degreesToUnit(x / 3600.0)
            else -> Double.NaN
        }
    }

    private fun degreesToUnit(d: Double): Double = when (ctx.angleMode) {
        AngleMode.DEG -> d
        AngleMode.GRAD -> d * 10.0 / 9.0
        AngleMode.RAD -> Math.toRadians(d)
    }

    private fun gcd(p: Double, q: Double): Double {
        var x = Math.abs(p)
        var y = Math.abs(q)
        while (y != 0.0) { val t = x % y; x = y; y = t }
        return x
    }

    // Exact values at the angles people expect (sin 180° is 0, not 1.2E-16).
    private fun sinExact(x: Double): Double {
        if (ctx.angleMode == AngleMode.RAD) return Math.sin(x)
        val r = reduced(x)
        return when (r) { 0.0, 180.0 -> 0.0; 90.0 -> 1.0; 270.0 -> -1.0; else -> Math.sin(Math.toRadians(r)) }
    }

    private fun cosExact(x: Double): Double {
        if (ctx.angleMode == AngleMode.RAD) return Math.cos(x)
        val r = reduced(x)
        return when (r) { 90.0, 270.0 -> 0.0; 0.0 -> 1.0; 180.0 -> -1.0; else -> Math.cos(Math.toRadians(r)) }
    }

    private fun tanExact(x: Double): Double {
        if (ctx.angleMode == AngleMode.RAD) return Math.tan(x)
        val r = reduced(x)
        return when (r) { 0.0, 180.0 -> 0.0; 90.0, 270.0 -> Double.NaN; else -> Math.tan(Math.toRadians(r)) }
    }

    /** The angle in degrees reduced to 0 until 360 (gradians are converted first). */
    private fun reduced(x: Double): Double {
        val deg = if (ctx.angleMode == AngleMode.GRAD) x * 0.9 else x
        val r = deg % 360.0
        return if (r < 0) r + 360.0 else r
    }

    private fun series(node: CallNode, vars: Map<String, Double>): Double {
        Functions.checkArity(node.name, node.args.size)
        val v = node.args[1] as? NameNode ?: throw CalcException(ErrorKind.SYNTAX, "The second value of ${node.name} must be a variable name")
        val from = eval(node.args[2], vars)
        val to = eval(node.args[3], vars)
        if (to - from > Numeric.MAX_TERMS) throw CalcException(ErrorKind.TOO_COMPLEX, "Too many terms (maximum ${Numeric.MAX_TERMS})")
        var acc = if (node.name == "sum") 0.0 else 1.0
        var k = from
        while (k <= to) {
            val t = eval(node.args[0], vars + (v.name to k))
            acc = if (node.name == "sum") acc + t else acc * t
            k += 1.0
        }
        return acc
    }
}

/** Numeric integral, derivative and equation solver on top of [NumericEval]. */
internal object Numeric {
    const val MAX_TERMS = 10_000

    private fun fail(kind: ErrorKind, message: String): Nothing = throw CalcException(kind, message)

    // ---------------------------------------------------------------- integral

    // Gauss-Kronrod 7-15 nodes and weights.
    private val XGK = doubleArrayOf(
        0.991455371120812639206854697526329, 0.949107912342758524526189684047851, 0.864864423359769072789712788640926,
        0.741531185599394439863864773280788, 0.586087235467691130294144838258730, 0.405845151377397166906606412076961,
        0.207784955007898467600689403773245, 0.0
    )
    private val WGK = doubleArrayOf(
        0.022935322010529224963732008058970, 0.063092092629978553290700663189204, 0.104790010322250183839876322541518,
        0.140653259715525918745189590510238, 0.169004726639267902826583426598550, 0.190350578064785409913256402421014,
        0.204432940075298892414161999234649, 0.209482141084727828012999174891714
    )
    private val WG = doubleArrayOf(
        0.129484966168869693270611432679082, 0.279705391489276667901467771423780,
        0.381830050505118944950369775488975, 0.417959183673469387755102040816327
    )

    private class Panel(val a: Double, val b: Double, val value: Double, val error: Double)

    private fun gk15(f: (Double) -> Double, a: Double, b: Double): Panel {
        val c = 0.5 * (a + b)
        val h = 0.5 * (b - a)
        val fc = f(c)
        var resK = fc * WGK[7]
        var resG = fc * WG[3]
        for (j in 0 until 7) {
            val dx = h * XGK[j]
            val f1 = f(c - dx)
            val f2 = f(c + dx)
            resK += WGK[j] * (f1 + f2)
            if (j % 2 == 1) resG += WG[j / 2] * (f1 + f2)
        }
        return Panel(a, b, resK * h, Math.abs((resK - resG) * h))
    }

    /** ∫ f from a to b with adaptive Gauss-Kronrod; a and b may be ±infinity. */
    fun integrate(f: (Double) -> Double, a: Double, b: Double): Double {
        if (a == b) return 0.0
        if (a > b) return -integrate(f, b, a)
        fun checked(x: Double): Double {
            val y = f(x)
            if (y.isNaN() || y.isInfinite()) fail(ErrorKind.UNDEFINED, "The function is not defined everywhere on the interval")
            return y
        }
        return when {
            a.isInfinite() && b.isInfinite() -> adaptive({ t -> val d = 1.0 - t * t; checked(t / d) * (1.0 + t * t) / (d * d) }, -1.0, 1.0)
            b.isInfinite() -> adaptive({ t -> val d = 1.0 - t; checked(a + t / d) / (d * d) }, 0.0, 1.0)
            a.isInfinite() -> adaptive({ t -> val d = 1.0 - t; checked(b - t / d) / (d * d) }, 0.0, 1.0)
            else -> adaptive(::checked, a, b)
        }
    }

    private fun adaptive(f: (Double) -> Double, a: Double, b: Double): Double {
        val queue = java.util.PriorityQueue<Panel>(compareByDescending { it.error })
        // Start from several panels so a narrow peak in the middle is not missed.
        val start = 8
        for (i in 0 until start) queue.add(gk15(f, a + (b - a) * i / start, a + (b - a) * (i + 1) / start))
        var iterations = 0
        while (iterations < 2000) {
            var total = 0.0
            var err = 0.0
            for (p in queue) { total += p.value; err += p.error }
            if (err <= 1e-13 * Math.abs(total) || err < 1e-300) return total
            val worst = queue.poll()
            val mid = 0.5 * (worst.a + worst.b)
            if (mid <= worst.a || mid >= worst.b) return total
            queue.add(gk15(f, worst.a, mid))
            queue.add(gk15(f, mid, worst.b))
            iterations++
        }
        var total = 0.0
        var err = 0.0
        for (p in queue) { total += p.value; err += p.error }
        if (err > 1e-6 * Math.max(1.0, Math.abs(total))) fail(ErrorKind.DOMAIN, "The integral did not converge (it may be infinite)")
        return total
    }

    // ---------------------------------------------------------------- derivative

    /** Ridders' method: derivative of f at x, accurate to roughly 10-12 digits for smooth functions. */
    fun derivative(f: (Double) -> Double, x: Double): Double {
        var h = 0.1 * Math.max(1.0, Math.abs(x))
        val con = 1.4
        val con2 = con * con
        val a = Array(10) { DoubleArray(10) }
        fun at(v: Double): Double = f(v)
        a[0][0] = (at(x + h) - at(x - h)) / (2.0 * h)
        var best = a[0][0]
        var err = Double.MAX_VALUE
        if (best.isNaN()) fail(ErrorKind.UNDEFINED, "The function is not defined near this point")
        for (i in 1 until 10) {
            h /= con
            a[0][i] = (at(x + h) - at(x - h)) / (2.0 * h)
            var fac = con2
            for (j in 1..i) {
                a[j][i] = (a[j - 1][i] * fac - a[j - 1][i - 1]) / (fac - 1.0)
                fac *= con2
                val e = Math.max(Math.abs(a[j][i] - a[j - 1][i]), Math.abs(a[j][i] - a[j - 1][i - 1]))
                if (e <= err) { err = e; best = a[j][i] }
            }
            if (Math.abs(a[i][i] - a[i - 1][i - 1]) >= 2.0 * err) break
        }
        if (best.isNaN() || best.isInfinite()) fail(ErrorKind.UNDEFINED, "The derivative does not exist at this point")
        return best
    }

    // ---------------------------------------------------------------- solve

    /** A root of f near [guess] (Newton, then a bracketing search), or an error when none is found. */
    fun findRoot(f: (Double) -> Double, guess: Double): Double {
        newton(f, guess)?.let { return it }
        // Bracketing search that widens around the guess.
        var radius = 0.5
        while (radius <= 1e8) {
            val steps = 400
            var prevX = guess - radius
            var prevY = f(prevX)
            for (i in 1..steps) {
                val x = guess - radius + 2.0 * radius * i / steps
                val y = f(x)
                if (!prevY.isNaN() && !y.isNaN() && prevY * y <= 0.0 && !(prevY == 0.0 && y == 0.0)) {
                    bisect(f, prevX, x)?.let { return it }
                }
                prevX = x
                prevY = y
            }
            radius *= 4.0
        }
        fail(ErrorKind.DOMAIN, "No solution found near the starting value. Try a different starting value.")
    }

    private fun newton(f: (Double) -> Double, start: Double): Double? {
        var x = start
        repeat(100) {
            val y = f(x)
            if (y.isNaN()) return null
            if (y == 0.0) return x
            val h = 1e-7 * Math.max(1.0, Math.abs(x))
            val d = (f(x + h) - f(x - h)) / (2.0 * h)
            if (d == 0.0 || d.isNaN() || d.isInfinite()) return null
            val next = x - y / d
            if (next.isNaN() || next.isInfinite()) return null
            if (Math.abs(next - x) <= 1e-15 * Math.max(1.0, Math.abs(next))) return verify(f, next)
            x = next
        }
        return null
    }

    private fun verify(f: (Double) -> Double, x: Double): Double? {
        val y = f(x)
        if (y.isNaN()) return null
        val h = 1e-7 * Math.max(1.0, Math.abs(x))
        val scale = Math.max(Math.abs(f(x + h) - f(x - h)) / (2.0 * h), 1.0)
        return if (Math.abs(y) <= 1e-8 * scale) x else null
    }

    private fun bisect(f: (Double) -> Double, lo0: Double, hi0: Double): Double? {
        var lo = lo0
        var hi = hi0
        var flo = f(lo)
        repeat(200) {
            val mid = 0.5 * (lo + hi)
            val fm = f(mid)
            if (fm == 0.0) return mid
            if ((fm < 0) == (flo < 0)) { lo = mid; flo = fm } else hi = mid
            if (Math.abs(hi - lo) <= 1e-16 * Math.max(1.0, Math.abs(mid))) return 0.5 * (lo + hi)
        }
        val mid = 0.5 * (lo + hi)
        val y = f(mid)
        // A sign change at a pole (1/x) is not a root.
        return if (!y.isNaN() && Math.abs(y) < 1e-6) mid else null
    }

    /** Refines a double-precision root to ~30 digits with Newton steps at full precision; null if that fails. */
    fun polish(node: Node, variable: String, root: Double, ctx: EvalContext): CalcValue? {
        return try {
            fun value(x: BigDecimal): BigDecimal {
                val vars = HashMap(ctx.variables)
                vars[variable] = CalcValue.ofDecimal(x)
                return Evaluator(EvalContext(ctx.angleMode, vars, ctx.ans)).eval(node).decimal
            }
            val mc = MathContext(50)
            var x = BigDecimal(root)
            val h = BigDecimal("1E-12")
            repeat(4) {
                val y = value(x)
                if (y.signum() == 0) return@repeat
                val d = value(x.add(h, mc)).subtract(value(x.subtract(h, mc)), mc).divide(h.multiply(BigDecimal(2)), mc)
                if (d.signum() == 0) return null
                x = x.subtract(y.divide(d, mc), mc)
            }
            if (x.subtract(BigDecimal(root)).abs() > BigDecimal("1E-6").multiply(BigDecimal.ONE.max(BigDecimal(root).abs()))) return null
            snap(x)
        } catch (e: ArithmeticException) {
            null
        } catch (e: CalcException) {
            null
        }
    }

    /** The exact fraction when [x] is within 1e-24 of a small one (so x² = 4 gives exactly 2), else a decimal. */
    fun snap(x: BigDecimal): CalcValue {
        val r = ExactForms.rationalize(x, 100_000, 1_000_000_000_000L)
        return if (r != null) CalcValue.ofRational(r) else CalcValue.ofDecimal(x)
    }

    /** A double answer from a numeric method, cleaned to [digits] significant digits and snapped to a simple fraction. */
    fun fromDouble(d: Double, digits: Int): CalcValue {
        if (d.isNaN() || d.isInfinite()) fail(ErrorKind.UNDEFINED, "The result is not a finite number")
        if (d == 0.0) return CalcValue.ZERO
        val clean = BigDecimal(d).round(MathContext(digits))
        val r = ExactForms.rationalize(clean, 10_000, 1_000_000_000_000L, BigDecimal.ONE.movePointLeft(digits - 2))
        return if (r != null) CalcValue.ofRational(r) else CalcValue.ofDecimal(clean)
    }
}

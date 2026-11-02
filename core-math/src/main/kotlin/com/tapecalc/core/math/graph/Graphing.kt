package com.tapecalc.core.math.graph

import com.tapecalc.core.math.CalcError
import com.tapecalc.core.math.CalcException
import com.tapecalc.core.math.ErrorKind
import com.tapecalc.core.math.EvalContext
import com.tapecalc.core.math.Node
import com.tapecalc.core.math.NumericEval
import com.tapecalc.core.math.Parser
import com.tapecalc.core.math.Statement
import com.tapecalc.core.math.Tokenizer
import kotlin.math.abs

/** A function of one variable that can be evaluated thousands of times quickly. NaN means "undefined here". */
class GraphFunction internal constructor(
    val expression: String,
    val variable: String,
    private val node: Node,
    private val eval: NumericEval
) {
    private val vars = HashMap<String, Double>()

    fun at(x: Double): Double = try {
        vars[variable] = x
        val v = eval.eval(node, vars)
        if (v.isInfinite()) Double.NaN else v
    } catch (e: CalcException) {
        Double.NaN
    } catch (e: ArithmeticException) {
        Double.NaN
    }
}

sealed class GraphCompile {
    class Ok(val function: GraphFunction) : GraphCompile()
    class Err(val error: CalcError) : GraphCompile()
}

/** One continuous piece of a curve, as parallel arrays of x and y (in graph units). */
class GraphSegment(val xs: DoubleArray, val ys: DoubleArray) {
    val size: Int get() = xs.size
}

/**
 * Pure maths for the graph screen (no Android). The screen asks for [segments] to draw, and for [roots],
 * [extremes] and [intersections] to mark special points.
 */
object Graphing {

    /** Accepts `x^2 - 3`, `y = sin(x)`, `f = 2x`. The variable is `x` unless another single free name is used. */
    fun compile(text: String, ctx: EvalContext = EvalContext(), variable: String = "x"): GraphCompile = try {
        if (text.isBlank()) throw CalcException(ErrorKind.EMPTY, "Type a function of x")
        val (statement, _) = Parser.parse(Tokenizer.tokenize(text.trim()))
        val node = when (statement) {
            is Statement.Expr -> statement.node
            is Statement.Assign -> statement.node
        }
        val f = GraphFunction(text.trim(), variable, node, NumericEval(ctx))
        // A function that is undefined everywhere we try is almost surely a typo (unknown name).
        f.at(0.37); f.at(1.9)
        probeNames(node, ctx, variable)
        GraphCompile.Ok(f)
    } catch (e: CalcException) {
        GraphCompile.Err(e.error)
    }

    /** Evaluating once with a throwing path surfaces unknown names as an error instead of a blank graph. */
    private fun probeNames(node: Node, ctx: EvalContext, variable: String) {
        NumericEval(ctx).eval(node, mapOf(variable to 1.2345))
    }

    /**
     * Breaks the curve into drawable pieces. A new piece starts at undefined points and across vertical
     * asymptotes (where the value jumps through the visible window between neighbouring samples).
     */
    fun segments(f: GraphFunction, xMin: Double, xMax: Double, yMin: Double, yMax: Double, samples: Int = 800): List<GraphSegment> {
        require(xMax > xMin) { "xMax must be greater than xMin" }
        val n = samples.coerceIn(20, 5000)
        val step = (xMax - xMin) / n
        val height = yMax - yMin
        val out = ArrayList<GraphSegment>()
        var xs = ArrayList<Double>()
        var ys = ArrayList<Double>()
        fun flush() {
            if (xs.size >= 2) out.add(GraphSegment(xs.toDoubleArray(), ys.toDoubleArray()))
            xs = ArrayList(); ys = ArrayList()
        }
        var prevX = Double.NaN
        var prevY = Double.NaN
        for (i in 0..n) {
            val x = xMin + step * i
            val y = f.at(x)
            if (y.isNaN()) { flush(); prevY = Double.NaN; continue }
            if (!prevY.isNaN()) {
                val jump = abs(y - prevY)
                // Sign change with a jump larger than the window: an asymptote, not a steep line.
                if (jump > 4 * height && (y > 0) != (prevY > 0) && abs(y) > height && abs(prevY) > height) flush()
            }
            xs.add(x); ys.add(y)
            prevX = x; prevY = y
        }
        flush()
        return out
    }

    /** Zeros of f in [a, b], found by sign changes then bisection/secant refinement. */
    fun roots(f: GraphFunction, a: Double, b: Double, samples: Int = 2000, max: Int = 50): List<Double> {
        val out = ArrayList<Double>()
        val step = (b - a) / samples
        var x0 = a
        var y0 = f.at(a)
        for (i in 1..samples) {
            val x1 = a + step * i
            val y1 = f.at(x1)
            if (!y0.isNaN() && !y1.isNaN()) {
                if (y0 == 0.0) addDistinct(out, x0)
                else if (y0 * y1 < 0) {
                    val r = bisect(f, x0, x1)
                    // Reject asymptotes: the value at the "root" must be tiny compared with the neighbours.
                    if (r != null) addDistinct(out, r)
                }
            }
            x0 = x1; y0 = y1
            if (out.size >= max) break
        }
        if (!y0.isNaN() && y0 == 0.0) addDistinct(out, x0)
        return out
    }

    private fun addDistinct(list: MutableList<Double>, x: Double) {
        if (list.none { abs(it - x) < 1e-9 * maxOf(1.0, abs(x)) }) list.add(x)
    }

    private fun bisect(f: GraphFunction, lo0: Double, hi0: Double): Double? {
        var lo = lo0
        var hi = hi0
        var flo = f.at(lo)
        val scale = maxOf(abs(f.at(lo)), abs(f.at(hi)))
        repeat(200) {
            val mid = (lo + hi) / 2
            val fm = f.at(mid)
            if (fm.isNaN()) return null
            if (fm == 0.0) return mid
            if (flo * fm < 0) hi = mid else { lo = mid; flo = fm }
            if (hi - lo < 1e-14 * maxOf(1.0, abs(mid))) return@repeat
        }
        val r = (lo + hi) / 2
        val fr = f.at(r)
        return if (fr.isNaN() || abs(fr) > 1e-6 * maxOf(1.0, scale)) null else r
    }

    class Extreme(val x: Double, val y: Double, val isMax: Boolean)

    /** Local maxima and minima in [a, b] (interior points only), located with golden-section refinement. */
    fun extremes(f: GraphFunction, a: Double, b: Double, samples: Int = 1500, max: Int = 30): List<Extreme> {
        val out = ArrayList<Extreme>()
        val step = (b - a) / samples
        val xs = DoubleArray(samples + 1) { a + step * it }
        val ys = DoubleArray(samples + 1) { f.at(xs[it]) }
        for (i in 1 until samples) {
            val p = ys[i - 1]; val c = ys[i]; val q = ys[i + 1]
            if (p.isNaN() || c.isNaN() || q.isNaN()) continue
            val isMax = c > p && c >= q
            val isMin = c < p && c <= q
            if (!isMax && !isMin) continue
            val x = golden(f, xs[i - 1], xs[i + 1], isMax)
            val y = f.at(x)
            if (y.isNaN()) continue
            if (out.none { abs(it.x - x) < 1e-7 * maxOf(1.0, abs(x)) }) out.add(Extreme(x, y, isMax))
            if (out.size >= max) break
        }
        return out
    }

    private fun golden(f: GraphFunction, a0: Double, b0: Double, maximise: Boolean): Double {
        val g = (Math.sqrt(5.0) - 1) / 2
        var a = a0
        var b = b0
        fun v(x: Double): Double { val y = f.at(x); return if (maximise) -y else y }
        var c = b - g * (b - a)
        var d = a + g * (b - a)
        var fc = v(c)
        var fd = v(d)
        repeat(120) {
            if (fc < fd) { b = d; d = c; fd = fc; c = b - g * (b - a); fc = v(c) }
            else { a = c; c = d; fc = fd; d = a + g * (b - a); fd = v(d) }
        }
        return (a + b) / 2
    }

    /** Points where two graphs cross, as the roots of f − g. */
    fun intersections(f: GraphFunction, g: GraphFunction, a: Double, b: Double): List<Pair<Double, Double>> {
        val diff = object { fun at(x: Double): Double { val u = f.at(x); val w = g.at(x); return if (u.isNaN() || w.isNaN()) Double.NaN else u - w } }
        val out = ArrayList<Pair<Double, Double>>()
        val samples = 2000
        val step = (b - a) / samples
        var x0 = a
        var y0 = diff.at(a)
        for (i in 1..samples) {
            val x1 = a + step * i
            val y1 = diff.at(x1)
            if (!y0.isNaN() && !y1.isNaN() && (y0 == 0.0 || y0 * y1 < 0)) {
                var lo = x0; var hi = x1; var flo = y0
                repeat(100) {
                    val mid = (lo + hi) / 2
                    val fm = diff.at(mid)
                    if (fm.isNaN()) return@repeat
                    if (flo * fm <= 0) hi = mid else { lo = mid; flo = fm }
                }
                val x = (lo + hi) / 2
                val y = f.at(x)
                if (!y.isNaN() && abs(diff.at(x)) < 1e-6 * maxOf(1.0, abs(y)) && out.none { abs(it.first - x) < 1e-9 }) out.add(x to y)
            }
            x0 = x1; y0 = y1
            if (out.size >= 30) break
        }
        return out
    }

    /** Slope of the curve at x (central difference), for the trace read-out. */
    fun slopeAt(f: GraphFunction, x: Double): Double {
        val h = 1e-6 * maxOf(1.0, abs(x))
        return (f.at(x + h) - f.at(x - h)) / (2 * h)
    }

    /** A friendly vertical range that fits the curve between a and b, ignoring runaway values near asymptotes. */
    fun autoYRange(f: GraphFunction, a: Double, b: Double, samples: Int = 400): Pair<Double, Double> {
        val ys = ArrayList<Double>()
        for (i in 0..samples) {
            val y = f.at(a + (b - a) * i / samples)
            if (!y.isNaN() && abs(y) < 1e12) ys.add(y)
        }
        if (ys.isEmpty()) return -10.0 to 10.0
        ys.sort()
        // Trim the extreme 3% each side so a single asymptote does not flatten the picture.
        val lo = ys[(ys.size * 0.03).toInt()]
        val hi = ys[(ys.size * 0.97).toInt().coerceAtMost(ys.size - 1)]
        if (hi - lo < 1e-9) return lo - 1 to hi + 1
        val pad = (hi - lo) * 0.12
        return lo - pad to hi + pad
    }

    /** "Nice" tick spacing (1, 2, 5 × 10^k) giving roughly [target] marks across [range]. */
    fun tickStep(range: Double, target: Int = 6): Double {
        if (range <= 0 || range.isNaN()) return 1.0
        val raw = range / target
        val mag = Math.pow(10.0, Math.floor(Math.log10(raw)))
        val norm = raw / mag
        val nice = when {
            norm < 1.5 -> 1.0
            norm < 3.5 -> 2.0
            norm < 7.5 -> 5.0
            else -> 10.0
        }
        return nice * mag
    }
}

package com.tapecalc.core.math.tools

import com.tapecalc.core.math.BaseNCalc
import com.tapecalc.core.math.BaseNOutcome
import com.tapecalc.core.math.CalcException
import com.tapecalc.core.math.CalcValue
import com.tapecalc.core.math.Calculator
import com.tapecalc.core.math.EvalContext
import com.tapecalc.core.math.EvalOutcome
import com.tapecalc.core.math.LinearSolution
import com.tapecalc.core.math.LinearSystem
import com.tapecalc.core.math.Polynomial
import com.tapecalc.core.math.StepSolver
import com.tapecalc.core.math.StepsOutcome
import com.tapecalc.core.math.algebra.ComplexCalc
import com.tapecalc.core.math.algebra.ComplexOutcome
import com.tapecalc.core.math.algebra.Matrix
import com.tapecalc.core.math.algebra.MatrixExpr
import com.tapecalc.core.math.algebra.MatrixOps
import com.tapecalc.core.math.algebra.MatrixOutcome
import com.tapecalc.core.math.algebra.MatrixValue
import com.tapecalc.core.math.algebra.VectorOps
import com.tapecalc.core.math.stats.Distributions
import com.tapecalc.core.math.stats.RegressionType
import com.tapecalc.core.math.stats.Stats
import com.tapecalc.core.math.symbolic.SymOutcome
import com.tapecalc.core.math.symbolic.Symbolic

/** Every tool on the Tools screen. The graph, constants table and QR screens are drawn separately. */
object Tools {

    private fun line(label: String, value: String) = ResultLine(label, value)
    private fun line(label: String, value: CalcValue) = ResultLine(label, value.toString())
    private fun ok(vararg l: ResultLine) = ToolResult.Ok(l.toList())
    private fun err(message: String) = ToolResult.Err(message)

    /** Runs [block] and turns any calculator error into a friendly [ToolResult.Err]. */
    private inline fun guarded(block: () -> ToolResult): ToolResult = try {
        block()
    } catch (e: CalcException) {
        err(e.error.message)
    } catch (e: ArithmeticException) {
        err(e.message ?: "Math error")
    } catch (e: IllegalArgumentException) {
        err(e.message ?: "Check the input")
    } catch (e: IndexOutOfBoundsException) {
        err("Check the input: a list or matrix is the wrong size")
    }

    private fun number(text: String, ctx: EvalContext, what: String): CalcValue {
        if (text.isBlank()) throw CalcException(com.tapecalc.core.math.ErrorKind.EMPTY, "Enter $what")
        return when (val o = Calculator.evaluate(text, ctx)) {
            is EvalOutcome.Success -> o.result.value
            is EvalOutcome.Failure -> throw CalcException(com.tapecalc.core.math.ErrorKind.SYNTAX, "$what: ${o.error.message}")
        }
    }

    private fun intOf(text: String, ctx: EvalContext, what: String): Int {
        val v = number(text, ctx, what)
        val exact = v.exact
        if (exact == null || !exact.isInteger || exact.num.bitLength() > 31 || exact.signum < 0) {
            throw CalcException(com.tapecalc.core.math.ErrorKind.INVALID_ARGUMENT, "$what must be a whole number, 0 or more")
        }
        return exact.num.toInt()
    }

    private fun splitCells(text: String): List<String> =
        text.split(Regex("[,;\\s]+")).map { it.trim() }.filter { it.isNotEmpty() }

    private fun parseMatrix(text: String, ctx: EvalContext): Matrix? {
        val rows = text.split('\n', ';').map { r -> r.split(Regex("[,\\s]+")).map { it.trim() }.filter { it.isNotEmpty() } }.filter { it.isNotEmpty() }
        if (rows.isEmpty()) return null
        return Matrix.parse(rows, ctx)
    }

    private fun matrixLines(label: String, m: Matrix): List<ResultLine> {
        val rows = m.format()
        return rows.mapIndexed { i, r -> ResultLine(if (i == 0) "$label (${m.size})" else "", "[ " + r.joinToString("   ") + " ]") }
    }

    // ------------------------------------------------------------------ the tools

    private val polynomial = ToolSpec(
        id = "poly", title = "Polynomial roots",
        summary = "Solve a·x⁴ + b·x³ + c·x² + d·x + e = 0 (up to degree 4)",
        fields = listOf(ToolField("coef", "Coefficients, highest power first", "1, -3, 2", default = "1, -3, 2"))
    ) { input ->
        guarded {
            val texts = splitCells(input.text("coef"))
            if (texts.size < 2) return@guarded err("Enter at least two coefficients, for example 1, -3, 2")
            val roots = Polynomial.solve(Polynomial.parseCoefficients(texts, input.ctx))
            if (roots.isEmpty()) err("There is no solution")
            else ToolResult.Ok(roots.mapIndexed { i, r -> line("x${subscript(i + 1)}", r.format()) })
        }
    }

    private fun subscript(n: Int): String = n.toString().map { "₀₁₂₃₄₅₆₇₈₉"[it - '0'] }.joinToString("")

    private val simultaneous = ToolSpec(
        id = "linear", title = "Simultaneous equations",
        summary = "Up to 4 unknowns. One equation per line: the coefficients, then the constant",
        fields = listOf(
            ToolField("rows", "Each line: a, b, c   means a·x + b·y = c", "2, 1, 5\n1, -1, 1", multiline = true, default = "2, 1, 5\n1, -1, 1")
        )
    ) { input ->
        guarded {
            val rows = input.text("rows").split('\n', ';').map { splitCells(it) }.filter { it.isNotEmpty() }
            if (rows.isEmpty()) return@guarded err("Enter at least one equation")
            val (a, b) = LinearSystem.parse(rows, input.ctx)
            when (val s = LinearSystem.solve(a, b)) {
                is LinearSolution.Unique -> ToolResult.Ok(s.values.mapIndexed { i, v -> line("xyzw"[i].toString(), v) })
                is LinearSolution.Infinite -> err("Infinitely many solutions (${s.rank} independent equations for ${s.unknowns} unknowns)")
                LinearSolution.None -> err("No solution: the equations contradict each other")
            }
        }
    }

    private val solveFx = ToolSpec(
        id = "solve", title = "Solve f(x) = 0",
        summary = "Finds a root near your starting guess",
        fields = listOf(
            ToolField("f", "f(x)", "x^3 - 2x - 5", default = "x^3 - 2x - 5"),
            ToolField("guess", "Starting guess", "2", default = "1")
        )
    ) { input ->
        guarded {
            val f = input.text("f")
            if (f.isEmpty()) return@guarded err("Enter f(x)")
            val guess = input.text("guess").ifEmpty { "1" }
            when (val o = Calculator.evaluate("solve($f, x, $guess)", input.ctx)) {
                is EvalOutcome.Success -> ToolResult.Ok(listOf(line("x", o.result.value)))
                is EvalOutcome.Failure -> err(o.error.message)
            }
        }
    }

    private val calculus = ToolSpec(
        id = "calculus", title = "Calculus (numbers)",
        summary = "Derivative at a point, definite integral, sums and products",
        fields = listOf(
            ToolField("f", "f(x)", "sin(x)", default = "x^2"),
            ToolField("a", "Point, or lower limit", "1", default = "1"),
            ToolField("b", "Upper limit (integral, sum, product)", "3", default = "3")
        ),
        options = listOf(ToolOption("kind", "Operation", listOf("Derivative", "Integral", "Sum", "Product")))
    ) { input ->
        guarded {
            val f = input.text("f")
            if (f.isEmpty()) return@guarded err("Enter f(x)")
            val a = input.text("a").ifEmpty { "0" }
            val b = input.text("b")
            val (label, expr) = when (input.choice("kind")) {
                0 -> "f′($a)" to "deriv($f, x, $a)"
                1 -> "∫ f dx from $a to $b" to "integral($f, x, $a, $b)"
                2 -> "Σ f(x) for x = $a … $b" to "sum($f, x, $a, $b)"
                else -> "Π f(x) for x = $a … $b" to "prod($f, x, $a, $b)"
            }
            if (input.choice("kind") != 0 && b.isEmpty()) return@guarded err("Enter the upper limit")
            when (val o = Calculator.evaluate(expr, input.ctx)) {
                is EvalOutcome.Success -> ToolResult.Ok(listOf(line(label, o.result.value)))
                is EvalOutcome.Failure -> err(o.error.message)
            }
        }
    }

    private val symbolic = ToolSpec(
        id = "algebra", title = "Algebra (symbols)",
        summary = "Simplify, expand, factor, differentiate and integrate with letters",
        fields = listOf(
            ToolField("expr", "Expression", "(x+1)^2", default = "(x+1)^2"),
            ToolField("var", "Variable", "x", default = "x")
        ),
        options = listOf(ToolOption("op", "Operation", listOf("Simplify", "Expand", "Factor", "d/dx", "∫ dx")))
    ) { input ->
        guarded {
            val e = input.text("expr")
            val v = input.text("var").ifEmpty { "x" }
            val r = when (input.choice("op")) {
                0 -> Symbolic.simplify(e)
                1 -> Symbolic.expand(e)
                2 -> Symbolic.factor(e, if (input.text("var").isEmpty()) null else v)
                3 -> Symbolic.derivative(e, v)
                else -> Symbolic.integral(e, v)
            }
            when (r) {
                is SymOutcome.Ok -> ToolResult.Ok(listOf(line("", r.text)), r.text)
                is SymOutcome.Err -> err(r.error.message)
            }
        }
    }

    private val oneVar = ToolSpec(
        id = "stats1", title = "Statistics: one list",
        summary = "Mean, deviation, median, quartiles, mode…",
        fields = listOf(
            ToolField("x", "Data", "3, 5, 5, 8, 10", multiline = true, default = "3, 5, 5, 8, 10"),
            ToolField("f", "Frequencies (optional)", "1, 2, 1, 1, 3")
        )
    ) { input ->
        guarded {
            val xs = Stats.parseList(input.text("x"), input.ctx)
            val fs = input.text("f").takeIf { it.isNotEmpty() }?.let { Stats.parseList(it, input.ctx) }
            val s = Stats.oneVar(xs, fs)
            val lines = ArrayList<ResultLine>()
            lines += line("n", s.n); lines += line("Σx", s.sum); lines += line("Σx²", s.sumSq)
            lines += line("Mean x̄", s.mean); lines += line("σx (population)", s.sdPop)
            lines += line("sx (sample)", s.sdSample?.toString() ?: "needs 2 or more values")
            lines += line("Min", s.min); lines += line("Q1", s.q1); lines += line("Median", s.median)
            lines += line("Q3", s.q3); lines += line("Max", s.max); lines += line("Range", s.range)
            lines += line("Mode", if (s.modes.isEmpty()) "none" else s.modes.joinToString(", "))
            ToolResult.Ok(lines)
        }
    }

    private val regression = ToolSpec(
        id = "stats2", title = "Statistics: pairs & regression",
        summary = "Fit a line or curve, then predict y from x (or x from y)",
        fields = listOf(
            ToolField("x", "x values", "1, 2, 3, 4, 5", default = "1, 2, 3, 4, 5"),
            ToolField("y", "y values", "2.2, 4.1, 6.2, 7.9, 10.1", default = "2.2, 4.1, 6.2, 7.9, 10.1"),
            ToolField("f", "Frequencies (optional)"),
            ToolField("px", "Predict y for x = (optional)"),
            ToolField("py", "Estimate x for y = (optional)")
        ),
        options = listOf(ToolOption("type", "Curve", listOf("Linear", "Quadratic", "Log", "Exp (e)", "Exp (b)", "Power", "Inverse")))
    ) { input ->
        guarded {
            val xs = Stats.parseList(input.text("x"), input.ctx)
            val ys = Stats.parseList(input.text("y"), input.ctx)
            val fs = input.text("f").takeIf { it.isNotEmpty() }?.let { Stats.parseList(it, input.ctx) }
            val type = RegressionType.entries[input.choice("type").coerceIn(0, RegressionType.entries.lastIndex)]
            val two = Stats.twoVar(xs, ys, fs)
            val reg = Stats.regression(type, xs, ys, fs)
            val lines = ArrayList<ResultLine>()
            lines += line("Curve", type.label)
            lines += line("Equation", reg.equation)
            reg.coefficients.forEachIndexed { i, c -> lines += line("abc"[i].toString(), c) }
            lines += line("r", reg.r?.toString() ?: "not defined")
            lines += line("r²", reg.r2)
            lines += line("x̄ / ȳ", "${two.x.mean} / ${two.y.mean}")
            lines += line("Σxy", two.sumXY)
            input.text("px").takeIf { it.isNotEmpty() }?.let { lines += line("ŷ at x = $it", reg.predictY(number(it, input.ctx, "x"))) }
            input.text("py").takeIf { it.isNotEmpty() }?.let {
                lines += line("x̂ at y = $it", reg.estimateX(number(it, input.ctx, "y")).joinToString("  or  "))
            }
            ToolResult.Ok(lines, lines.joinToString("\n") { "${it.label}: ${it.value}" })
        }
    }

    private val distributions = ToolSpec(
        id = "dist", title = "Distributions",
        summary = "Normal, binomial and Poisson: probabilities and inverses",
        fields = listOf(
            ToolField("p1", "Value x / k / area (see below)", "1.5", default = "1.5"),
            ToolField("p2", "Mean μ  /  n  /  λ", "0", default = "0"),
            ToolField("p3", "Standard deviation σ  /  probability p", "1", default = "1"),
            ToolField("p4", "Upper bound (normal range only)")
        ),
        options = listOf(
            ToolOption(
                "kind", "Function",
                listOf("Normal pdf", "Normal P(≤x)", "Normal range", "Inverse normal", "Binomial pdf", "Binomial P(≤k)", "Poisson pdf", "Poisson P(≤k)")
            )
        )
    ) { input ->
        guarded {
            val c = input.ctx
            val p1 = input.text("p1"); val p2 = input.text("p2"); val p3 = input.text("p3"); val p4 = input.text("p4")
            fun orDefault(t: String, d: String) = t.ifEmpty { d }
            when (input.choice("kind")) {
                0 -> ok(line("f(x)", Distributions.normalPdf(number(p1, c, "x"), number(orDefault(p2, "0"), c, "μ"), number(orDefault(p3, "1"), c, "σ"))))
                1 -> ok(line("P(X ≤ x)", Distributions.normalCdf(null, number(p1, c, "x"), number(orDefault(p2, "0"), c, "μ"), number(orDefault(p3, "1"), c, "σ"))))
                2 -> ok(line("P(lower ≤ X ≤ upper)", Distributions.normalCdf(number(p1, c, "lower bound"), number(p4, c, "upper bound"), number(orDefault(p2, "0"), c, "μ"), number(orDefault(p3, "1"), c, "σ"))))
                3 -> ok(line("x", Distributions.invNormal(number(p1, c, "area"), number(orDefault(p2, "0"), c, "μ"), number(orDefault(p3, "1"), c, "σ"))))
                4 -> ok(line("P(X = k)", Distributions.binomialPdf(intOf(p2, c, "n"), number(p3, c, "p"), intOf(p1, c, "k"))))
                5 -> ok(line("P(X ≤ k)", Distributions.binomialCdf(intOf(p2, c, "n"), number(p3, c, "p"), intOf(p1, c, "k"))))
                6 -> ok(line("P(X = k)", Distributions.poissonPdf(number(p2, c, "λ"), intOf(p1, c, "k"))))
                else -> ok(line("P(X ≤ k)", Distributions.poissonCdf(number(p2, c, "λ"), intOf(p1, c, "k"))))
            }
        }
    }

    private val baseN = ToolSpec(
        id = "basen", title = "BASE-N (logic)",
        summary = "Binary, octal, decimal and hex with and, or, xor, not, shl, shr",
        fields = listOf(ToolField("expr", "Expression", "FF and 0F", default = "FF and 0F")),
        options = listOf(
            ToolOption("base", "Input base", listOf("BIN", "OCT", "DEC", "HEX"), default = 3),
            ToolOption("bits", "Word size", listOf("8 bit", "16 bit", "32 bit", "64 bit"), default = 2)
        )
    ) { input ->
        val base = BaseNCalc.BASES[input.choice("base").coerceIn(0, 3)]
        val bits = BaseNCalc.WORD_SIZES[input.choice("bits").coerceIn(0, 3)]
        when (val o = BaseNCalc.evaluate(input.text("expr"), base, bits)) {
            is BaseNOutcome.Ok -> ToolResult.Ok(
                listOf(line("DEC", o.result.dec), line("HEX", o.result.hex), line("OCT", o.result.oct), line("BIN", o.result.binGrouped))
            )
            is BaseNOutcome.Err -> err(o.message)
        }
    }

    private val matrix = ToolSpec(
        id = "matrix", title = "Matrices",
        summary = "A, B, C: rows on separate lines (or split by ;), cells by commas or spaces",
        fields = listOf(
            ToolField("A", "Matrix A", "1 2\n3 4", multiline = true, default = "1 2\n3 4"),
            ToolField("B", "Matrix B (optional)", "5 6\n7 8", multiline = true),
            ToolField("C", "Matrix C (optional)", multiline = true),
            ToolField("expr", "Expression", "A*B + 2A   det(A)   inv(A)   A^T   rref(A)", default = "det(A)")
        )
    ) { input ->
        guarded {
            val map = LinkedHashMap<String, Matrix>()
            for (n in listOf("A", "B", "C")) parseMatrix(input.text(n), input.ctx)?.let { map[n] = it }
            if (map.isEmpty()) return@guarded err("Enter matrix A")
            when (val o = MatrixExpr.evaluate(input.text("expr"), map, input.ctx)) {
                is MatrixOutcome.Err -> err(o.error.message)
                is MatrixOutcome.Ok -> when (val v = o.value) {
                    is MatrixValue.Scalar -> ok(line("", v.value))
                    is MatrixValue.Mat -> {
                        val lines = matrixLines("Result", v.matrix)
                        ToolResult.Ok(lines, v.matrix.toString())
                    }
                }
            }
        }
    }

    private val eigen = ToolSpec(
        id = "eigen", title = "Eigenvalues & more",
        summary = "For a square matrix (up to 4×4): eigenvalues, determinant, rank, trace",
        fields = listOf(ToolField("A", "Matrix", "2 1\n1 2", multiline = true, default = "2 1\n1 2"))
    ) { input ->
        guarded {
            val m = parseMatrix(input.text("A"), input.ctx) ?: return@guarded err("Enter a matrix")
            val lines = ArrayList<ResultLine>()
            lines += line("Rank", MatrixOps.rank(m).toString())
            if (m.isSquare) {
                lines += line("Determinant", MatrixOps.determinant(m))
                lines += line("Trace", MatrixOps.trace(m))
                if (m.rows <= 4) MatrixOps.eigenvalues(m).forEachIndexed { i, r -> lines += line("λ${subscript(i + 1)}", r.format()) }
            }
            ToolResult.Ok(lines)
        }
    }

    private val vector = ToolSpec(
        id = "vector", title = "Vectors",
        summary = "Two vectors (2 or 3 numbers each): dot, cross, length, angle, projection",
        fields = listOf(
            ToolField("u", "Vector u", "1, 2, 3", default = "1, 2, 3"),
            ToolField("v", "Vector v", "4, 5, 6", default = "4, 5, 6")
        )
    ) { input ->
        guarded {
            val u = Stats.parseList(input.text("u"), input.ctx)
            val v = Stats.parseList(input.text("v"), input.ctx)
            fun vec(l: List<CalcValue>) = "(" + l.joinToString(", ") + ")"
            val lines = ArrayList<ResultLine>()
            lines += line("u + v", vec(VectorOps.add(u, v)))
            lines += line("u − v", vec(VectorOps.sub(u, v)))
            lines += line("u · v", VectorOps.dot(u, v))
            if (u.size == 3 && v.size == 3) lines += line("u × v", vec(VectorOps.cross(u, v)))
            lines += line("|u|", VectorOps.magnitude(u)); lines += line("|v|", VectorOps.magnitude(v))
            lines += line("Angle", "${VectorOps.angle(u, v, input.ctx.angleMode)} ${input.ctx.angleMode.label}")
            lines += line("Projection of u on v", vec(VectorOps.projection(u, v)))
            ToolResult.Ok(lines)
        }
    }

    private val complex = ToolSpec(
        id = "complex", title = "Complex numbers",
        summary = "Use i: (3+4i)*(1-2i), sqrt(-4), abs(3+4i), exp(i*pi), conj(z)",
        fields = listOf(ToolField("expr", "Expression", "(3+4i)*(1-2i)", default = "(3+4i)*(1-2i)"))
    ) { input ->
        when (val o = ComplexCalc.evaluate(input.text("expr"), input.ctx)) {
            is ComplexOutcome.Ok -> ToolResult.Ok(listOf(line("a + bi", o.rect), line("r∠θ", o.polar)), o.rect)
            is ComplexOutcome.Err -> err(o.error.message)
        }
    }

    private val steps = ToolSpec(
        id = "steps", title = "Step by step",
        summary = "See an expression worked out one operation at a time",
        fields = listOf(ToolField("expr", "Expression", "2 + 3 × (4 − 1)^2", default = "2 + 3 × (4 − 1)^2"))
    ) { input ->
        when (val o = StepSolver.solve(input.text("expr"), input.ctx)) {
            is StepsOutcome.Err -> err(o.error.message)
            is StepsOutcome.Ok -> {
                val lines = ArrayList<ResultLine>()
                lines += line("Start", input.text("expr"))
                o.steps.forEachIndexed { i, s -> lines += line("${i + 1}. ${s.description}", s.expression) }
                ToolResult.Ok(lines, o.steps.joinToString("\n") { "= ${it.expression}" })
            }
        }
    }

    /** All form-style tools, in the order they appear on screen. */
    val all: List<ToolSpec> = listOf(
        polynomial, simultaneous, solveFx, calculus, symbolic, oneVar, regression, distributions,
        baseN, matrix, eigen, vector, complex, steps
    )

    fun byId(id: String): ToolSpec? = all.firstOrNull { it.id == id }
}

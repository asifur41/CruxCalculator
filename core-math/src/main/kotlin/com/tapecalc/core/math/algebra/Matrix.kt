package com.tapecalc.core.math.algebra

import com.tapecalc.core.math.AngleMode
import com.tapecalc.core.math.Arith
import com.tapecalc.core.math.CalcException
import com.tapecalc.core.math.CalcValue
import com.tapecalc.core.math.Calculator
import com.tapecalc.core.math.ErrorKind
import com.tapecalc.core.math.EvalContext
import com.tapecalc.core.math.EvalOutcome
import com.tapecalc.core.math.PolyRoot
import com.tapecalc.core.math.Polynomial
import com.tapecalc.core.math.Trig
import com.tapecalc.core.precision.NumberFormatter

internal fun domain(message: String): Nothing = throw CalcException(ErrorKind.DOMAIN, message)

/** A number for a matrix cell or a result line: exact non-integers stay fractions ("3/2"), others use 15 digits. */
internal fun cellText(v: CalcValue): String {
    val ex = v.exact
    if (ex != null && !ex.isInteger && ex.bitSize <= 64) return ex.toString()
    return NumberFormatter.format(v.decimal)
}

/** A rectangular grid of numbers (exact fractions while the entries are exact). Stored row by row. */
class Matrix(val rows: Int, val cols: Int, cells: List<CalcValue>) {
    private val data: List<CalcValue>

    init {
        require(rows in 1..MAX_DIM && cols in 1..MAX_DIM) { "A matrix has 1 to $MAX_DIM rows and columns" }
        require(cells.size == rows * cols) { "A $rows×$cols matrix needs ${rows * cols} entries" }
        data = cells.toList()
    }

    operator fun get(r: Int, c: Int): CalcValue = data[r * cols + c]

    fun toRows(): List<List<CalcValue>> = List(rows) { r -> List(cols) { c -> this[r, c] } }

    /** Display text of every entry. */
    fun format(): List<List<String>> = toRows().map { row -> row.map { cellText(it) } }

    val isSquare: Boolean get() = rows == cols

    val size: String get() = "$rows×$cols"

    override fun toString(): String = format().joinToString("; ") { it.joinToString(" ") }

    companion object {
        const val MAX_DIM = 6

        fun identity(n: Int): Matrix = Matrix(n, n, List(n * n) { if (it / n == it % n) CalcValue.ONE else CalcValue.ZERO })

        fun zero(r: Int, c: Int): Matrix = Matrix(r, c, List(r * c) { CalcValue.ZERO })

        fun of(rows: List<List<CalcValue>>): Matrix {
            require(rows.isNotEmpty() && rows.all { it.size == rows[0].size }) { "All rows need the same length" }
            return Matrix(rows.size, rows[0].size, rows.flatten())
        }

        /** Each cell is evaluated as a calculator expression, so `1/2` and `sqrt(2)` work; empty cells are 0. */
        fun parse(rows: List<List<String>>, ctx: EvalContext = EvalContext()): Matrix {
            val values = rows.mapIndexed { r, row ->
                row.mapIndexed { c, t ->
                    val text = t.trim().ifEmpty { "0" }
                    when (val o = Calculator.evaluate(text, ctx)) {
                        is EvalOutcome.Success -> o.result.value
                        is EvalOutcome.Failure ->
                            throw CalcException(ErrorKind.SYNTAX, "Row ${r + 1}, column ${c + 1} ('$text'): ${o.error.message}")
                    }
                }
            }
            return of(values)
        }
    }
}

/** Matrix arithmetic. Every function checks sizes and reports problems in plain words. */
object MatrixOps {

    private fun sameSize(a: Matrix, b: Matrix, verb: String) {
        if (a.rows != b.rows || a.cols != b.cols) domain("Cannot $verb a ${a.size} and a ${b.size} matrix: the sizes must match")
    }

    private fun square(a: Matrix, what: String) {
        if (!a.isSquare) domain("$what needs a square matrix (this one is ${a.size})")
    }

    fun add(a: Matrix, b: Matrix): Matrix {
        sameSize(a, b, "add")
        return Matrix(a.rows, a.cols, List(a.rows * a.cols) { Arith.add(a[it / a.cols, it % a.cols], b[it / a.cols, it % a.cols]) })
    }

    fun sub(a: Matrix, b: Matrix): Matrix {
        sameSize(a, b, "subtract")
        return Matrix(a.rows, a.cols, List(a.rows * a.cols) { Arith.sub(a[it / a.cols, it % a.cols], b[it / a.cols, it % a.cols]) })
    }

    fun scale(a: Matrix, k: CalcValue): Matrix = Matrix(a.rows, a.cols, List(a.rows * a.cols) { Arith.mul(a[it / a.cols, it % a.cols], k) })

    fun mul(a: Matrix, b: Matrix): Matrix {
        if (a.cols != b.rows) domain("Cannot multiply a ${a.size} by a ${b.size} matrix: the first needs as many columns as the second has rows")
        return Matrix(a.rows, b.cols, List(a.rows * b.cols) { idx ->
            val r = idx / b.cols
            val c = idx % b.cols
            var sum = CalcValue.ZERO
            for (k in 0 until a.cols) sum = Arith.add(sum, Arith.mul(a[r, k], b[k, c]))
            sum
        })
    }

    fun transpose(a: Matrix): Matrix = Matrix(a.cols, a.rows, List(a.rows * a.cols) { a[it % a.rows, it / a.rows] })

    fun trace(a: Matrix): CalcValue {
        square(a, "The trace")
        var t = CalcValue.ZERO
        for (i in 0 until a.rows) t = Arith.add(t, a[i, i])
        return t
    }

    private fun grid(a: Matrix): Array<Array<CalcValue>> = Array(a.rows) { r -> Array(a.cols) { c -> a[r, c] } }

    /** Gauss elimination with partial pivoting; returns the sign of the row swaps and the pivot columns. */
    private class Elimination(val rows: Array<Array<CalcValue>>, val swaps: Int, val pivots: List<Int>)

    private fun eliminate(g: Array<Array<CalcValue>>, cols: Int, fullJordan: Boolean): Elimination {
        var row = 0
        var swaps = 0
        val pivots = ArrayList<Int>()
        for (col in 0 until cols) {
            if (row >= g.size) break
            var p = -1
            for (r in row until g.size) {
                if (!g[r][col].isZero && (p < 0 || Arith.compare(Arith.abs(g[r][col]), Arith.abs(g[p][col])) > 0)) p = r
            }
            if (p < 0) continue
            if (p != row) { val t = g[row]; g[row] = g[p]; g[p] = t; swaps++ }
            val start = if (fullJordan) 0 else row + 1
            for (r in start until g.size) {
                if (r == row || g[r][col].isZero) continue
                val f = Arith.div(g[r][col], g[row][col])
                for (c in col until g[r].size) g[r][c] = Arith.sub(g[r][c], Arith.mul(f, g[row][c]))
            }
            pivots.add(col)
            row++
        }
        return Elimination(g, swaps, pivots)
    }

    fun determinant(a: Matrix): CalcValue {
        square(a, "The determinant")
        val e = eliminate(grid(a), a.cols, false)
        if (e.pivots.size < a.rows) return CalcValue.ZERO
        var det = if (e.swaps % 2 == 0) CalcValue.ONE else CalcValue.ofLong(-1)
        for (i in 0 until a.rows) det = Arith.mul(det, e.rows[i][i])
        return det
    }

    fun rref(a: Matrix): Matrix {
        val e = eliminate(grid(a), a.cols, true)
        val out = e.rows
        for ((r, c) in e.pivots.withIndex()) {
            val p = out[r][c]
            for (k in 0 until a.cols) out[r][k] = Arith.div(out[r][k], p)
        }
        return Matrix(a.rows, a.cols, out.flatMap { it.toList() })
    }

    fun rank(a: Matrix): Int = eliminate(grid(a), a.cols, false).pivots.size

    fun inverse(a: Matrix): Matrix {
        square(a, "The inverse")
        val n = a.rows
        val g = Array(n) { r -> Array(2 * n) { c -> if (c < n) a[r, c] else if (c - n == r) CalcValue.ONE else CalcValue.ZERO } }
        val e = eliminate(g, n, true)
        if (e.pivots.size < n) domain("Matrix is singular (determinant 0), so it has no inverse")
        return Matrix(n, n, List(n * n) { Arith.div(g[it / n][n + it % n], g[it / n][it / n]) })
    }

    /** Integer powers; a negative power uses the inverse. */
    fun power(a: Matrix, n: Int): Matrix {
        square(a, "A power")
        if (Math.abs(n) > 1000) domain("The power must be between -1000 and 1000")
        var base = if (n < 0) inverse(a) else a
        var e = Math.abs(n)
        var result = Matrix.identity(a.rows)
        while (e > 0) {
            if (e and 1 == 1) result = mul(result, base)
            e = e shr 1
            if (e > 0) base = mul(base, base)
        }
        return result
    }

    /** Solves A·x = b for a square A with a unique answer. */
    fun solve(a: Matrix, b: Matrix): Matrix {
        square(a, "Solving A·x = b")
        if (b.rows != a.rows) domain("The right-hand side needs ${a.rows} rows (it has ${b.rows})")
        if (determinant(a).isZero) domain("The matrix is singular, so A·x = b has no single solution")
        return mul(inverse(a), b)
    }

    /** Coefficients of det(λI − A), highest power first (Faddeev-LeVerrier); 1 × 1 up to 4 × 4. */
    fun charPolyCoefficients(a: Matrix): List<CalcValue> {
        square(a, "The characteristic polynomial")
        val n = a.rows
        if (n > 4) domain("Eigenvalues are available for matrices up to 4×4")
        val coeffs = ArrayList<CalcValue>()
        coeffs.add(CalcValue.ONE)
        var m = Matrix.zero(n, n)
        var c = CalcValue.ONE
        for (k in 1..n) {
            m = add(mul(a, m), scale(Matrix.identity(n), c))
            c = Arith.div(Arith.neg(trace(mul(a, m))), CalcValue.ofLong(k.toLong()))
            coeffs.add(c)
        }
        return coeffs
    }

    /** Eigenvalues (real or complex) of a square matrix up to 4×4. */
    fun eigenvalues(a: Matrix): List<PolyRoot> = Polynomial.solve(charPolyCoefficients(a))
}

/** Vector arithmetic on plain lists (2 to 6 components). */
object VectorOps {
    private fun check(a: List<CalcValue>) {
        if (a.size !in 2..6) domain("A vector has 2 to 6 components")
    }

    private fun same(a: List<CalcValue>, b: List<CalcValue>) {
        check(a); check(b)
        if (a.size != b.size) domain("The vectors must have the same number of components (${a.size} and ${b.size})")
    }

    fun add(a: List<CalcValue>, b: List<CalcValue>): List<CalcValue> { same(a, b); return a.indices.map { Arith.add(a[it], b[it]) } }
    fun sub(a: List<CalcValue>, b: List<CalcValue>): List<CalcValue> { same(a, b); return a.indices.map { Arith.sub(a[it], b[it]) } }
    fun scale(a: List<CalcValue>, k: CalcValue): List<CalcValue> { check(a); return a.map { Arith.mul(it, k) } }

    fun dot(a: List<CalcValue>, b: List<CalcValue>): CalcValue {
        same(a, b)
        var s = CalcValue.ZERO
        for (i in a.indices) s = Arith.add(s, Arith.mul(a[i], b[i]))
        return s
    }

    fun cross(a: List<CalcValue>, b: List<CalcValue>): List<CalcValue> {
        same(a, b)
        if (a.size != 3) domain("The cross product needs 3-component vectors")
        return listOf(
            Arith.sub(Arith.mul(a[1], b[2]), Arith.mul(a[2], b[1])),
            Arith.sub(Arith.mul(a[2], b[0]), Arith.mul(a[0], b[2])),
            Arith.sub(Arith.mul(a[0], b[1]), Arith.mul(a[1], b[0]))
        )
    }

    fun magnitude(a: List<CalcValue>): CalcValue { check(a); return Arith.sqrt(dot(a, a)) }

    fun unit(a: List<CalcValue>): List<CalcValue> {
        val m = magnitude(a)
        if (m.isZero) domain("The zero vector has no direction")
        return a.map { Arith.div(it, m) }
    }

    /** Angle between the vectors in the chosen unit (exact for right angles and similar). */
    fun angle(a: List<CalcValue>, b: List<CalcValue>, mode: AngleMode): CalcValue {
        same(a, b)
        val ma = magnitude(a)
        val mb = magnitude(b)
        if (ma.isZero || mb.isZero) domain("The angle with the zero vector is undefined")
        var cos = Arith.div(dot(a, b), Arith.mul(ma, mb))
        if (Arith.compare(cos, CalcValue.ONE) > 0) cos = CalcValue.ONE
        if (Arith.compare(cos, CalcValue.ofLong(-1)) < 0) cos = CalcValue.ofLong(-1)
        return Trig.acos(cos.decimal, mode)
    }

    /** The projection of [a] onto [onto]. */
    fun projection(a: List<CalcValue>, onto: List<CalcValue>): List<CalcValue> {
        same(a, onto)
        val d = dot(onto, onto)
        if (d.isZero) domain("Cannot project onto the zero vector")
        return scale(onto, Arith.div(dot(a, onto), d))
    }
}

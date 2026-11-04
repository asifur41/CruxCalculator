package com.tapecalc.core.math.algebra

import com.tapecalc.core.math.AngleMode
import com.tapecalc.core.math.CalcException
import com.tapecalc.core.math.CalcValue
import com.tapecalc.core.math.ErrorKind
import com.tapecalc.core.math.EvalContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AlgebraTest {
    private fun m(vararg rows: String): Matrix = Matrix.parse(rows.map { it.trim().split(Regex("\\s+")) })
    private fun v(s: String) = CalcValue.parseLiteral(s)
    private fun vec(vararg s: String) = s.map { v(it) }
    private fun txt(x: Matrix) = x.format().joinToString(" | ") { it.joinToString(" ") }
    private fun msg(block: () -> Unit): String = try { block(); "no error" } catch (e: CalcException) { e.error.message }

    // ---------------------------------------------------------------- matrices

    @Test fun addSubScale() {
        val a = m("1 2", "3 4"); val b = m("5 6", "7 8")
        assertEquals("6 8 | 10 12", txt(MatrixOps.add(a, b)))
        assertEquals("4 4 | 4 4", txt(MatrixOps.sub(b, a)))
        assertEquals("1/2 1 | 3/2 2", txt(MatrixOps.scale(a, v("0.5"))))
    }

    @Test fun multiply() {
        assertEquals("19 22 | 43 50", txt(MatrixOps.mul(m("1 2", "3 4"), m("5 6", "7 8"))))
        assertEquals("58 64 | 139 154", txt(MatrixOps.mul(m("1 2 3", "4 5 6"), m("7 8", "9 10", "11 12"))))
    }

    @Test fun transposeAndTrace() {
        assertEquals("1 4 | 2 5 | 3 6", txt(MatrixOps.transpose(m("1 2 3", "4 5 6"))))
        assertEquals("5", MatrixOps.trace(m("1 2", "3 4")).toString())
    }

    @Test fun determinants() {
        assertEquals("-2", MatrixOps.determinant(m("1 2", "3 4")).toString())
        assertEquals("18", MatrixOps.determinant(m("2 0 1", "1 3 2", "1 1 4")).toString())
        assertEquals("1", MatrixOps.determinant(Matrix.identity(4)).toString())
        assertEquals("0", MatrixOps.determinant(m("1 2", "2 4")).toString())
        assertEquals("30", MatrixOps.determinant(m("2 0 0 0", "0 3 0 0", "0 0 5 0", "0 0 0 1")).toString())
        assertEquals("-1", MatrixOps.determinant(m("0 1", "1 0")).toString())   // a row swap flips the sign
    }

    @Test fun inverseIsExact() {
        assertEquals("-2 1 | 3/2 -1/2", txt(MatrixOps.inverse(m("1 2", "3 4"))))
        val a = m("2 1 1", "1 3 2", "1 0 0")
        assertEquals("1 0 0 | 0 1 0 | 0 0 1", txt(MatrixOps.mul(a, MatrixOps.inverse(a))))
        val b = m("1 2 3 4", "0 1 5 6", "0 0 1 7", "0 0 0 1")
        assertEquals(txt(Matrix.identity(4)), txt(MatrixOps.mul(b, MatrixOps.inverse(b))))
    }

    @Test fun singularMatrixHasNoInverse() {
        assertTrue(msg { MatrixOps.inverse(m("1 2", "2 4")) }.contains("singular"))
    }

    @Test fun powers() {
        val a = m("1 1", "1 0")
        assertEquals("89 55 | 55 34", txt(MatrixOps.power(a, 10)))
        assertEquals("1 0 | 0 1", txt(MatrixOps.power(a, 0)))
        assertEquals("0 1 | 1 -1", txt(MatrixOps.power(a, -1)))
        assertTrue(msg { MatrixOps.power(m("1 2 3", "4 5 6"), 2) }.contains("square"))
    }

    @Test fun rrefAndRank() {
        assertEquals("1 0 -1 | 0 1 2 | 0 0 0", txt(MatrixOps.rref(m("1 2 3", "4 5 6", "7 8 9"))))
        assertEquals(2, MatrixOps.rank(m("1 2 3", "4 5 6", "7 8 9")))
        assertEquals(3, MatrixOps.rank(Matrix.identity(3)))
        assertEquals(1, MatrixOps.rank(m("1 2", "2 4")))
        assertEquals(0, MatrixOps.rank(Matrix.zero(2, 2)))
    }

    @Test fun solveLinearSystem() {
        val x = MatrixOps.solve(m("2 1", "1 3"), m("3", "5"))
        assertEquals("4/5 | 7/5", txt(x))
        assertTrue(msg { MatrixOps.solve(m("1 2", "2 4"), m("1", "2")) }.contains("singular"))
    }

    @Test fun characteristicPolynomialAndEigenvalues() {
        assertEquals(listOf("1", "-5", "6"), MatrixOps.charPolyCoefficients(m("2 0", "0 3")).map { it.toString() })
        assertEquals(listOf("2", "3"), MatrixOps.eigenvalues(m("2 0", "0 3")).map { it.format() })
        assertEquals(listOf("1", "3"), MatrixOps.eigenvalues(m("2 1", "1 2")).map { it.format() })
        assertEquals(listOf("i", "-i"), MatrixOps.eigenvalues(m("0 -1", "1 0")).map { it.format() })
        assertEquals(listOf("1", "2", "3"), MatrixOps.eigenvalues(m("1 0 0", "0 2 0", "0 0 3")).map { it.format() })
        assertEquals(listOf("2", "2", "3"), MatrixOps.eigenvalues(m("2 1 0", "0 2 0", "0 0 3")).map { it.format() })
    }

    @Test fun sizeErrors() {
        assertTrue(msg { MatrixOps.add(m("1 2"), m("1", "2")) }.contains("sizes must match"))
        assertTrue(msg { MatrixOps.mul(m("1 2"), m("1 2")) }.contains("columns"))
        assertTrue(msg { MatrixOps.determinant(m("1 2 3")) }.contains("square"))
        assertTrue(msg { Matrix.parse(listOf(listOf("1", "abc!"))) }.startsWith("Row 1, column 2"))
        try { Matrix(2, 2, listOf(CalcValue.ONE)); fail() } catch (e: IllegalArgumentException) { assertTrue(e.message!!.contains("needs 4")) }
    }

    @Test fun cellsAcceptExpressions() = assertEquals("1/2 2", txt(m("1/2 sqrt(4)")))

    // ---------------------------------------------------------------- matrix expressions

    private fun expr(text: String, vararg named: Pair<String, Matrix>): String =
        when (val o = MatrixExpr.evaluate(text, mapOf(*named))) {
            is MatrixOutcome.Ok -> when (val value = o.value) {
                is MatrixValue.Scalar -> value.value.toString()
                is MatrixValue.Mat -> txt(value.matrix)
            }
            is MatrixOutcome.Err -> "ERR: " + o.error.message
        }

    private val A = m("1 2", "3 4")
    private val B = m("0 1", "1 0")

    @Test fun matrixExpressions() {
        assertEquals("1 3 | 4 4", expr("A + B", "A" to A, "B" to B))
        assertEquals("2 1 | 4 3", expr("A*B", "A" to A, "B" to B))
        assertEquals("2 4 | 6 8", expr("2A", "A" to A))
        assertEquals("2 4 | 6 8", expr("A*2", "A" to A))
        assertEquals("-2", expr("det(A)", "A" to A))
        assertEquals("-2 1 | 3/2 -1/2", expr("inv(A)", "A" to A))
        assertEquals("-2 1 | 3/2 -1/2", expr("A^-1", "A" to A))
        assertEquals("7 10 | 15 22", expr("A^2", "A" to A))
        assertEquals("1 3 | 2 4", expr("A^T", "A" to A))
        assertEquals("1 3 | 2 4", expr("trans(A)", "A" to A))
        assertEquals("5", expr("tr(A)", "A" to A))
        assertEquals("2", expr("rank(A)", "A" to A))
        assertEquals("1 0 | 0 1", expr("rref(A)", "A" to A))
        assertEquals("25", expr("(tr(A))^2", "A" to A))
        assertEquals("25", expr("tr(A)^2", "A" to A))
        assertEquals("-4", expr("2 * det(A)", "A" to A))
        assertEquals("1 0 | 1 4", expr("A - 2B", "A" to A, "B" to B))
        assertEquals("1 2 | 3 4", expr("inv(inv(A))", "A" to A))
        assertEquals("1/2 1 | 3/2 2", expr("A/2", "A" to A))
        assertEquals("-1 -2 | -3 -4", expr("-A", "A" to A))
        assertEquals("6", expr("sqrt(36)", "A" to A))
    }

    @Test fun matrixExpressionErrors() {
        assertTrue(expr("A + 1", "A" to A).startsWith("ERR: Cannot add"))
        assertTrue(expr("C", "A" to A).contains("Matrix C is not defined"))
        assertTrue(expr("1 / A", "A" to A).contains("Cannot divide by a matrix"))
        assertTrue(expr("A * M", "A" to A, "M" to m("1 2 3")).contains("Cannot multiply"))
        assertTrue(expr("", "A" to A).startsWith("ERR"))
        assertTrue(expr("det(5)", "A" to A).contains("needs a matrix"))
        assertTrue(expr("x = 1", "A" to A).startsWith("ERR"))
    }

    // ---------------------------------------------------------------- vectors

    @Test fun vectors() {
        assertEquals("32", VectorOps.dot(vec("1", "2", "3"), vec("4", "5", "6")).toString())
        assertEquals(listOf("-3", "6", "-3"), VectorOps.cross(vec("1", "2", "3"), vec("4", "5", "6")).map { it.toString() })
        assertEquals("5", VectorOps.magnitude(vec("3", "4")).toString())
        assertEquals(listOf("3/5", "4/5"), VectorOps.unit(vec("3", "4")).map { it.exact.toString() })
        assertEquals(listOf("4", "6"), VectorOps.add(vec("1", "2"), vec("3", "4")).map { it.toString() })
        assertEquals(listOf("2", "2"), VectorOps.sub(vec("3", "4"), vec("1", "2")).map { it.toString() })
        assertEquals(listOf("2", "4"), VectorOps.scale(vec("1", "2"), v("2")).map { it.toString() })
        assertEquals(listOf("3", "0"), VectorOps.projection(vec("3", "4"), vec("1", "0")).map { it.toString() })
    }

    @Test fun vectorAngles() {
        assertEquals("90", VectorOps.angle(vec("1", "0"), vec("0", "1"), AngleMode.DEG).toString())
        assertEquals("45", VectorOps.angle(vec("1", "0"), vec("1", "1"), AngleMode.DEG).toString())
        assertEquals("180", VectorOps.angle(vec("1", "0"), vec("-2", "0"), AngleMode.DEG).toString())
        assertEquals("0", VectorOps.angle(vec("1", "2"), vec("2", "4"), AngleMode.DEG).toString())
        assertEquals("1.5707963267949", VectorOps.angle(vec("1", "0"), vec("0", "1"), AngleMode.RAD).toString())
        assertEquals("100", VectorOps.angle(vec("1", "0"), vec("0", "1"), AngleMode.GRAD).toString())
    }

    @Test fun vectorErrors() {
        assertTrue(msg { VectorOps.dot(vec("1", "2"), vec("1", "2", "3")) }.contains("same number"))
        assertTrue(msg { VectorOps.cross(vec("1", "2"), vec("3", "4")) }.contains("3-component"))
        assertTrue(msg { VectorOps.unit(vec("0", "0")) }.contains("zero vector"))
        assertTrue(msg { VectorOps.angle(vec("0", "0"), vec("1", "1"), AngleMode.DEG) }.contains("zero vector"))
    }

    // ---------------------------------------------------------------- complex

    private fun cx(text: String, mode: AngleMode = AngleMode.DEG): String =
        when (val o = ComplexCalc.evaluate(text, EvalContext(mode))) {
            is ComplexOutcome.Ok -> o.rect
            is ComplexOutcome.Err -> "ERR: " + o.error.message
        }

    @Test fun complexArithmeticIsExact() {
        assertEquals("4 + 6i", cx("(1+2i) + (3+4i)"))
        assertEquals("5 + 5i", cx("(1+2i)*(3-i)"))
        assertEquals("1 + 2i", cx("(5+5i)/(3-i)"))
        assertEquals("1/2 - 1/2i", cx("1/(1+i)"))
        assertEquals("-1", cx("i^2"))
        assertEquals("16", cx("(1+i)^8"))
        assertEquals("-i", cx("i^3"))
        assertEquals("1/2 + 1/2i", cx("1/(1-i)"))
        assertEquals("-3 + 4i", cx("-3 + 4i"))
    }

    @Test fun squareRoots() {
        assertEquals("2i", cx("sqrt(-4)"))
        assertEquals("2 + i", cx("sqrt(3+4i)"))
        assertEquals("3", cx("sqrt(9)"))
        assertEquals("1.4142135623731i", cx("sqrt(-2)"))
        assertEquals("2 - i", cx("sqrt(3-4i)"))
    }

    @Test fun modulusArgumentConjugate() {
        assertEquals("5", cx("abs(3+4i)"))
        assertEquals("45", cx("arg(1+i)"))
        assertEquals("0.785398163397448", cx("arg(1+i)", AngleMode.RAD))
        assertEquals("90", cx("arg(i)"))
        assertEquals("180", cx("arg(-1)"))
        assertEquals("2 - 3i", cx("conj(2+3i)"))
        assertEquals("3", cx("re(3+4i)"))
        assertEquals("4", cx("im(3+4i)"))
    }

    @Test fun eulerIdentity() {
        assertEquals("-1", cx("exp(i*pi)"))
        assertEquals("1", cx("exp(0)"))
        assertEquals("i", cx("exp(i*pi/2)"))
    }

    @Test fun logarithmAndTrig() {
        assertEquals("1.5707963267949i", cx("ln(i)"))
        assertEquals("1", cx("ln(e)"))
        assertEquals("0.5", cx("sin(30)"))
        assertEquals("1.1752011936438i", cx("sin(i)"))
    }

    @Test fun complexPolarForms() {
        val o = ComplexCalc.evaluate("3+4i") as ComplexOutcome.Ok
        assertTrue(o.polar.startsWith("5∠53.13010235415"))
        val rad = ComplexCalc.evaluate("1+i", EvalContext(AngleMode.RAD)) as ComplexOutcome.Ok
        assertTrue(rad.polar.endsWith(" rad"))
        assertEquals("1 + i", cx("1+i"))
        val z = Complex.polar(v("2"), v("90"), AngleMode.DEG)
        assertEquals("2i", z.format())
    }

    @Test fun fractionalPowersAndDeMoivre() {
        assertEquals("2i", cx("(-4)^(1/2)"))
        val cube = cx("(1+i)^(1/3)")
        assertTrue(cube, cube.contains("i"))
        assertEquals("-8", cx("(1+sqrt(3)i)^3"))
        assertEquals("1", cx("(-1)^2"))
    }

    @Test fun complexErrors() {
        assertEquals("ERR: Division by zero", cx("1/0"))
        assertEquals("ERR: Division by zero", cx("(1+i)/(0)"))
        assertTrue(cx("foo + 1").startsWith("ERR: Unknown name"))
        assertTrue(cx("").startsWith("ERR"))
        assertTrue(cx("x = 3").startsWith("ERR"))
        assertTrue(cx("floor(1+i)").contains("not available for complex"))
        assertTrue(cx("arg(0)").startsWith("ERR"))
        assertTrue(cx("5 mod 3i").startsWith("ERR"))
        assertTrue(cx("ln(0)").contains("undefined"))
    }

    @Test fun ansIsSupported() {
        val first = (ComplexCalc.evaluate("1+i") as ComplexOutcome.Ok).value
        val second = ComplexCalc.evaluate("ans * ans", EvalContext(), first) as ComplexOutcome.Ok
        assertEquals("2i", second.rect)
    }
}

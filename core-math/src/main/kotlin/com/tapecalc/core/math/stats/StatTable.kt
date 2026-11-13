package com.tapecalc.core.math.stats

import com.tapecalc.core.math.CalcException
import com.tapecalc.core.math.CalcValue
import com.tapecalc.core.math.Calculator
import com.tapecalc.core.math.ErrorKind
import com.tapecalc.core.math.EvalContext
import com.tapecalc.core.math.EvalOutcome
import com.tapecalc.core.math.tools.ResultLine
import com.tapecalc.core.math.tools.ToolResult

/** One line of the data table. Every cell is the text the person typed, so `1/2` or `sqrt(2)` can be entered as they are. */
data class StatRow(val x: String = "", val y: String = "", val f: String = "") {
    fun isBlank(twoVar: Boolean, useFreq: Boolean): Boolean =
        x.isBlank() && (!twoVar || y.isBlank()) && (!useFreq || f.isBlank())
}

/** The three columns of the table. */
enum class StatColumn { X, Y, F }

/**
 * Everything on the statistics data-entry screen: whether it is the one-variable (X, FREQ) or two-variable (X, Y, FREQ)
 * table, the cells, and the curve chosen for the regression. It is an immutable value; each edit returns a new one.
 *
 * Like the table of a physical calculator, the last row is always empty: typing into it adds another empty row below,
 * up to [MAX_ROWS].
 */
data class StatTableState(
    val twoVar: Boolean = false,
    val useFreq: Boolean = false,
    /** Index into [RegressionType.entries]. */
    val regression: Int = 0,
    val rows: List<StatRow> = List(START_ROWS) { StatRow() },
    /** "Predict y for x =" and "Estimate x for y =" (two-variable only). */
    val predictY: String = "",
    val estimateX: String = ""
) {
    fun withMode(twoVar: Boolean) = copy(twoVar = twoVar)
    fun withFreq(useFreq: Boolean) = copy(useFreq = useFreq)
    fun withRegression(index: Int) = copy(regression = index.coerceIn(0, RegressionType.entries.lastIndex))

    /** The cell at [row] / [column] set to [text] (a single line, at most [MAX_CELL] characters). */
    fun withCell(row: Int, column: StatColumn, text: String): StatTableState {
        if (row !in rows.indices) return this
        val clean = text.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ').take(MAX_CELL)
        val old = rows[row]
        val updated = when (column) {
            StatColumn.X -> old.copy(x = clean)
            StatColumn.Y -> old.copy(y = clean)
            StatColumn.F -> old.copy(f = clean)
        }
        val next = rows.toMutableList().also { it[row] = updated }
        return copy(rows = growTo(next))
    }

    /** Adds one empty row at the end (the "+ Row" button). */
    fun withRowAdded(): StatTableState = if (rows.size >= MAX_ROWS) this else copy(rows = rows + StatRow())

    /** Removes [row] (the "Delete row" button), always keeping one empty row to type in. */
    fun withRowRemoved(row: Int): StatTableState {
        if (row !in rows.indices) return this
        val next = rows.toMutableList().also { it.removeAt(row) }
        if (next.isEmpty()) next += StatRow()
        return copy(rows = growTo(next))
    }

    /** Empties every cell and the prediction boxes but keeps the chosen mode. */
    fun cleared(): StatTableState = copy(rows = List(START_ROWS) { StatRow() }, predictY = "", estimateX = "")

    fun withPredictY(text: String) = copy(predictY = text.take(MAX_CELL))
    fun withEstimateX(text: String) = copy(estimateX = text.take(MAX_CELL))

    /** The number of rows that hold data in the table as it is shown now. */
    val filledRows: Int get() = rows.count { !it.isBlank(twoVar, useFreq) }

    /** One text, for keeping the table on the device. [decode] reads it back. */
    fun encode(): String {
        val sb = StringBuilder()
        sb.append("v1\t").append(if (twoVar) 1 else 0).append('\t').append(if (useFreq) 1 else 0)
            .append('\t').append(regression).append('\t').append(predictY).append('\t').append(estimateX)
        for (r in rows) sb.append('\n').append(r.x).append('\t').append(r.y).append('\t').append(r.f)
        return sb.toString()
    }

    companion object {
        const val MAX_ROWS = 80
        const val START_ROWS = 6
        const val MAX_CELL = 60

        /** Keeps the rule "the last row is empty" after an edit. */
        private fun growTo(rows: List<StatRow>): List<StatRow> {
            val capped = if (rows.size > MAX_ROWS) rows.take(MAX_ROWS) else rows
            val last = capped.last()
            return if (capped.size < MAX_ROWS && (last.x.isNotEmpty() || last.y.isNotEmpty() || last.f.isNotEmpty())) capped + StatRow() else capped
        }

        /** The table saved by [encode]; an empty table for anything missing or damaged. */
        fun decode(text: String?): StatTableState {
            if (text.isNullOrEmpty()) return StatTableState()
            return try {
                val lines = text.split('\n')
                val head = lines[0].split('\t')
                if (head.size < 6 || head[0] != "v1") return StatTableState()
                val rows = lines.drop(1).take(MAX_ROWS).map { line ->
                    val c = line.split('\t')
                    StatRow(c.getOrElse(0) { "" }.take(MAX_CELL), c.getOrElse(1) { "" }.take(MAX_CELL), c.getOrElse(2) { "" }.take(MAX_CELL))
                }
                StatTableState(
                    twoVar = head[1] == "1",
                    useFreq = head[2] == "1",
                    regression = (head[3].toIntOrNull() ?: 0).coerceIn(0, RegressionType.entries.lastIndex),
                    rows = growTo(rows.ifEmpty { List(START_ROWS) { StatRow() } }),
                    predictY = head[4].take(MAX_CELL),
                    estimateX = head[5].take(MAX_CELL)
                )
            } catch (e: Exception) {
                StatTableState()
            }
        }
    }
}

/** Turns a [StatTableState] into the statistics a calculator's STAT menu shows. */
object StatTableEngine {

    /** The data of the table, one entry per filled row, ready for [Stats]. */
    class Data(val xs: List<CalcValue>, val ys: List<CalcValue>?, val freqs: List<CalcValue>?)

    private fun cell(text: String, rowNumber: Int, name: String, ctx: EvalContext): CalcValue =
        when (val o = Calculator.evaluate(text, ctx)) {
            is EvalOutcome.Success -> o.result.value
            is EvalOutcome.Failure -> throw CalcException(ErrorKind.SYNTAX, "Row $rowNumber, $name: ${o.error.message}")
        }

    /**
     * Reads the filled rows. An empty row is skipped; a row with only some of its cells filled is an error that names the
     * row, and an empty FREQ cell means 1.
     */
    fun parse(state: StatTableState, ctx: EvalContext): Data {
        val xs = ArrayList<CalcValue>()
        val ys = ArrayList<CalcValue>()
        val fs = ArrayList<CalcValue>()
        state.rows.forEachIndexed { index, row ->
            if (row.isBlank(state.twoVar, state.useFreq)) return@forEachIndexed
            val n = index + 1
            if (row.x.isBlank()) throw CalcException(ErrorKind.EMPTY, "Row $n: enter x")
            if (state.twoVar && row.y.isBlank()) throw CalcException(ErrorKind.EMPTY, "Row $n: enter y")
            xs += cell(row.x, n, "x", ctx)
            if (state.twoVar) ys += cell(row.y, n, "y", ctx)
            if (state.useFreq) {
                val f = if (row.f.isBlank()) CalcValue.ONE else cell(row.f, n, "freq", ctx)
                if (f.signum <= 0) throw CalcException(ErrorKind.DOMAIN, "Row $n: the frequency must be greater than 0")
                fs += f
            }
        }
        if (xs.isEmpty()) throw CalcException(ErrorKind.EMPTY, "Enter at least one row of data")
        return Data(xs, if (state.twoVar) ys else null, if (state.useFreq) fs else null)
    }

    private fun line(label: String, value: CalcValue) = ResultLine(label, value.toString())
    private fun line(label: String, value: String) = ResultLine(label, value)

    /** The lines of a one-variable report: n, sums, mean, deviations, quartiles, mode. */
    fun oneVarLines(s: OneVarStats, suffix: String = "x"): List<ResultLine> = buildList {
        add(line("n", s.n)); add(line("Σ$suffix", s.sum)); add(line("Σ$suffix²", s.sumSq))
        add(line("Mean $suffix̄", s.mean)); add(line("σ$suffix (population)", s.sdPop))
        add(line("s$suffix (sample)", s.sdSample?.toString() ?: "needs 2 or more values"))
        add(line("Min", s.min)); add(line("Q1", s.q1)); add(line("Median", s.median))
        add(line("Q3", s.q3)); add(line("Max", s.max)); add(line("Range", s.range))
        add(line("Mode", if (s.modes.isEmpty()) "none" else s.modes.joinToString(", ")))
    }

    /** Runs the statistics for the table; every problem becomes a friendly [ToolResult.Err]. */
    fun run(state: StatTableState, ctx: EvalContext): ToolResult = try {
        val data = parse(state, ctx)
        val lines = ArrayList<ResultLine>()
        if (data.ys == null) {
            lines += oneVarLines(Stats.oneVar(data.xs, data.freqs))
        } else {
            val two = Stats.twoVar(data.xs, data.ys, data.freqs)
            lines += line("n", two.x.n)
            lines += line("Σx", two.x.sum); lines += line("Σx²", two.x.sumSq)
            lines += line("Mean x̄", two.x.mean); lines += line("σx (population)", two.x.sdPop)
            lines += line("sx (sample)", two.x.sdSample?.toString() ?: "needs 2 or more values")
            lines += line("Σy", two.y.sum); lines += line("Σy²", two.y.sumSq)
            lines += line("Mean ȳ", two.y.mean); lines += line("σy (population)", two.y.sdPop)
            lines += line("sy (sample)", two.y.sdSample?.toString() ?: "needs 2 or more values")
            lines += line("Σxy", two.sumXY)
            lines += line("r", two.r?.toString() ?: "not defined (x or y does not vary)")
            val type = RegressionType.entries[state.regression.coerceIn(0, RegressionType.entries.lastIndex)]
            try {
                val reg = Stats.regression(type, data.xs, data.ys, data.freqs)
                lines += line("Curve", type.label)
                lines += line("Equation", reg.equation)
                reg.coefficients.forEachIndexed { i, c -> lines += line("abc"[i].toString(), c) }
                lines += line("r² of the fit", reg.r2)
                state.predictY.takeIf { it.isNotBlank() }?.let {
                    lines += line("ŷ at x = $it", reg.predictY(cell(it, 0, "x", ctx)))
                }
                state.estimateX.takeIf { it.isNotBlank() }?.let {
                    lines += line("x̂ at y = $it", reg.estimateX(cell(it, 0, "y", ctx)).joinToString("  or  "))
                }
            } catch (e: CalcException) {
                // the statistics above are still right; only this curve cannot be fitted to this data
                lines += line("Curve", type.label)
                lines += line("Fit", e.error.message)
            }
        }
        ToolResult.Ok(lines)
    } catch (e: CalcException) {
        ToolResult.Err(e.error.message)
    } catch (e: ArithmeticException) {
        ToolResult.Err(e.message ?: "Math error")
    }

    /** What the person entered, on one line, for the tape: `[2-Var] [Linear] (1, 2) (2, 4) …`. */
    fun inputText(state: StatTableState): String {
        val parts = ArrayList<String>()
        parts += if (state.twoVar) "[2-Var]" else "[1-Var]"
        if (state.useFreq) parts += "[FREQ]"
        if (state.twoVar) parts += "[${RegressionType.entries[state.regression.coerceIn(0, RegressionType.entries.lastIndex)].label.substringBefore("  ")}]"
        val rows = state.rows.filter { !it.isBlank(state.twoVar, state.useFreq) }.joinToString(" ") { r ->
            val point = if (state.twoVar) "(${r.x.trim()}, ${r.y.trim()})" else r.x.trim()
            if (state.useFreq) point + "×" + r.f.trim().ifEmpty { "1" } else point
        }
        return "Statistics table: " + (parts + rows).joinToString(" ")
    }
}

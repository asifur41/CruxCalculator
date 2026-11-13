package com.tapecalc.core.math.sheet

import com.tapecalc.core.math.Arith
import com.tapecalc.core.math.CalcValue
import com.tapecalc.core.math.Calculator
import com.tapecalc.core.math.EvalContext
import com.tapecalc.core.math.EvalOutcome

/** Cell names: columns A–E, rows 1–45. */
object SheetRef {
    const val COLS = 5
    const val ROWS = 45
    const val CELLS = COLS * ROWS

    fun index(col: Int, row: Int): Int = row * COLS + col
    fun colOf(index: Int): Int = index % COLS
    fun rowOf(index: Int): Int = index / COLS
    fun inGrid(col: Int, row: Int): Boolean = col in 0 until COLS && row in 0 until ROWS

    /** "A1" for column 0, row 0. */
    fun name(col: Int, row: Int): String = "${'A' + col}${row + 1}"

    /** (column, row) of a name like "B12" (any case); null when it is not a cell of the grid. */
    fun parse(text: String): Pair<Int, Int>? {
        val t = text.trim()
        if (t.length < 2) return null
        val col = t[0].uppercaseChar() - 'A'
        val row = (t.substring(1).takeIf { s -> s.all { it in '0'..'9' } }?.toIntOrNull() ?: return null) - 1
        return if (inGrid(col, row)) col to row else null
    }
}

/** A rectangle of cells, both corners included; the corners may be given in any order. */
data class CellRange(val col1: Int, val row1: Int, val col2: Int, val row2: Int) {
    val left get() = minOf(col1, col2)
    val right get() = maxOf(col1, col2)
    val top get() = minOf(row1, row2)
    val bottom get() = maxOf(row1, row2)
    val isSingleCell get() = left == right && top == bottom
    val cellCount get() = (right - left + 1) * (bottom - top + 1)

    fun contains(col: Int, row: Int) = col in left..right && row in top..bottom

    /** "A1:B5", or just "A1" for one cell. */
    val text: String
        get() = if (isSingleCell) SheetRef.name(left, top) else "${SheetRef.name(left, top)}:${SheetRef.name(right, bottom)}"

    /** Every cell index of the range, row by row. */
    fun indices(): List<Int> = buildList { for (r in top..bottom) for (c in left..right) add(SheetRef.index(c, r)) }
}

/** What a cell holds once its formula has been worked out. */
sealed interface CellValue {
    data object Empty : CellValue
    data class Num(val value: CalcValue) : CellValue
    data class Text(val text: String) : CellValue

    /** [short] is what the cell shows ("#ERR"), [message] what the formula bar explains. */
    data class Error(val message: String, val short: String = "#ERR") : CellValue
}

/** Count, sum, mean, smallest and largest of the numbers in a range. Text and empty cells are not counted. */
data class RangeStats(
    val count: Int,
    val sum: CalcValue,
    val mean: CalcValue?,
    val min: CalcValue?,
    val max: CalcValue?,
    /** The first error found in the range, if any; the other fields then cover only the cells before it. */
    val error: String? = null
)

/** The worked-out values of all cells, from [SheetEngine.evaluate]. */
class SheetValues internal constructor(private val values: List<CellValue>) {
    operator fun get(col: Int, row: Int): CellValue = values[SheetRef.index(col, row)]
    operator fun get(index: Int): CellValue = values[index]

    fun stats(range: CellRange): RangeStats {
        var count = 0
        var sum = CalcValue.ZERO
        var min: CalcValue? = null
        var max: CalcValue? = null
        for (i in range.indices()) {
            when (val v = values[i]) {
                is CellValue.Num -> {
                    count++
                    sum = Arith.add(sum, v.value)
                    if (min == null || Arith.compare(v.value, min) < 0) min = v.value
                    if (max == null || Arith.compare(v.value, max) > 0) max = v.value
                }
                is CellValue.Error -> return RangeStats(count, sum, null, min, max, error = "${SheetRef.name(SheetRef.colOf(i), SheetRef.rowOf(i))}: ${v.message}")
                else -> Unit
            }
        }
        return RangeStats(count, sum, if (count > 0) Arith.div(sum, CalcValue.ofLong(count.toLong())) else null, min, max)
    }
}

/** What is typed in the 5 × 45 cells. An immutable value: every edit returns a new one. */
class SheetState private constructor(private val raw: List<String>) {

    constructor() : this(List(SheetRef.CELLS) { "" })

    fun text(col: Int, row: Int): String = raw[SheetRef.index(col, row)]
    fun text(index: Int): String = raw[index]

    fun with(col: Int, row: Int, text: String): SheetState {
        if (!SheetRef.inGrid(col, row)) return this
        val clean = text.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ').take(MAX_CELL)
        val i = SheetRef.index(col, row)
        if (raw[i] == clean) return this
        return SheetState(raw.toMutableList().also { it[i] = clean })
    }

    /** Every cell of [range] emptied. */
    fun withCleared(range: CellRange): SheetState {
        val drop = range.indices().toSet()
        if (drop.all { raw[it].isEmpty() }) return this
        return SheetState(raw.mapIndexed { i, t -> if (i in drop) "" else t })
    }

    fun cleared(): SheetState = SheetState()

    val filledCells: Int get() = raw.count { it.isNotEmpty() }

    /** The filled cells as text, one per line: "B3<TAB>=A1+A2". [decode] reads it back. */
    fun encode(): String = raw.withIndex().filter { it.value.isNotEmpty() }
        .joinToString("\n") { "${SheetRef.name(SheetRef.colOf(it.index), SheetRef.rowOf(it.index))}\t${it.value}" }

    override fun equals(other: Any?) = other is SheetState && other.raw == raw
    override fun hashCode() = raw.hashCode()

    companion object {
        const val MAX_CELL = 200

        fun decode(text: String?): SheetState {
            if (text.isNullOrEmpty()) return SheetState()
            val out = MutableList(SheetRef.CELLS) { "" }
            for (line in text.split('\n')) {
                val tab = line.indexOf('\t')
                if (tab < 1) continue
                val (c, r) = SheetRef.parse(line.substring(0, tab)) ?: continue
                out[SheetRef.index(c, r)] = line.substring(tab + 1).take(MAX_CELL)
            }
            return SheetState(out)
        }
    }
}

/**
 * Works out every cell. A cell is a number or expression (`12`, `1/4`, `3e2`), a label (any other text), or a formula
 * starting with `=`. A formula is an ordinary calculator expression in which cell names stand for their values
 * (`=A1+B1*2`), and in which `sum`, `mean` (also `avg`, `average`), `min`, `max` and `count` accept cells and ranges
 * (`=sum(A1:A9)`, `=mean(A1:B4, 10)`). The formulas use the calculator's own engine, so exact fractions stay exact and
 * the angle unit of [EvalContext] applies to sin, cos and tan.
 *
 * A cell that depends on itself, directly or through other cells, shows `#CYCLE`.
 */
object SheetEngine {

    fun evaluate(state: SheetState, ctx: EvalContext): SheetValues {
        val run = Run(state, ctx)
        return SheetValues(List(SheetRef.CELLS) { run.cell(it) })
    }

    /**
     * Where an AutoSum of [range] goes: below a column of numbers, to the right of a single row, as in a spreadsheet.
     * Null when there is no room in the grid.
     */
    fun autoSumTarget(range: CellRange): Pair<Int, Int>? {
        val rightOfRow = range.top == range.bottom && range.left != range.right
        if (rightOfRow && range.right + 1 < SheetRef.COLS) return (range.right + 1) to range.top
        return if (range.bottom + 1 < SheetRef.ROWS) range.left to (range.bottom + 1) else null
    }

    /** The formula for [function] ("sum", "mean", ...) over [range]. */
    fun formula(function: String, range: CellRange): String = "=$function(${range.text})"

    private class Failure(override val message: String, val short: String = "#ERR") : RuntimeException(message, null, false, false)

    private val RANGE = Regex("""(?<![A-Za-z0-9_.])([A-Ea-e])([0-9]{1,2}):([A-Ea-e])([0-9]{1,2})(?![A-Za-z0-9_(])""")
    private val REF = Regex("""(?<![A-Za-z0-9_.])([A-Ea-e][0-9]{1,2})(?![A-Za-z0-9_(])""")
    private val CALL = Regex("""(?<![A-Za-z0-9_])(sum|mean|avg|average|min|max|count)\s*\(([^()]*)\)""", RegexOption.IGNORE_CASE)
    private val PLAIN_NUMBER = Regex("""[0-9\s.,+\-*/^()%eE]+""")

    private class Run(private val state: SheetState, private val ctx: EvalContext) {
        private val memo = arrayOfNulls<CellValue>(SheetRef.CELLS)
        private val working = BooleanArray(SheetRef.CELLS)

        fun cell(index: Int): CellValue {
            memo[index]?.let { return it }
            if (working[index]) return CellValue.Error("A cell refers to itself", "#CYCLE")
            working[index] = true
            val value = try {
                compute(state.text(index).trim())
            } catch (f: Failure) {
                CellValue.Error(f.message ?: "Error", f.short)
            } catch (e: StackOverflowError) {
                CellValue.Error("Too many cells depend on each other", "#CYCLE")
            }
            working[index] = false
            memo[index] = value
            return value
        }

        private fun compute(text: String): CellValue = when {
            text.isEmpty() -> CellValue.Empty
            text.startsWith("=") -> formula(text.substring(1))
            PLAIN_NUMBER.matches(text) && text.any { it in '0'..'9' } -> when (val o = Calculator.evaluate(text, ctx)) {
                is EvalOutcome.Success -> CellValue.Num(o.result.value)
                is EvalOutcome.Failure -> CellValue.Text(text)
            }
            else -> CellValue.Text(text)
        }

        private fun formula(body: String): CellValue {
            if (body.isBlank()) throw Failure("Type a formula after =")
            var expr = body
            // 1. sum(...), mean(...) and friends, innermost first (their arguments hold no brackets)
            var guard = 0
            while (true) {
                val m = CALL.find(expr) ?: break
                if (++guard > 60) throw Failure("The formula is too complicated")
                expr = expr.replaceRange(m.range, literal(call(m.groupValues[1].lowercase(), m.groupValues[2])))
            }
            // 2. a range anywhere else makes no sense
            if (RANGE.containsMatchIn(expr)) throw Failure("A range such as A1:A5 goes inside sum( ), mean( ), min( ), max( ) or count( )")
            // 3. the remaining cell names stand for their values
            expr = REF.replace(expr) { m ->
                val (c, r) = SheetRef.parse(m.groupValues[1])!!
                when (val v = cell(SheetRef.index(c, r))) {
                    is CellValue.Num -> literal(v.value)
                    is CellValue.Empty -> "0"
                    is CellValue.Text -> throw Failure("${SheetRef.name(c, r)} holds text, not a number")
                    is CellValue.Error -> throw Failure("${SheetRef.name(c, r)}: ${v.message}", v.short)
                }
            }
            return when (val o = Calculator.evaluate(expr, ctx)) {
                is EvalOutcome.Success -> CellValue.Num(o.result.value)
                is EvalOutcome.Failure -> throw Failure(o.error.message)
            }
        }

        /** The numbers named by an argument list such as "A1:A5, B2, 10", worked out into one value. */
        private fun call(function: String, args: String): CalcValue {
            val numbers = ArrayList<CalcValue>()
            if (args.isBlank()) throw Failure("$function( ) needs cells or numbers")
            for (piece in args.split(',', ';')) {
                val arg = piece.trim()
                if (arg.isEmpty()) continue
                val range = RANGE.matchEntire(arg)
                val single = SheetRef.parse(arg).takeIf { REF.matchEntire(arg) != null }
                when {
                    range != null -> {
                        val a = SheetRef.parse(range.groupValues[1] + range.groupValues[2])
                        val b = SheetRef.parse(range.groupValues[3] + range.groupValues[4])
                        if (a == null || b == null) throw Failure("${arg.uppercase()} is outside the grid (A1 to E45)")
                        for (i in CellRange(a.first, a.second, b.first, b.second).indices()) collect(i, numbers)
                    }
                    single != null -> collect(SheetRef.index(single.first, single.second), numbers)
                    else -> when (val o = Calculator.evaluate(arg, ctx)) {
                        is EvalOutcome.Success -> numbers += o.result.value
                        is EvalOutcome.Failure -> throw Failure("'$arg': ${o.error.message}")
                    }
                }
            }
            return when (function) {
                "sum" -> numbers.fold(CalcValue.ZERO) { a, b -> Arith.add(a, b) }
                "count" -> CalcValue.ofLong(numbers.size.toLong())
                "mean", "avg", "average" -> {
                    if (numbers.isEmpty()) throw Failure("There are no numbers to average")
                    Arith.div(numbers.fold(CalcValue.ZERO) { a, b -> Arith.add(a, b) }, CalcValue.ofLong(numbers.size.toLong()))
                }
                "min" -> numbers.minWithOrNull { a, b -> Arith.compare(a, b) } ?: throw Failure("There are no numbers to compare")
                "max" -> numbers.maxWithOrNull { a, b -> Arith.compare(a, b) } ?: throw Failure("There are no numbers to compare")
                else -> throw Failure("Unknown function $function")
            }
        }

        /** Numbers are collected; empty cells and labels are skipped; an error cell stops the formula. */
        private fun collect(index: Int, into: MutableList<CalcValue>) {
            when (val v = cell(index)) {
                is CellValue.Num -> into += v.value
                is CellValue.Error -> throw Failure("${SheetRef.name(SheetRef.colOf(index), SheetRef.rowOf(index))}: ${v.message}", v.short)
                else -> Unit
            }
        }

        /** A value written so the calculator reads it back exactly: a whole number, a fraction in brackets, or a decimal. */
        private fun literal(v: CalcValue): String {
            val exact = v.exact
            if (exact != null) {
                return if (exact.isInteger) "(${exact.num})" else "((${exact.num})/(${exact.den}))"
            }
            return "(" + v.decimal.toString().replace("E+", "e").replace("E-", "e-").replace("E", "e") + ")"
        }
    }
}

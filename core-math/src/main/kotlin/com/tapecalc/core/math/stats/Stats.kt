package com.tapecalc.core.math.stats

import com.tapecalc.core.math.Arith
import com.tapecalc.core.math.CalcException
import com.tapecalc.core.math.CalcValue
import com.tapecalc.core.math.Calculator
import com.tapecalc.core.math.ErrorKind
import com.tapecalc.core.math.EvalContext
import com.tapecalc.core.math.EvalOutcome

/**
 * Summary statistics of one data set (optionally weighted by frequencies).
 *
 * Every number is a [CalcValue], so results stay exact fractions while the inputs are exact
 * (the mean of 1, 2, 4 is exactly 7/3).
 *
 * @property n total weight (the number of data points when there are no frequencies)
 * @property sdPop population standard deviation σx
 * @property sdSample sample standard deviation sx (null when n ≤ 1)
 * @property q1 first quartile, @property q3 third quartile — see [Stats] for the rule used
 * @property modes every value with the highest total weight; empty when no value repeats
 */
data class OneVarStats(
    val n: CalcValue,
    val sum: CalcValue,
    val sumSq: CalcValue,
    val mean: CalcValue,
    val variancePop: CalcValue,
    val sdPop: CalcValue,
    val varianceSample: CalcValue?,
    val sdSample: CalcValue?,
    val min: CalcValue,
    val max: CalcValue,
    val median: CalcValue,
    val q1: CalcValue,
    val q3: CalcValue,
    val modes: List<CalcValue>,
    val range: CalcValue
)

/** Statistics of paired data: both variables, Σxy, covariance and the correlation coefficient r. */
data class TwoVarStats(
    val x: OneVarStats,
    val y: OneVarStats,
    val sumXY: CalcValue,
    val covariancePop: CalcValue,
    val covarianceSample: CalcValue?,
    /** Pearson correlation; null when x or y has no spread. */
    val r: CalcValue?
)

/**
 * One- and two-variable statistics.
 *
 * Quartile rule (the Casio fx-991 / TI-84 method): the median splits the sorted data in a lower and an
 * upper half; for an odd number of values the median itself belongs to neither half. Q1 is the median of
 * the lower half, Q3 the median of the upper half. Frequencies behave exactly like repeated values.
 */
object Stats {

    private val TWO = CalcValue.ofLong(2)

    private fun fail(kind: ErrorKind, message: String): Nothing = throw CalcException(kind, message)

    // ------------------------------------------------------------------ input

    /**
     * Splits [text] on commas, semicolons and line breaks (and, when a piece is not one valid expression,
     * on spaces) and evaluates every entry, so `1/2`, `3.5e2` and `sqrt(4)` all work.
     */
    fun parseList(text: String, ctx: EvalContext = EvalContext()): List<CalcValue> {
        val out = ArrayList<CalcValue>()
        for (piece in text.split(',', ';', '\n', '\r')) {
            val trimmed = piece.trim()
            if (trimmed.isEmpty()) continue
            val whole = Calculator.evaluate(trimmed, ctx)
            if (whole is EvalOutcome.Success) {
                out.add(whole.result.value)
                continue
            }
            val parts = trimmed.split(Regex("\\s+"))
            if (parts.size == 1) {
                fail(ErrorKind.SYNTAX, "Entry ${out.size + 1} ('$trimmed'): ${(whole as EvalOutcome.Failure).error.message}")
            }
            for (part in parts) {
                when (val outcome = Calculator.evaluate(part, ctx)) {
                    is EvalOutcome.Success -> out.add(outcome.result.value)
                    is EvalOutcome.Failure ->
                        fail(ErrorKind.SYNTAX, "Entry ${out.size + 1} ('$part'): ${outcome.error.message}")
                }
            }
        }
        return out
    }

    // ------------------------------------------------------------------ one variable

    fun oneVar(xs: List<CalcValue>, freqs: List<CalcValue>? = null): OneVarStats {
        if (xs.isEmpty()) fail(ErrorKind.EMPTY, "Enter at least one data value")
        return compute(xs, weights(xs.size, freqs))
    }

    /** Frequencies, or weight 1 for each value; checks that there is one frequency per value and each is positive. */
    internal fun weights(count: Int, freqs: List<CalcValue>?): List<CalcValue> {
        if (freqs == null) return List(count) { CalcValue.ONE }
        if (freqs.size != count) {
            fail(ErrorKind.INVALID_ARGUMENT, "There are ${freqs.size} frequencies but $count data values")
        }
        freqs.forEachIndexed { i, f ->
            if (f.signum <= 0) fail(ErrorKind.DOMAIN, "Frequency of entry ${i + 1} must be greater than 0")
        }
        return freqs
    }

    private class Group(val value: CalcValue, val weight: CalcValue)

    private fun compute(xs: List<CalcValue>, w: List<CalcValue>): OneVarStats {
        var n = CalcValue.ZERO
        var sum = CalcValue.ZERO
        var sumSq = CalcValue.ZERO
        for (i in xs.indices) {
            n = Arith.add(n, w[i])
            sum = Arith.add(sum, Arith.mul(w[i], xs[i]))
            sumSq = Arith.add(sumSq, Arith.mul(w[i], Arith.mul(xs[i], xs[i])))
        }
        val mean = Arith.div(sum, n)
        var ss = CalcValue.ZERO
        for (i in xs.indices) {
            val d = Arith.sub(xs[i], mean)
            ss = Arith.add(ss, Arith.mul(w[i], Arith.mul(d, d)))
        }
        val variancePop = Arith.div(ss, n)
        val hasSample = Arith.compare(n, CalcValue.ONE) > 0
        val varianceSample = if (hasSample) Arith.div(ss, Arith.sub(n, CalcValue.ONE)) else null

        // Sorted, merged groups of equal values.
        val order = xs.indices.sortedWith { a, b -> Arith.compare(xs[a], xs[b]) }
        val groups = ArrayList<Group>()
        for (i in order) {
            val last = groups.lastOrNull()
            if (last != null && Arith.compare(last.value, xs[i]) == 0) {
                groups[groups.size - 1] = Group(last.value, Arith.add(last.weight, w[i]))
            } else {
                groups.add(Group(xs[i], w[i]))
            }
        }

        val min = groups.first().value
        val max = groups.last().value
        val median = weightedMedian(groups)
        val oddInteger = n.exact?.let { it.isInteger && it.num.testBit(0) } == true
        val lowerTotal = if (oddInteger) Arith.div(Arith.sub(n, CalcValue.ONE), TWO) else Arith.div(n, TWO)
        val q1: CalcValue
        val q3: CalcValue
        if (lowerTotal.isZero) {
            q1 = min
            q3 = max
        } else {
            q1 = weightedMedian(truncate(groups, lowerTotal))
            q3 = weightedMedian(truncate(groups.asReversed(), lowerTotal).asReversed())
        }

        val maxWeight = groups.map { it.weight }.reduce { a, b -> if (Arith.compare(b, a) > 0) b else a }
        val modes = if (Arith.compare(maxWeight, CalcValue.ONE) > 0) {
            groups.filter { Arith.compare(it.weight, maxWeight) == 0 }.map { it.value }
        } else emptyList()

        return OneVarStats(
            n = n,
            sum = sum,
            sumSq = sumSq,
            mean = mean,
            variancePop = variancePop,
            sdPop = Arith.sqrt(variancePop),
            varianceSample = varianceSample,
            sdSample = varianceSample?.let { Arith.sqrt(it) },
            min = min,
            max = max,
            median = median,
            q1 = q1,
            q3 = q3,
            modes = modes,
            range = Arith.sub(max, min)
        )
    }

    /** Median of weighted, ascending groups: the value where the running weight passes half the total. */
    private fun weightedMedian(groups: List<Group>): CalcValue {
        var total = CalcValue.ZERO
        for (g in groups) total = Arith.add(total, g.weight)
        val half = Arith.div(total, TWO)
        var cumulative = CalcValue.ZERO
        for (i in groups.indices) {
            cumulative = Arith.add(cumulative, groups[i].weight)
            val c = Arith.compare(cumulative, half)
            if (c > 0) return groups[i].value
            if (c == 0) {
                val next = groups.getOrNull(i + 1) ?: return groups[i].value
                return Arith.div(Arith.add(groups[i].value, next.value), TWO)
            }
        }
        return groups.last().value
    }

    /** The first [limit] units of weight of [groups] (the last group may be cut short). */
    private fun truncate(groups: List<Group>, limit: CalcValue): List<Group> {
        val out = ArrayList<Group>()
        var taken = CalcValue.ZERO
        for (g in groups) {
            val room = Arith.sub(limit, taken)
            if (room.signum <= 0) break
            val used = if (Arith.compare(g.weight, room) <= 0) g.weight else room
            out.add(Group(g.value, used))
            taken = Arith.add(taken, used)
        }
        return out
    }

    // ------------------------------------------------------------------ two variables

    fun twoVar(xs: List<CalcValue>, ys: List<CalcValue>, freqs: List<CalcValue>? = null): TwoVarStats {
        if (xs.isEmpty()) fail(ErrorKind.EMPTY, "Enter at least one data pair")
        if (xs.size != ys.size) {
            fail(ErrorKind.INVALID_ARGUMENT, "There are ${xs.size} x values but ${ys.size} y values")
        }
        val w = weights(xs.size, freqs)
        val sx = compute(xs, w)
        val sy = compute(ys, w)
        var sumXY = CalcValue.ZERO
        var sxx = CalcValue.ZERO
        var syy = CalcValue.ZERO
        var sxy = CalcValue.ZERO
        for (i in xs.indices) {
            val dx = Arith.sub(xs[i], sx.mean)
            val dy = Arith.sub(ys[i], sy.mean)
            sumXY = Arith.add(sumXY, Arith.mul(w[i], Arith.mul(xs[i], ys[i])))
            sxx = Arith.add(sxx, Arith.mul(w[i], Arith.mul(dx, dx)))
            syy = Arith.add(syy, Arith.mul(w[i], Arith.mul(dy, dy)))
            sxy = Arith.add(sxy, Arith.mul(w[i], Arith.mul(dx, dy)))
        }
        val spread = Arith.mul(sxx, syy)
        val r = if (spread.isZero) null else Arith.div(sxy, Arith.sqrt(spread))
        val hasSample = Arith.compare(sx.n, CalcValue.ONE) > 0
        return TwoVarStats(
            x = sx,
            y = sy,
            sumXY = sumXY,
            covariancePop = Arith.div(sxy, sx.n),
            covarianceSample = if (hasSample) Arith.div(sxy, Arith.sub(sx.n, CalcValue.ONE)) else null,
            r = r
        )
    }

    // ------------------------------------------------------------------ regression

    fun regression(
        type: RegressionType,
        xs: List<CalcValue>,
        ys: List<CalcValue>,
        freqs: List<CalcValue>? = null
    ): Regression {
        if (xs.isEmpty()) fail(ErrorKind.EMPTY, "Enter at least one data pair")
        if (xs.size != ys.size) {
            fail(ErrorKind.INVALID_ARGUMENT, "There are ${xs.size} x values but ${ys.size} y values")
        }
        return RegressionEngine.fit(type, xs, ys, weights(xs.size, freqs))
    }
}

package com.tapecalc.feature.basic

/**
 * Decides which earlier lines fit above the line being worked on. Pure arithmetic, so it is easy to test.
 */
object HistoryFit {
    /**
     * [heights] are the measured heights of the rows, oldest first; [gap] is the space between two rows and [limit] the
     * height available. Returns the index of the first row to show: the rows from there to the end all fit completely
     * and are the newest ones, so the oldest rows are dropped, never half of a row. Returns `heights.size` when not even
     * the newest row fits. A negative [limit] (unbounded height is `Int.MAX_VALUE`) fits nothing.
     */
    fun firstVisible(heights: List<Int>, gap: Int, limit: Int): Int {
        var used = 0
        var first = heights.size
        for (i in heights.indices.reversed()) {
            val need = heights[i] + if (first < heights.size) gap else 0
            if (used.toLong() + need > limit) break
            used += need
            first = i
        }
        return first
    }

    /** Total height of the rows from [first] to the end, including the gaps between them. */
    fun usedHeight(heights: List<Int>, gap: Int, first: Int): Int {
        val count = heights.size - first
        if (count <= 0) return 0
        return heights.subList(first, heights.size).sum() + gap * (count - 1)
    }
}

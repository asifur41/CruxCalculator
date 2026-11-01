package com.tapecalc.core.math

/**
 * "Did you mean …?" support: finds the known word that is closest to a mistyped one.
 *
 * Uses the optimal-string-alignment edit distance, which counts a swap of two neighbouring letters
 * ("sni" for "sin") as a single mistake. Very short words get no suggestion, because "x" being one
 * letter away from "e" would only be noise.
 */
object Suggestions {

    /** Words shorter than this never get a suggestion. */
    private const val MIN_WORD_LENGTH = 3

    /** Allowed number of mistakes: 1 for words of 3-4 letters, 2 for longer words. */
    private fun maxDistance(length: Int): Int = if (length <= 4) 1 else 2

    /**
     * Returns the candidate (in its original spelling) closest to [word], or null when nothing is
     * close enough. An exact match (ignoring case) is not a suggestion. When several candidates are
     * equally close, the one listed first wins, so callers put the words they want preferred first
     * (the person's own variables, then common functions).
     */
    fun closest(word: String, candidates: Collection<String>): String? {
        val w = word.trim().lowercase()
        if (w.length < MIN_WORD_LENGTH) return null
        val limit = maxDistance(w.length)
        var best: String? = null
        var bestDistance = Int.MAX_VALUE
        for (candidate in candidates) {
            val c = candidate.lowercase()
            if (c == w) continue
            if (Math.abs(c.length - w.length) > limit) continue
            val d = distance(w, c)
            if (d <= limit && d < bestDistance) {
                best = candidate
                bestDistance = d
            }
        }
        return best
    }

    /** Optimal string alignment distance (insert, delete, replace, swap of neighbours). */
    internal fun distance(a: String, b: String): Int {
        val n = a.length
        val m = b.length
        if (n == 0) return m
        if (m == 0) return n
        val d = Array(n + 1) { IntArray(m + 1) }
        for (i in 0..n) d[i][0] = i
        for (j in 0..m) d[0][j] = j
        for (i in 1..n) {
            for (j in 1..m) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                var v = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + cost)
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) {
                    v = minOf(v, d[i - 2][j - 2] + 1)
                }
                d[i][j] = v
            }
        }
        return d[n][m]
    }

    /** "Unknown name 'sinn'" becomes "Unknown name 'sinn'. Did you mean 'sin'?" when a suggestion exists. */
    fun withHint(message: String, suggestion: String?): String =
        if (suggestion == null) message else "$message. Did you mean '$suggestion'?"
}

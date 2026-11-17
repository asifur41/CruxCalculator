package com.tapecalc.feature.basic

/**
 * A place on the input line where the cursor can be put by touch: a piece of text, or an empty box of a template.
 *
 * The screen fills one of these in for every drawn piece (position and size on screen, and where each character
 * boundary falls) and [CaretHit.pick] turns a finger position into a cursor index. No Android types, so it is
 * unit tested without a device.
 */
class CaretTarget {
    /** Index in the whole input text of this piece's first character. */
    var start: Int = 0

    /** Characters in the piece; 0 for an empty template box (the only position it offers is [start]). */
    var length: Int = 0

    var left: Float = 0f
    var top: Float = 0f
    var right: Float = 0f
    var bottom: Float = 0f

    /** True once the piece has been placed on screen. */
    var placed: Boolean = false

    /**
     * Horizontal position of each character boundary, `length + 1` values measured from [left] (value 0 is the left edge
     * of the first character, value `length` the right edge of the last). Null until the text has been laid out.
     */
    var boundaries: FloatArray? = null

    fun place(l: Float, t: Float, r: Float, b: Float) {
        left = l; top = t; right = r; bottom = b
        placed = true
    }
}

object CaretHit {

    /**
     * Distance from [v] to the range [lo, hi]; 0 when it lies inside.
     */
    private fun gap(v: Float, lo: Float, hi: Float): Float = when {
        v < lo -> lo - v
        v > hi -> v - hi
        else -> 0f
    }

    /**
     * The cursor index for a finger at ([x], [y]), in the same coordinates as the targets, or null when there is nothing to
     * point at. The nearest piece wins; being on the wrong row costs more than being off to the side, so a touch beside
     * a row still picks that row. Inside a text piece the nearest character boundary is chosen, so a touch on the left half
     * of a digit puts the cursor before it and on the right half after it. A touch past either end of the line gives the
     * first or last position.
     */
    fun pick(targets: Collection<CaretTarget>, x: Float, y: Float): Int? {
        var best: CaretTarget? = null
        var bestScore = Float.MAX_VALUE
        for (t in targets) {
            if (!t.placed) continue
            val dx = gap(x, t.left, t.right)
            val dy = gap(y, t.top, t.bottom)
            val score = dx * dx + 4f * dy * dy
            if (score < bestScore) {
                bestScore = score
                best = t
            }
        }
        val t = best ?: return null
        if (t.length <= 0) return t.start
        val local = x - t.left
        val marks = t.boundaries
        val index = if (marks != null && marks.size == t.length + 1) {
            var nearest = 0
            var nearestDist = Float.MAX_VALUE
            for (i in marks.indices) {
                val d = kotlin.math.abs(marks[i] - local)
                if (d < nearestDist) {
                    nearestDist = d
                    nearest = i
                }
            }
            nearest
        } else {
            // layout not reported yet: assume equal-width characters
            val width = t.right - t.left
            if (width <= 0f) 0 else Math.round((local / width) * t.length).coerceIn(0, t.length)
        }
        return t.start + index
    }
}

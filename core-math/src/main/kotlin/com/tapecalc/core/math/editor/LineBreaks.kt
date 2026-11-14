package com.tapecalc.core.math.editor

/**
 * Decides where the input line may be broken onto a second row.
 *
 * The screen draws the line as a flow of small pieces and starts a new row between two pieces. A run such as
 * `78999999999999999999` or `ans+ans+ans+ans+ans` used to be ONE piece, so nothing could break inside it and it was
 * cut off at the edge of the screen. [chunks] cuts the text into pieces that are small enough to wrap.
 *
 * Joining the chunks always gives back exactly the original text, so the cursor position and the text itself are
 * never changed. Pure Kotlin, so it is unit tested without Android.
 */
object LineBreaks {

    /** Longest stretch of digits that stays together. A row can end up to this many digits short of the edge. */
    const val DEFAULT_DIGIT_RUN = 4

    /** A name such as `floor` or `lcm` is kept whole up to this length. */
    const val DEFAULT_WORD_RUN = 10

    /** Characters that must never start a row: they stay on the row of what comes before them. */
    private const val STICK_TO_PREVIOUS = "),!%°′″'²³¹⁻"

    private enum class Kind { DIGITS, LETTERS, OTHER }

    private fun kindOf(c: Char): Kind = when {
        c.isDigit() || c == '.' -> Kind.DIGITS
        c.isLetter() -> Kind.LETTERS
        else -> Kind.OTHER
    }

    /**
     * Splits [text] into breakable chunks.
     *
     * - digits (and a decimal point) are cut into groups of at most [digitRun];
     * - letters (function names, `ans`, variables) stay whole up to [wordRun];
     * - every other character, for example `+` or `×`, is a chunk of its own;
     * - `(` stays on the row of the name before it (`sin(`), and `)`, `,`, `!`, `%`, degree marks, powers and spaces
     *   stay on the row of what is before them, so a row never starts with a closing bracket.
     */
    fun chunks(text: String, digitRun: Int = DEFAULT_DIGIT_RUN, wordRun: Int = DEFAULT_WORD_RUN): List<String> {
        require(digitRun >= 1 && wordRun >= 1) { "run lengths must be at least 1" }
        if (text.isEmpty()) return emptyList()

        val out = ArrayList<String>()
        var i = 0
        while (i < text.length) {
            val c = text[i]
            val kind = kindOf(c)
            if (kind == Kind.OTHER) {
                if (out.isNotEmpty() && (c == '(' || c.isWhitespace() || c in STICK_TO_PREVIOUS)) {
                    out[out.size - 1] = out[out.size - 1] + c
                } else {
                    out.add(c.toString())
                }
                i++
                continue
            }
            var end = i
            while (end < text.length && kindOf(text[end]) == kind) end++
            val limit = if (kind == Kind.DIGITS) digitRun else wordRun
            var start = i
            while (start < end) {
                val stop = minOf(start + limit, end)
                out.add(text.substring(start, stop))
                start = stop
            }
            i = end
        }
        return out
    }
}

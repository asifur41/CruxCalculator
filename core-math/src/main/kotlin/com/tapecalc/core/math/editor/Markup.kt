package com.tapecalc.core.math.editor

/**
 * The calculator's input line is plain text, but a few "templates" (a fraction with a box above and a box
 * below, log with a base box, an integral with limits...) need structure. They are written into the same text
 * with private-use marker characters that the user can never type:
 *
 *     FRAC  a  SEP  b  END            a fraction  a / b
 *
 * Every template has an OPEN marker that says which kind it is, SEP markers between its boxes ("slots") and
 * an END marker. Templates can be nested. [toEngine] turns the marked text into the ordinary text the math
 * engine understands; [parse] turns it into a tree the screen can draw.
 */
object Mk {
    const val FRAC = ''
    const val LOGB = ''
    const val NROOT = ''
    const val INTEG = ''
    const val SUM = ''
    const val PROD = ''
    const val DERIV = ''
    const val MIXED = ''
    const val SEP = ''
    const val END = ''

    /** Number of boxes in each template, in typing order. */
    private val SLOT_COUNT: Map<Char, Int> = mapOf(
        FRAC to 2,    // numerator, denominator
        LOGB to 2,    // base, argument
        NROOT to 2,   // index, radicand
        INTEG to 3,   // body, lower limit, upper limit
        SUM to 3,     // body, from, to
        PROD to 3,    // body, from, to
        DERIV to 2,   // body, point
        MIXED to 3    // whole part, numerator, denominator
    )

    fun isOpen(c: Char): Boolean = SLOT_COUNT.containsKey(c)
    fun isMarker(c: Char): Boolean = c == SEP || c == END || isOpen(c)
    fun slotCount(open: Char): Int = SLOT_COUNT[open] ?: 0

    /** The empty template of [open]: its marker, empty boxes and END. */
    fun emptyTemplate(open: Char): String =
        open + (SEP.toString()).repeat((slotCount(open) - 1).coerceAtLeast(0)) + END

    /** True if the text contains any template marker. */
    fun hasMarkers(text: String): Boolean = text.any { isMarker(it) }

    /** Removes every marker (used for plain-text copies where the structure does not matter). */
    fun stripMarkers(text: String): String = text.filterNot { isMarker(it) }
}

// ---------------------------------------------------------------------------------------------------------
// Tree for drawing
// ---------------------------------------------------------------------------------------------------------

sealed interface MathNode

/** A stretch of plain text. [start] is the index of its first character in the whole text (may be empty). */
data class RunNode(val text: String, val start: Int) : MathNode {
    val end: Int get() = start + text.length
}

/** One box of a template; its content spans the text range [start, end). */
data class SlotNode(val nodes: List<MathNode>, val start: Int, val end: Int) {
    val isEmpty: Boolean get() = start == end
}

/** A template: [open] is its kind, [openIndex] and [endIndex] are the positions of its OPEN and END markers. */
data class TemplateNode(val open: Char, val slots: List<SlotNode>, val openIndex: Int, val endIndex: Int) : MathNode

object MathTree {

    /**
     * Parses [text] into runs and templates. The result always alternates run, template, run, ... starting and
     * ending with a (possibly empty) run, so a cursor position always belongs to exactly one run.
     * Broken marker structure (which the editor never produces) is tolerated: stray markers are dropped.
     */
    fun parse(text: String): List<MathNode> = Parser(text).parseRoot()

    /** Which slot of which template holds [cursor]? Innermost first; null when the cursor is outside templates. */
    fun slotAt(nodes: List<MathNode>, cursor: Int): Pair<TemplateNode, Int>? {
        for (node in nodes) {
            if (node !is TemplateNode) continue
            if (cursor <= node.openIndex || cursor > node.endIndex) continue
            for ((i, slot) in node.slots.withIndex()) {
                if (cursor in slot.start..slot.end) {
                    return slotAt(slot.nodes, cursor) ?: (node to i)
                }
            }
        }
        return null
    }

    private class Parser(val s: String) {
        var i = 0

        fun parseRoot(): List<MathNode> {
            val nodes = ArrayList<MathNode>()
            parseSequence(nodes, stopAtSepOrEnd = false)
            return nodes
        }

        /** Reads runs and templates until the end of text, or (inside a template) until SEP or END. */
        private fun parseSequence(out: MutableList<MathNode>, stopAtSepOrEnd: Boolean) {
            var runStart = i
            val run = StringBuilder()
            fun flush() {
                out.add(RunNode(run.toString(), runStart))
                run.setLength(0)
            }
            while (i < s.length) {
                val c = s[i]
                when {
                    stopAtSepOrEnd && (c == Mk.SEP || c == Mk.END) -> {
                        flush()
                        return
                    }
                    c == Mk.SEP || c == Mk.END -> i++ // stray marker at top level: ignore
                    Mk.isOpen(c) -> {
                        flush()
                        out.add(parseTemplate())
                        runStart = i
                    }
                    else -> {
                        run.append(c)
                        i++
                    }
                }
            }
            flush()
        }

        private fun parseTemplate(): TemplateNode {
            val openIndex = i
            val open = s[i]
            i++
            val slots = ArrayList<SlotNode>()
            val want = Mk.slotCount(open)
            while (true) {
                val slotStart = i
                val nodes = ArrayList<MathNode>()
                parseSequence(nodes, stopAtSepOrEnd = true)
                slots.add(SlotNode(nodes, slotStart, i))
                if (i >= s.length) break // unterminated: treat the end of text as END
                val c = s[i]
                i++
                if (c == Mk.END) break
                // c == SEP: another slot follows
            }
            while (slots.size < want) slots.add(SlotNode(listOf(RunNode("", i)), i, i))
            val endIndex = if (i > 0 && i <= s.length && s[i - 1] == Mk.END) i - 1 else i
            return TemplateNode(open, slots, openIndex, endIndex)
        }
    }
}

// ---------------------------------------------------------------------------------------------------------
// Conversion to engine text
// ---------------------------------------------------------------------------------------------------------

/** The engine text for a marked input line, and whether any box was left empty. */
data class EngineText(val text: String, val hasBlank: Boolean)

object MathEngineText {

    private val SIMPLE = Regex("[A-Za-z0-9.π_]+")

    /** Wraps [s] in brackets unless it is already a single number or name. */
    private fun wrap(s: String): String = if (s.isEmpty() || SIMPLE.matches(s)) s else "($s)"

    private fun endsWithOperand(sb: CharSequence): Boolean {
        val c = sb.lastOrNull() ?: return false
        return c.isLetterOrDigit() || c == ')' || c == '.' || c == '%' || c == '!' || c == 'π'
    }

    /** Text being built, plus whether the last thing appended was a whole template. */
    private class Out {
        val sb = StringBuilder()
        var lastWasTemplate = false
        var blank = false
    }

    fun convert(text: String): EngineText {
        if (!Mk.hasMarkers(text)) return EngineText(text, false)
        val out = Out()
        render(MathTree.parse(text), out)
        return EngineText(out.sb.toString(), out.blank)
    }

    private fun render(nodes: List<MathNode>, out: Out) {
        for (node in nodes) {
            when (node) {
                is RunNode -> {
                    val t = node.text
                    // an operand right after a finished template needs an explicit multiplication: (1÷2)3
                    if (t.isNotEmpty() && out.lastWasTemplate && (t[0].isLetterOrDigit() || t[0] == '(' || t[0] == 'π')) {
                        out.sb.append('×')
                    }
                    out.sb.append(t)
                    if (t.isNotEmpty()) out.lastWasTemplate = false
                }
                is TemplateNode -> {
                    if (endsWithOperand(out.sb)) out.sb.append('×')
                    val parts = node.slots.map { slot ->
                        val inner = Out()
                        render(slot.nodes, inner)
                        val t = inner.sb.toString().trim()
                        if (t.isEmpty() || inner.blank) out.blank = true
                        t
                    }
                    out.sb.append(build(node.open, parts))
                    out.lastWasTemplate = true
                }
            }
        }
    }

    private fun build(open: Char, p: List<String>): String = when (open) {
        Mk.FRAC -> "(${wrap(p[0])}÷${wrap(p[1])})"
        Mk.LOGB -> "log(${p[1]}, ${p[0]})"
        Mk.NROOT -> "root(${p[1]}, ${p[0]})"
        Mk.INTEG -> "integral(${p[0]}, x, ${p[1]}, ${p[2]})"
        Mk.SUM -> "sum(${p[0]}, x, ${p[1]}, ${p[2]})"
        Mk.PROD -> "prod(${p[0]}, x, ${p[1]}, ${p[2]})"
        Mk.DERIV -> "deriv(${p[0]}, x, ${p[1]})"
        Mk.MIXED -> "(${wrap(p[0])}+${wrap(p[1])}÷${wrap(p[2])})"
        else -> ""
    }
}

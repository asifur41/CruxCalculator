package com.tapecalc.pro.presentation

import com.tapecalc.core.math.editor.EngineText
import com.tapecalc.core.math.editor.MathEngineText
import com.tapecalc.core.math.editor.MathTree
import com.tapecalc.core.math.editor.Mk
import com.tapecalc.core.math.editor.TemplateNode

/** The input line and where the cursor is. [cursor] counts characters of [text]. */
data class EditorState(val text: String = "", val cursor: Int = text.length) {
    fun withCursor(position: Int) = copy(cursor = position.coerceIn(0, text.length))
}

/**
 * Cursor-aware editing of the input line (no Android types, unit-tested). Typing, deleting and moving all
 * happen at the cursor. Typing keeps the keypad rules of [InputEditor] (operators replace each other, one
 * decimal point per number...) but applies them only to the box the cursor is in, so a template's boxes
 * behave like separate little expressions.
 */
object MathEditor {

    private const val MOD = " mod "

    /** What the EXP key types. With nothing in front of it (or an operator) there is nothing to multiply, so it types "10^(". */
    const val EXP = "×10^("
    private val WORD_AT_END = Regex("[A-Za-z]+\\(?$")
    private val WORD_AT_START = Regex("^[A-Za-z]+\\(?")
    private val OPERAND_AT_END = Regex("(\\d+\\.?\\d*|\\.\\d+|ans|pi|π)$")

    fun fromText(text: String) = EditorState(text, text.length)

    /** The engine text for [state], and whether a template box was left empty. */
    fun engineText(state: EditorState): EngineText = MathEngineText.convert(state.text)

    // ------------------------------------------------------------------ geometry helpers

    /** Index where the box containing [cursor] starts (0 when the cursor is not inside a template). */
    internal fun slotStart(text: String, cursor: Int): Int {
        var depth = 0
        var i = cursor.coerceIn(0, text.length) - 1
        while (i >= 0) {
            val c = text[i]
            when {
                c == Mk.END -> depth++
                Mk.isOpen(c) -> if (depth == 0) return i + 1 else depth--
                c == Mk.SEP -> if (depth == 0) return i + 1
            }
            i--
        }
        return 0
    }

    private fun enclosing(text: String, cursor: Int): Pair<TemplateNode, Int>? =
        MathTree.slotAt(MathTree.parse(text), cursor)

    // ------------------------------------------------------------------ typing

    /**
     * Types [token] at the cursor. With [afterResult] the previous answer is on screen: an operator continues
     * from it, anything else starts a new line.
     */
    fun insert(s: EditorState, token: String, afterResult: Boolean): EditorState {
        val text = s.text
        val c = s.cursor.coerceIn(0, text.length)
        if (afterResult) {
            val r = InputEditor.insert(text, if (token == EXP) "ans$EXP" else token, afterResult = true)
            return EditorState(r, r.length)
        }
        val ss = slotStart(text, c)
        val local = text.substring(ss, c)
        val tok = if (token == EXP && !endsWithOperand(local)) "10^(" else token
        val r = InputEditor.insert(local, tok, afterResult = false)
        val out = text.substring(0, ss) + r + text.substring(c)
        if (out.length > InputEditor.MAX_LENGTH) return s
        return EditorState(out, ss + r.length)
    }

    private fun endsWithOperand(text: String): Boolean {
        val ch = text.lastOrNull() ?: return false
        return ch.isLetterOrDigit() || ch == ')' || ch == '.' || ch == '%' || ch == '!' || ch == 'π' || ch == Mk.END
    }

    /** Inserts the template of kind [open] (one of the [Mk] constants); the cursor lands in its first box. */
    fun insertTemplate(s: EditorState, open: Char, afterResult: Boolean): EditorState {
        var text = s.text
        var c = s.cursor.coerceIn(0, text.length)
        if (afterResult) {
            // a fraction typed right after "=" takes the answer as its numerator, anything else starts fresh
            text = if (open == Mk.FRAC) "ans" else ""
            c = text.length
        }
        val ss = slotStart(text, c)
        val local = text.substring(ss, c)
        val grab = if (open == Mk.FRAC) OPERAND_AT_END.find(local)?.value.orEmpty() else ""
        val before = text.substring(0, c - grab.length)
        val after = text.substring(c)
        val template = Mk.emptyTemplate(open)
        val out: String
        val cursor: Int
        if (grab.isEmpty()) {
            out = before + template + after
            cursor = before.length + 1
        } else {
            // Mk.FRAC + grab + SEP + END: the cursor goes below the line, ready for the denominator
            out = before + Mk.FRAC + grab + Mk.SEP + Mk.END + after
            cursor = before.length + 1 + grab.length + 1
        }
        if (out.length > InputEditor.MAX_LENGTH) return s
        return EditorState(out, cursor)
    }

    fun toggleSign(s: EditorState): EditorState {
        val text = s.text
        val c = s.cursor.coerceIn(0, text.length)
        val ss = slotStart(text, c)
        val local = text.substring(ss, c)
        val r = InputEditor.toggleSign(local)
        val out = text.substring(0, ss) + r + text.substring(c)
        if (out.length > InputEditor.MAX_LENGTH) return s
        return EditorState(out, ss + r.length)
    }

    // ------------------------------------------------------------------ deleting

    fun backspace(s: EditorState): EditorState {
        val text = s.text
        val c = s.cursor.coerceIn(0, text.length)
        if (c == 0) return s
        val prev = text[c - 1]
        when {
            // right edge of a template: step inside it instead of destroying it
            prev == Mk.END -> return s.copy(cursor = c - 1)
            prev == Mk.SEP || Mk.isOpen(prev) -> {
                val hit = enclosing(text, c)
                if (hit != null) {
                    val (template, _) = hit
                    return if (template.slots.all { it.isEmpty }) {
                        // nothing typed in it: remove the whole template
                        EditorState(text.removeRange(template.openIndex, template.endIndex + 1), template.openIndex)
                    } else {
                        // something is typed in it: just step out / to the previous box
                        s.copy(cursor = c - 1)
                    }
                }
                return s.copy(cursor = c - 1)
            }
        }
        val ss = slotStart(text, c)
        val local = text.substring(ss, c)
        val shorter = InputEditor.backspace(local)
        return EditorState(text.substring(0, ss) + shorter + text.substring(c), ss + shorter.length)
    }

    // ------------------------------------------------------------------ moving

    fun moveLeft(s: EditorState): EditorState {
        val text = s.text
        var pos = s.cursor.coerceIn(0, text.length)
        if (pos == 0) return s
        val prefix = text.substring(0, pos)
        pos -= when {
            prefix.endsWith(MOD) -> MOD.length
            else -> WORD_AT_END.find(prefix)?.value?.length ?: 1
        }
        // skip the separating space that is inserted between a word and the token before it (not the spaces of " mod ")
        while (pos > 0 && text[pos - 1] == ' ' && !(pos >= MOD.length && text.startsWith(MOD, pos - MOD.length))) pos--
        return EditorState(text, pos.coerceAtLeast(0))
    }

    fun moveRight(s: EditorState): EditorState {
        val text = s.text
        var pos = s.cursor.coerceIn(0, text.length)
        if (pos >= text.length) return s
        while (pos < text.length && text[pos] == ' ' && !text.startsWith(MOD, pos)) pos++
        if (pos >= text.length) return EditorState(text, text.length)
        pos += when {
            text.startsWith(MOD, pos) -> MOD.length
            text[pos].isLetter() -> WORD_AT_START.find(text.substring(pos))?.value?.length ?: 1
            else -> 1
        }
        return EditorState(text, pos.coerceAtMost(text.length))
    }

    fun moveHome(s: EditorState) = EditorState(s.text, 0)
    fun moveEnd(s: EditorState) = EditorState(s.text, s.text.length)

    /** Top-to-bottom order of a template's boxes as drawn, as indices into its slots. */
    private fun verticalOrder(open: Char): List<Int> = when (open) {
        Mk.INTEG, Mk.SUM, Mk.PROD -> listOf(2, 0, 1) // upper limit, body, lower limit
        Mk.LOGB -> listOf(1, 0)                      // argument, base (written low)
        Mk.MIXED -> listOf(1, 2)                     // numerator, denominator
        else -> listOf(0, 1)
    }

    /**
     * Up / down inside a template: from the numerator to the denominator and back, from an integral's body to
     * its limits... Returns null when there is no box in that direction (the caller can then use the key for
     * something else, such as recalling an earlier line).
     */
    fun moveVertical(s: EditorState, up: Boolean): EditorState? {
        val c = s.cursor.coerceIn(0, s.text.length)
        var hit = enclosing(s.text, c) ?: return null
        // walk outwards until a template has a box in the wanted direction
        while (true) {
            val (template, index) = hit
            val order = verticalOrder(template.open)
            val at = order.indexOf(index)
            val target = order.getOrNull(if (up) at - 1 else at + 1)
            if (at >= 0 && target != null && target in template.slots.indices) {
                val from = template.slots[index]
                val to = template.slots[target]
                val offset = (c - from.start).coerceIn(0, to.end - to.start)
                return EditorState(s.text, to.start + offset)
            }
            hit = enclosing(s.text, template.openIndex) ?: return null
        }
    }
}

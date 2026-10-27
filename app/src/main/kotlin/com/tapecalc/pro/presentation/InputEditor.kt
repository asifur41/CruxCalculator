package com.tapecalc.pro.presentation

/**
 * Pure text-editing rules of the calculator keypad (no Android types, fully unit-tested).
 * The expression is plain text such as "2+3×sin(30)"; the math engine understands × ÷ − directly.
 */
object InputEditor {
    const val MAX_LENGTH = 300

    private const val OPERATOR_CHARS = "+−×÷^"
    private val FUNCTION_AT_END = Regex("[A-Za-z][A-Za-z0-9]*\\($")
    private const val MOD = " mod "

    /** Tokens that continue from the previous result (typing "+" after "=" means ans + ...). */
    private fun continuesResult(token: String): Boolean {
        val t = token.trimStart()
        return t.isNotEmpty() && (t[0] in "+−×÷^%!" || t.startsWith("mod"))
    }

    private fun isOperator(c: Char?) = c != null && c in OPERATOR_CHARS

    private fun endsWithOperand(text: String): Boolean {
        val c = text.lastOrNull() ?: return false
        return c.isLetterOrDigit() || c == ')' || c == '.' || c == '%' || c == '!' || c == 'π'
    }

    private fun openParens(text: String): Int {
        var open = 0
        for (c in text) {
            if (c == '(') open++ else if (c == ')' && open > 0) open--
        }
        return open
    }

    /** Applies [token] to [current]; when [afterResult] the previous answer has just been shown. */
    fun insert(current: String, token: String, afterResult: Boolean): String {
        var base = current
        if (afterResult) base = if (continuesResult(token)) "ans" else ""
        val result = append(base, token)
        return if (result.length > MAX_LENGTH) base else result
    }

    private fun append(base: String, token: String): String {
        if (token.isEmpty()) return base
        val last = base.lastOrNull()

        // decimal point
        if (token == ".") {
            if (last == null || !(last.isDigit() || last == '.')) return base + "0."
            val number = base.takeLastWhile { it.isDigit() || it == '.' }
            return if (number.contains('.')) base else "$base."
        }

        // digits
        if (token.all { it.isDigit() }) {
            val needsSpace = last != null && last.isLetter()
            if (token == "00" && (last == null || !(last.isDigit() || last == '.'))) return base + "0"
            return if (needsSpace) "$base $token" else base + token
        }

        // binary operators (and the modulo keyword)
        if (token.length == 1 && token[0] in OPERATOR_CHARS || token == MOD) {
            val op = token
            if (base.isEmpty()) return if (op == "−" || op == "+") op else base
            val trimmed = base.trimEnd()
            val tail = trimmed.lastOrNull()
            if (trimmed.endsWith("mod")) return trimmed.removeSuffix("mod").trimEnd() + (if (op == MOD) MOD else op)
            if (tail == '(') return if (op == "−" || op == "+") base + op else base
            if (isOperator(tail)) {
                // allow a sign after × ÷ ^ (for example 5×−3), otherwise replace the operator
                if (op == "−" && tail != null && tail in "×÷^") return base + op
                var cut = trimmed
                while (isOperator(cut.lastOrNull())) cut = cut.dropLast(1)
                return if (cut.isEmpty()) (if (op == "−" || op == "+") op else base) else cut + op
            }
            return base + op
        }

        // postfix operators
        if (token == "%" || token == "!") return if (endsWithOperand(base)) base + token else base
        if (token == "^2" || token == "^3" || token == "^(-1)") return if (endsWithOperand(base)) base + token else base

        // argument separator, only inside an open function call such as log(8, 2)
        if (token == ",") return if (openParens(base) > 0 && endsWithOperand(base)) "$base, " else base

        // closing parenthesis
        if (token == ")") return if (openParens(base) > 0 && last != '(' && !isOperator(last)) base + token else base

        // constants, functions, opening parenthesis
        if (token == "e" && last != null && last.isDigit()) return "$base e"
        if (token.first().isLetter() && last != null && last.isLetter()) return "$base $token"
        return base + token
    }

    fun backspace(current: String): String {
        if (current.isEmpty()) return current
        if (current.endsWith(MOD)) return current.dropLast(MOD.length)
        FUNCTION_AT_END.find(current)?.let { return current.substring(0, it.range.first) }
        var out = current.dropLast(1)
        if (out.endsWith(" ")) out = out.trimEnd()
        return out
    }

    /** ± key: negate the number at the end, or the whole expression. */
    fun toggleSign(current: String): String {
        if (current.isEmpty()) return "−"
        val number = current.takeLastWhile { it.isDigit() || it == '.' }
        if (number.isNotEmpty()) {
            val start = current.length - number.length
            val before = current.substring(0, start)
            val prev = before.lastOrNull()
            if (prev == '−') {
                val beforeSign = before.dropLast(1)
                val signIsUnary = beforeSign.isEmpty() || isOperator(beforeSign.last()) || beforeSign.last() == '('
                if (signIsUnary) return beforeSign + number
            }
            if (prev != null && (prev.isLetter() || prev == ')')) return "−($current)"
            return "$before−$number"
        }
        if (current.startsWith("−") && wrapsEntirely(current.drop(1))) return current.drop(1)
        if (wrapsEntirely(current)) return "−$current"
        return "−($current)"
    }

    /** True when the first "(" of [text] is closed by the very last character. */
    private fun wrapsEntirely(text: String): Boolean {
        if (!text.startsWith("(") || !text.endsWith(")")) return false
        var depth = 0
        for ((i, c) in text.withIndex()) {
            if (c == '(') depth++
            if (c == ')') {
                depth--
                if (depth == 0) return i == text.lastIndex
            }
        }
        return false
    }
}

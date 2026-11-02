package com.tapecalc.core.math

enum class T { NUMBER, IDENT, PLUS, MINUS, STAR, SLASH, CARET, BANG, PERCENT, DEGREE, LPAREN, RPAREN, COMMA, EQUALS, SQRT, EOF }

data class Token(val type: T, val text: String, val pos: Int)

object Tokenizer {

    /** Degrees-minutes-seconds entry such as `2°30'15"`, `45°` or `30'`: every part is optional but one must be there. */
    private val DMS = Regex("""(?:(\d+(?:\.\d+)?|\.\d+)\s*°)?\s*(?:(\d+(?:\.\d+)?|\.\d+)\s*['′])?\s*(?:(\d+(?:\.\d+)?|\.\d+)\s*(?:["″]|''|′′))?""")

    fun tokenize(input: String): List<Token> {
        val out = ArrayList<Token>()
        val n = input.length
        var i = 0
        while (i < n) {
            val c = input[i]
            when {
                c.isWhitespace() || c == ' ' -> i++
                c.isDigit() || (c == '.' && i + 1 < n && input[i + 1].isDigit()) -> {
                    val next = readDms(input, i, out)
                    i = if (next > i) next else readNumber(input, i, out)
                }
                c.isLetter() || c == '_' -> i = readIdent(input, i, out)
                else -> i = readSymbol(input, i, out)
            }
        }
        out.add(Token(T.EOF, "", n))
        return out
    }

    private fun fail(message: String, pos: Int): Nothing =
        throw CalcException(ErrorKind.SYNTAX, message, pos)

    /**
     * Turns `2°30'15"` into the tokens of `(_deg(2) + _min(30) + _sec(15))`, which the evaluator converts to the
     * current angle unit exactly. Returns the index after the entry, or [start] when there is no DMS entry here.
     */
    private fun readDms(s: String, start: Int, out: MutableList<Token>): Int {
        val m = DMS.matchAt(s, start) ?: return start
        val parts = listOf("_deg" to m.groups[1]?.value, "_min" to m.groups[2]?.value, "_sec" to m.groups[3]?.value)
            .filter { it.second != null }
        if (parts.isEmpty()) return start
        out.add(Token(T.LPAREN, "(", start))
        parts.forEachIndexed { index, (name, number) ->
            if (index > 0) out.add(Token(T.PLUS, "+", start))
            out.add(Token(T.IDENT, name, start))
            out.add(Token(T.LPAREN, "(", start))
            out.add(Token(T.NUMBER, number!!, start))
            out.add(Token(T.RPAREN, ")", start))
        }
        out.add(Token(T.RPAREN, ")", start))
        return start + m.value.trimEnd().length
    }

    private fun readNumber(s: String, start: Int, out: MutableList<Token>): Int {
        val n = s.length
        var j = start
        var seenDot = false
        while (j < n && (s[j].isDigit() || s[j] == '.')) {
            if (s[j] == '.') {
                if (seenDot) fail("Invalid number", j)
                seenDot = true
            }
            j++
        }
        // Scientific notation: 1e5, 2.5E-3. A bare "e" (as in 2e) stays the constant e.
        if (j < n && (s[j] == 'e' || s[j] == 'E')) {
            var k = j + 1
            if (k < n && (s[k] == '+' || s[k] == '-' || s[k] == '−')) k++
            if (k < n && s[k].isDigit()) {
                while (k < n && s[k].isDigit()) k++
                j = k
            }
        }
        val text = s.substring(start, j).replace('−', '-')
        out.add(Token(T.NUMBER, text, start))
        return j
    }

    private fun readIdent(s: String, start: Int, out: MutableList<Token>): Int {
        val n = s.length
        var j = start
        while (j < n && (s[j].isLetterOrDigit() || s[j] == '_')) j++
        var text = s.substring(start, j)
        if (text == "π") text = "pi"
        if (text == "φ") text = "phi"
        out.add(Token(T.IDENT, text, start))
        return j
    }

    private fun readSymbol(s: String, i: Int, out: MutableList<Token>): Int {
        val c = s[i]
        fun add(type: T, text: String = c.toString()): Int {
            out.add(Token(type, text, i))
            return i + 1
        }
        return when (c) {
            '+' -> add(T.PLUS)
            '-', '−', '–' -> add(T.MINUS, "-")
            '*', '×', '·', '⋅', '∗' -> {
                if (c == '*' && i + 1 < s.length && s[i + 1] == '*') {
                    out.add(Token(T.CARET, "^", i))
                    i + 2
                } else add(T.STAR, "*")
            }
            '/', '÷' -> add(T.SLASH, "/")
            '^' -> add(T.CARET)
            '!' -> add(T.BANG)
            '%' -> add(T.PERCENT)
            '°' -> add(T.DEGREE)
            '(', '[', '{' -> add(T.LPAREN, "(")
            ')', ']', '}' -> add(T.RPAREN, ")")
            ',', ';' -> add(T.COMMA, ",")
            '=' -> add(T.EQUALS)
            '√' -> add(T.SQRT, "sqrt")
            '²' -> {
                out.add(Token(T.CARET, "^", i)); out.add(Token(T.NUMBER, "2", i)); i + 1
            }
            '³' -> {
                out.add(Token(T.CARET, "^", i)); out.add(Token(T.NUMBER, "3", i)); i + 1
            }
            else -> fail("Unexpected character '$c'", i)
        }
    }
}

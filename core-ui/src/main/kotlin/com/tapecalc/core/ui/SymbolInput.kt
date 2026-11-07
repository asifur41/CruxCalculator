package com.tapecalc.core.ui

/**
 * Plain logic behind the symbol row that appears under an expression box while a letter is being typed.
 * No Android or Compose code here, so it is unit tested.
 */
object SymbolInput {

    /** One key of the row. [label] is what is drawn, [insert] is the text put into the box. */
    data class Symbol(val label: String, val insert: String, val description: String)

    val symbols: List<Symbol> = listOf(
        Symbol("+", "+", "plus"),
        Symbol("−", "-", "minus"),
        Symbol("×", "*", "times"),
        Symbol("÷", "/", "divide"),
        Symbol("^", "^", "power"),
        Symbol("(", "(", "open bracket"),
        Symbol(")", ")", "close bracket"),
        Symbol("=", "=", "equals"),
        Symbol(",", ",", "comma"),
        Symbol(".", ".", "decimal point"),
        Symbol("π", "pi", "pi"),
        Symbol("√", "sqrt(", "square root"),
        Symbol("|x|", "abs(", "absolute value"),
        Symbol("%", "%", "percent")
    )

    /**
     * What a change of the text means for the row: `true` = a letter was just typed (show the row), `false` = something
     * else was typed (hide it), `null` = nothing was typed (a deletion or a cursor move), so leave the row as it is.
     * [caret] is the cursor position after the change.
     */
    fun reaction(oldText: String, newText: String, caret: Int): Boolean? {
        if (newText.length <= oldText.length) return null
        if (caret <= 0 || caret > newText.length) return null
        return newText[caret - 1].isLetter()
    }

    /** Puts [symbol] in place of the selection [selStart, selEnd) and returns the new text with the new cursor position. */
    fun insert(text: String, selStart: Int, selEnd: Int, symbol: String): Pair<String, Int> {
        val start = minOf(selStart, selEnd).coerceIn(0, text.length)
        val end = maxOf(selStart, selEnd).coerceIn(0, text.length)
        return text.substring(0, start) + symbol + text.substring(end) to start + symbol.length
    }
}

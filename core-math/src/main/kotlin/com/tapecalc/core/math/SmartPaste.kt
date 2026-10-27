package com.tapecalc.core.math

/**
 * Turns clipboard text into something the calculator can use:
 *  "$1,234.50" -> "1234.50", "1.234,56" -> "1234.56", "total: 42 items" -> "42",
 *  "2+2*3" stays an expression.
 */
object SmartPaste {
    private val CURRENCY = Regex("\\p{Sc}")
    private val US_NUMBER = Regex("^[-+]?\\d{1,3}(,\\d{3})+(\\.\\d+)?$")
    private val EU_NUMBER = Regex("^[-+]?\\d{1,3}(\\.\\d{3})+(,\\d+)?$")
    private val COMMA_DECIMAL = Regex("^[-+]?\\d+,\\d{1,2}$")
    private val NUMBER_ANYWHERE = Regex("[-+]?\\d[\\d,.]*")

    fun extract(raw: String): String? {
        var s = raw.replace(' ', ' ').trim()
        if (s.isEmpty()) return null
        if (s.length > 500) s = s.take(500)
        s = s.replace(CURRENCY, "").trim()
        normalizeNumber(s)?.let { return it }
        if (Calculator.evaluate(Calculator.autoClose(s)) is EvalOutcome.Success) return s
        val match = NUMBER_ANYWHERE.find(s) ?: return null
        val token = match.value.trimEnd(',', '.')
        return normalizeNumber(token) ?: token
    }

    private fun normalizeNumber(s: String): String? = when {
        US_NUMBER.matches(s) -> s.replace(",", "")
        EU_NUMBER.matches(s) -> s.replace(".", "").replace(',', '.')
        COMMA_DECIMAL.matches(s) -> s.replace(',', '.')
        else -> null
    }
}

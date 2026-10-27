package com.tapecalc.core.precision

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/** Central precision policy: internal precision is separate from display precision. */
object Precision {
    /** Precision of every stored intermediate value (>= 34 significant digits). */
    val INTERNAL: MathContext = MathContext(40, RoundingMode.HALF_EVEN)

    /** Precision used inside transcendental-function series (extra guard digits). */
    val WORK: MathContext = MathContext(60, RoundingMode.HALF_EVEN)

    const val DISPLAY_DIGITS: Int = 15
    const val FULL_DIGITS: Int = 34

    /** Largest / smallest decimal exponent allowed before Overflow / Underflow is reported. */
    const val MAX_EXPONENT: Int = 9999
    const val MIN_EXPONENT: Int = -9999

    /** Exact (rational) arithmetic is dropped for numbers whose parts exceed this many bits. */
    const val MAX_EXACT_BITS: Int = 20_000
}

class OverflowException(message: String = "Overflow") : ArithmeticException(message)
class UnderflowException(message: String = "Underflow") : ArithmeticException(message)
class DomainException(message: String) : ArithmeticException(message)

/** Converts numbers into text; display formatting is independent from internal precision. */
object NumberFormatter {

    /**
     * Rounds to [sigDigits] significant digits, strips trailing zeros and switches to
     * scientific notation (1.5E20, 2E-7) for very large or very small magnitudes.
     */
    fun format(value: BigDecimal, sigDigits: Int = Precision.DISPLAY_DIGITS): String {
        if (value.signum() == 0) return "0"
        val rounded = value.round(MathContext(sigDigits, RoundingMode.HALF_UP)).stripTrailingZeros()
        val exp10 = rounded.precision() - rounded.scale() - 1
        if (exp10 >= sigDigits || exp10 < -6) {
            val digits = rounded.unscaledValue().abs().toString()
            val mantissa = if (digits.length > 1) digits[0] + "." + digits.substring(1) else digits
            val sign = if (rounded.signum() < 0) "-" else ""
            return "$sign${mantissa}E$exp10"
        }
        return rounded.toPlainString()
    }

    /** Inserts thousands separators into the integer part of a plain (non-scientific) number string. */
    fun group(text: String, separator: Char = ','): String {
        if (text.contains('E') || text.contains('(') || text.contains('/')) return text
        val negative = text.startsWith("-")
        val body = if (negative) text.substring(1) else text
        val dot = body.indexOf('.')
        val intPart = if (dot >= 0) body.substring(0, dot) else body
        val frac = if (dot >= 0) body.substring(dot) else ""
        if (intPart.length <= 3 || !intPart.all { it.isDigit() }) return text
        val sb = StringBuilder()
        intPart.forEachIndexed { i, c ->
            if (i > 0 && (intPart.length - i) % 3 == 0) sb.append(separator)
            sb.append(c)
        }
        return (if (negative) "-" else "") + sb + frac
    }
}

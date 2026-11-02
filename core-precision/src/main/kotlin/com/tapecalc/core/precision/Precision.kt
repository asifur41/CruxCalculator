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

    /**
     * Always-scientific notation with one digit before the point: 12345 -> "1.2345E4", 0.00012 -> "1.2E-4".
     * Zero stays "0". The exponent is always written, even when it is 0 ("5E0").
     */
    fun formatScientific(value: BigDecimal, sigDigits: Int = Precision.DISPLAY_DIGITS): String {
        if (value.signum() == 0) return "0"
        val rounded = value.round(MathContext(sigDigits, RoundingMode.HALF_UP)).stripTrailingZeros()
        val exp10 = rounded.precision() - rounded.scale() - 1
        return composeExponential(rounded, exp10, intDigits = 1)
    }

    /**
     * Engineering notation: the exponent is always a multiple of 3 and the mantissa is between 1 and 999.x,
     * so 12345 -> "12.345E3", 0.00012 -> "120E-6", 100000 -> "100E3". Zero stays "0".
     */
    fun formatEngineering(value: BigDecimal, sigDigits: Int = Precision.DISPLAY_DIGITS): String {
        if (value.signum() == 0) return "0"
        val rounded = value.round(MathContext(sigDigits, RoundingMode.HALF_UP)).stripTrailingZeros()
        val exp10 = rounded.precision() - rounded.scale() - 1
        val shift = Math.floorMod(exp10, 3)
        return composeExponential(rounded, exp10 - shift, intDigits = shift + 1)
    }

    /**
     * Fixed decimal places (Casio "Fix"): rounds half up to [places] decimals and keeps the trailing zeros,
     * so 3.5 with 2 places is "3.50". A result that rounds to zero is written without a minus sign.
     */
    fun formatFixed(value: BigDecimal, places: Int): String {
        val p = places.coerceIn(0, 15)
        val rounded = value.setScale(p, RoundingMode.HALF_UP)
        if (rounded.signum() == 0) return BigDecimal.ZERO.setScale(p).toPlainString()
        return rounded.toPlainString()
    }

    /**
     * Casio "Norm 1" / "Norm 2": plain digits normally, scientific notation for very large or small values.
     * Norm 1 switches below 1E-2 or from 1E10; Norm 2 switches below 1E-9 or from 1E10.
     */
    fun formatNorm(value: BigDecimal, mode: Int, sigDigits: Int = Precision.DISPLAY_DIGITS): String {
        if (value.signum() == 0) return "0"
        val rounded = value.round(MathContext(sigDigits, RoundingMode.HALF_UP)).stripTrailingZeros()
        val exp10 = rounded.precision() - rounded.scale() - 1
        val low = if (mode == 1) -2 else -9
        if (exp10 >= 10 || exp10 < low) return formatScientific(value, sigDigits)
        return rounded.toPlainString()
    }

    /** Writes [rounded] as `<intDigits digits>[.<rest>]E<exponent>`, padding with zeros when it has too few digits. */
    private fun composeExponential(rounded: BigDecimal, exponent: Int, intDigits: Int): String {
        var digits = rounded.unscaledValue().abs().toString()
        if (digits.length < intDigits) digits += "0".repeat(intDigits - digits.length)
        val head = digits.substring(0, intDigits)
        val tail = digits.substring(intDigits)
        val mantissa = if (tail.isEmpty()) head else "$head.$tail"
        val sign = if (rounded.signum() < 0) "-" else ""
        return "$sign${mantissa}E$exponent"
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

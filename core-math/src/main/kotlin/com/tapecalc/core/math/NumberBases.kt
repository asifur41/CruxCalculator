package com.tapecalc.core.math

import java.math.BigInteger

/** A positional number system the converter can read and write (binary, octal, hexadecimal ...). */
class NumberBase internal constructor(
    val radix: Int,
    val name: String,
    /** Short tag shown in "all bases" lists, for example BIN or HEX. */
    val tag: String
) {
    /** Human-readable label for pickers: "Hexadecimal (base 16)", or just "Base 32". */
    val label: String get() = if (name.startsWith("Base")) name else "$name (base $radix)"
}

/** Outcome of a number-system conversion. Never throws; wrong input becomes [Invalid] with a readable message. */
sealed interface BaseConversion {
    /** [value] is the exact integer that was read; [text] is it written in the target base (upper case). */
    data class Ok(val value: BigInteger, val text: String) : BaseConversion

    data class Invalid(val message: String) : BaseConversion
}

/**
 * Offline conversion between number systems. Works on whole numbers of any size (BigInteger),
 * so values never overflow. A leading minus sign is kept: -10 (base 10) is -1010 in binary.
 */
object NumberBases {

    val BINARY = NumberBase(2, "Binary", "BIN")
    val TERNARY = NumberBase(3, "Ternary", "TER")
    val QUATERNARY = NumberBase(4, "Quaternary", "QUAT")
    val SENARY = NumberBase(6, "Senary", "SEN")
    val OCTAL = NumberBase(8, "Octal", "OCT")
    val DECIMAL = NumberBase(10, "Decimal", "DEC")
    val DUODECIMAL = NumberBase(12, "Duodecimal", "DOZ")
    val HEXADECIMAL = NumberBase(16, "Hexadecimal", "HEX")
    val BASE32 = NumberBase(32, "Base 32", "B32")
    val BASE36 = NumberBase(36, "Base 36", "B36")

    /** Bases offered in the converter, smallest first. */
    val all: List<NumberBase> = listOf(
        BINARY, TERNARY, QUATERNARY, SENARY, OCTAL, DECIMAL, DUODECIMAL, HEXADECIMAL, BASE32, BASE36
    )

    /** The four bases most people want to see side by side. */
    val common: List<NumberBase> = listOf(BINARY, OCTAL, DECIMAL, HEXADECIMAL)

    private const val MAX_INPUT_LENGTH = 1000

    /** The digits a base uses, written as a short hint: "0-1", "0-7", "0-9 and A-F". */
    fun digitHint(base: NumberBase): String = when {
        base.radix <= 10 -> "0-${base.radix - 1}"
        else -> "0-9 and A-${Character.forDigit(base.radix - 1, base.radix).uppercaseChar()}"
    }

    /**
     * Reads [text] as a number written in [from] and writes it in [to].
     * Accepts spaces and underscores as separators (1111 1111), an optional + or - sign, and the
     * unambiguous prefixes 0b (binary), 0o (octal) and 0x (hexadecimal) when they match [from].
     */
    fun convert(text: String, from: NumberBase, to: NumberBase): BaseConversion =
        when (val read = read(text, from)) {
            is Read.Bad -> BaseConversion.Invalid(read.message)
            is Read.Value -> BaseConversion.Ok(read.value, format(read.value, to))
        }

    /** Writes [value] in [base] using upper-case letters, with a minus sign when negative. */
    fun format(value: BigInteger, base: NumberBase): String = value.toString(base.radix).uppercase()

    /**
     * Writes [value] in [base] in readable groups: binary in fours from the right (1111 1111),
     * hexadecimal in fours as well; other bases are returned unchanged.
     */
    fun formatGrouped(value: BigInteger, base: NumberBase): String {
        val plain = format(value, base)
        if (base.radix != 2 && base.radix != 16) return plain
        val negative = plain.startsWith("-")
        val digits = if (negative) plain.substring(1) else plain
        val grouped = digits.reversed().chunked(4).joinToString(" ").reversed()
        return if (negative) "-$grouped" else grouped
    }

    private sealed interface Read {
        data class Value(val value: BigInteger) : Read
        data class Bad(val message: String) : Read
    }

    private fun read(text: String, from: NumberBase): Read {
        if (text.length > MAX_INPUT_LENGTH) {
            return Read.Bad("That number is too long to convert")
        }
        var s = text.filterNot { it.isWhitespace() || it == '_' }
        if (s.isEmpty()) return Read.Bad("Type a number to convert")

        var negative = false
        when (s[0]) {
            '-', '−' -> { negative = true; s = s.substring(1) }
            '+' -> s = s.substring(1)
        }

        val prefix = when (from.radix) {
            2 -> "0b"
            8 -> "0o"
            16 -> "0x"
            else -> null
        }
        if (prefix != null && s.length > prefix.length && s.startsWith(prefix, ignoreCase = true)) {
            s = s.substring(prefix.length)
        }

        if (s.isEmpty()) return Read.Bad("Type a number to convert")

        if (s.contains('.') || s.contains(',')) {
            return Read.Bad("Number-system conversion works with whole numbers only")
        }

        for (ch in s) {
            if (Character.digit(ch, from.radix) < 0 || ch.code > 127) {
                val shown = if (ch.isLetterOrDigit()) "'${ch.uppercaseChar()}'" else "'$ch'"
                return Read.Bad(
                    "$shown is not a valid digit in ${from.label.replaceFirstChar { it.lowercaseChar() }}. Use ${digitHint(from)}."
                )
            }
        }

        val magnitude = BigInteger(s, from.radix)
        return Read.Value(if (negative) magnitude.negate() else magnitude)
    }
}

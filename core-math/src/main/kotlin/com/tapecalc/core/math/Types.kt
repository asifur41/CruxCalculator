package com.tapecalc.core.math

import com.tapecalc.core.precision.NumberFormatter
import com.tapecalc.core.precision.Precision
import com.tapecalc.core.precision.Rational
import java.math.BigDecimal
import java.math.BigInteger

enum class AngleMode(val label: String) {
    DEG("DEG"), RAD("RAD"), GRAD("GRAD");

    fun next(): AngleMode = entries[(ordinal + 1) % entries.size]

    companion object {
        fun fromName(name: String?): AngleMode = entries.firstOrNull { it.name == name } ?: DEG
    }
}

/**
 * How the main result is written: normal, always scientific (1.2345E4), engineering (12.345E3),
 * a fixed number of decimals (Fix) or Casio's Norm 1 / Norm 2 switch points.
 */
enum class NumberNotation(val label: String) {
    AUTO("Auto"), SCIENTIFIC("Scientific"), ENGINEERING("Engineering"),
    FIXED("Fix"), NORM1("Norm 1"), NORM2("Norm 2");

    companion object {
        fun fromName(name: String?): NumberNotation = entries.firstOrNull { it.name == name } ?: AUTO
    }
}

enum class ErrorKind {
    EMPTY, SYNTAX, UNBALANCED_PARENTHESES, DIVISION_BY_ZERO, DOMAIN, OVERFLOW, UNDERFLOW,
    UNKNOWN_NAME, UNKNOWN_UNIT, UNIT_MISMATCH, UNDEFINED, INVALID_ARGUMENT, TOO_COMPLEX
}

/**
 * [suggestion] is the word the person probably meant (for example "sin" for "sinn"); it is already
 * mentioned in [message], and is exposed separately so a screen can offer it as a one-tap fix later.
 */
data class CalcError(
    val kind: ErrorKind,
    val message: String,
    val position: Int? = null,
    val suggestion: String? = null
)

class CalcException(val error: CalcError) : RuntimeException(error.message) {
    constructor(kind: ErrorKind, message: String, position: Int? = null) : this(CalcError(kind, message, position))
}

/**
 * A calculated number. [decimal] always holds the value at internal precision (40 digits);
 * [exact] additionally holds the exact rational value while one is known.
 */
class CalcValue private constructor(val exact: Rational?, val decimal: BigDecimal) {

    val isZero: Boolean get() = decimal.signum() == 0
    val signum: Int get() = decimal.signum()
    val isExactInteger: Boolean get() = exact?.isInteger == true

    /** Stable text form used for persistence: "r:3/10" (exact) or "d:0.333…" (decimal only). */
    fun serialize(): String = if (exact != null) "r:$exact" else "d:${decimal.toPlainStringSafe()}"

    override fun toString(): String = NumberFormatter.format(decimal)

    companion object {
        val ZERO: CalcValue = CalcValue(Rational.ZERO, BigDecimal.ZERO)
        val ONE: CalcValue = CalcValue(Rational.ONE, BigDecimal.ONE)

        fun ofRational(r: Rational): CalcValue {
            val dec = r.toBigDecimal(Precision.INTERNAL)
            checkRange(dec)
            return if (r.bitSize > Precision.MAX_EXACT_BITS) CalcValue(null, normalize(dec)) else CalcValue(r, normalize(dec))
        }

        fun ofLong(v: Long): CalcValue = ofRational(Rational.of(v))

        fun ofInteger(v: BigInteger): CalcValue = ofRational(Rational.of(v))

        /** A value known only approximately (results of transcendental functions, huge numbers). */
        fun ofDecimal(d: BigDecimal): CalcValue {
            val r = normalize(d.round(Precision.INTERNAL))
            if (r.signum() == 0) return ZERO
            checkRange(r)
            return CalcValue(null, r)
        }

        /** A decimal that is mathematically exact (for example log10(1000) = 3). */
        fun ofExactDecimal(d: BigDecimal): CalcValue = ofRational(Rational.fromBigDecimal(d))

        fun parseLiteral(text: String): CalcValue = ofRational(Rational.parse(text))

        fun deserialize(text: String): CalcValue? = try {
            when {
                text.startsWith("r:") -> {
                    val body = text.substring(2)
                    val slash = body.indexOf('/')
                    if (slash < 0) ofRational(Rational.of(BigInteger(body)))
                    else ofRational(Rational.of(BigInteger(body.substring(0, slash)), BigInteger(body.substring(slash + 1))))
                }
                text.startsWith("d:") -> ofDecimal(BigDecimal(text.substring(2)))
                else -> parseLiteral(text)
            }
        } catch (e: Exception) {
            null
        }

        private fun normalize(d: BigDecimal): BigDecimal = if (d.signum() == 0) BigDecimal.ZERO else d.stripTrailingZeros()

        private fun checkRange(d: BigDecimal) {
            if (d.signum() == 0) return
            val adjusted = d.precision() - d.scale() - 1
            if (adjusted > Precision.MAX_EXPONENT) throw CalcException(ErrorKind.OVERFLOW, "Overflow: number is too large")
            if (adjusted < Precision.MIN_EXPONENT) throw CalcException(ErrorKind.UNDERFLOW, "Underflow: number is too small")
        }

        private fun BigDecimal.toPlainStringSafe(): String = this.toString()
    }
}

/** Everything an expression may depend on besides its own text. */
class EvalContext(
    val angleMode: AngleMode = AngleMode.DEG,
    val variables: Map<String, CalcValue> = emptyMap(),
    val ans: CalcValue? = null
)

/** A successfully evaluated expression, with several ready-to-display views of the number. */
data class EvalResult(
    val value: CalcValue,
    /** 15 significant digits, e.g. "0.333333333333333". */
    val display: String,
    /** 34 significant digits (or all digits of an exact integer). */
    val full: String,
    /** Exact fraction "7/4" when the result is an exact non-integer. */
    val fraction: String?,
    /** Mixed number "1 3/4" for improper fractions. */
    val mixed: String?,
    /** Decimal with repeating block, e.g. "0.(3)". */
    val repeating: String?,
    val unit: String?,
    val warnings: List<String>,
    val assignedName: String?,
    /** Every variable this input set (colon chains `A=5 : B=A+1`, and Pol/Rec which set X and Y). */
    val assignments: Map<String, CalcValue> = emptyMap(),
    /** Extra named outputs, e.g. the angle θ of `Pol(3, 4)`. */
    val extras: List<Pair<String, CalcValue>> = emptyList(),
    /** Degrees-minutes-seconds reading of the value, e.g. "2°30′0″" (null for huge numbers). */
    val dms: String? = null,
    /** The value as a multiple of π, e.g. "2π/3", when it is one. */
    val piForm: String? = null,
    /** The value as a simplified root, e.g. "√2/2" or "3√5", when it is one. */
    val radicalForm: String? = null
) {
    /** The exact form shown by the S⇔D toggle: a fraction, else a root, else a multiple of π. */
    val exactForm: String? get() = fraction ?: radicalForm ?: piForm

    val displayWithUnit: String get() = if (unit != null) "$display $unit" else display
    val hasExactView: Boolean get() = exactForm != null

    /**
     * The number (without unit) written in the chosen [notation]; AUTO is the same as [display].
     * [fixDigits] is the number of decimals used by [NumberNotation.FIXED].
     */
    fun formatted(notation: NumberNotation, fixDigits: Int = 2): String = when (notation) {
        NumberNotation.AUTO -> display
        NumberNotation.SCIENTIFIC -> NumberFormatter.formatScientific(value.decimal)
        NumberNotation.ENGINEERING -> NumberFormatter.formatEngineering(value.decimal)
        NumberNotation.FIXED -> NumberFormatter.formatFixed(value.decimal, fixDigits)
        NumberNotation.NORM1 -> NumberFormatter.formatNorm(value.decimal, 1)
        NumberNotation.NORM2 -> NumberFormatter.formatNorm(value.decimal, 2)
    }
}

sealed interface EvalOutcome {
    data class Success(val result: EvalResult) : EvalOutcome
    data class Failure(val error: CalcError) : EvalOutcome
}

/** Static facts about an expression, used to track dependencies on the tape. */
data class Analysis(
    val isValid: Boolean,
    val usesAns: Boolean,
    val referencedNames: Set<String>,
    val assignedName: String?,
    /** Every name the input assigns (a colon chain can assign several). */
    val assignedNames: Set<String> = assignedName?.let { setOf(it) } ?: emptySet()
)

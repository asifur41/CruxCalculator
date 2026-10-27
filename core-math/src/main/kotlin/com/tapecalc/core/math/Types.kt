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

enum class ErrorKind {
    EMPTY, SYNTAX, UNBALANCED_PARENTHESES, DIVISION_BY_ZERO, DOMAIN, OVERFLOW, UNDERFLOW,
    UNKNOWN_NAME, UNKNOWN_UNIT, UNIT_MISMATCH, UNDEFINED, INVALID_ARGUMENT, TOO_COMPLEX
}

data class CalcError(val kind: ErrorKind, val message: String, val position: Int? = null)

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
    val assignedName: String?
) {
    val displayWithUnit: String get() = if (unit != null) "$display $unit" else display
    val hasExactView: Boolean get() = fraction != null
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
    val assignedName: String?
)

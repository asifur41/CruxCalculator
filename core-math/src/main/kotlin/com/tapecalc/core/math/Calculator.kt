package com.tapecalc.core.math

import com.tapecalc.core.precision.DomainException
import com.tapecalc.core.precision.NumberFormatter
import com.tapecalc.core.precision.OverflowException
import com.tapecalc.core.precision.Precision
import com.tapecalc.core.precision.UnderflowException

/**
 * Public entry point of the math engine.
 *
 *   Calculator.evaluate("12 km to mi")
 *   Calculator.evaluate("sin(30) + 1/3", EvalContext(AngleMode.DEG))
 */
object Calculator {
    const val MAX_INPUT_LENGTH = 2000

    private class Conversion(val expression: String, val from: UnitDef, val to: UnitDef)

    private val SEPARATOR = Regex("""\s+(?:to|in)(?=\s)|\s*(?:→|->)""", RegexOption.IGNORE_CASE)
    private val HAS_TO_WORD = Regex("""\s+to\s+""", RegexOption.IGNORE_CASE)

    fun evaluate(input: String, ctx: EvalContext = EvalContext()): EvalOutcome = try {
        EvalOutcome.Success(evaluateOrThrow(input, ctx))
    } catch (e: CalcException) {
        EvalOutcome.Failure(e.error)
    } catch (e: OverflowException) {
        EvalOutcome.Failure(CalcError(ErrorKind.OVERFLOW, "Overflow: number is too large"))
    } catch (e: UnderflowException) {
        EvalOutcome.Failure(CalcError(ErrorKind.UNDERFLOW, "Underflow: number is too small"))
    } catch (e: DomainException) {
        EvalOutcome.Failure(CalcError(ErrorKind.DOMAIN, e.message ?: "Invalid input for this function"))
    } catch (e: ArithmeticException) {
        val msg = e.message ?: "Math error"
        val kind = if (msg.contains("zero", ignoreCase = true)) ErrorKind.DIVISION_BY_ZERO else ErrorKind.DOMAIN
        EvalOutcome.Failure(CalcError(kind, if (kind == ErrorKind.DIVISION_BY_ZERO) "Division by zero" else msg))
    } catch (e: StackOverflowError) {
        EvalOutcome.Failure(CalcError(ErrorKind.TOO_COMPLEX, "Expression is too complex"))
    }

    private fun evaluateOrThrow(input: String, ctx: EvalContext): EvalResult {
        val text = input.trim()
        if (text.isEmpty()) throw CalcException(ErrorKind.EMPTY, "Nothing to calculate")
        if (text.length > MAX_INPUT_LENGTH) throw CalcException(ErrorKind.TOO_COMPLEX, "Expression is too long")

        val conversion = splitConversion(text)
        if (conversion != null) {
            val (value, warnings) = if (conversion.expression.isBlank()) CalcValue.ONE to emptyList()
            else evalExpressionOnly(conversion.expression, ctx)
            val converted = UnitRegistry.convert(value, conversion.from, conversion.to)
            return buildResult(converted, conversion.to.symbol, warnings, null)
        }
        if (HAS_TO_WORD.containsMatchIn(text)) {
            throw CalcException(ErrorKind.UNKNOWN_UNIT, "Unknown unit in conversion. Example: 12 km to mi")
        }

        val (statement, warnings) = Parser.parse(Tokenizer.tokenize(text))
        val evaluator = Evaluator(ctx)
        return when (statement) {
            is Statement.Expr -> buildResult(evaluator.eval(statement.node), null, warnings, null)
            is Statement.Assign -> buildResult(evaluator.eval(statement.node), null, warnings, statement.name)
        }
    }

    private fun evalExpressionOnly(text: String, ctx: EvalContext): Pair<CalcValue, List<String>> {
        val (statement, warnings) = Parser.parse(Tokenizer.tokenize(text))
        val node = (statement as? Statement.Expr)?.node
            ?: throw CalcException(ErrorKind.SYNTAX, "Assignments cannot be combined with a unit conversion")
        return Evaluator(ctx).eval(node) to warnings
    }

    private fun splitConversion(text: String): Conversion? {
        for (m in SEPARATOR.findAll(text)) {
            val left = text.substring(0, m.range.first).trim()
            val right = text.substring(m.range.last + 1).trim()
            if (left.isEmpty() || right.isEmpty()) continue
            val to = UnitRegistry.find(right) ?: continue
            val (from, expr) = UnitRegistry.matchSuffix(left) ?: continue
            return Conversion(expr, from, to)
        }
        return null
    }

    private fun buildResult(value: CalcValue, unit: String?, warnings: List<String>, assigned: String?): EvalResult {
        val exact = value.exact
        val display = NumberFormatter.format(value.decimal, Precision.DISPLAY_DIGITS)
        val full = if (exact != null && exact.isInteger && exact.num.abs().toString().length <= 60) exact.num.toString()
        else NumberFormatter.format(value.decimal, Precision.FULL_DIGITS)
        val showFraction = exact != null && !exact.isInteger && exact.bitSize <= 160
        val repeating = if (exact != null && !exact.isInteger && !exact.isTerminating() && exact.den.bitLength() <= 128)
            exact.toRepeatingDecimal(60) else null
        return EvalResult(
            value = value,
            display = display,
            full = full,
            fraction = if (showFraction) exact.toString() else null,
            mixed = if (showFraction) exact.toMixedString() else null,
            repeating = repeating,
            unit = unit,
            warnings = warnings,
            assignedName = assigned
        )
    }

    /** Static facts about an expression: does it use `ans`, which variables, what does it assign. */
    fun analyze(input: String): Analysis {
        val text = input.trim()
        return try {
            val conversion = splitConversion(text)
            val body = conversion?.expression ?: text
            if (body.isBlank()) return Analysis(true, false, emptySet(), null)
            val tokens = Tokenizer.tokenize(body)
            val assigned = if (tokens.size >= 3 && tokens[0].type == T.IDENT && tokens[1].type == T.EQUALS) tokens[0].text else null
            var usesAns = false
            val names = linkedSetOf<String>()
            tokens.forEachIndexed { index, t ->
                if (t.type == T.IDENT) {
                    when {
                        t.text.equals("ans", ignoreCase = true) -> usesAns = true
                        Functions.isReserved(t.text) -> Unit
                        index == 0 && assigned != null -> Unit
                        else -> names.add(t.text)
                    }
                }
            }
            Analysis(true, usesAns, names, assigned)
        } catch (e: CalcException) {
            Analysis(false, false, emptySet(), null)
        }
    }

    /** Appends any missing closing parentheses (used for live preview and when pressing "="). */
    fun autoClose(input: String): String {
        var open = 0
        for (c in input) {
            when (c) {
                '(', '[', '{' -> open++
                ')', ']', '}' -> if (open > 0) open--
            }
        }
        return if (open > 0) input + ")".repeat(open) else input
    }

    private const val TRAILING = "+-−–*×·/÷^=, ("

    /** Removes dangling operators so "5+" can still preview as 5, then closes parentheses. */
    fun previewText(input: String): String {
        var s = input.trimEnd()
        while (s.isNotEmpty() && TRAILING.indexOf(s.last()) >= 0) s = s.dropLast(1).trimEnd()
        return autoClose(s)
    }

    /** True if the text is a single plain number (no operators), so a preview would be redundant. */
    fun isPlainNumber(input: String): Boolean {
        val s = input.trim().removePrefix("-").removePrefix("−")
        return s.isNotEmpty() && s.all { it.isDigit() || it == '.' }
    }
}

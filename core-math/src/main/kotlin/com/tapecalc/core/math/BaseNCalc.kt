package com.tapecalc.core.math

import java.math.BigInteger

/** The answer of a BASE-N calculation, shown in the four bases a Casio shows. */
class BaseNResult(
    /** The signed value of the word. */
    val value: BigInteger,
    val bits: Int,
    val bin: String,
    val oct: String,
    val dec: String,
    val hex: String
) {
    /** Binary in groups of four: "1111 0000 1010 0101". */
    val binGrouped: String get() = bin.reversed().chunked(4).joinToString(" ").reversed()
}

sealed interface BaseNOutcome {
    data class Ok(val result: BaseNResult) : BaseNOutcome
    data class Err(val message: String) : BaseNOutcome
}

/**
 * Integer arithmetic in binary, octal, decimal or hexadecimal with a fixed word size (like the BASE-N mode of a
 * Casio). Operators: + - * / mod, and or xor xnor not neg, shl shr, with parentheses. Negative numbers are
 * two's complement: in 8 bits, `not 0` is -1 and is written 11111111 in binary and FF in hexadecimal.
 * Precedence (low to high): or xor xnor, and, shl shr, + -, * / mod, not neg and unary minus.
 */
object BaseNCalc {
    val BASES = listOf(2, 8, 10, 16)
    val WORD_SIZES = listOf(8, 16, 32, 64)
    private val WORDS = setOf("and", "or", "xor", "xnor", "not", "neg", "shl", "shr", "mod")

    private class Fail(message: String) : RuntimeException(message)

    fun evaluate(text: String, base: Int, bits: Int = 32): BaseNOutcome = try {
        require(base in BASES) { "Base must be 2, 8, 10 or 16" }
        require(bits in WORD_SIZES) { "Word size must be 8, 16, 32 or 64 bits" }
        val tokens = tokenize(text, base)
        if (tokens.isEmpty()) throw Fail("Nothing to calculate")
        val p = P(tokens, base, bits)
        val v = p.expression(0)
        if (p.pos < tokens.size) throw Fail("Unexpected '${tokens[p.pos]}'")
        BaseNOutcome.Ok(describe(v, bits))
    } catch (e: Fail) {
        BaseNOutcome.Err(e.message ?: "Invalid expression")
    } catch (e: IllegalArgumentException) {
        BaseNOutcome.Err(e.message ?: "Invalid input")
    }

    private fun describe(signed: BigInteger, bits: Int): BaseNResult {
        val unsigned = if (signed.signum() < 0) signed.add(BigInteger.ONE.shiftLeft(bits)) else signed
        return BaseNResult(
            value = signed,
            bits = bits,
            bin = unsigned.toString(2),
            oct = unsigned.toString(8),
            dec = signed.toString(),
            hex = unsigned.toString(16).uppercase()
        )
    }

    /** Wraps [v] into the signed range of a [bits]-bit word. */
    internal fun wrap(v: BigInteger, bits: Int): BigInteger {
        val mod = BigInteger.ONE.shiftLeft(bits)
        var r = v.mod(mod)
        if (r.testBit(bits - 1)) r = r.subtract(mod)
        return r
    }

    private fun tokenize(text: String, base: Int): List<String> {
        val out = ArrayList<String>()
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                c.isWhitespace() -> i++
                c.isLetterOrDigit() -> {
                    var j = i
                    while (j < text.length && text[j].isLetterOrDigit()) j++
                    val word = text.substring(i, j)
                    val lower = word.lowercase()
                    if (lower in WORDS) out.add(lower)
                    else {
                        val body = stripPrefix(lower, base)
                        if (body.isEmpty() || body.any { Character.digit(it, base) < 0 }) {
                            throw Fail("'$word' is not a valid ${baseName(base)} number")
                        }
                        out.add("#$body")
                    }
                    i = j
                }
                c in "+-*/()" -> { out.add(c.toString()); i++ }
                c == '×' || c == '·' -> { out.add("*"); i++ }
                c == '÷' -> { out.add("/"); i++ }
                c == '−' || c == '–' -> { out.add("-"); i++ }
                else -> throw Fail("Unexpected character '$c'")
            }
        }
        return out
    }

    private fun stripPrefix(s: String, base: Int): String = when {
        base == 2 && s.startsWith("0b") -> s.substring(2)
        base == 8 && s.startsWith("0o") -> s.substring(2)
        base == 16 && s.startsWith("0x") -> s.substring(2)
        else -> s
    }

    private fun baseName(base: Int) = when (base) { 2 -> "binary"; 8 -> "octal"; 10 -> "decimal"; else -> "hexadecimal" }

    private class P(val t: List<String>, val base: Int, val bits: Int) {
        var pos = 0
        private fun peek(): String? = t.getOrNull(pos)
        private fun w(v: BigInteger) = wrap(v, bits)

        private fun level(op: String): Int = when (op) {
            "or", "xor", "xnor" -> 1
            "and" -> 2
            "shl", "shr" -> 3
            "+", "-" -> 4
            "*", "/", "mod" -> 5
            else -> 0
        }

        fun expression(minLevel: Int): BigInteger {
            var left = unary()
            while (true) {
                val op = peek() ?: break
                val lv = level(op)
                if (lv == 0 || lv < minLevel) break
                pos++
                val right = expression(lv + 1)
                left = apply(op, left, right)
            }
            return left
        }

        private fun apply(op: String, a: BigInteger, b: BigInteger): BigInteger = w(when (op) {
            "+" -> a.add(b)
            "-" -> a.subtract(b)
            "*" -> a.multiply(b)
            "/" -> { if (b.signum() == 0) throw Fail("Division by zero"); a.divide(b) }
            "mod" -> { if (b.signum() == 0) throw Fail("Modulo by zero"); a.mod(b.abs()) }
            "and" -> a.and(b)
            "or" -> a.or(b)
            "xor" -> a.xor(b)
            "xnor" -> a.xor(b).not()
            "shl" -> { checkShift(b); a.shiftLeft(b.toInt()) }
            "shr" -> { checkShift(b); a.shiftRight(b.toInt()) }
            else -> throw Fail("Unknown operator '$op'")
        })

        private fun checkShift(b: BigInteger) {
            if (b.signum() < 0 || b > BigInteger.valueOf(bits.toLong())) throw Fail("Shift by 0 to $bits places")
        }

        private fun unary(): BigInteger {
            val tok = peek() ?: throw Fail("The expression ends too early")
            return when (tok) {
                "not" -> { pos++; w(unary().not()) }
                "neg", "-" -> { pos++; w(unary().negate()) }
                "+" -> { pos++; unary() }
                "(" -> {
                    pos++
                    val v = expression(0)
                    if (peek() != ")") throw Fail("Missing closing parenthesis")
                    pos++
                    v
                }
                else -> {
                    if (!tok.startsWith("#")) throw Fail("'$tok' needs a number before it")
                    pos++
                    val v = BigInteger(tok.substring(1), base)
                    if (v.bitLength() > bits) throw Fail("${tok.substring(1).uppercase()} does not fit in $bits bits")
                    w(v)
                }
            }
        }
    }
}

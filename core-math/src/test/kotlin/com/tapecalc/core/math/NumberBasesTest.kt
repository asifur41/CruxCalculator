package com.tapecalc.core.math

import java.math.BigInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NumberBasesTest {

    private fun ok(text: String, from: NumberBase, to: NumberBase): String {
        val result = NumberBases.convert(text, from, to)
        assertTrue("expected success for '$text' but got $result", result is BaseConversion.Ok)
        return (result as BaseConversion.Ok).text
    }

    private fun problem(text: String, from: NumberBase, to: NumberBase): String {
        val result = NumberBases.convert(text, from, to)
        assertTrue("expected a problem for '$text' but got $result", result is BaseConversion.Invalid)
        return (result as BaseConversion.Invalid).message
    }

    @Test
    fun decimalToOtherBases() {
        assertEquals("11111111", ok("255", NumberBases.DECIMAL, NumberBases.BINARY))
        assertEquals("377", ok("255", NumberBases.DECIMAL, NumberBases.OCTAL))
        assertEquals("FF", ok("255", NumberBases.DECIMAL, NumberBases.HEXADECIMAL))
        assertEquals("0", ok("0", NumberBases.DECIMAL, NumberBases.BINARY))
    }

    @Test
    fun otherBasesToDecimal() {
        assertEquals("255", ok("11111111", NumberBases.BINARY, NumberBases.DECIMAL))
        assertEquals("255", ok("377", NumberBases.OCTAL, NumberBases.DECIMAL))
        assertEquals("255", ok("FF", NumberBases.HEXADECIMAL, NumberBases.DECIMAL))
        assertEquals("1295", ok("zz", NumberBases.BASE36, NumberBases.DECIMAL))
    }

    @Test
    fun betweenNonDecimalBases() {
        assertEquals("FF", ok("11111111", NumberBases.BINARY, NumberBases.HEXADECIMAL))
        assertEquals("1010", ok("A", NumberBases.HEXADECIMAL, NumberBases.BINARY))
        assertEquals("12", ok("A", NumberBases.HEXADECIMAL, NumberBases.OCTAL))
        assertEquals("5", ok("12", NumberBases.TERNARY, NumberBases.DECIMAL))
        assertEquals("5", ok("12", NumberBases.TERNARY, NumberBases.SENARY))
        assertEquals("11", ok("5", NumberBases.DECIMAL, NumberBases.QUATERNARY))
        assertEquals("1V", ok("63", NumberBases.DECIMAL, NumberBases.BASE32))
    }

    @Test
    fun letterCaseDoesNotMatterAndOutputIsUpperCase() {
        assertEquals("DEADBEEF", ok("deadbeef", NumberBases.HEXADECIMAL, NumberBases.HEXADECIMAL))
        assertEquals("3735928559", ok("DeadBeef", NumberBases.HEXADECIMAL, NumberBases.DECIMAL))
    }

    @Test
    fun negativeNumbersKeepTheirSign() {
        assertEquals("-1010", ok("-10", NumberBases.DECIMAL, NumberBases.BINARY))
        assertEquals("-A", ok("-1010", NumberBases.BINARY, NumberBases.HEXADECIMAL))
        assertEquals("-10", ok("−10", NumberBases.DECIMAL, NumberBases.DECIMAL))
        assertEquals("10", ok("+10", NumberBases.DECIMAL, NumberBases.DECIMAL))
    }

    @Test
    fun separatorsAndPrefixesAreAccepted() {
        assertEquals("255", ok("1111 1111", NumberBases.BINARY, NumberBases.DECIMAL))
        assertEquals("255", ok("1111_1111", NumberBases.BINARY, NumberBases.DECIMAL))
        assertEquals("255", ok("0b11111111", NumberBases.BINARY, NumberBases.DECIMAL))
        assertEquals("255", ok("0xFF", NumberBases.HEXADECIMAL, NumberBases.DECIMAL))
        assertEquals("255", ok("0XFF", NumberBases.HEXADECIMAL, NumberBases.DECIMAL))
        assertEquals("255", ok("0o377", NumberBases.OCTAL, NumberBases.DECIMAL))
        assertEquals("-255", ok("-0xFF", NumberBases.HEXADECIMAL, NumberBases.DECIMAL))
    }

    @Test
    fun zeroBInHexadecimalIsADigitPairNotAPrefix() {
        // 0B = 11 in hexadecimal; "b" is only a prefix letter for binary input.
        assertEquals("11", ok("0B", NumberBases.HEXADECIMAL, NumberBases.DECIMAL))
        assertEquals("177", ok("0B1", NumberBases.HEXADECIMAL, NumberBases.DECIMAL))
    }

    @Test
    fun hugeNumbersDoNotOverflow() {
        // 2^100 = 16^25
        assertEquals("1" + "0".repeat(25), ok("1267650600228229401496703205376", NumberBases.DECIMAL, NumberBases.HEXADECIMAL))
        assertEquals("1267650600228229401496703205376", ok("1" + "0".repeat(100), NumberBases.BINARY, NumberBases.DECIMAL))
    }

    @Test
    fun returnsTheExactValueRead() {
        val result = NumberBases.convert("FF", NumberBases.HEXADECIMAL, NumberBases.BINARY) as BaseConversion.Ok
        assertEquals(BigInteger.valueOf(255), result.value)
    }

    @Test
    fun badDigitsGiveAReadableMessage() {
        val msg = problem("12", NumberBases.BINARY, NumberBases.DECIMAL)
        assertTrue(msg, msg.contains("'2'"))
        assertTrue(msg, msg.contains("binary (base 2)"))
        assertTrue(msg, msg.contains("0-1"))

        val hex = problem("12G", NumberBases.HEXADECIMAL, NumberBases.DECIMAL)
        assertTrue(hex, hex.contains("'G'"))
        assertTrue(hex, hex.contains("0-9 and A-F"))

        val b32 = problem("W", NumberBases.BASE32, NumberBases.DECIMAL)
        assertTrue(b32, b32.contains("base 32"))
        assertTrue(b32, b32.contains("A-V"))

        val octal = problem("9", NumberBases.OCTAL, NumberBases.DECIMAL)
        assertTrue(octal, octal.contains("0-7"))
    }

    @Test
    fun emptyAndSignOnlyInputAskForANumber() {
        assertEquals("Type a number to convert", problem("", NumberBases.DECIMAL, NumberBases.BINARY))
        assertEquals("Type a number to convert", problem("   ", NumberBases.DECIMAL, NumberBases.BINARY))
        assertEquals("Type a number to convert", problem("-", NumberBases.DECIMAL, NumberBases.BINARY))
    }

    @Test
    fun fractionsAreRejectedClearly() {
        assertTrue(problem("10.5", NumberBases.DECIMAL, NumberBases.BINARY).contains("whole numbers"))
        assertTrue(problem("1,5", NumberBases.DECIMAL, NumberBases.BINARY).contains("whole numbers"))
    }

    @Test
    fun nonAsciiDigitsAreRejected() {
        // Fullwidth digit one
        assertTrue(problem("１", NumberBases.DECIMAL, NumberBases.BINARY).contains("not a valid digit"))
    }

    @Test
    fun overlongInputIsRejected() {
        assertTrue(problem("1".repeat(1001), NumberBases.DECIMAL, NumberBases.BINARY).contains("too long"))
    }

    @Test
    fun groupedOutputForBinaryAndHex() {
        val n = BigInteger.valueOf(255)
        assertEquals("1111 1111", NumberBases.formatGrouped(n, NumberBases.BINARY))
        assertEquals("1 0000 0000", NumberBases.formatGrouped(BigInteger.valueOf(256), NumberBases.BINARY))
        assertEquals("FF", NumberBases.formatGrouped(n, NumberBases.HEXADECIMAL))
        assertEquals("DEAD BEEF", NumberBases.formatGrouped(BigInteger("3735928559"), NumberBases.HEXADECIMAL))
        assertEquals("-1010", NumberBases.formatGrouped(BigInteger.valueOf(-10), NumberBases.BINARY))
        assertEquals("377", NumberBases.formatGrouped(n, NumberBases.OCTAL))
        assertEquals("255", NumberBases.formatGrouped(n, NumberBases.DECIMAL))
    }

    @Test
    fun pickerListIsOrderedAndLabelled() {
        assertEquals(listOf(2, 3, 4, 6, 8, 10, 12, 16, 32, 36), NumberBases.all.map { it.radix })
        assertEquals("Hexadecimal (base 16)", NumberBases.HEXADECIMAL.label)
        assertEquals("Base 32", NumberBases.BASE32.label)
        assertEquals(listOf(2, 8, 10, 16), NumberBases.common.map { it.radix })
    }

    @Test
    fun everyBaseRoundTrips() {
        val value = "123456789012345678901234567890"
        for (base in NumberBases.all) {
            val there = ok(value, NumberBases.DECIMAL, base)
            assertEquals("round trip through ${base.label}", value, ok(there, base, NumberBases.DECIMAL))
        }
    }
}

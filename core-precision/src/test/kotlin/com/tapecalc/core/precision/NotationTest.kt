package com.tapecalc.core.precision

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Test

class NotationTest {

    private fun sci(text: String, digits: Int = Precision.DISPLAY_DIGITS) =
        NumberFormatter.formatScientific(BigDecimal(text), digits)

    private fun eng(text: String, digits: Int = Precision.DISPLAY_DIGITS) =
        NumberFormatter.formatEngineering(BigDecimal(text), digits)

    @Test
    fun scientificBasics() {
        assertEquals("1.2345E4", sci("12345"))
        assertEquals("1.2E-4", sci("0.00012"))
        assertEquals("5E0", sci("5"))
        assertEquals("-5E0", sci("-5"))
        assertEquals("1E6", sci("1000000"))
        assertEquals("-2.5E-3", sci("-0.0025"))
        assertEquals("1.5E0", sci("1.5"))
    }

    @Test
    fun zeroIsPlainInBothNotations() {
        assertEquals("0", sci("0"))
        assertEquals("0", eng("0"))
        assertEquals("0", sci("0.000"))
    }

    @Test
    fun scientificRoundsToSignificantDigits() {
        assertEquals("3.33333333333333E-1", sci("0.333333333333333333333"))
        // 9.99… rounds up and carries into the exponent.
        assertEquals("1E1", sci("9.9999999999999999999"))
        assertEquals("1.23E2", sci("123.456", 3))
    }

    @Test
    fun engineeringExponentIsAlwaysMultipleOfThree() {
        assertEquals("12.345E3", eng("12345"))
        assertEquals("120E-6", eng("0.00012"))
        assertEquals("100E3", eng("100000"))
        assertEquals("1.234567E6", eng("1234567"))
        assertEquals("123.4E0", eng("123.4"))
        assertEquals("1E3", eng("1000"))
        assertEquals("999E0", eng("999"))
        assertEquals("-500E-3", eng("-0.5"))
        assertEquals("1E-3", eng("0.001"))
    }

    @Test
    fun engineeringCarriesAfterRounding() {
        // 999.99 rounded to 3 digits is 1000 -> 1E3, not "1000E0".
        assertEquals("1E3", eng("999.99", 3))
    }

    @Test
    fun hugeAndTinyNumbers() {
        assertEquals("1E9999", sci("1E+9999"))
        assertEquals("1E-9999", sci("1E-9999"))
        assertEquals("100E3", eng("1E+5"))
    }

    @Test
    fun existingAutoFormatIsUnchanged() {
        assertEquals("12345", NumberFormatter.format(BigDecimal("12345")))
        assertEquals("1E20", NumberFormatter.format(BigDecimal("1E+20")))
        assertEquals("1.234E-7", NumberFormatter.format(BigDecimal("0.0000001234")))
    }
}

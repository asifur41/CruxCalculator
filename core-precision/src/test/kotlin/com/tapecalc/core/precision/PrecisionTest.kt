package com.tapecalc.core.precision

import java.math.BigDecimal
import java.math.MathContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrecisionTest {

    private fun bd(s: String) = BigDecimal(s)
    private fun fmt(v: BigDecimal, digits: Int = 30) = NumberFormatter.format(v, digits)

    @Test
    fun rationalAddsExactly() {
        val sum = Rational.parse("0.1") + Rational.parse("0.2")
        assertEquals(Rational.parse("0.3"), sum)
        assertEquals("3/10", sum.toString())
    }

    @Test
    fun rationalSimplifiesAndConvertsToDecimal() {
        val r = Rational.of(6, 8)
        assertEquals("3/4", r.toString())
        assertEquals("0.75", NumberFormatter.format(r.toBigDecimal(Precision.INTERNAL)))
        assertEquals("1 3/4", Rational.of(7, 4).toMixedString())
    }

    @Test
    fun repeatingDecimals() {
        assertEquals("0.(3)", Rational.of(1, 3).toRepeatingDecimal())
        assertEquals("0.1(6)", Rational.of(1, 6).toRepeatingDecimal())
        assertEquals("0.125", Rational.of(1, 8).toRepeatingDecimal())
        assertEquals("-0.(142857)", Rational.of(-1, 7).toRepeatingDecimal())
        assertTrue(Rational.of(1, 8).isTerminating())
        assertFalse(Rational.of(1, 3).isTerminating())
    }

    @Test
    fun piIsAccurate() {
        assertEquals("3.1415926535897932384626433832795028841971693993751", fmt(BigMath.pi, 50))
    }

    @Test
    fun expAndLn() {
        assertEquals("2.71828182845904523536028747135266", fmt(BigMath.exp(BigDecimal.ONE), 33))
        assertEquals("1", fmt(BigMath.ln(BigMath.exp(BigDecimal.ONE)), 30))
        assertEquals("2.30258509299404568401799145468436", fmt(BigMath.ln(bd("10")), 33))
        assertEquals("3", fmt(BigMath.log10(bd("1000"))))
    }

    @Test
    fun trigRadians() {
        assertEquals("0.84147098480789650665250232163", fmt(BigMath.sin(BigDecimal.ONE)))
        assertEquals("0.540302305868139717400936607443", fmt(BigMath.cos(BigDecimal.ONE)))
        assertEquals("0", fmt(BigMath.sin(BigMath.pi.round(MathContext(40)))))
        assertEquals("0.78539816339744830961566084582", fmt(BigMath.atan(BigDecimal.ONE)))
    }

    @Test
    fun sqrtAndInverseTrig() {
        assertEquals("1.41421356237309504880168872421", fmt(BigMath.sqrt(bd("2")), 30))
        assertEquals("0.523598775598298873077107230547", fmt(BigMath.asin(bd("0.5")), 30))
    }

    @Test
    fun formatterUsesScientificForExtremes() {
        assertEquals("0.3", NumberFormatter.format(bd("0.30000000000000000000")))
        assertEquals("1.5E20", NumberFormatter.format(bd("150000000000000000000")))
        assertEquals("1E-7", NumberFormatter.format(bd("0.0000001")))
        assertEquals("123456789012345", NumberFormatter.format(bd("123456789012345")))
        assertEquals("1,234,567.5", NumberFormatter.group("1234567.5"))
    }
}

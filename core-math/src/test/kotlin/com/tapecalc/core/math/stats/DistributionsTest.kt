package com.tapecalc.core.math.stats

import com.tapecalc.core.math.CalcException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class DistributionsTest {
    private val z = v("0")
    private val o = v("1")

    @Test fun normalPdfAtZero() = assertEquals("0.398942280401", Distributions.normalPdf(z).toString())

    @Test fun normalPdfWithParameters() {
        assertClose(0.19947114020071635, Distributions.normalPdf(v("10"), v("10"), v("2")), 1e-11)
    }

    @Test fun normalCdfClassicValues() {
        assertEquals("0.975002104852", Distributions.normalCdf(null, v("1.96")).toString())
        assertEquals("0.5", Distributions.normalCdf(null, z).toString())
        assertEquals("0.5", Distributions.normalCdf(z, null).toString())
        assertEquals("1", Distributions.normalCdf(null, null).toString())
    }

    @Test fun normalCdfBetweenWithMeanAndSd() {
        assertClose(0.532807207342556, Distributions.normalCdf(v("-1"), v("2"), v("1"), v("2")), 1e-11)
    }

    @Test fun normalCdfKeepsDigitsInTheTail() {
        assertClose(2.856649842341556e-07, Distributions.normalCdf(v("5"), v("6")), 1e-10)
    }

    @Test fun invNormal() {
        assertEquals("1.95996398454", Distributions.invNormal(v("0.975")).toString())
        assertEquals("0", Distributions.invNormal(v("0.5")).toString())
        assertClose(-3.090232306167813, Distributions.invNormal(v("0.001")), 1e-10)
        assertClose(100 + 15 * 1.959963984540054, Distributions.invNormal(v("0.975"), v("100"), v("15")), 1e-10)
    }

    @Test fun invNormalRoundTrip() {
        for (p in listOf("0.0001", "0.02", "0.3", "0.9", "0.9999")) {
            val x = Distributions.invNormal(v(p))
            assertClose(p.toDouble(), Distributions.normalCdf(null, x), 1e-9)
        }
    }

    @Test fun normalErrors() {
        for (block in listOf<() -> Unit>(
            { Distributions.normalPdf(z, z, z) },
            { Distributions.invNormal(v("1")) },
            { Distributions.invNormal(v("0")) },
            { Distributions.normalCdf(v("2"), v("1")) }
        )) try { block(); fail() } catch (e: CalcException) { assertTrue(e.error.message.isNotEmpty()) }
    }

    @Test fun binomialIsExactForSmallCases() {
        assertEquals("0.24609375", Distributions.binomialPdf(10, v("0.5"), 5).toString())
        assertClose(0.6496107184, Distributions.binomialCdf(10, v("0.3"), 3), 1e-12)
        assertEquals("1", Distributions.binomialCdf(10, v("0.3"), 10).toString())
        assertEquals("0", Distributions.binomialCdf(10, v("0.3"), -1).toString())
        assertEquals("0", Distributions.binomialPdf(10, v("0.3"), 11).toString())
    }

    @Test fun binomialEdgeProbabilities() {
        assertEquals("1", Distributions.binomialPdf(5, z, 0).toString())
        assertEquals("1", Distributions.binomialPdf(5, o, 5).toString())
        assertEquals("0", Distributions.binomialPdf(5, o, 4).toString())
    }

    @Test fun inverseBinomial() {
        assertEquals(11, Distributions.invBinomial(20, v("0.4"), v("0.9")))
        assertEquals(0, Distributions.invBinomial(5, v("0.5"), v("0.01")))
        assertEquals(5, Distributions.invBinomial(5, v("0.5"), v("1")))
    }

    @Test fun largeBinomialUsesTheRecurrence() {
        // n = 5000: symmetric, so P(X <= 2500) is a little above one half.
        val c = Distributions.binomialCdf(5000, v("0.5"), 2500).decimal.toDouble()
        assertTrue("was $c", c > 0.5 && c < 0.52)
        assertClose(0.011284, Distributions.binomialPdf(5000, v("0.5"), 2500), 1e-3)
    }

    @Test fun poissonValues() {
        assertClose(0.22404180765538775, Distributions.poissonPdf(v("3"), 2), 1e-12)
        assertClose(0.42319008112684364, Distributions.poissonCdf(v("3"), 2), 1e-12)
        assertEquals(5, Distributions.invPoisson(v("3"), v("0.9")))
        assertEquals("0", Distributions.poissonPdf(v("3"), -1).toString())
    }

    @Test fun poissonLargeLambdaDoesNotUnderflow() {
        assertClose(0.0398942, Distributions.poissonPdf(v("100"), 100), 1e-3)
    }

    @Test fun parameterErrors() {
        for (block in listOf<() -> Unit>(
            { Distributions.binomialPdf(-1, v("0.5"), 0) },
            { Distributions.binomialPdf(5, v("1.5"), 0) },
            { Distributions.poissonPdf(v("0"), 1) },
            { Distributions.poissonPdf(v("-2"), 1) },
            { Distributions.invPoisson(v("3"), v("1")) },
            { Distributions.invBinomial(5, v("0.5"), v("2")) }
        )) try { block(); fail() } catch (e: CalcException) { assertTrue(e.error.message.isNotEmpty()) }
    }
}

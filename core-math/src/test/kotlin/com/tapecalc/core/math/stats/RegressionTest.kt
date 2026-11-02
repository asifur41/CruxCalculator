package com.tapecalc.core.math.stats

import com.tapecalc.core.math.CalcException
import com.tapecalc.core.math.ErrorKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class RegressionTest {
    private val x8 = vs("1", "2", "3", "4", "5", "6", "7", "8")

    @Test fun perfectLineIsExact() {
        val r = Stats.regression(RegressionType.LINEAR, vs("1", "2", "3"), vs("5", "8", "11"))
        assertEquals("2", r.coefficients[0].toString())
        assertEquals("3", r.coefficients[1].toString())
        assertEquals("1", r.r.toString())
        assertEquals("1", r.r2.toString())
        assertEquals("y = 2 + 3x", r.equation)
    }

    @Test fun noisyLineMatchesScipy() {
        val r = Stats.regression(RegressionType.LINEAR, x8, vs("2.1", "3.9", "6.2", "7.8", "10.1", "12.2", "13.8", "16.1"))
        assertClose(0.0357142857142847, r.coefficients[0])
        assertClose(1.9976190476190478, r.coefficients[1])
        assertClose(0.9994194747958132, r.r!!)
    }

    @Test fun fractionsStayExactInTheLineFit() {
        val r = Stats.regression(RegressionType.LINEAR, vs("1", "2", "4"), vs("1", "3", "4"))
        assertEquals("true", (r.coefficients[0].exact != null).toString())
    }

    @Test fun negativeSlopeEquation() {
        val r = Stats.regression(RegressionType.LINEAR, vs("0", "1", "2"), vs("5", "3", "1"))
        assertEquals("y = 5 - 2x", r.equation)
    }

    @Test fun predictAndEstimateLinear() {
        val r = Stats.regression(RegressionType.LINEAR, vs("1", "2", "3"), vs("5", "8", "11"))
        assertEquals("14", r.predictY(v("4")).toString())
        assertEquals(listOf("4"), r.estimateX(v("14")).map { it.toString() })
    }

    @Test fun quadraticPerfectFit() {
        val r = Stats.regression(RegressionType.QUADRATIC, vs("0", "1", "2", "3"), vs("1", "2", "5", "10"))
        assertEquals(listOf("1", "0", "1"), r.coefficients.map { it.toString() })
        assertNull(r.r)
        assertEquals("1", r.r2.toString())
        assertEquals("y = 1 + 0x + 1x²", r.equation)
    }

    @Test fun quadraticMatchesNumpy() {
        val r = Stats.regression(RegressionType.QUADRATIC, x8, vs("2", "3", "5", "4", "7", "8", "8", "11"))
        assertClose(1.35714286, r.coefficients[0], 1e-7)
        assertClose(0.76190476, r.coefficients[1], 1e-7)
        assertClose(0.04761905, r.coefficients[2], 1e-7)
    }

    @Test fun quadraticEstimateGivesBothRoots() {
        val r = Stats.regression(RegressionType.QUADRATIC, vs("0", "1", "2", "3"), vs("1", "2", "5", "10"))
        assertEquals(listOf("-2", "2"), r.estimateX(v("5")).map { it.toString() })
        assertTrue(r.estimateX(v("0")).isEmpty())
        assertEquals(listOf("0"), r.estimateX(v("1")).map { it.toString() })
    }

    private val xs5 = vs("1", "2", "3", "4", "5")
    private val ys5 = vs("2.7", "5.1", "9.8", "19", "40.2")

    @Test fun exponentialMatchesNumpy() {
        val r = Stats.regression(RegressionType.EXPONENTIAL, xs5, ys5)
        assertClose(1.3413914480100848, r.coefficients[0])
        assertClose(0.6716428884665545, r.coefficients[1])
        assertClose(0.9994936558034304, r.r!!)
    }

    @Test fun abExponentialIsTheSameCurve() {
        val e = Stats.regression(RegressionType.EXPONENTIAL, xs5, ys5)
        val ab = Stats.regression(RegressionType.AB_EXPONENTIAL, xs5, ys5)
        assertClose(e.coefficients[0].decimal.toDouble(), ab.coefficients[0])
        assertClose(Math.exp(e.coefficients[1].decimal.toDouble()), ab.coefficients[1])
        assertClose(e.predictY(v("3.5")).decimal.toDouble(), ab.predictY(v("3.5")))
    }

    @Test fun exactExponentialData() {
        val r = Stats.regression(RegressionType.AB_EXPONENTIAL, vs("1", "2", "3"), vs("6", "18", "54"))
        assertClose(2.0, r.coefficients[0], 1e-12)
        assertClose(3.0, r.coefficients[1], 1e-12)
        assertClose(162.0, r.predictY(v("4")), 1e-9)
    }

    @Test fun powerMatchesNumpy() {
        val r = Stats.regression(RegressionType.POWER, xs5, ys5)
        assertClose(2.1405157341758154, r.coefficients[0])
        assertClose(1.6162840799652092, r.coefficients[1])
    }

    @Test fun logarithmicMatchesNumpy() {
        val r = Stats.regression(RegressionType.LOGARITHMIC, xs5, ys5)
        assertClose(-3.490532634113567, r.coefficients[0])
        assertClose(19.687274304476816, r.coefficients[1])
    }

    @Test fun inverseMatchesNumpy() {
        val r = Stats.regression(RegressionType.INVERSE, xs5, ys5)
        assertClose(30.29545934530096, r.coefficients[0])
        assertClose(-32.70538542766632, r.coefficients[1])
    }

    @Test fun estimateInverseOfEachType() {
        for (t in listOf(RegressionType.LOGARITHMIC, RegressionType.EXPONENTIAL, RegressionType.AB_EXPONENTIAL, RegressionType.POWER, RegressionType.INVERSE)) {
            val r = Stats.regression(t, xs5, ys5)
            val y = r.predictY(v("2.5"))
            val back = r.estimateX(y)
            assertEquals(t.name, 1, back.size)
            assertClose(2.5, back[0], 1e-9)
        }
    }

    @Test fun frequenciesGiveTheSameFitAsRepeatedPoints() {
        val a = Stats.regression(RegressionType.LINEAR, vs("1", "2", "3"), vs("2", "3", "7"), vs("2", "1", "3"))
        val b = Stats.regression(RegressionType.LINEAR, vs("1", "1", "2", "3", "3", "3"), vs("2", "2", "3", "7", "7", "7"))
        assertEquals(b.coefficients[0].toString(), a.coefficients[0].toString())
        assertEquals(b.coefficients[1].toString(), a.coefficients[1].toString())
        assertEquals(b.r.toString(), a.r.toString())
    }

    @Test fun errorsAreClear() {
        fun msg(block: () -> Unit): String = try { block(); "no error" } catch (e: CalcException) { e.error.message }
        assertTrue(msg { Stats.regression(RegressionType.LINEAR, vs("2", "2"), vs("1", "3")) }.contains("same"))
        assertTrue(msg { Stats.regression(RegressionType.LINEAR, vs("1"), vs("1")) }.contains("two"))
        assertTrue(msg { Stats.regression(RegressionType.LOGARITHMIC, vs("0", "1"), vs("1", "2")) }.contains("greater than 0"))
        assertTrue(msg { Stats.regression(RegressionType.EXPONENTIAL, vs("1", "2"), vs("1", "-2")) }.contains("greater than 0"))
        assertTrue(msg { Stats.regression(RegressionType.INVERSE, vs("0", "1"), vs("1", "2")) }.contains("0"))
        assertTrue(msg { Stats.regression(RegressionType.QUADRATIC, vs("1", "2"), vs("1", "2")) }.contains("three"))
        assertEquals(ErrorKind.EMPTY, try { Stats.regression(RegressionType.LINEAR, emptyList(), emptyList()); null } catch (e: CalcException) { e.error.kind })
    }
}

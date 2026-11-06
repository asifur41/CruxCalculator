package com.tapecalc.core.math

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A number with a gigantic exponent must be refused at once, not expanded into millions of digits first. */
class HugeNumberTest {

    private fun failureKind(expr: String): ErrorKind {
        val o = Calculator.evaluate(expr)
        assertTrue("'$expr' should fail but gave $o", o is EvalOutcome.Failure)
        return (o as EvalOutcome.Failure).error.kind
    }

    private fun value(expr: String): String {
        val o = Calculator.evaluate(expr)
        assertTrue("'$expr' failed: $o", o is EvalOutcome.Success)
        return (o as EvalOutcome.Success).result.display
    }

    @Test(timeout = 3_000)
    fun hugeExponentsFailAtOnce() {
        assertEquals(ErrorKind.OVERFLOW, failureKind("1e100000000"))
        assertEquals(ErrorKind.OVERFLOW, failureKind("1e10000000"))
        assertEquals(ErrorKind.UNDERFLOW, failureKind("1e-100000000"))
        assertEquals(ErrorKind.SYNTAX, failureKind("1e99999999999"))
        for (e in listOf("2*1e100000000", "sin(1e100000000)", "sum(1e100000000,k,1,3)", "5 km to mi + 1e100000000")) {
            assertTrue("'$e' should fail quickly", Calculator.evaluate(e) is EvalOutcome.Failure)
        }
    }

    @Test
    fun numbersInsideTheRangeStillWork() {
        assertEquals("200000", value("2e5"))
        assertEquals("0.0025", value("2.5e-3"))
        assertEquals("1E9999", value("1e9999"))
        assertEquals("1E-9999", value("1e-9999"))
        assertEquals("0", value("0e100000000"))
        assertEquals(ErrorKind.OVERFLOW, failureKind("1e10000"))
        assertEquals(ErrorKind.UNDERFLOW, failureKind("1e-10000"))
    }
}

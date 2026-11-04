package com.tapecalc.core.math

import com.tapecalc.core.math.tools.ToolInput
import com.tapecalc.core.math.tools.ToolResult
import com.tapecalc.core.math.tools.Tools
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolsTest {
    private fun run(id: String, texts: Map<String, String> = emptyMap(), choices: Map<String, Int> = emptyMap()): ToolResult {
        val spec = Tools.byId(id)!!
        val values = spec.fields.associate { it.id to (texts[it.id] ?: it.default) }
        val ch = spec.options.associate { it.id to (choices[it.id] ?: it.default) }
        return spec.run(ToolInput(values, ch, EvalContext()))
    }

    private fun lines(r: ToolResult): List<Pair<String, String>> {
        assertTrue("expected OK but was ${(r as? ToolResult.Err)?.message}", r is ToolResult.Ok)
        return (r as ToolResult.Ok).lines.map { it.label to it.value }
    }

    @Test fun everyToolWorksWithItsDefaults() {
        for (spec in Tools.all) {
            val r = run(spec.id)
            assertTrue("${spec.id}: ${(r as? ToolResult.Err)?.message}", r is ToolResult.Ok)
            assertTrue((r as ToolResult.Ok).lines.isNotEmpty())
        }
    }

    @Test fun everyOptionChoiceWorks() {
        for (spec in Tools.all) for (opt in spec.options) for (i in opt.choices.indices) {
            val r = run(spec.id, choices = mapOf(opt.id to i))
            // a few choices need extra fields; they must still fail politely, never crash
            assertTrue("${spec.id}/${opt.id}=$i", r is ToolResult.Ok || r is ToolResult.Err)
        }
    }

    @Test fun polynomialRoots() {
        val l = lines(run("poly"))
        assertEquals(listOf("1", "2"), l.map { it.second }.sortedBy { it.toDouble() })
    }

    @Test fun simultaneous() {
        val l = lines(run("linear"))
        assertEquals(listOf("x" to "2", "y" to "1"), l)
    }

    @Test fun solveAndCalculus() {
        val l = lines(run("solve", mapOf("f" to "x^2 - 2", "guess" to "1")))
        assertTrue(l[0].second.startsWith("1.41421356"))
        assertEquals("2", lines(run("calculus", mapOf("f" to "x^2", "a" to "1"), mapOf("kind" to 0)))[0].second)
        assertEquals("9", lines(run("calculus", mapOf("f" to "x^2", "a" to "0", "b" to "3"), mapOf("kind" to 1)))[0].second)
        assertEquals("14", lines(run("calculus", mapOf("f" to "x^2", "a" to "1", "b" to "3"), mapOf("kind" to 2)))[0].second)
    }

    @Test fun symbolicTool() {
        assertEquals("x^2 + 2x + 1", lines(run("algebra", choices = mapOf("op" to 1)))[0].second)
        assertEquals("(x + 1)^2", lines(run("algebra", mapOf("expr" to "x^2+2x+1"), mapOf("op" to 2)))[0].second)
    }

    @Test fun statisticsAndRegression() {
        val s = lines(run("stats1")).toMap()
        assertEquals("6.2", s["Mean x̄"])
        assertEquals("5", s["Median"])
        val r = lines(run("stats2", mapOf("px" to "6"))).toMap()
        assertTrue(r["Equation"]!!.startsWith("y = "))
        assertTrue(r.keys.any { it.startsWith("ŷ at x") })
    }

    @Test fun distributionsAndBases() {
        val d = lines(run("dist", mapOf("p1" to "0", "p2" to "0", "p3" to "1"), mapOf("kind" to 1)))
        assertEquals("0.5", d[0].second)
        val b = lines(run("basen")).toMap()
        assertEquals("15", b["DEC"])
        assertEquals("F", b["HEX"]!!.trimStart('0'))
    }

    @Test fun matricesVectorsComplexSteps() {
        assertEquals("-2", lines(run("matrix"))[0].second)
        val m = lines(run("matrix", mapOf("B" to "5 6\n7 8", "expr" to "A*B")))
        assertTrue(m.any { it.second.contains("19") && it.second.contains("22") })
        val e = lines(run("eigen")).toMap()
        assertEquals("3", e["Determinant"])
        assertEquals("32", lines(run("vector")).first { it.first == "u · v" }.second)
        assertEquals("11 - 2i", lines(run("complex"))[0].second)
        val st = lines(run("steps"))
        assertTrue(st.last().second.endsWith("29"))
    }

    @Test fun badInputIsAFriendlyError() {
        assertTrue(run("poly", mapOf("coef" to "1")) is ToolResult.Err)
        assertTrue(run("linear", mapOf("rows" to "1, 1, 2\n1, 1, 3")) is ToolResult.Err)
        assertTrue(run("matrix", mapOf("expr" to "inv(A)", "A" to "1 2\n2 4")) is ToolResult.Err)
        assertTrue(run("solve", mapOf("f" to "")) is ToolResult.Err)
        assertTrue(run("complex", mapOf("expr" to "")) is ToolResult.Err)
    }
}

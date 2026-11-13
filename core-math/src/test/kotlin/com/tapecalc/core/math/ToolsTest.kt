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
        // the default menu is Normal / PD / Variable at x = 1
        assertEquals("0.241970724519", lines(run("dist"))[0].second.take(14))
        val b = lines(run("basen")).toMap()
        assertEquals("15", b["DEC"])
        assertEquals("F", b["HEX"]!!.trimStart('0'))
    }

    private fun dist(dist: Int, fn: Int, data: Int = 0, tail: Int = 0, texts: Map<String, String> = emptyMap()) =
        run("dist", texts, mapOf("dist" to dist, "fn" to fn, "data" to data, "tail" to tail))

    @Test fun normalMenu() {
        // CD with both bounds, one bound, and a blank bound meaning infinity
        assertEquals("0.682689492137", lines(dist(0, 1, texts = mapOf("lo" to "-1", "hi" to "1")))[0].second.take(14))
        assertEquals("0.5", lines(dist(0, 1, texts = mapOf("lo" to "", "hi" to "0")))[0].second)
        assertEquals("0.5", lines(dist(0, 1, texts = mapOf("lo" to "0", "hi" to "")))[0].second)
        assertTrue(dist(0, 1, texts = mapOf("lo" to "", "hi" to "")) is ToolResult.Err)
        // inverse: left tail, right tail, central
        assertEquals("1.95996398454", lines(dist(0, 2, tail = 0, texts = mapOf("area" to "0.975")))[0].second.take(13))
        assertEquals("1.95996398454", lines(dist(0, 2, tail = 1, texts = mapOf("area" to "0.025")))[0].second.take(13))
        val central = lines(dist(0, 2, tail = 2, texts = mapOf("area" to "0.95")))
        assertEquals(2, central.size)
        assertEquals("-1.95996398454", central[0].second.take(14))
        assertEquals("1.95996398454", central[1].second.take(13))
        // mean and deviation are used
        assertEquals("10", lines(dist(0, 2, texts = mapOf("area" to "0.5", "mu" to "10", "sigma" to "2")))[0].second)
    }

    @Test fun binomialAndPoissonMenus() {
        assertEquals("0.24609375", lines(dist(1, 0, texts = mapOf("x" to "5", "n" to "10", "p" to "0.5")))[0].second)
        assertEquals("0.623046875", lines(dist(1, 1, texts = mapOf("x" to "5", "n" to "10", "p" to "0.5")))[0].second)
        assertEquals("5", lines(dist(1, 2, texts = mapOf("area" to "0.5", "n" to "10", "p" to "0.5")))[0].second)
        assertEquals("0.4231900811", lines(dist(2, 1, texts = mapOf("x" to "2", "lambda" to "3")))[0].second.take(12))
        assertEquals("0.224041807", lines(dist(2, 0, texts = mapOf("x" to "3", "lambda" to "3")))[0].second.take(11))
        assertTrue(dist(1, 0, texts = mapOf("x" to "2.5", "n" to "10", "p" to "0.5")) is ToolResult.Err)
        assertTrue(dist(1, 0, texts = mapOf("x" to "-1", "n" to "10", "p" to "0.5")) is ToolResult.Err)
    }

    @Test fun listInputGivesOneLinePerValue() {
        val pd = lines(dist(1, 0, data = 1, texts = mapOf("x" to "0, 1, 2", "n" to "2", "p" to "0.5")))
        assertEquals(listOf("P(X = 0)" to "0.25", "P(X = 1)" to "0.5", "P(X = 2)" to "0.25"), pd)
        // a list of lower bounds against one upper bound is broadcast
        val cd = lines(dist(0, 1, data = 1, texts = mapOf("lo" to "-1, 0", "hi" to "1")))
        assertEquals(2, cd.size)
        assertEquals("0.5", lines(dist(0, 1, data = 1, texts = mapOf("lo" to "0", "hi" to "")))[0].second)
        // two lists of different sizes cannot be paired
        assertTrue(dist(0, 1, data = 1, texts = mapOf("lo" to "-1, 0", "hi" to "1, 2, 3")) is ToolResult.Err)
        assertTrue(dist(1, 0, data = 1, texts = mapOf("x" to "", "n" to "2", "p" to "0.5")) is ToolResult.Err)
    }

    @Test fun menuShowsOnlyTheBoxesThatApply() {
        val spec = Tools.byId("dist")!!
        fun ids(dist: Int, fn: Int, data: Int = 0) =
            spec.fieldsFor(spec.choiceLookup(mapOf("dist" to dist, "fn" to fn, "data" to data))).map { it.id }
        assertEquals(listOf("x", "sigma", "mu"), ids(0, 0))
        assertEquals(listOf("lo", "hi", "sigma", "mu"), ids(0, 1))
        assertEquals(listOf("area", "sigma", "mu"), ids(0, 2))
        assertEquals(listOf("x", "n", "p"), ids(1, 0))
        assertEquals(listOf("area", "n", "p"), ids(1, 2))
        assertEquals(listOf("x", "lambda"), ids(2, 1))
        // the same box is relabelled for a list
        val one = spec.fieldsFor(spec.choiceLookup(mapOf("data" to 0))).first().label
        val many = spec.fieldsFor(spec.choiceLookup(mapOf("data" to 1))).first().label
        assertTrue(one != many)
        // the tail chip only exists for the inverse normal
        assertTrue(spec.optionsFor(spec.choiceLookup(mapOf("dist" to 0, "fn" to 2))).any { it.id == "tail" })
        assertTrue(spec.optionsFor(spec.choiceLookup(mapOf("dist" to 0, "fn" to 0))).none { it.id == "tail" })
        assertTrue(spec.optionsFor(spec.choiceLookup(mapOf("dist" to 1, "fn" to 2))).none { it.id == "tail" })
    }

    @Test fun tapeRecordsOnlyWhatTheMenuShowed() {
        val spec = Tools.byId("dist")!!
        val texts = mapOf("x" to "0, 1", "n" to "2", "p" to "0.5", "lo" to "-9", "sigma" to "7")
        val choices = mapOf("dist" to 1, "fn" to 0, "data" to 1, "tail" to 2)
        val input = com.tapecalc.core.math.tools.ToolHistory.inputText(spec, texts, choices)
        assertTrue(input, input.contains("[Binomial]") && input.contains("[List]") && input.contains("k list: 0, 1"))
        // boxes that were hidden by the menu (the normal distribution's bounds and sigma) are not recorded
        assertTrue(input, !input.contains("-9") && !input.contains("7") && !input.contains("Central"))
        val rec = com.tapecalc.core.math.tools.ToolHistory.record(spec, texts, choices, spec.run(ToolInput(texts, choices, EvalContext())))
        assertTrue(rec!!.summary.contains("P(X = 0) = 0.25"))
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

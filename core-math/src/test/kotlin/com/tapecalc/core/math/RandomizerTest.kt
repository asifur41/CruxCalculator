package com.tapecalc.core.math

import com.tapecalc.core.math.tools.Checked
import com.tapecalc.core.math.tools.CoinSide
import com.tapecalc.core.math.tools.RandomSource
import com.tapecalc.core.math.tools.Randomizer
import com.tapecalc.core.math.tools.ToolHistory
import com.tapecalc.core.math.tools.ToolInput
import com.tapecalc.core.math.tools.ToolResult
import com.tapecalc.core.math.tools.Tools
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger
import kotlin.random.Random

class RandomizerTest {

    /** Hands out the given numbers in turn (wrapping round) and remembers every bound it was asked for. */
    private class Scripted(vararg values: Long) : RandomSource {
        private val script = values
        private var next = 0
        val bounds = ArrayList<Long>()
        override fun nextLong(bound: Long): Long {
            bounds += bound
            return script[next++ % script.size]
        }
    }

    private fun seeded(seed: Int = 7) = RandomSource.of(Random(seed))

    private fun <T> ok(c: Checked<T>): T {
        assertTrue("expected OK but was ${(c as? Checked.Invalid)?.message}", c is Checked.Ok)
        return (c as Checked.Ok<T>).value
    }

    private fun invalid(c: Checked<*>): String {
        assertTrue("expected an error but the input was accepted", c is Checked.Invalid)
        return (c as Checked.Invalid).message
    }

    // ------------------------------------------------------------------ input validation

    @Test fun countAcceptsWholeNumbersInRange() {
        assertEquals(1, ok(Randomizer.checkCount("1", "Number of coins", 100)))
        assertEquals(100, ok(Randomizer.checkCount("100", "Number of coins", 100)))
        assertEquals(12, ok(Randomizer.checkCount("  12 ", "Number of coins", 100)))
        assertEquals(5, ok(Randomizer.checkCount("+5", "Number of coins", 100)))
    }

    @Test fun countRejectsBadInputWithAMessageThatNamesTheBox() {
        for (bad in listOf("", "   ", "abc", "2.5", "1e2", "0", "-3", "101", "99999999999999999999", "1,5", "--2")) {
            val message = invalid(Randomizer.checkCount(bad, "Number of coins", 100))
            assertTrue("$bad -> $message", message.startsWith("Number of coins"))
            assertTrue("$bad -> $message", message.contains("1 to 100"))
        }
        assertTrue(invalid(Randomizer.checkCount("", "Number of dice", 100)).contains("empty"))
        assertTrue(invalid(Randomizer.checkCount("2.5", "Number of dice", 100)).contains("whole number"))
    }

    @Test fun sidesMustBeAtLeastTwoAndNotHuge() {
        assertEquals(2, ok(Randomizer.checkSides("2")))
        assertEquals(1_000_000, ok(Randomizer.checkSides("1000000")))
        assertEquals(37, ok(Randomizer.checkSides(" 37 ")))
        for (bad in listOf("", "1", "0", "-6", "1000001", "six", "6.5")) {
            val message = invalid(Randomizer.checkSides(bad))
            assertTrue("$bad -> $message", message.startsWith("Sides"))
            assertTrue("$bad -> $message", message.contains("2 to 1,000,000"))
        }
    }

    @Test fun rangeAcceptsNegativeEqualAndLargeBounds() {
        val (min, max) = ok(Randomizer.checkRange("-5", "5"))
        assertEquals(BigInteger.valueOf(-5), min)
        assertEquals(BigInteger.valueOf(5), max)
        val (a, b) = ok(Randomizer.checkRange("7", "7"))
        assertEquals(a, b)
        val (lo, hi) = ok(Randomizer.checkRange("-1000000000000", "1000000000000"))
        assertEquals(BigInteger("-1000000000000"), lo)
        assertEquals(BigInteger("1000000000000"), hi)
    }

    @Test fun rangeRejectsMissingOrNonWholeBounds() {
        assertEquals("Minimum is empty. Enter a whole number, for example 1.", invalid(Randomizer.checkRange("", "10")))
        assertEquals("Maximum is empty. Enter a whole number, for example 100.", invalid(Randomizer.checkRange("1", "  ")))
        assertTrue(invalid(Randomizer.checkRange("one", "10")).startsWith("Minimum must be a whole number"))
        assertTrue(invalid(Randomizer.checkRange("1", "10.5")).startsWith("Maximum must be a whole number"))
        assertTrue(invalid(Randomizer.checkRange("1", "2+3")).startsWith("Maximum must be a whole number"))
    }

    @Test fun rangeRejectsMinimumAboveMaximum() {
        assertEquals(
            "Minimum (10) is larger than maximum (3). Make the maximum at least 10.",
            invalid(Randomizer.checkRange("10", "3"))
        )
        assertTrue(invalid(Randomizer.checkRange("-1", "-2")).contains("Minimum (-1) is larger than maximum (-2)"))
    }

    @Test fun rangeRejectsAnAbsurdlyWideRange() {
        val message = invalid(Randomizer.checkRange("-9223372036854775808", "9223372036854775807"))
        assertTrue(message, message.contains("too wide"))
        // the widest range that still fits is accepted
        ok(Randomizer.checkRange("0", "9223372036854775806"))
        assertTrue(invalid(Randomizer.checkRange("0", "9223372036854775807")).contains("too wide"))
        assertTrue(invalid(Randomizer.checkRange("0", "99999999999999999999999")).contains("too wide"))
    }

    @Test fun otherScriptDigitsAreAcceptedAsWholeNumbers() {
        // Bengali digits for 10 and 20
        val (min, max) = ok(Randomizer.checkRange("১০", "২০"))
        assertEquals(BigInteger.valueOf(10), min)
        assertEquals(BigInteger.valueOf(20), max)
        assertEquals(3, ok(Randomizer.checkCount("৩", "Number of coins", 100)))
    }

    @Test fun choicesNeedAtLeastOneNonEmptyLine() {
        assertEquals("Enter at least one choice, one per line.", invalid(Randomizer.checkChoices("")))
        assertEquals("Enter at least one choice, one per line.", invalid(Randomizer.checkChoices("   \n\n \t \n")))
        assertEquals(listOf("Pizza"), ok(Randomizer.checkChoices("Pizza")))
    }

    @Test fun choicesAreTrimmedAndBlankLinesDropped() {
        assertEquals(
            listOf("Pizza", "Burgers", "Sushi sushi"),
            ok(Randomizer.checkChoices("  Pizza \n\nBurgers\r\n   \n Sushi sushi  \n"))
        )
        // repeats are kept: listing a choice twice makes it twice as likely
        assertEquals(listOf("a", "a", "b"), ok(Randomizer.checkChoices("a\na\nb")))
    }

    // ------------------------------------------------------------------ coins

    @Test fun coinFlipsMapEachDrawToASideAndCountTotals() {
        val flips = Randomizer.flipCoins(5, Scripted(0, 1, 1, 0, 1))
        assertEquals(
            listOf(CoinSide.HEADS, CoinSide.TAILS, CoinSide.TAILS, CoinSide.HEADS, CoinSide.TAILS),
            flips.sides
        )
        assertEquals(2, flips.heads)
        assertEquals(3, flips.tails)
        assertEquals(flips.sides.size, flips.heads + flips.tails)
    }

    @Test fun eachCoinAsksForOneDrawOutOfTwo() {
        val source = Scripted(0)
        Randomizer.flipCoins(4, source)
        assertEquals(listOf(2L, 2L, 2L, 2L), source.bounds)
    }

    @Test fun manyCoinsTotalUpAndBothSidesAppear() {
        val flips = Randomizer.flipCoins(100, seeded())
        assertEquals(100, flips.sides.size)
        assertEquals(100, flips.heads + flips.tails)
        assertTrue(flips.heads > 0 && flips.tails > 0)
    }

    // ------------------------------------------------------------------ dice

    @Test fun diceAddOneToEachDrawAndSumTheTotal() {
        val roll = Randomizer.rollDice(4, 6, Scripted(0, 5, 2, 3))
        assertEquals(listOf(1, 6, 3, 4), roll.rolls)
        assertEquals(14L, roll.total)
        assertEquals(6, roll.sides)
    }

    @Test fun eachDieAsksForOneDrawOutOfItsSides() {
        val source = Scripted(0)
        Randomizer.rollDice(3, 20, source)
        assertEquals(listOf(20L, 20L, 20L), source.bounds)
    }

    @Test fun everyPresetRollsStayWithinOneToSides() {
        for (sides in Randomizer.DICE_PRESETS) {
            val roll = Randomizer.rollDice(100, sides, seeded(sides))
            assertEquals(100, roll.rolls.size)
            assertTrue("d$sides: ${roll.rolls}", roll.rolls.all { it in 1..sides })
            assertEquals(roll.rolls.sumOf { it.toLong() }, roll.total)
        }
    }

    @Test fun aSmallDieShowsEveryFaceOverManyRolls() {
        val faces = Randomizer.rollDice(100, 6, seeded()).rolls.toSet() +
            Randomizer.rollDice(100, 6, seeded(99)).rolls.toSet()
        assertEquals(setOf(1, 2, 3, 4, 5, 6), faces)
    }

    @Test fun theLargestDieStaysInRangeAndTotalsWithoutOverflow() {
        val roll = Randomizer.rollDice(100, Randomizer.MAX_SIDES, Scripted((Randomizer.MAX_SIDES - 1).toLong()))
        assertTrue(roll.rolls.all { it == Randomizer.MAX_SIDES })
        assertEquals(100L * Randomizer.MAX_SIDES, roll.total)
    }

    // ------------------------------------------------------------------ random number

    @Test fun randomIntegerIsMinPlusTheDraw() {
        val source = Scripted(0, 4, 9)
        assertEquals(BigInteger.valueOf(1), Randomizer.randomInteger(BigInteger.valueOf(1), BigInteger.valueOf(10), source))
        assertEquals(BigInteger.valueOf(5), Randomizer.randomInteger(BigInteger.valueOf(1), BigInteger.valueOf(10), source))
        assertEquals(BigInteger.valueOf(10), Randomizer.randomInteger(BigInteger.valueOf(1), BigInteger.valueOf(10), source))
        assertEquals(listOf(10L, 10L, 10L), source.bounds)
    }

    @Test fun randomIntegerIncludesBothEndsOfANegativeRange() {
        val min = BigInteger.valueOf(-3)
        val max = BigInteger.valueOf(3)
        assertEquals(min, Randomizer.randomInteger(min, max, Scripted(0)))
        assertEquals(max, Randomizer.randomInteger(min, max, Scripted(6)))
    }

    @Test fun randomIntegerOfASingleValueRangeIsThatValue() {
        val source = Scripted(0)
        assertEquals(BigInteger.valueOf(42), Randomizer.randomInteger(BigInteger.valueOf(42), BigInteger.valueOf(42), source))
        assertEquals(listOf(1L), source.bounds)
    }

    @Test fun randomIntegerStaysInsideTheRangeAndReachesBothEnds() {
        val source = seeded()
        val min = BigInteger.valueOf(-2)
        val max = BigInteger.valueOf(2)
        val seen = (1..500).map { Randomizer.randomInteger(min, max, source).toInt() }
        assertTrue(seen.all { it in -2..2 })
        assertEquals(setOf(-2, -1, 0, 1, 2), seen.toSet())
    }

    @Test fun randomIntegerHandlesTheWidestAllowedRange() {
        val min = BigInteger.ZERO
        val max = BigInteger.valueOf(Long.MAX_VALUE - 1)
        // 0..(Long.MAX_VALUE - 1) holds Long.MAX_VALUE numbers, so the highest draw is Long.MAX_VALUE - 1
        assertEquals(max, Randomizer.randomInteger(min, max, Scripted(Long.MAX_VALUE - 1)))
        assertEquals(min, Randomizer.randomInteger(min, max, Scripted(0)))
        // a range that starts below zero and ends above Long.MAX_VALUE in absolute terms still adds up exactly
        val lo = BigInteger.valueOf(Long.MIN_VALUE)
        val hi = BigInteger.valueOf(-2)
        assertEquals(hi, Randomizer.randomInteger(lo, hi, Scripted(Long.MAX_VALUE - 1)))
    }

    // ------------------------------------------------------------------ picker

    @Test fun pickReturnsTheChoiceAtTheDrawnIndex() {
        val choices = listOf("Pizza", "Burgers", "Sushi")
        val source = Scripted(2, 0, 1)
        assertEquals("Sushi", Randomizer.pick(choices, source).chosen)
        assertEquals("Pizza", Randomizer.pick(choices, source).chosen)
        assertEquals(1, Randomizer.pick(choices, source).index)
        assertEquals(listOf(3L, 3L, 3L), source.bounds)
    }

    @Test fun pickingFromOneChoiceAlwaysReturnsIt() {
        assertEquals("Only", Randomizer.pick(listOf("Only"), seeded()).chosen)
    }

    @Test fun everyChoiceCanBePickedAndNothingElse() {
        val choices = listOf("a", "b", "c", "d")
        val source = seeded()
        val seen = (1..200).map { Randomizer.pick(choices, source).chosen }.toSet()
        assertEquals(choices.toSet(), seen)
    }

    // ------------------------------------------------------------------ the random source contract

    @Test fun aSourceThatBreaksItsContractIsCaughtNotTrusted() {
        for (bad in listOf(-1L, 2L, 99L)) {
            try {
                Randomizer.flipCoins(1, RandomSource { bad })
                throw AssertionError("a draw of $bad for a bound of 2 should have been rejected")
            } catch (expected: IllegalStateException) {
                assertTrue(expected.message!!.contains("bound of 2"))
            }
        }
    }

    @Test fun theSecureSourceIsBoundedAndAvailable() {
        val source = RandomSource.secure
        assertSame(source, RandomSource.secure)
        repeat(200) {
            val v = source.nextLong(6)
            assertTrue(v in 0L..5L)
        }
        assertEquals(0L, source.nextLong(1))
    }

    // ------------------------------------------------------------------ the tool on the Tools screen

    private fun runTool(
        source: RandomSource,
        mode: Int,
        texts: Map<String, String> = emptyMap(),
        choices: Map<String, Int> = emptyMap()
    ): ToolResult {
        val spec = Randomizer.toolSpec(source)
        val values = spec.fields.associate { it.id to (texts[it.id] ?: it.default) }
        val picked = spec.options.associate { it.id to (choices[it.id] ?: it.default) } + ("mode" to mode)
        return spec.run(ToolInput(values, picked, EvalContext()))
    }

    private fun lines(r: ToolResult): List<Pair<String, String>> {
        assertTrue("expected OK but was ${(r as? ToolResult.Err)?.message}", r is ToolResult.Ok)
        return (r as ToolResult.Ok).lines.map { it.label to it.value }
    }

    private fun message(r: ToolResult): String {
        assertTrue("expected an error", r is ToolResult.Err)
        return (r as ToolResult.Err).message
    }

    @Test fun theToolIsOnTheToolsScreenAndWorksWithItsDefaults() {
        val spec = Tools.byId("random")
        assertNotNull(spec)
        assertEquals("Randomizer", spec!!.title)
        assertTrue(Tools.all.count { it.id == "random" } == 1)
        // default mode is Coin with one coin
        val result = spec.run(ToolInput(spec.fields.associate { it.id to it.default }, spec.options.associate { it.id to it.default }, EvalContext()))
        val l = lines(result)
        assertEquals(listOf("Result", "Heads", "Tails"), l.map { it.first })
        assertEquals(1, l[1].second.toInt() + l[2].second.toInt())
    }

    @Test fun coinModeShowsEachResultAndTheTotals() {
        val l = lines(runTool(Scripted(0, 1, 1, 0, 1), mode = 0, texts = mapOf("coins" to "5")))
        assertEquals("Results" to "Heads, Tails, Tails, Heads, Tails", l[0])
        assertEquals("Heads" to "2", l[1])
        assertEquals("Tails" to "3", l[2])
        assertEquals("Result" to "Tails", lines(runTool(Scripted(1), mode = 0, texts = mapOf("coins" to "1")))[0])
    }

    @Test fun coinModeRejectsBadCounts() {
        assertTrue(message(runTool(Scripted(0), 0, mapOf("coins" to "0"))).contains("1 to 100"))
        assertTrue(message(runTool(Scripted(0), 0, mapOf("coins" to "101"))).contains("1 to 100"))
        assertTrue(message(runTool(Scripted(0), 0, mapOf("coins" to ""))).contains("empty"))
    }

    @Test fun diceModeUsesThePresetChip() {
        // chip order: d4 d6 d8 d10 d12 d20 d100 Custom
        val d20 = lines(runTool(Scripted(19, 0, 9), 1, mapOf("dice" to "3"), mapOf("die" to 5)))
        assertEquals("Rolls (3d20)" to "20, 1, 10", d20[0])
        assertEquals("Total" to "31", d20[1])

        val d100 = lines(runTool(Scripted(99), 1, mapOf("dice" to "1"), mapOf("die" to 6)))
        assertEquals("Roll (d100)" to "100", d100[0])
        assertEquals("Total" to "100", d100[1])

        val source = Scripted(0)
        runTool(source, 1, mapOf("dice" to "2"), mapOf("die" to 0))
        assertEquals(listOf(4L, 4L), source.bounds)
    }

    @Test fun diceModeUsesCustomSidesOnlyForTheCustomChip() {
        val custom = lines(runTool(Scripted(29, 0), 1, mapOf("dice" to "2", "sides" to "30"), mapOf("die" to 7)))
        assertEquals("Rolls (2d30)" to "30, 1", custom[0])
        assertEquals("Total" to "31", custom[1])
        // a stray custom value is ignored while a preset is picked
        val preset = Scripted(0)
        runTool(preset, 1, mapOf("dice" to "1", "sides" to "30"), mapOf("die" to 1))
        assertEquals(listOf(6L), preset.bounds)
    }

    @Test fun diceModeRejectsBadInput() {
        assertTrue(message(runTool(Scripted(0), 1, mapOf("dice" to "0"))).startsWith("Number of dice"))
        assertTrue(message(runTool(Scripted(0), 1, mapOf("dice" to "x"))).startsWith("Number of dice"))
        assertTrue(message(runTool(Scripted(0), 1, mapOf("dice" to "1", "sides" to "1"), mapOf("die" to 7))).startsWith("Sides"))
        assertTrue(message(runTool(Scripted(0), 1, mapOf("dice" to "1", "sides" to ""), mapOf("die" to 7))).contains("empty"))
    }

    @Test fun numberModeGivesAnIntegerInsideTheRange() {
        val l = lines(runTool(Scripted(0), 2, mapOf("min" to "-10", "max" to "10")))
        assertEquals("Random number from -10 to 10" to "-10", l.single())
        assertEquals("10", lines(runTool(Scripted(20), 2, mapOf("min" to "-10", "max" to "10"))).single().second)
        val source = seeded(3)
        repeat(100) {
            val n = lines(runTool(source, 2, mapOf("min" to "5", "max" to "8"))).single().second.toInt()
            assertTrue(n in 5..8)
        }
    }

    @Test fun numberModeRejectsAnInvalidRange() {
        assertEquals(
            "Minimum (9) is larger than maximum (2). Make the maximum at least 9.",
            message(runTool(Scripted(0), 2, mapOf("min" to "9", "max" to "2")))
        )
        assertTrue(message(runTool(Scripted(0), 2, mapOf("min" to "", "max" to "2"))).startsWith("Minimum is empty"))
        assertTrue(message(runTool(Scripted(0), 2, mapOf("min" to "1", "max" to "1.5"))).startsWith("Maximum must be"))
    }

    @Test fun pickerModePicksOneAndSaysHowManyChoices() {
        val l = lines(runTool(Scripted(1), 3, mapOf("choices" to "Pizza\n\n  Burgers \nSushi")))
        assertEquals("Picked" to "Burgers", l[0])
        assertEquals("Chosen from" to "3 choices", l[1])
        assertEquals("1 choice", lines(runTool(Scripted(0), 3, mapOf("choices" to "Only one")))[1].second)
    }

    @Test fun pickerModeNeedsAtLeastOneChoice() {
        assertEquals("Enter at least one choice, one per line.", message(runTool(Scripted(0), 3, mapOf("choices" to ""))))
        assertEquals("Enter at least one choice, one per line.", message(runTool(Scripted(0), 3, mapOf("choices" to " \n \n"))))
    }

    @Test fun anInvalidInputNeverDrawsFromTheSource() {
        val source = Scripted(0)
        runTool(source, 0, mapOf("coins" to "abc"))
        runTool(source, 1, mapOf("dice" to "-1"))
        runTool(source, 2, mapOf("min" to "5", "max" to "1"))
        runTool(source, 3, mapOf("choices" to ""))
        assertTrue(source.bounds.isEmpty())
    }

    @Test fun eachModeShowsItsOwnBoxesAndChips() {
        val spec = Randomizer.toolSpec(Scripted(0))
        fun ids(mode: Int, die: Int = 1): Pair<List<String>, List<String>> {
            val pick = spec.choiceLookup(mapOf("mode" to mode, "die" to die))
            return spec.fieldsFor(pick).map { it.id } to spec.optionsFor(pick).map { it.id }
        }
        assertEquals(listOf("coins") to listOf("mode"), ids(0))
        assertEquals(listOf("dice") to listOf("mode", "die"), ids(1))
        assertEquals(listOf("dice", "sides") to listOf("mode", "die"), ids(1, die = 7))
        assertEquals(listOf("min", "max") to listOf("mode"), ids(2))
        assertEquals(listOf("choices") to listOf("mode"), ids(3))
        val die = spec.options.first { it.id == "die" }
        assertEquals(listOf("d4", "d6", "d8", "d10", "d12", "d20", "d100", "Custom"), die.choices)
        assertEquals("d6", die.choices[die.default])
    }

    @Test fun theMainButtonNamesTheActionAndTheToolIsNeverReRunOnReturn() {
        val spec = Randomizer.toolSpec(Scripted(0))
        fun label(mode: Int) = spec.actionLabelFor(spec.choiceLookup(mapOf("mode" to mode)))
        assertEquals(listOf("Flip", "Roll", "Generate", "Pick one"), (0..3).map(::label))
        assertFalse(spec.rerunOnRestore)
        // every other tool keeps the old behaviour
        assertTrue(Tools.all.filter { it.id != "random" }.all { it.rerunOnRestore && it.actionLabelFor { 0 } == "Calculate" })
    }

    @Test fun pickerBoxIsPlainTextAndHoldsALongList() {
        val box = Randomizer.toolSpec(Scripted(0)).fields.first { it.id == "choices" }
        assertTrue(box.plainText && box.multiline)
        assertTrue(box.maxLength >= 4000)
        // no other tool has a plain-text box
        assertTrue(Tools.all.filter { it.id != "random" }.flatMap { it.fields }.none { it.plainText })
    }

    @Test fun aSuccessfulDrawMakesAOneLineTapeRecordAndAnErrorMakesNone() {
        val spec = Randomizer.toolSpec(Scripted(0, 5))
        val texts = mapOf("coins" to "1", "dice" to "2", "sides" to "6", "min" to "1", "max" to "100", "choices" to "a\nb")
        val choices = mapOf("mode" to 1, "die" to 1)
        val result = spec.run(ToolInput(texts, choices, EvalContext()))
        val record = ToolHistory.record(spec, texts, choices, result)
        assertNotNull(record)
        assertEquals("Randomizer: [Dice]; [d6]; 2", record!!.input)
        assertEquals("Rolls (2d6) = 1, 6; Total = 7", record.summary)
        assertFalse(record.summary.contains('\n'))

        val bad = spec.run(ToolInput(texts + ("dice" to "0"), choices, EvalContext()))
        assertNull(ToolHistory.record(spec, texts + ("dice" to "0"), choices, bad))
    }

    @Test fun aPickerRecordKeepsTheListOnOneLine() {
        val spec = Randomizer.toolSpec(Scripted(1))
        val texts = mapOf("choices" to "Pizza\nBurgers\nSushi")
        val choices = mapOf("mode" to 3)
        val record = ToolHistory.record(spec, texts, choices, spec.run(ToolInput(texts, choices, EvalContext())))!!
        assertEquals("Randomizer: [Picker]; Pizza / Burgers / Sushi", record.input)
        assertEquals("Picked = Burgers; Chosen from = 3 choices", record.summary)
    }
}

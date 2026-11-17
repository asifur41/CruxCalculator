package com.tapecalc.core.math.tools

import java.math.BigInteger
import java.security.SecureRandom
import kotlin.random.asKotlinRandom

/**
 * Where the Randomizer gets its luck from. [nextLong] must return a whole number from 0 up to, but not including,
 * [bound] (which is at least 1), every value equally likely. Tests give it a scripted source; the app uses [secure].
 */
fun interface RandomSource {
    fun nextLong(bound: Long): Long

    companion object {
        /** A source backed by any Kotlin [random] (handy with a fixed seed in tests). Kotlin's bounded draw has no modulo bias. */
        fun of(random: kotlin.random.Random): RandomSource = RandomSource { bound -> random.nextLong(bound) }

        /** The source the app uses: the system's secure generator, created on first use. */
        val secure: RandomSource by lazy { of(SecureRandom().asKotlinRandom()) }
    }
}

/** Either a checked value or a plain-language reason the input is not acceptable. */
sealed interface Checked<out T> {
    class Ok<T>(val value: T) : Checked<T>
    class Invalid(val message: String) : Checked<Nothing>
}

enum class CoinSide(val label: String) { HEADS("Heads"), TAILS("Tails") }

class CoinFlips(val sides: List<CoinSide>) {
    val heads: Int get() = sides.count { it == CoinSide.HEADS }
    val tails: Int get() = sides.count { it == CoinSide.TAILS }
}

class DiceRoll(val sides: Int, val rolls: List<Int>) {
    val total: Long get() = rolls.sumOf { it.toLong() }
}

class PickedChoice(val index: Int, val choices: List<String>) {
    val chosen: String get() = choices[index]
}

/**
 * The Randomizer tool: coin flips, dice, a random whole number in a range, and a random pick from a list.
 *
 * The maths is split in two so each half can be tested alone: `check…` functions turn what the person typed into
 * validated values (or an [Checked.Invalid] message), and the draw functions ([flipCoins], [rollDice], [randomInteger],
 * [pick]) do the drawing from a [RandomSource]. [toolSpec] wires both into the Tools screen.
 */
object Randomizer {
    const val ID = "random"

    const val MAX_COINS = 100
    const val MAX_DICE = 100
    const val MIN_SIDES = 2
    const val MAX_SIDES = 1_000_000

    /** The dice chips, in order; the chip after the last one is "Custom". */
    val DICE_PRESETS: List<Int> = listOf(4, 6, 8, 10, 12, 20, 100)
    const val DEFAULT_DIE_CHOICE = 1 // d6

    private val MAX_RANGE: BigInteger = BigInteger.valueOf(Long.MAX_VALUE)
    private val WHOLE_NUMBER = Regex("[+-]?\\p{Nd}+")

    // ------------------------------------------------------------------ drawing

    private fun draw(source: RandomSource, bound: Long): Long {
        val v = source.nextLong(bound)
        check(v in 0 until bound) { "Random source returned $v for a bound of $bound" }
        return v
    }

    fun flipCoins(count: Int, source: RandomSource): CoinFlips {
        require(count >= 1) { "count must be at least 1" }
        return CoinFlips(List(count) { if (draw(source, 2) == 0L) CoinSide.HEADS else CoinSide.TAILS })
    }

    fun rollDice(count: Int, sides: Int, source: RandomSource): DiceRoll {
        require(count >= 1) { "count must be at least 1" }
        require(sides >= 1) { "sides must be at least 1" }
        return DiceRoll(sides, List(count) { (draw(source, sides.toLong()) + 1).toInt() })
    }

    /** A whole number from [min] to [max], both included. The range must already be checked with [checkRange]. */
    fun randomInteger(min: BigInteger, max: BigInteger, source: RandomSource): BigInteger {
        val size = max.subtract(min).add(BigInteger.ONE)
        require(size.signum() > 0 && size <= MAX_RANGE) { "range is empty or too wide" }
        return min.add(BigInteger.valueOf(draw(source, size.toLong())))
    }

    fun pick(choices: List<String>, source: RandomSource): PickedChoice {
        require(choices.isNotEmpty()) { "choices must not be empty" }
        return PickedChoice(draw(source, choices.size.toLong()).toInt(), choices)
    }

    // ------------------------------------------------------------------ checking what was typed

    private fun grouped(n: Int): String = "%,d".format(java.util.Locale.US, n)

    /** A whole number such as `5`, `-12` or `+7` (any script's digits). Null when [text] is not one. */
    private fun wholeNumber(text: String): BigInteger? {
        val t = text.trim()
        return if (WHOLE_NUMBER.matches(t)) BigInteger(t) else null
    }

    /** [what] is the box's name, for example "Number of coins". */
    fun checkCount(text: String, what: String, max: Int): Checked<Int> {
        val t = text.trim()
        if (t.isEmpty()) return Checked.Invalid("$what is empty. Enter a whole number from 1 to $max.")
        val n = wholeNumber(t) ?: return Checked.Invalid("$what must be a whole number from 1 to $max, not \"$t\".")
        if (n < BigInteger.ONE || n > BigInteger.valueOf(max.toLong())) {
            return Checked.Invalid("$what must be from 1 to $max.")
        }
        return Checked.Ok(n.toInt())
    }

    fun checkSides(text: String): Checked<Int> {
        val t = text.trim()
        val limits = "from $MIN_SIDES to ${grouped(MAX_SIDES)}"
        if (t.isEmpty()) return Checked.Invalid("Sides is empty. Enter a whole number $limits.")
        val n = wholeNumber(t) ?: return Checked.Invalid("Sides must be a whole number $limits, not \"$t\".")
        if (n < BigInteger.valueOf(MIN_SIDES.toLong()) || n > BigInteger.valueOf(MAX_SIDES.toLong())) {
            return Checked.Invalid("Sides must be $limits.")
        }
        return Checked.Ok(n.toInt())
    }

    /** The minimum and maximum boxes together: both whole numbers, minimum not above maximum, range not absurdly wide. */
    fun checkRange(minText: String, maxText: String): Checked<Pair<BigInteger, BigInteger>> {
        val a = minText.trim()
        val b = maxText.trim()
        if (a.isEmpty()) return Checked.Invalid("Minimum is empty. Enter a whole number, for example 1.")
        if (b.isEmpty()) return Checked.Invalid("Maximum is empty. Enter a whole number, for example 100.")
        val min = wholeNumber(a) ?: return Checked.Invalid("Minimum must be a whole number, for example 1, not \"$a\".")
        val max = wholeNumber(b) ?: return Checked.Invalid("Maximum must be a whole number, for example 100, not \"$b\".")
        if (min > max) return Checked.Invalid("Minimum ($min) is larger than maximum ($max). Make the maximum at least $min.")
        if (max.subtract(min).add(BigInteger.ONE) > MAX_RANGE) {
            return Checked.Invalid("That range is too wide. Use numbers that are closer together.")
        }
        return Checked.Ok(min to max)
    }

    /** One choice per line; blank lines and spaces around a choice are ignored. */
    fun checkChoices(text: String): Checked<List<String>> {
        val choices = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (choices.isEmpty()) return Checked.Invalid("Enter at least one choice, one per line.")
        return Checked.Ok(choices)
    }

    // ------------------------------------------------------------------ the tool

    private const val MODE = "mode"
    private const val DIE = "die"
    private const val COINS = "coins"
    private const val DICE = "dice"
    private const val SIDES = "sides"
    private const val MIN = "min"
    private const val MAX = "max"
    private const val CHOICES = "choices"

    private const val COIN_MODE = 0
    private const val DICE_MODE = 1
    private const val NUMBER_MODE = 2
    private const val PICKER_MODE = 3

    private val modeNames = listOf("Coin", "Dice", "Number", "Picker")
    private val dieNames = DICE_PRESETS.map { "d$it" } + "Custom"
    private val customDieChoice = DICE_PRESETS.size

    private val coinsField = ToolField(COINS, "Number of coins", "1 to $MAX_COINS", default = "1")
    private val diceField = ToolField(DICE, "Number of dice", "1 to $MAX_DICE", default = "1")
    private val sidesField = ToolField(SIDES, "Sides on each die", "2 to ${grouped(MAX_SIDES)}", default = "6")
    private val minField = ToolField(MIN, "Minimum", "1", default = "1")
    private val maxField = ToolField(MAX, "Maximum", "100", default = "100")
    private val choicesField = ToolField(
        CHOICES, "Choices, one per line", "Pizza\nBurgers\nSushi", multiline = true, default = "", maxLength = 4000, plainText = true
    )

    private val modeOption = ToolOption(MODE, "What to randomize", modeNames, default = COIN_MODE)
    private val dieOption = ToolOption(DIE, "Die", dieNames, default = DEFAULT_DIE_CHOICE)

    private fun fieldsFor(choice: (String) -> Int): List<ToolField> = when (choice(MODE)) {
        DICE_MODE -> if (choice(DIE) == customDieChoice) listOf(diceField, sidesField) else listOf(diceField)
        NUMBER_MODE -> listOf(minField, maxField)
        PICKER_MODE -> listOf(choicesField)
        else -> listOf(coinsField)
    }

    private fun optionsFor(choice: (String) -> Int): List<ToolOption> =
        if (choice(MODE) == DICE_MODE) listOf(modeOption, dieOption) else listOf(modeOption)

    private fun actionLabel(choice: (String) -> Int): String = when (choice(MODE)) {
        DICE_MODE -> "Roll"
        NUMBER_MODE -> "Generate"
        PICKER_MODE -> "Pick one"
        else -> "Flip"
    }

    private fun joinedValues(values: List<Int>): String = values.joinToString(", ")

    private fun runCoin(input: ToolInput, source: RandomSource): ToolResult {
        val count = when (val c = checkCount(input.text(COINS), "Number of coins", MAX_COINS)) {
            is Checked.Invalid -> return ToolResult.Err(c.message)
            is Checked.Ok -> c.value
        }
        val flips = flipCoins(count, source)
        return ToolResult.Ok(
            listOf(
                ResultLine(if (count == 1) "Result" else "Results", flips.sides.joinToString(", ") { it.label }),
                ResultLine("Heads", flips.heads.toString()),
                ResultLine("Tails", flips.tails.toString())
            )
        )
    }

    private fun runDice(input: ToolInput, source: RandomSource): ToolResult {
        val count = when (val c = checkCount(input.text(DICE), "Number of dice", MAX_DICE)) {
            is Checked.Invalid -> return ToolResult.Err(c.message)
            is Checked.Ok -> c.value
        }
        val die = input.choice(DIE).coerceIn(0, customDieChoice)
        val sides = if (die == customDieChoice) {
            when (val s = checkSides(input.text(SIDES))) {
                is Checked.Invalid -> return ToolResult.Err(s.message)
                is Checked.Ok -> s.value
            }
        } else {
            DICE_PRESETS[die]
        }
        val roll = rollDice(count, sides, source)
        val name = if (count == 1) "d$sides" else "${count}d$sides"
        return ToolResult.Ok(
            listOf(
                ResultLine(if (count == 1) "Roll ($name)" else "Rolls ($name)", joinedValues(roll.rolls)),
                ResultLine("Total", roll.total.toString())
            )
        )
    }

    private fun runNumber(input: ToolInput, source: RandomSource): ToolResult {
        val (min, max) = when (val r = checkRange(input.text(MIN), input.text(MAX))) {
            is Checked.Invalid -> return ToolResult.Err(r.message)
            is Checked.Ok -> r.value
        }
        val n = randomInteger(min, max, source)
        return ToolResult.Ok(listOf(ResultLine("Random number from $min to $max", n.toString())))
    }

    private fun runPicker(input: ToolInput, source: RandomSource): ToolResult {
        val choices = when (val c = checkChoices(input.text(CHOICES))) {
            is Checked.Invalid -> return ToolResult.Err(c.message)
            is Checked.Ok -> c.value
        }
        val picked = pick(choices, source)
        return ToolResult.Ok(
            listOf(
                ResultLine("Picked", picked.chosen),
                ResultLine("Chosen from", if (choices.size == 1) "1 choice" else "${choices.size} choices")
            )
        )
    }

    /** The Tools-screen entry. Pass a different [source] to make it deterministic. */
    fun toolSpec(source: RandomSource = RandomSource.secure): ToolSpec = ToolSpec(
        id = ID,
        title = "Randomizer",
        summary = "Flip coins, roll dice, draw a random number or pick from a list. Works offline.",
        fields = listOf(coinsField, diceField, sidesField, minField, maxField, choicesField),
        options = listOf(modeOption, dieOption),
        visibleFields = ::fieldsFor,
        visibleOptions = ::optionsFor,
        actionLabel = ::actionLabel,
        // a new draw every time: never re-run it just because the person came back to the tool
        rerunOnRestore = false,
        run = { input ->
            when (input.choice(MODE)) {
                DICE_MODE -> runDice(input, source)
                NUMBER_MODE -> runNumber(input, source)
                PICKER_MODE -> runPicker(input, source)
                else -> runCoin(input, source)
            }
        }
    )
}

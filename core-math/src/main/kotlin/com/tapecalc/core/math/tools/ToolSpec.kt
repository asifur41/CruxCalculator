package com.tapecalc.core.math.tools

import com.tapecalc.core.math.EvalContext

/** A text box on a tool screen. */
data class ToolField(
    val id: String,
    val label: String,
    val hint: String = "",
    val multiline: Boolean = false,
    val default: String = ""
)

/** A one-of-several choice shown as chips (for example "Derivative / Integral / Sum"). */
data class ToolOption(val id: String, val label: String, val choices: List<String>, val default: Int = 0)

data class ResultLine(val label: String, val value: String)

sealed interface ToolResult {
    /** [lines] are shown as label/value rows; [copyText] is what the Copy button puts on the clipboard. */
    class Ok(val lines: List<ResultLine>, val copyText: String = lines.joinToString("\n") { if (it.label.isEmpty()) it.value else "${it.label} = ${it.value}" }) : ToolResult
    class Err(val message: String) : ToolResult
}

/** The values the person typed and the chips they picked, handed to [ToolSpec.run]. */
class ToolInput(private val texts: Map<String, String>, private val choices: Map<String, Int>, val ctx: EvalContext) {
    fun text(id: String): String = texts[id]?.trim().orEmpty()
    fun choice(id: String): Int = choices[id] ?: 0
}

/**
 * A calculator tool described as data: which boxes to show and how to turn them into an answer. The screen
 * draws any [ToolSpec] the same way, so all the maths lives here, in plain Kotlin that has unit tests.
 *
 * [fields] and [options] list everything the tool can ever show (the screen keeps one saved value per id). Most tools show
 * all of it. A tool whose prompts depend on an earlier choice, like a calculator menu that asks for different things after
 * you pick Normal, Binomial or Poisson, also gives [visibleFields] and/or [visibleOptions]: functions from "which chip
 * is picked" to what is shown now.
 */
class ToolSpec(
    val id: String,
    val title: String,
    val summary: String,
    val fields: List<ToolField>,
    val options: List<ToolOption> = emptyList(),
    val visibleFields: ((choice: (String) -> Int) -> List<ToolField>)? = null,
    val visibleOptions: ((choice: (String) -> Int) -> List<ToolOption>)? = null,
    val run: (ToolInput) -> ToolResult
) {
    /** The boxes to show for the current chips; [choice] returns the picked index of an option id. */
    fun fieldsFor(choice: (String) -> Int): List<ToolField> = visibleFields?.invoke(choice) ?: fields

    /** The chip rows to show for the current chips. */
    fun optionsFor(choice: (String) -> Int): List<ToolOption> = visibleOptions?.invoke(choice) ?: options

    /** The picked index of every option, from [picked] with each option's default for any that is missing. */
    fun choiceLookup(picked: Map<String, Int>): (String) -> Int = { id -> picked[id] ?: options.firstOrNull { it.id == id }?.default ?: 0 }
}

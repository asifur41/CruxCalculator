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
 */
class ToolSpec(
    val id: String,
    val title: String,
    val summary: String,
    val fields: List<ToolField>,
    val options: List<ToolOption> = emptyList(),
    val run: (ToolInput) -> ToolResult
)

package com.tapecalc.core.math.tools

/**
 * Turns a finished tool calculation into the three texts the tape stores: what was entered, the result on one line,
 * and the result with one line per value. Pure text work, so it is unit tested.
 */
object ToolHistory {

    class Record(
        /** For example `Polynomial roots: 1, -3, 2`. */
        val input: String,
        /** For example `x₁ = 1; x₂ = 2`. */
        val summary: String,
        /** The same result, one value per line. */
        val full: String
    )

    private fun oneLine(text: String): String =
        text.trim().replace(Regex("\\s*[\\r\\n]+\\s*"), " / ")

    /** What the person entered: the picked chips, then each filled box (labelled when the tool has several boxes). */
    fun inputText(spec: ToolSpec, texts: Map<String, String>, choices: Map<String, Int>): String {
        val parts = ArrayList<String>()
        for (option in spec.options) {
            val index = choices[option.id] ?: option.default
            option.choices.getOrNull(index)?.let { parts.add("[$it]") }
        }
        val labelled = spec.fields.size > 1
        for (field in spec.fields) {
            val value = oneLine(texts[field.id].orEmpty())
            if (value.isEmpty()) continue
            parts.add(if (labelled) "${field.label}: $value" else value)
        }
        val body = parts.joinToString("; ")
        return if (body.isEmpty()) spec.title else "${spec.title}: $body"
    }

    /** The record for a successful result, or null when there is nothing worth saving (an error). */
    fun record(spec: ToolSpec, texts: Map<String, String>, choices: Map<String, Int>, result: ToolResult): Record? {
        if (result !is ToolResult.Ok) return null
        if (result.lines.isEmpty()) return null
        val perLine = result.lines.map { line ->
            val value = oneLine(line.value)
            if (line.label.isEmpty()) value else "${line.label} = $value"
        }
        return Record(
            input = inputText(spec, texts, choices),
            summary = perLine.joinToString("; "),
            full = result.copyText
        )
    }
}

package com.tapecalc.core.data.domain

import java.time.Instant

/** Turns a list of tape entries into text for sharing or saving. Pure and deterministic. */
object HistoryExporter {

    fun export(format: ExportFormat, workspaceName: String, entries: List<HistoryEntry>): String = when (format) {
        ExportFormat.PLAIN_TEXT -> plain(workspaceName, entries)
        ExportFormat.MARKDOWN -> markdown(workspaceName, entries)
        ExportFormat.CSV -> csv(workspaceName, entries)
        ExportFormat.SHARE -> share(workspaceName, entries)
    }

    private fun line(e: HistoryEntry): String = "${e.expression} = ${e.displayResult}"

    private fun plain(workspace: String, entries: List<HistoryEntry>): String = buildString {
        val header = "CruxCalculator - $workspace"
        append(header).append('\n')
        append("=".repeat(header.length)).append('\n')
        entries.forEachIndexed { i, e ->
            append(i + 1).append(". ")
            if (!e.label.isNullOrBlank()) append('[').append(e.label).append("] ")
            append(line(e)).append('\n')
            if (!e.note.isNullOrBlank()) append("   note: ").append(e.note).append('\n')
            if (e.tags.isNotEmpty()) append("   tags: ").append(e.tags.joinToString(", ")).append('\n')
        }
    }

    private fun mdCell(text: String?): String = (text ?: "").replace("|", "\\|").replace("\n", " ")

    private fun markdown(workspace: String, entries: List<HistoryEntry>): String = buildString {
        append("# ").append(workspace).append("\n\n")
        append("| # | Label | Expression | Result | Note |\n")
        append("|---|-------|------------|--------|------|\n")
        entries.forEachIndexed { i, e ->
            append("| ").append(i + 1)
            append(" | ").append(mdCell(e.label))
            append(" | `").append(mdCell(e.expression)).append('`')
            append(" | ").append(mdCell(e.displayResult))
            append(" | ").append(mdCell(e.note))
            append(" |\n")
        }
    }

    /** RFC 4180 style quoting. */
    fun csvCell(value: String?): String {
        val v = value ?: ""
        return if (v.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + v.replace("\"", "\"\"") + "\"" else v
    }

    private fun csv(workspace: String, entries: List<HistoryEntry>): String = buildString {
        append("id,workspace,label,expression,result,unit,note,tags,pinned,status,created_at\n")
        for (e in entries) {
            val cells = listOf(
                e.id.toString(), workspace, e.label, e.expression,
                if (e.isError) (e.errorMessage ?: "Error") else e.resultText,
                e.unit, e.note, e.tags.joinToString(";"), e.pinned.toString(), e.status.name,
                Instant.ofEpochMilli(e.createdAt).toString()
            )
            append(cells.joinToString(",") { csvCell(it) }).append('\n')
        }
    }

    private fun share(workspace: String, entries: List<HistoryEntry>): String = buildString {
        append(workspace).append('\n')
        for (e in entries) {
            append('\n')
            if (!e.label.isNullOrBlank()) append(e.label).append('\n')
            append("  ").append(e.expression).append('\n')
            append("  = ").append(e.displayResult).append('\n')
            if (!e.note.isNullOrBlank()) append("  (").append(e.note).append(")\n")
        }
        append("\nCalculated offline with CruxCalculator")
    }
}

/** Search used by the tape screen: every word must match expression, result, label, note or a tag. */
object HistoryFilter {
    fun filter(entries: List<HistoryEntry>, query: String, pinnedOnly: Boolean = false): List<HistoryEntry> {
        val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        return entries.filter { e ->
            if (pinnedOnly && !e.pinned) return@filter false
            if (words.isEmpty()) return@filter true
            val haystack = buildString {
                append(e.expression).append(' ').append(e.displayResult).append(' ')
                append(e.label ?: "").append(' ').append(e.note ?: "").append(' ')
                append(e.tags.joinToString(" ") { "#$it" }).append(' ').append(e.tags.joinToString(" "))
            }.lowercase()
            words.all { haystack.contains(it) }
        }
    }
}

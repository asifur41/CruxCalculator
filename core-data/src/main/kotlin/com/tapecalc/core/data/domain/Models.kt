package com.tapecalc.core.data.domain

import com.tapecalc.core.math.AngleMode
import com.tapecalc.core.math.CalcError
import com.tapecalc.core.math.EvalResult

data class Workspace(val id: Long, val name: String, val sortOrder: Int)

/** OK = up to date, STALE = depends on something that was edited, UPDATED = recalculated after an edit. */
enum class EntryStatus {
    OK, STALE, UPDATED;

    companion object {
        fun fromName(name: String?): EntryStatus = entries.firstOrNull { it.name == name } ?: OK
    }
}

data class HistoryEntry(
    val id: Long = 0,
    val workspaceId: Long,
    val expression: String,
    /** Result at 15 significant digits (without unit). */
    val resultText: String = "",
    /** Result at 34 significant digits. */
    val resultFull: String = "",
    /** Serialized CalcValue used by `ans`; empty when the entry is an error. */
    val resultValue: String = "",
    val fraction: String? = null,
    val unit: String? = null,
    val isError: Boolean = false,
    val errorMessage: String? = null,
    val label: String? = null,
    val note: String? = null,
    val tags: List<String> = emptyList(),
    val pinned: Boolean = false,
    val status: EntryStatus = EntryStatus.OK,
    val angleMode: AngleMode = AngleMode.DEG,
    val createdAt: Long = 0,
    val updatedAt: Long = 0
) {
    /** What the tape shows after the equals sign. */
    val displayResult: String
        get() = when {
            isError -> errorMessage ?: "Error"
            unit != null -> "$resultText $unit"
            else -> resultText
        }
}

data class VariableItem(val name: String, val valueText: String, val displayText: String, val note: String? = null)

enum class ExportFormat(val label: String, val extension: String, val mimeType: String) {
    PLAIN_TEXT("Plain text", "txt", "text/plain"),
    MARKDOWN("Markdown", "md", "text/markdown"),
    CSV("CSV", "csv", "text/csv"),
    SHARE("Formatted text for sharing", "txt", "text/plain")
}

sealed interface SubmitResult {
    data class Added(val entry: HistoryEntry, val result: EvalResult) : SubmitResult
    data class Failed(val error: CalcError) : SubmitResult
}

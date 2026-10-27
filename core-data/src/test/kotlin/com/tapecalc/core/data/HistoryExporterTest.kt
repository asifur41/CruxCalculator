package com.tapecalc.core.data

import com.tapecalc.core.data.domain.EntryStatus
import com.tapecalc.core.data.domain.ExportFormat
import com.tapecalc.core.data.domain.HistoryEntry
import com.tapecalc.core.data.domain.HistoryExporter
import com.tapecalc.core.data.domain.HistoryFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryExporterTest {

    private val entries = listOf(
        HistoryEntry(
            id = 1, workspaceId = 1, expression = "1200+350", resultText = "1550",
            label = "Rent", note = "March, \"incl. parking\"", tags = listOf("home", "monthly"),
            pinned = true, createdAt = 0
        ),
        HistoryEntry(
            id = 2, workspaceId = 1, expression = "12 km to mi", resultText = "7.45645430684", unit = "mi",
            status = EntryStatus.UPDATED, createdAt = 86_400_000
        ),
        HistoryEntry(id = 3, workspaceId = 1, expression = "1/0", isError = true, errorMessage = "Division by zero")
    )

    @Test
    fun plainText() {
        val text = HistoryExporter.export(ExportFormat.PLAIN_TEXT, "Personal", entries)
        val lines = text.lines()
        assertEquals("CruxCalculator - Personal", lines[0])
        assertTrue(text.contains("1. [Rent] 1200+350 = 1550"))
        assertTrue(text.contains("2. 12 km to mi = 7.45645430684 mi"))
        assertTrue(text.contains("3. 1/0 = Division by zero"))
        assertTrue(text.contains("   note: March, \"incl. parking\""))
        assertTrue(text.contains("   tags: home, monthly"))
    }

    @Test
    fun markdownTable() {
        val text = HistoryExporter.export(ExportFormat.MARKDOWN, "Work | Q1", entries)
        assertTrue(text.startsWith("# Work | Q1\n"))
        assertTrue(text.contains("| 1 | Rent | `1200+350` | 1550 | March, \"incl. parking\" |"))
        assertTrue(text.contains("| 2 |  | `12 km to mi` | 7.45645430684 mi |  |"))
    }

    @Test
    fun csvEscapesCommasAndQuotes() {
        val text = HistoryExporter.export(ExportFormat.CSV, "Personal", entries)
        val lines = text.trimEnd().lines()
        assertEquals("id,workspace,label,expression,result,unit,note,tags,pinned,status,created_at", lines[0])
        assertEquals(
            "1,Personal,Rent,1200+350,1550,,\"March, \"\"incl. parking\"\"\",home;monthly,true,OK,1970-01-01T00:00:00Z",
            lines[1]
        )
        assertEquals("2,Personal,,12 km to mi,7.45645430684,mi,,,false,UPDATED,1970-01-02T00:00:00Z", lines[2])
        assertEquals("3,Personal,,1/0,Division by zero,,,,false,OK,1970-01-01T00:00:00Z", lines[3])
        assertEquals("\"a,b\"", HistoryExporter.csvCell("a,b"))
        assertEquals("plain", HistoryExporter.csvCell("plain"))
    }

    @Test
    fun shareText() {
        val text = HistoryExporter.export(ExportFormat.SHARE, "Personal", entries)
        assertTrue(text.startsWith("Personal\n"))
        assertTrue(text.contains("Rent\n  1200+350\n  = 1550"))
        assertTrue(text.endsWith("Calculated offline with CruxCalculator"))
    }

    @Test
    fun searchMatchesExpressionResultLabelNoteAndTags() {
        assertEquals(3, HistoryFilter.filter(entries, "").size)
        assertEquals(listOf(1L), HistoryFilter.filter(entries, "rent").map { it.id })
        assertEquals(listOf(1L), HistoryFilter.filter(entries, "#monthly").map { it.id })
        assertEquals(listOf(1L), HistoryFilter.filter(entries, "parking march").map { it.id })
        assertEquals(listOf(2L), HistoryFilter.filter(entries, "km").map { it.id })
        assertEquals(listOf(2L), HistoryFilter.filter(entries, "7.456").map { it.id })
        assertEquals(listOf(1L), HistoryFilter.filter(entries, "", pinnedOnly = true).map { it.id })
        assertTrue(HistoryFilter.filter(entries, "nothing-matches").isEmpty())
    }
}

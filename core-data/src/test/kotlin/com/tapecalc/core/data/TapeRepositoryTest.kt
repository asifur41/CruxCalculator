package com.tapecalc.core.data

import com.tapecalc.core.data.domain.EntryStatus
import com.tapecalc.core.data.domain.ExportFormat
import com.tapecalc.core.data.domain.SubmitResult
import com.tapecalc.core.data.repo.TapeRepository
import com.tapecalc.core.math.AngleMode
import com.tapecalc.core.math.ErrorKind
import com.tapecalc.core.math.EvalOutcome
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** History persistence and editing, exercised against in-memory DAOs. */
class TapeRepositoryTest {

    private var clock = 1_000L
    private lateinit var repo: TapeRepository
    private var wid = 0L

    @Before
    fun setUp() = runBlocking {
        clock = 1_000L
        repo = TapeRepository(FakeWorkspaceDao(), FakeHistoryDao(), FakeVariableDao()) { clock++ }
        wid = repo.ensureDefaults().first().id
    }

    private suspend fun add(expr: String, mode: AngleMode = AngleMode.DEG): SubmitResult.Added {
        val r = repo.submit(wid, expr, mode)
        assertTrue("'$expr' should succeed but was $r", r is SubmitResult.Added)
        return r as SubmitResult.Added
    }

    private suspend fun tape() = repo.observeEntries(wid).first()

    @Test
    fun defaultWorkspacesAreCreatedOnce() = runBlocking {
        val first = repo.ensureDefaults()
        assertEquals(listOf("Personal", "Work", "Study", "Engineering"), first.map { it.name })
        assertEquals(4, repo.ensureDefaults().size)
    }

    @Test
    fun submittedEntriesPersistInOrder() = runBlocking {
        add("2+2")
        add("10/4")
        add("sin(30)")
        val entries = tape()
        assertEquals(listOf("2+2", "10/4", "sin(30)"), entries.map { it.expression })
        assertEquals(listOf("4", "2.5", "0.5"), entries.map { it.resultText })
        assertEquals("5/2", entries[1].fraction)
        assertTrue(entries.all { it.status == EntryStatus.OK && !it.isError })
        assertTrue(entries[0].createdAt < entries[1].createdAt)
    }

    @Test
    fun failedEvaluationIsNotStored() = runBlocking {
        val r = repo.submit(wid, "1/0", AngleMode.DEG)
        assertTrue(r is SubmitResult.Failed)
        assertEquals(ErrorKind.DIVISION_BY_ZERO, (r as SubmitResult.Failed).error.kind)
        assertTrue(tape().isEmpty())
    }

    @Test
    fun ansRefersToThePreviousResult() = runBlocking {
        add("10")
        val second = add("ans*2")
        assertEquals("20", second.entry.resultText)
        assertEquals("30", add("ans+10").entry.resultText)
    }

    @Test
    fun variablesPersistAndAreReused() = runBlocking {
        val set = add("rate = 0.18")
        assertEquals("rate", set.result.assignedName)
        assertEquals("36", add("200*rate").entry.resultText)
        assertEquals(setOf("rate"), repo.loadVariables().keys)
        assertEquals("0.18", repo.observeVariables().first().single().displayText)
        // setVariable (variables panel) and unknown names
        assertTrue(repo.setVariable("radius = 7") is EvalOutcome.Success)
        assertTrue(repo.setVariable("1+1") is EvalOutcome.Failure)
        assertEquals("153.9380400259", add("pi*radius^2").entry.resultText)
        repo.deleteVariable("rate")
        assertNull(repo.loadVariables()["rate"])
    }

    @Test
    fun editingAnEntryUpdatesDependentResults() = runBlocking {
        val a = add("10")
        add("ans*2")
        add("ans+1")
        add("5+5")            // independent: must not change
        assertTrue(repo.editEntry(a.entry.id, "20", autoRecalc = true))
        val e = tape()
        assertEquals(listOf("20", "40", "41", "10"), e.map { it.resultText })
        assertEquals(listOf(EntryStatus.OK, EntryStatus.UPDATED, EntryStatus.UPDATED, EntryStatus.OK), e.map { it.status })
        assertEquals("20", e[0].expression)
    }

    @Test
    fun editingWithoutAutoRecalcMarksDependentsStaleThenRecalculates() = runBlocking {
        val a = add("10")
        add("ans*2")
        add("ans+1")
        repo.editEntry(a.entry.id, "100", autoRecalc = false)
        var e = tape()
        assertEquals(listOf("100", "20", "21"), e.map { it.resultText })
        assertEquals(listOf(EntryStatus.OK, EntryStatus.STALE, EntryStatus.STALE), e.map { it.status })
        repo.recalculateStale(wid)
        e = tape()
        assertEquals(listOf("100", "200", "201"), e.map { it.resultText })
        assertEquals(listOf(EntryStatus.OK, EntryStatus.UPDATED, EntryStatus.UPDATED), e.map { it.status })
    }

    @Test
    fun editingAVariableUpdatesEntriesThatUseIt() = runBlocking {
        val set = add("price = 100")
        add("price*2")
        add("1+1")
        repo.editEntry(set.entry.id, "price = 250", autoRecalc = true)
        val e = tape()
        assertEquals("500", e[1].resultText)
        assertEquals(EntryStatus.UPDATED, e[1].status)
        assertEquals(EntryStatus.OK, e[2].status)
        assertEquals("250", repo.observeVariables().first().single().displayText)
    }

    @Test
    fun editingToAnInvalidExpressionKeepsTheEntryAsAnError() = runBlocking {
        val a = add("10")
        add("ans+1")
        repo.editEntry(a.entry.id, "10/0", autoRecalc = true)
        val e = tape()
        assertTrue(e[0].isError)
        assertEquals("Division by zero", e[0].displayResult)
        assertTrue(e[1].isError)
        assertEquals(EntryStatus.UPDATED, e[1].status)
        // fixing it again repairs the chain
        repo.editEntry(a.entry.id, "10", autoRecalc = true)
        assertEquals(listOf("10", "11"), tape().map { it.resultText })
    }

    @Test
    fun labelsNotesTagsPinAndDuplicate() = runBlocking {
        val a = add("1200+350")
        repo.updateMeta(a.entry.id, "Rent", "March, includes parking", listOf("home", "#monthly", "home"))
        repo.togglePin(a.entry.id)
        var e = tape().single()
        assertEquals("Rent", e.label)
        assertEquals("March, includes parking", e.note)
        assertEquals(listOf("home", "monthly"), e.tags)
        assertTrue(e.pinned)
        val copyId = repo.duplicate(a.entry.id)
        assertNotNull(copyId)
        val all = tape()
        assertEquals(2, all.size)
        assertEquals("1550", all[1].resultText)
        assertFalse(all[1].pinned)
        repo.delete(copyId!!)
        assertEquals(1, tape().size)
        e = tape().single()
        repo.togglePin(e.id)
        assertFalse(tape().single().pinned)
    }

    @Test
    fun workspacesKeepSeparateTapes() = runBlocking {
        add("1+1")
        val work = repo.observeWorkspaces().first()[1].id
        repo.submit(work, "9*9", AngleMode.DEG)
        assertEquals(1, tape().size)
        assertEquals("81", repo.observeEntries(work).first().single().resultText)
        assertTrue(repo.deleteWorkspace(work))
        assertTrue(repo.observeEntries(work).first().isEmpty())
        val id = repo.addWorkspace("Side project")
        repo.renameWorkspace(id, "Side project 2")
        assertTrue(repo.observeWorkspaces().first().any { it.name == "Side project 2" })
    }

    @Test
    fun exportProducesAllFormats() = runBlocking {
        val a = add("2+3")
        repo.updateMeta(a.entry.id, "Quick sum", null, emptyList())
        for (format in ExportFormat.entries) {
            val text = repo.export(wid, format)
            assertTrue("$format mentions the expression", text.contains("2+3"))
            assertTrue("$format mentions the result", text.contains("5"))
        }
        assertTrue(repo.export(wid, ExportFormat.CSV).startsWith("id,workspace,label,expression"))
    }
}

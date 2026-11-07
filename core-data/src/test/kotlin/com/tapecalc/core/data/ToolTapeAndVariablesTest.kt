package com.tapecalc.core.data

import com.tapecalc.core.data.domain.SubmitResult
import com.tapecalc.core.data.repo.TapeRepository
import com.tapecalc.core.math.AngleMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Tools-tab results on the tape, and the promise that saved variables stay until the person deletes them. */
class ToolTapeAndVariablesTest {

    private var clock = 1_000L
    private lateinit var repo: TapeRepository
    private var wid = 0L

    @Before
    fun setUp() = runBlocking {
        clock = 1_000L
        repo = TapeRepository(FakeWorkspaceDao(), FakeHistoryDao(), FakeVariableDao()) { clock++ }
        wid = repo.ensureDefaults().first().id
    }

    @Test
    fun toolResultIsWrittenToTheTape() = runBlocking {
        repo.addToolEntry(wid, "Polynomial roots", "Polynomial roots: 1, -3, 2", "x₁ = 1; x₂ = 2", "x₁ = 1\nx₂ = 2")
        val e = repo.observeEntries(wid).first().single()
        assertTrue(e.isTool)
        assertEquals("Polynomial roots: 1, -3, 2", e.expression)
        assertEquals("x₁ = 1; x₂ = 2", e.displayResult)
        assertEquals("Polynomial roots", e.label)
        assertFalse(e.isError)
    }

    @Test
    fun toolResultDoesNotBecomeAnsAndSurvivesEdits() = runBlocking {
        val first = repo.submit(wid, "10", AngleMode.DEG) as SubmitResult.Added
        repo.addToolEntry(wid, "Statistics", "Statistics: 1, 2, 3", "mean = 2", "mean = 2")
        // the next calculation still sees 10 as `ans`, not the tool record
        repo.submit(wid, "ans*3", AngleMode.DEG)
        val tape = repo.observeEntries(wid).first()
        assertEquals(listOf("10", "mean = 2", "30"), tape.map { it.displayResult })
        // editing the first line recalculates `ans*3`; the tool record is untouched
        repo.editEntry(tape[0].id, "20", true)
        val after = repo.observeEntries(wid).first()
        assertEquals("mean = 2", after[1].displayResult)
        assertTrue(after[1].isTool)
        assertEquals("60", after[2].displayResult)
    }

    @Test
    fun savedVariablesSurviveClearingAndDeletingWorkspaces() = runBlocking {
        repo.submit(wid, "rate = 0.18", AngleMode.DEG)
        repo.saveRawVariable("a", "7", "7")
        val other = repo.addWorkspace("Scratch")
        repo.clearWorkspace(wid)
        assertTrue(repo.deleteWorkspace(other))
        assertEquals(setOf("rate", "a"), repo.loadVariables().keys)
    }

    @Test
    fun aVariableChangesOnlyWhenThePersonChangesOrDeletesIt() = runBlocking {
        repo.saveRawVariable("a", "7", "7")
        repo.submit(wid, "1+1", AngleMode.DEG)
        repo.addToolEntry(wid, "Tool", "in", "out", "out")
        assertEquals("7", repo.observeVariables().first().single { it.name == "a" }.displayText)
        repo.deleteVariable("a")
        assertNull(repo.loadVariables()["a"])
        assertNotNull(repo.observeEntries(wid).first())
    }
}

package com.tapecalc.core.data

import app.cash.turbine.test
import com.tapecalc.core.data.repo.TapeRepository
import com.tapecalc.core.math.AngleMode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** Verifies that the tape is a live Flow: new entries and edits are pushed to collectors. */
class TapeFlowTest {

    @Test
    fun tapeEmitsOnAddAndEdit() = runTest {
        val repo = TapeRepository(FakeWorkspaceDao(), FakeHistoryDao(), FakeVariableDao())
        val workspace = repo.ensureDefaults().first().id

        repo.observeEntries(workspace).test {
            assertEquals(0, awaitItem().size)

            val added = repo.submit(workspace, "6*7", AngleMode.DEG)
            check(added is com.tapecalc.core.data.domain.SubmitResult.Added)
            val afterAdd = awaitItem()
            assertEquals(listOf("42"), afterAdd.map { it.resultText })

            repo.editEntry(added.entry.id, "6*8", autoRecalc = true)
            assertEquals(listOf("48"), awaitItem().map { it.resultText })

            cancelAndIgnoreRemainingEvents()
        }
    }
}

@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package staff.android.streams

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import kotlin.test.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

abstract class UploadsContract {
    abstract fun ledger(): UploadLedger

    @Test fun twoFilesContributeToOneCoherentTotal() {
        val store = ledger()
        val photo = store.start("photo", 100)
        val receipt = store.start("receipt", 200)
        store.report(photo, 40); store.report(receipt, 60)
        assertEquals(2, store.state.value.files.size)
        assertEquals(100, store.state.value.totalSent)
    }

    @Test fun restartResetsOnlyOneFileAndRejectsOldGeneration() {
        val store = ledger()
        val old = store.start("photo", 100)
        val receipt = store.start("receipt", 200)
        store.report(receipt, 60)
        val current = store.start("photo", 100)
        store.report(old, 90)
        assertEquals(0, store.state.value.files.getValue("photo").sentBytes)
        assertEquals(60, store.state.value.totalSent)
        store.report(current, 25)
        assertEquals(85, store.state.value.totalSent)
    }

    @Test fun olderProgressCannotMoveCurrentFileBackwards() {
        val store = ledger(); val ticket = store.start("a", 100)
        store.report(ticket, 80); store.report(ticket, 20)
        assertEquals(80, store.state.value.totalSent)
    }

    @Test fun removePreservesOtherFilesAndLateReportsCannotResurrect() {
        val store = ledger(); val a = store.start("a", 100); val b = store.start("b", 100)
        store.report(b, 50); store.remove("a"); store.report(a, 80)
        assertEquals(setOf("b"), store.state.value.files.keys)
        assertEquals(50, store.state.value.totalSent)
    }

    @Test fun removeThenReaddNeverReusesATicket() {
        val store = ledger(); val old = store.start("a", 100)
        store.remove("a"); val current = store.start("a", 100)
        assertNotEquals(old, current)
        store.report(old, 90)
        assertEquals(0, store.state.value.totalSent)
    }

    @Test fun invalidStartCannotDisturbExistingProgress() {
        val store = ledger(); val ticket = store.start("a", 100); store.report(ticket, 20)
        val before = store.state.value
        assertFailsWith<IllegalArgumentException> { store.start("", 100) }
        assertFailsWith<IllegalArgumentException> { store.start("a", 0) }
        assertFailsWith<IllegalArgumentException> { store.start("a", 1_000_000_000_001) }
        assertEquals(before, store.state.value)
    }

    @Test fun invalidActiveProgressIsRejectedWithoutPublication() {
        val store = ledger(); val ticket = store.start("a", 100)
        val before = store.state.value
        assertFailsWith<IllegalArgumentException> { store.report(ticket, -1) }
        assertFailsWith<IllegalArgumentException> { store.report(ticket, 101) }
        assertEquals(before, store.state.value)
    }

    @Test fun obsoleteReportIsIgnoredBeforePayloadValidation() {
        val store = ledger(); val old = store.start("a", 100)
        store.start("a", 100); val before = store.state.value
        store.report(old, -1)
        assertEquals(before, store.state.value)
    }

    @Test fun earlierSnapshotsRemainUnchanged() {
        val store = ledger(); val ticket = store.start("a", 100)
        val before = store.state.value
        store.report(ticket, 50)
        assertEquals(0, before.files.getValue("a").sentBytes)
        assertEquals(50, store.state.value.totalSent)
    }

    @Test fun duplicateProgressProducesNoAdditionalState() = runTest {
        val store = ledger(); val ticket = store.start("a", 100)
        val seen = mutableListOf<UploadBoard>()
        val reader = backgroundScope.launch { store.state.collect { seen += it } }
        runCurrent(); store.report(ticket, 10); runCurrent()
        store.report(ticket, 10); runCurrent()
        assertEquals(2, seen.size)
        reader.cancel()
    }

    @Test fun concurrentReportsPreserveAllFilesAndMaximumProgress() = runBlocking {
        val store = ledger()
        val tickets = (1..8).map { store.start("file-$it", 1_000) }
        coroutineScope {
            tickets.forEach { ticket ->
                launch(Dispatchers.Default) { for (sent in 1L..1_000L) store.report(ticket, sent) }
            }
        }
        assertEquals(8, store.state.value.files.size)
        assertEquals(8_000, store.state.value.totalSent)
    }

    @Test fun concurrentSameFileStartsReturnDistinctCommittedTickets() = runBlocking {
        val store = ledger()
        val tickets = coroutineScope {
            List(40) { async(Dispatchers.Default) { store.start("a", 100) } }.awaitAll()
        }
        assertEquals(40, tickets.toSet().size)
        val latest = store.state.value.files.getValue("a").ticket
        assertEquals(tickets.maxOf { it.generation }, latest.generation)
    }

    @Test fun fileLimitAllowsReplacementButRejectsAdditionalFile() {
        val store = ledger()
        repeat(100) { store.start("file-$it", 100) }
        store.start("file-0", 200)
        assertEquals(100, store.state.value.files.size)
        val before = store.state.value
        assertFailsWith<IllegalArgumentException> { store.start("extra", 100) }
        assertEquals(before, store.state.value)
    }

    @Test fun unknownRemoveIsANoOp() {
        val store = ledger(); store.start("a", 100)
        val before = store.state.value
        store.remove("other")
        assertEquals(before, store.state.value)
    }
}

class ReferenceUploadsTest : UploadsContract() {
    override fun ledger(): UploadLedger = SolutionUploadLedger()
}

@Tag("exercise")
class LearnerUploadsTest : UploadsContract() {
    override fun ledger(): UploadLedger = ExerciseUploadLedger()
}

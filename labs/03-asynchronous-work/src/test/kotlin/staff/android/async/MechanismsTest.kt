@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package staff.android.async

import java.io.IOException
import java.util.concurrent.Executors
import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.Test

class MechanismsTest {
    // region scheduler-test
    @Test
    fun aDelayDeadlineNeedsTheScheduledTaskToRun() = runTest {
        var finished = false
        launch { delay(100); finished = true }
        runCurrent()
        advanceTimeBy(100)
        assertFalse(finished)
        runCurrent()
        assertTrue(finished)
    }
    // endregion

    @Test fun suspensionLeavesTheSameLaneFreeForInput() = runTest {
        val reply = CompletableDeferred<String>()
        val events = mutableListOf<String>()
        val request = launch { events += "started"; events += reply.await() }
        runCurrent(); events += "input"; reply.complete("reply"); request.join()
        assertEquals(listOf("started", "input", "reply"), events)
    }
    @Test fun requiredChildFailureCancelsSiblingAndWaitsForCleanup() = runTest {
        val started = CompletableDeferred<Unit>()
        var cleaned = false
        assertFailsWith<IOException> {
            requiredPair(
                { started.complete(Unit); try { awaitCancellation() } finally { cleaned = true } },
                { started.await(); throw IOException("failed") },
            )
        }
        assertTrue(cleaned)
    }
    @Test fun requiredPairReturnsBothValues() = runTest {
        assertEquals("tea" to "stock", requiredPair({ delay(20); "tea" }, { "stock" }))
    }
    @Test fun optionalFailureKeepsRequiredTitle() = runTest {
        assertEquals(ProductPanel("Tea", emptyList()), productPanel(
            { delay(10); "Tea" }, { throw IOException("offline") }))
    }
    @Test fun requiredFailureCancelsOptionalSibling() = runTest {
        val started = CompletableDeferred<Unit>()
        var cleaned = false
        assertFailsWith<IOException> {
            productPanel({ started.await(); throw IOException("no title") }, {
                started.complete(Unit)
                try { awaitCancellation() } finally { cleaned = true }
            })
        }
        assertTrue(cleaned)
    }
    @Test fun parentCancellationStillCancelsSupervisedChildren() = runTest {
        val started = CompletableDeferred<Unit>()
        var cleaned = 0
        val job = launch {
            productPanel({ try { awaitCancellation() } finally { cleaned++ } }, {
                started.complete(Unit)
                try { awaitCancellation() } finally { cleaned++ }
            })
        }
        started.await(); job.cancelAndJoin()
        assertEquals(2, cleaned)
    }
    @Test fun optionalProgrammingErrorIsNotSilentlyEmpty() = runTest {
        assertFailsWith<IllegalArgumentException> {
            productPanel({ "Tea" }, { throw IllegalArgumentException("bug") })
        }
    }
    @Test fun cancelAndJoinWaitsForFinally() = runTest {
        val events = mutableListOf<String>()
        val request = launch { holdLease(events) }; runCurrent(); request.cancelAndJoin()
        assertEquals(listOf("lease acquired", "lease released"), events)
    }
    @Test fun boundedNonCancellableCleanupCanSuspend() = runTest {
        var released = false
        val request = launch { waitWithRelease { delay(20); released = true } }
        runCurrent(); request.cancelAndJoin()
        assertTrue(released)
        assertEquals(20, currentTime)
    }
    @Test fun cleanupTimeoutBoundsCooperativeReleaseButDoesNotGuaranteeCompletion() = runTest {
        var released = false
        val request = launch { waitWithRelease { delay(500); released = true } }
        runCurrent(); request.cancelAndJoin()
        assertFalse(released)
        assertEquals(100, currentTime)
    }
    @Test fun cancelledCpuOperationNeverBeginsMatching() = runTest {
        val job = launch { matchingTitles(listOf(SearchProduct("tea", "Tea")), "tea", StandardTestDispatcher(testScheduler)) }
        job.cancel(); runCurrent(); assertTrue(job.isCancelled)
    }
    @Test fun matchingRunsOnInjectedDispatcherAndReturnsMatches() = runTest {
        assertEquals(listOf("Assam tea"), matchingTitles(
            listOf(SearchProduct("tea", "Assam tea"), SearchProduct("pot", "Pot")),
            "TEA", StandardTestDispatcher(testScheduler)))
    }
    @Test fun cancellationInsideIterationIsObservedAtTheNextCheckpoint() = runTest {
        lateinit var task: Job
        var reads = 0
        val products = object : AbstractList<SearchProduct>() {
            override val size = 10
            override fun get(index: Int): SearchProduct {
                reads++
                if (index == 1) task.cancel()
                return SearchProduct(index.toString(), "Tea")
            }
        }
        task = launch { matchingTitles(products, "tea", StandardTestDispatcher(testScheduler)) }
        runCurrent()
        assertTrue(task.isCancelled)
        assertEquals(2, reads)
    }
    @Test fun blockingAdapterMovesCallAndRestoresCallerContext() = runBlocking {
        val caller = Thread.currentThread()
        var worker: Thread? = null
        Executors.newSingleThreadExecutor { task -> Thread(task, "test-io") }
            .asCoroutineDispatcher().use { io ->
                val source = MainSafeSearchSource(BlockingCatalog { worker = Thread.currentThread(); emptyList() }, io)
                assertEquals(emptyList(), source.search("tea"))
                assertNotSame(caller, worker)
                assertSame(caller, Thread.currentThread())
            }
    }
}

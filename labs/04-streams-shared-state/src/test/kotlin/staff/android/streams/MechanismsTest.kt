@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package staff.android.streams

import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import kotlin.test.*
import org.junit.jupiter.api.Test

class MechanismsTest {
    @Test fun sequentialPipelinePreservesEmptyButDiagnosticFilterDoesNot() = runTest {
        val rows = flowOf(listOf(SavedProduct("a", "Camera"), SavedProduct("b", "")), emptyList())
        assertEquals(listOf(listOf("Camera"), emptyList()), visibleTitles(rows).toList())
        assertEquals(listOf(2), nonEmptyCounts(rows).toList())
    }

    @Test fun flowOnMovesUpstreamButNotCollector() = runBlocking {
        val caller = Thread.currentThread()
        var reader: Thread? = null
        Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { io ->
            titlesFromBlockingSource({ reader = Thread.currentThread(); emptyList() }, io).collect {
                assertSame(caller, Thread.currentThread())
                assertNotSame(caller, reader)
            }
        }
    }

    @Test fun emittingFromAnotherContextViolatesFlowInvariant() = runBlocking {
        assertFailsWith<IllegalStateException> {
            flow { withContext(Dispatchers.Default) { emit(1) } }.collect {}
        }
        Unit
    }

    @Test fun retryRestartsSourceButNotOnProgrammingFailure() = runTest {
        var starts = 0
        val value = flow {
            starts++
            if (starts < 2) throw IOException("offline")
            emit(4)
        }.retryWhen { cause, attempt -> cause is IOException && attempt < 1 }.single()
        assertEquals(4, value); assertEquals(2, starts)
        assertFailsWith<IllegalStateException> {
            flow<Int> { throw IllegalStateException("bug") }
                .retryWhen { cause, _ -> cause is IOException }.collect {}
        }
    }

    @Test fun catchDoesNotCatchDownstreamFailure() = runTest {
        var caught = false
        assertFailsWith<IOException> {
            flowOf(1).catch { caught = true }.collect { throw IOException("renderer") }
        }
        assertFalse(caught)
    }

    @Test fun equalStateAndIntermediateValuesAreConflated() = runTest {
        val state = MutableStateFlow(0)
        val values = mutableListOf<Int>()
        val reader = backgroundScope.launch { state.collect { values += it } }
        runCurrent()
        state.value = 1; state.value = 1; state.value = 2
        runCurrent()
        assertEquals(listOf(0, 2), values)
        state.value = 2; runCurrent()
        assertEquals(listOf(0, 2), values)
        reader.cancel()
    }

    @Test fun noSubscribersRetainsOnlyReplayNotExtraCapacity() = runTest {
        val transient = MutableSharedFlow<Int>(extraBufferCapacity = 8)
        repeat(100) { assertTrue(transient.tryEmit(it)) }
        assertEquals(emptyList(), transient.replayCache)
        val replayed = MutableSharedFlow<Int>(replay = 2, extraBufferCapacity = 8)
        repeat(100) { replayed.emit(it) }
        assertEquals(listOf(98, 99), replayed.take(2).toList())
    }

    @Test fun rendezvousSharedFlowWaitsForSubscriberReadiness() = runTest {
        val events = MutableSharedFlow<Int>()
        val release = CompletableDeferred<Unit>()
        val seen = mutableListOf<Int>()
        backgroundScope.launch { events.collect { seen += it; release.await() } }
        runCurrent()
        val first = launch { events.emit(1) }
        runCurrent(); assertTrue(first.isCompleted)
        val second = launch { events.emit(2) }
        runCurrent(); assertFalse(second.isCompleted)
        release.complete(Unit); runCurrent()
        second.join(); assertEquals(listOf(1, 2), seen)
    }

    @Test fun extraBufferCapacityEventuallySuspendsWithASlowSubscriber() = runTest {
        val events = MutableSharedFlow<Int>(extraBufferCapacity = 1)
        val release = CompletableDeferred<Unit>()
        val seen = mutableListOf<Int>()
        backgroundScope.launch { events.collect { seen += it; release.await() } }
        runCurrent()
        events.emit(1); runCurrent()
        events.emit(2)
        val third = launch { events.emit(3) }
        runCurrent(); assertFalse(third.isCompleted)
        release.complete(Unit); runCurrent(); third.join()
        assertEquals(listOf(1, 2, 3), seen)
    }

    @Test fun dropOldestDropsPendingValueNotAlreadyRunningAction() = runTest {
        val events = MutableSharedFlow<Int>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
        val release = CompletableDeferred<Unit>()
        val seen = mutableListOf<Int>()
        backgroundScope.launch { events.collect { seen += it; release.await() } }
        runCurrent(); events.emit(1); runCurrent()
        events.emit(2); events.emit(3)
        release.complete(Unit); runCurrent()
        assertEquals(listOf(1, 3), seen)
    }

    @Test fun bufferOneBoundsQueuedValuesAndPreservesOrder() = runTest {
        val release = CompletableDeferred<Unit>()
        val emitted = mutableListOf<Int>()
        val collected = mutableListOf<Int>()
        val job = launch {
            flow { for (i in 1..4) { emit(i); emitted += i } }.buffer(1).collect {
                collected += it; if (it == 1) release.await()
            }
        }
        runCurrent()
        assertEquals(listOf(1, 2), emitted)
        assertEquals(listOf(1), collected)
        release.complete(Unit); job.join()
        assertEquals(listOf(1, 2, 3, 4), collected)
    }

    @Test fun conflateKeepsTheNewestPendingValue() = runTest {
        val input = Channel<Int>()
        val release = CompletableDeferred<Unit>()
        val collected = mutableListOf<Int>()
        val job = launch {
            input.receiveAsFlow().conflate().collect {
                collected += it; if (it == 1) release.await()
            }
        }
        input.send(1); runCurrent()
        input.send(2); input.send(3); input.close(); runCurrent()
        assertEquals(listOf(1), collected)
        release.complete(Unit); job.join()
        assertEquals(listOf(1, 3), collected)
    }

    @Test fun collectLatestCancelsActionAndRunsCleanupBeforeReplacement() = runTest {
        val input = Channel<Int>()
        val trace = mutableListOf<String>()
        val job = launch {
            input.receiveAsFlow().collectLatest {
                trace += "start $it"
                try { if (it == 1) awaitCancellation(); trace += "done $it" }
                finally { trace += "cleanup $it" }
            }
        }
        input.send(1); runCurrent(); input.send(2); runCurrent(); input.close(); job.join()
        assertEquals(listOf("start 1", "cleanup 1", "start 2", "done 2", "cleanup 2"), trace)
    }

    @Test fun twoReadersCanLoseAnUpdateOnOneThread() = runTest {
        val state = MutableStateFlow(0)
        val gate = CompletableDeferred<Unit>()
        val jobs = List(2) { launch { val old = state.value; gate.await(); state.value = old + 1 } }
        runCurrent(); gate.complete(Unit); jobs.joinAll()
        assertEquals(1, state.value)
    }

    @Test fun atomicCartPreservesIndependentConcurrentIncrements() = runBlocking {
        val cart = AtomicCart()
        coroutineScope {
            repeat(4) {
                launch(Dispatchers.Default) { repeat(1_000) { cart.addTea(); cart.addBag() } }
            }
        }
        assertEquals(CartTotals(4_000, 4_000), cart.state.value)
        assertEquals(8_000, cart.state.value.count)
    }

    @Test fun bundleChangesBothFieldsInOneSnapshot() = runTest {
        val cart = AtomicCart()
        val seen = mutableListOf<CartTotals>()
        val collector = backgroundScope.launch { cart.state.collect { seen += it } }
        runCurrent(); cart.addBundle(); runCurrent()
        assertEquals(listOf(CartTotals(), CartTotals(1, 1)), seen)
        collector.cancel()
    }

    @Test fun mutexPreventsSellingTheLastUnitTwice() = runBlocking {
        val stock = Stock(20)
        val decisions = coroutineScope { List(100) { async(Dispatchers.Default) { stock.reserve(1) } }.awaitAll() }
        assertEquals(20, decisions.count { it })
        assertEquals(0, stock.available())
        assertFailsWith<IllegalArgumentException> { stock.reserve(0) }
        Unit
    }

    @Test fun fixedWorkersBoundActiveWorkAndDrainEveryAcceptedItem() = runTest {
        var active = 0; var peak = 0
        var pulled = 0
        val input = sequence { for (i in 1..10) { pulled++; yield(i) } }.asIterable()
        val gate = CompletableDeferred<Unit>()
        val done = mutableListOf<Int>()
        val job = launch {
            processBounded(input, 2, 1) {
                active++; peak = maxOf(peak, active)
                try { gate.await(); done += it } finally { active-- }
            }
        }
        runCurrent(); assertEquals(2, active)
        assertEquals(4, pulled)
        gate.complete(Unit); job.join()
        assertEquals(2, peak); assertEquals((1..10).toList(), done.sorted())
    }

    @Test fun workerFailureCancelsOtherWorkersAndProducer() = runTest {
        var cleaned = 0
        val siblingStarted = CompletableDeferred<Unit>()
        assertFailsWith<IOException> {
            processBounded(1..1_000, 2, 1) {
                if (it == 1) { siblingStarted.await(); throw IOException("worker") }
                try { siblingStarted.complete(Unit); awaitCancellation() } finally { cleaned++ }
            }
        }
        assertEquals(1, cleaned)
    }

    @Test fun permitClientReleasesCancelledPermitForNextCaller() = runTest {
        val entered = Channel<String>(Channel.UNLIMITED)
        val client = PreviewClient({ id -> entered.send(id); if (id == "a") awaitCancellation(); id }, 1)
        val a = backgroundScope.launch { client.load("a") }
        assertEquals("a", entered.receive())
        val b = async { client.load("b") }
        runCurrent(); assertFalse(b.isCompleted)
        a.cancel(); runCurrent()
        assertEquals("b", b.await())
    }

    @Test fun producerFailureCancelsTheActiveWorker() = runTest {
        var cleaned = false
        val input = sequence { yield(1); throw IOException("input failed") }.asIterable()
        assertFailsWith<IOException> {
            processBounded(input, 1, 0) {
                try { awaitCancellation() } finally { cleaned = true }
            }
        }
        assertTrue(cleaned)
    }

    @Test fun permitClientReleasesPermitAfterFailure() = runTest {
        val client = PreviewClient({ if (it == "bad") throw IOException("offline"); it }, 1)
        assertFailsWith<IOException> { client.load("bad") }
        assertEquals("ok", client.load("ok"))
    }

    @Test fun invalidWorkerLimitsAreRejected() = runTest {
        assertFailsWith<IllegalArgumentException> { processBounded(listOf(1), 0, 1) {} }
        assertFailsWith<IllegalArgumentException> { processBounded(listOf(1), 1, -1) {} }
        assertFailsWith<IllegalArgumentException> {
            processBounded(listOf(1), 1, Channel.UNLIMITED) {}
        }
    }
}

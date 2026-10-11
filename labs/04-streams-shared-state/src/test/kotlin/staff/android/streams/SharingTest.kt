@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package staff.android.streams

import java.io.IOException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import kotlin.test.*
import org.junit.jupiter.api.Test

class SharingTest {
    @Test fun eachColdCollectionLoadsAgain() = runTest {
        val source = CountingSavedSource()
        val items = savedItems(source)
        assertEquals(0, source.loads)
        assertEquals(items.single(), items.single())
        assertEquals(2, source.loads)
    }

    @Test fun twoReadersShareOneExecutionAndOneCanLeave() = runTest {
        var starts = 0
        var ends = 0
        val session = SharedSavedSession(backgroundScope, {
            flow {
                starts++
                try { emit(emptyList()); awaitCancellation() } finally { ends++ }
            }
        })
        assertSame(session.state, session.state)
        runCurrent()
        assertEquals(0, starts)
        val a = backgroundScope.launch { session.state.collect {} }
        val b = backgroundScope.launch { session.state.collect {} }
        runCurrent()
        assertEquals(1, starts)
        a.cancel(); runCurrent(); advanceTimeBy(2_000); runCurrent()
        assertEquals(0, ends)
        b.cancel(); session.close(); runCurrent()
        assertEquals(1, ends)
    }

    // region sharing-lifetime-test
    @Test fun returnBeforeTimeoutKeepsTheRunThenStopResetsIt() = runTest {
        var starts = 0
        var ends = 0
        val session = SharedSavedSession(backgroundScope, {
            flow {
                starts += 1
                try {
                    emit(listOf(SavedProduct("camera", "Camera")))
                    awaitCancellation()
                } finally { ends += 1 }
            }
        })
        var reader = backgroundScope.launch { session.state.collect {} }
        runCurrent()
        assertEquals(1, starts)
        reader.cancel()
        runCurrent()
        advanceTimeBy(999)
        runCurrent()
        assertEquals(0, ends)
        reader = backgroundScope.launch { session.state.collect {} }
        runCurrent()
        assertEquals(1, starts)
        reader.cancel()
        runCurrent()
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(1, ends)
        assertEquals(SavedState.Idle, session.state.value)
        reader = backgroundScope.launch { session.state.collect {} }
        runCurrent()
        assertEquals(2, starts)
        session.close()
        runCurrent()
        assertEquals(2, ends)
        reader.cancel()
    }
    // endregion

    @Test fun replayExpirationCanRetainThenResetTheLastState() = runTest {
        val session = SharedSavedSession(backgroundScope,
            { flow { emit(emptyList()); awaitCancellation() } }, 0, 500)
        val reader = backgroundScope.launch { session.state.collect {} }
        runCurrent(); reader.cancel(); runCurrent()
        assertIs<SavedState.Ready>(session.state.value)
        advanceTimeBy(499); runCurrent()
        assertIs<SavedState.Ready>(session.state.value)
        advanceTimeBy(1); runCurrent()
        assertEquals(SavedState.Idle, session.state.value)
        session.close()
    }

    @Test fun normalCompletionDoesNotCompleteStateSubscribers() = runTest {
        var starts = 0
        val session = SharedSavedSession(backgroundScope,
            { flow { starts++; emit(emptyList()) } })
        val reader = backgroundScope.launch { session.state.collect {} }
        runCurrent()
        assertTrue(reader.isActive)
        assertIs<SavedState.Ready>(session.state.value)
        advanceTimeBy(10_000); runCurrent()
        assertEquals(1, starts)
        session.close(); reader.cancel()
    }

    @Test fun expectedFailureDoesNotRetryUntilSharingRestarts() = runTest {
        var starts = 0
        val session = SharedSavedSession(backgroundScope, {
            flow { starts++; throw IOException("offline") }
        }, 0, 0)
        var reader = backgroundScope.launch { session.state.collect {} }
        runCurrent()
        assertEquals(SavedState.Failed("Saved items unavailable"), session.state.value)
        advanceTimeBy(10_000); runCurrent()
        assertEquals(1, starts)
        reader.cancel(); runCurrent()
        reader = backgroundScope.launch { session.state.collect {} }
        runCurrent(); assertEquals(2, starts)
        reader.cancel(); session.close()
    }

    @Test fun unexpectedFailureEscapesToOwnerAndKillsSharing() = runTest {
        val errors = mutableListOf<Throwable>()
        val parent = SupervisorJob(backgroundScope.coroutineContext[Job])
        val scope = CoroutineScope(backgroundScope.coroutineContext + parent +
            CoroutineExceptionHandler { _, e -> errors += e })
        var starts = 0
        val session = SharedSavedSession(scope, {
            flow { starts++; throw IllegalStateException("bug") }
        }, 0, 0)
        val first = backgroundScope.launch { session.state.collect {} }
        runCurrent()
        assertEquals(1, errors.size)
        assertIs<IllegalStateException>(errors.single())
        first.cancel(); runCurrent()
        val second = backgroundScope.launch { session.state.collect {} }
        runCurrent(); assertEquals(1, starts)
        assertTrue(parent.isActive)
        second.cancel(); session.close(); parent.cancel()
    }

    @Test fun cancellationNeverBecomesAnOfflineState() = runTest {
        val session = SharedSavedSession(backgroundScope,
            { flow { awaitCancellation() } })
        val reader = backgroundScope.launch { session.state.collect {} }
        runCurrent(); session.close(); runCurrent()
        assertFalse(session.state.value is SavedState.Failed)
        reader.cancel()
    }

    @Test fun emittedRowsAreCapturedAndEmptyIsARealResult() = runTest {
        val rows = mutableListOf(SavedProduct("camera", "Camera"))
        val session = SharedSavedSession(backgroundScope, { flowOf(rows) })
        val reader = backgroundScope.launch { session.state.collect {} }
        runCurrent(); rows.clear()
        assertEquals(1, (session.state.value as SavedState.Ready).rows.size)
        session.close(); reader.cancel()
        assertTrue(savedItems(CountingSavedSource()).single().isNotEmpty())
        assertEquals(emptyList(), visibleTitles(flowOf(emptyList())).single())
    }

    @Test fun newSharingInstancePerCallDuplicatesUpstreamWork() = runTest {
        val source = CountingSavedSource()
        fun wrong() = savedItems(source).stateIn(backgroundScope, SharingStarted.Eagerly, emptyList())
        val one = wrong(); val two = wrong()
        runCurrent()
        assertNotSame(one, two)
        assertEquals(2, source.loads)
    }

    // region sharein-test
    @Test fun shareInMulticastsWithoutInventingAnInitialValue() = runTest {
        var starts = 0
        val source = flow { starts++; emit("Camera"); awaitCancellation() }
        val shared = source.shareIn(backgroundScope, SharingStarted.Lazily, replay = 1)
        assertEquals(emptyList(), shared.replayCache)
        val a = async { shared.first() }
        val b = async { shared.first() }
        runCurrent()
        assertEquals("Camera", a.await()); assertEquals("Camera", b.await())
        assertEquals(1, starts)
        assertEquals(listOf("Camera"), shared.replayCache)
    }
    // endregion
}

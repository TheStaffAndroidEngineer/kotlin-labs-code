@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package staff.android.async

import java.io.IOException
import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.Test
import staff.android.async.preview.SearchAttempt
import staff.android.async.preview.AttemptReply
import staff.android.async.preview.acceptCurrent

class SearchTest {
    private class Harness(scope: CoroutineScope, cooperative: Boolean) {
        val source = ManualSearchSource(cooperative)
        val states = mutableListOf<SearchState>()
        val controller = SearchController(scope, source, states::add)
        fun cleanup() {
            controller.close()
            source.requests.filterNot { it.completed }.forEach { it.succeed() }
        }
    }
    private suspend fun TestScope.withSearch(cooperative: Boolean = true,
        action: suspend Harness.() -> Unit) {
        val harness = Harness(this, cooperative)
        try { harness.action() } finally { harness.cleanup(); runCurrent() }
    }

    // region latest-search-test
    @Test
    fun oldReplyCannotReplaceTheLatestQuery() = runTest {
        val source = ManualSearchSource(cooperative = false)
        val shown = mutableListOf<SearchState>()
        val controller = SearchController(this, source, shown::add)
        try {
            controller.submit("tea")
            runCurrent()
            controller.submit("teapot")
            runCurrent()
            source.requests[1].succeed(SearchProduct("pot", "Glass teapot"))
            runCurrent()
            source.requests[0].succeed(SearchProduct("tea", "Assam tea"))
            runCurrent()
            assertEquals(
                SearchState.Results("teapot", listOf(SearchProduct("pot", "Glass teapot"))),
                shown.last(),
            )
            assertEquals(1, shown.count { it is SearchState.Results })
        } finally {
            controller.close()
            source.requests.filterNot { it.completed }.forEach { it.succeed() }
        }
    }
    // endregion

    @Test fun cancelOnlyCounterexampleActuallyPublishesOldResult() = runTest {
        val source = ManualSearchSource(false)
        val shown = mutableListOf<SearchState>()
        val controller = CancelOnlySearch(this, source, shown::add)
        controller.submit("tea"); runCurrent()
        controller.submit("teapot"); runCurrent()
        source.requests[1].succeed(SearchProduct("pot", "Glass teapot")); runCurrent()
        source.requests[0].succeed(SearchProduct("tea", "Assam tea")); runCurrent()
        assertEquals("tea", (shown.last() as SearchState.Results).query)
    }
    @Test fun newerQueryCancelsCooperativeRequest() = runTest { withSearch {
        controller.submit("tea"); runCurrent()
        controller.submit("teapot"); runCurrent()
        assertTrue(source.requests[0].cancelled)
        assertFalse(source.requests[1].cancelled)
    } }
    @Test fun sameQueryAttemptsHaveDistinctAuthority() = runTest { withSearch(false) {
        controller.submit("tea"); runCurrent()
        controller.submit("teapot"); runCurrent()
        controller.submit("tea"); runCurrent()
        source.requests[2].succeed(SearchProduct("new", "Fresh tea")); runCurrent()
        source.requests[0].succeed(SearchProduct("old", "Assam tea")); runCurrent()
        source.requests[1].succeed(SearchProduct("pot", "Glass teapot")); runCurrent()
        assertEquals(listOf("Fresh tea"), (states.last() as SearchState.Results).products.map { it.title })
        assertEquals(1, states.count { it is SearchState.Results })
    } }
    @Test fun generationPredicateRejectsSameQueryWithoutCancellation() {
        val current = SearchAttempt(3, "tea")
        val old = AttemptReply(SearchAttempt(1, "tea"), listOf("Assam tea"))
        assertEquals(listOf("Fresh tea"), acceptCurrent(current, listOf("Fresh tea"), old))
    }
    @Test fun staleFailureCannotReplaceNewLoading() = runTest { withSearch(false) {
        controller.submit("tea"); runCurrent()
        controller.submit("teapot"); runCurrent()
        source.requests[0].fail(); runCurrent()
        assertEquals(SearchState.Loading("teapot"), states.last())
        assertEquals(0, states.count { it is SearchState.Failed })
    } }
    @Test fun oldCompletionDoesNotClearNewLoading() = runTest { withSearch(false) {
        controller.submit("tea"); runCurrent()
        controller.submit("teapot"); runCurrent()
        source.requests[0].succeed(SearchProduct("tea", "Assam tea")); runCurrent()
        assertEquals(SearchState.Loading("teapot"), states.last())
    } }
    @Test fun activeExpectedFailureIsVisible() = runTest { withSearch {
        controller.submit("tea"); runCurrent(); source.requests[0].fail(); runCurrent()
        assertEquals(SearchState.Failed("tea", "Search unavailable"), states.last())
    } }
    @Test fun successfulEmptyResponseIsAResult() = runTest { withSearch {
        controller.submit("tea"); runCurrent(); source.requests[0].succeed(); runCurrent()
        assertEquals(SearchState.Results("tea", emptyList()), states.last())
    } }
    @Test fun blankQueryInvalidatesOldRequestAndShowsIdle() = runTest { withSearch(false) {
        controller.submit("tea"); runCurrent(); controller.submit("  ")
        source.requests[0].succeed(SearchProduct("tea", "Assam tea")); runCurrent()
        assertEquals(SearchState.Idle, states.last())
        assertEquals(1, source.requests.size)
    } }
    @Test fun closeRejectsLateSuccessAndLaterInput() = runTest { withSearch(false) {
        controller.submit("tea"); runCurrent(); controller.close()
        val count = states.size
        source.requests[0].succeed(SearchProduct("tea", "Assam tea")); runCurrent()
        assertEquals(count, states.size)
        assertFailsWith<CancellationException> { controller.submit("teapot") }
        controller.close()
    } }
    @Test fun parentCancellationStopsChildOwner() = runTest {
        val parent = Job(coroutineContext[Job])
        val harness = Harness(CoroutineScope(coroutineContext + parent), false)
        try {
            harness.controller.submit("tea"); runCurrent(); parent.cancel()
            harness.source.requests[0].fail(); runCurrent()
            assertEquals(listOf<SearchState>(SearchState.Loading("tea")), harness.states)
            assertFailsWith<CancellationException> { harness.controller.submit("new") }
        } finally { harness.cleanup(); parent.cancel(); runCurrent() }
    }
    @Test fun resultListIsCapturedBeforeCallerMutatesIt() = runTest {
        val rows = mutableListOf(SearchProduct("tea", "Assam tea"))
        val states = mutableListOf<SearchState>()
        val controller = SearchController(this, SearchSource { rows }, states::add)
        try {
            controller.submit("tea"); runCurrent(); rows.clear()
            assertEquals(1, (states.last() as SearchState.Results).products.size)
        } finally { controller.close() }
    }
    @Test fun unexpectedFailureEscapesInsteadOfBecomingUnavailable() = runTest {
        val states = mutableListOf<SearchState>()
        assertFailsWith<IllegalStateException> {
            coroutineScope {
                val controller = SearchController(this, SearchSource { error("bad invariant") }, states::add)
                try { controller.submit("tea"); awaitCancellation() }
                finally { controller.close() }
            }
        }
        assertEquals(0, states.count { it is SearchState.Failed })
    }
    @Test fun dependencySwallowingCancellationCannotPublishFallbackRows() = runTest {
        val states = mutableListOf<SearchState>()
        val source = SearchSource { query ->
            if (query == "tea") {
                try { awaitCancellation() }
                catch (cancelled: CancellationException) { listOf(SearchProduct("old", "stale fallback")) }
            } else emptyList()
        }
        val controller = SearchController(this, source, states::add)
        try {
            controller.submit("tea"); runCurrent()
            controller.submit("teapot"); runCurrent()
            assertEquals(listOf(SearchState.Results("teapot", emptyList())), states.filterIsInstance<SearchState.Results>())
        } finally { controller.close() }
    }
}

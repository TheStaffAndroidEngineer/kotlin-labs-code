@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package staff.android.async

import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import staff.android.async.exercise.*
import staff.android.async.solution.SolutionImageBindings

abstract class ImageBindingsContractTest {
    abstract fun create(scope: CoroutineScope, source: ImageSource,
        publish: (ImageSlot, ImageState) -> Unit): ImageBindings

    protected inner class Harness(scope: CoroutineScope, cooperative: Boolean) {
        val source = ManualSearchSource(cooperative)
        val history = mutableListOf<Pair<ImageSlot, ImageState>>()
        val shown = mutableMapOf<ImageSlot, ImageState>()
        val bindings = create(scope, ImageSource { source.search(it).single().title }) { slot, state ->
            history += slot to state
            shown[slot] = state
        }
        fun complete(index: Int, token: String) = source.requests[index].succeed(SearchProduct("image", token))
        fun cleanup() {
            bindings.close()
            source.requests.filterNot { it.completed }.forEach { it.succeed(SearchProduct("cleanup", "unused")) }
        }
    }
    private suspend fun TestScope.checkBindings(cooperative: Boolean = true,
        action: suspend Harness.() -> Unit) {
        val h = Harness(this, cooperative)
        try { h.action() } finally { h.cleanup(); runCurrent() }
    }

    @Test fun loadingAppearsSynchronouslyBeforeSourceStarts() = runTest { checkBindings {
        bindings.bind(ImageSlot.LEFT, "tea")
        assertEquals(ImageState.Loading("tea"), shown[ImageSlot.LEFT])
        assertTrue(source.requests.isEmpty())
        runCurrent(); assertEquals(1, source.requests.size)
    } }
    @Test fun twoSlotsAreIndependentEvenWhenUrlsMatch() = runTest { checkBindings {
        bindings.bind(ImageSlot.LEFT, "same"); runCurrent()
        bindings.bind(ImageSlot.RIGHT, "same"); runCurrent()
        assertFalse(source.requests[0].cancelled)
        complete(1, "right-image"); complete(0, "left-image"); runCurrent()
        assertEquals(ImageState.Ready("same", "left-image"), shown[ImageSlot.LEFT])
        assertEquals(ImageState.Ready("same", "right-image"), shown[ImageSlot.RIGHT])
    } }
    @Test fun replacingLeftDoesNotCancelRight() = runTest { checkBindings {
        bindings.bind(ImageSlot.RIGHT, "right"); runCurrent()
        bindings.bind(ImageSlot.LEFT, "old"); runCurrent()
        bindings.bind(ImageSlot.LEFT, "new"); runCurrent()
        assertFalse(source.requests[0].cancelled)
        assertTrue(source.requests[1].cancelled)
        assertFalse(source.requests[2].cancelled)
    } }
    @Test fun oldLeftSuccessCannotReplaceNewLeftImage() = runTest { checkBindings(false) {
        bindings.bind(ImageSlot.LEFT, "old"); runCurrent()
        bindings.bind(ImageSlot.LEFT, "new"); runCurrent()
        complete(1, "new-image"); runCurrent(); complete(0, "old-image"); runCurrent()
        assertEquals(ImageState.Ready("new", "new-image"), shown[ImageSlot.LEFT])
    } }
    @Test fun everyBindIsANewAttemptEvenForTheSameUrl() = runTest { checkBindings(false) {
        bindings.bind(ImageSlot.LEFT, "same"); runCurrent()
        bindings.bind(ImageSlot.LEFT, "same"); runCurrent()
        complete(1, "fresh"); runCurrent(); complete(0, "stale"); runCurrent()
        assertEquals(ImageState.Ready("same", "fresh"), shown[ImageSlot.LEFT])
    } }
    @Test fun unbindCancelsOnlyItsSlot() = runTest { checkBindings {
        bindings.bind(ImageSlot.LEFT, "left"); runCurrent()
        bindings.bind(ImageSlot.RIGHT, "right"); runCurrent()
        bindings.unbind(ImageSlot.RIGHT); runCurrent()
        assertTrue(source.requests[1].cancelled)
        assertFalse(source.requests[0].cancelled)
        assertEquals(ImageState.Empty, shown[ImageSlot.RIGHT])
    } }
    @Test fun unboundSlotIgnoresLateSuccess() = runTest { checkBindings(false) {
        bindings.bind(ImageSlot.LEFT, "old"); runCurrent()
        bindings.unbind(ImageSlot.LEFT); complete(0, "late"); runCurrent()
        assertEquals(ImageState.Empty, shown[ImageSlot.LEFT])
    } }
    @Test fun rebindAfterUnbindHasNewAuthorityEvenForSameUrl() = runTest { checkBindings(false) {
        bindings.bind(ImageSlot.LEFT, "same"); runCurrent(); bindings.unbind(ImageSlot.LEFT)
        bindings.bind(ImageSlot.LEFT, "same"); runCurrent()
        complete(1, "fresh"); runCurrent(); complete(0, "stale"); runCurrent()
        assertEquals(ImageState.Ready("same", "fresh"), shown[ImageSlot.LEFT])
    } }
    @Test fun expectedFailureBelongsOnlyToMatchingSlot() = runTest { checkBindings {
        bindings.bind(ImageSlot.LEFT, "left"); runCurrent(); source.requests[0].fail(); runCurrent()
        assertEquals(ImageState.Failed("left"), shown[ImageSlot.LEFT])
        assertNull(shown[ImageSlot.RIGHT])
    } }
    @Test fun staleFailureCannotEndNewLoading() = runTest { checkBindings(false) {
        bindings.bind(ImageSlot.LEFT, "old"); runCurrent()
        bindings.bind(ImageSlot.LEFT, "new"); runCurrent()
        source.requests[0].fail(); runCurrent()
        assertEquals(ImageState.Loading("new"), shown[ImageSlot.LEFT])
    } }
    @Test fun cancellationIsNotRenderedAsFailure() = runTest { checkBindings {
        bindings.bind(ImageSlot.LEFT, "old"); runCurrent()
        bindings.bind(ImageSlot.LEFT, "new"); runCurrent()
        assertEquals(0, history.count { it.second is ImageState.Failed })
    } }
    @Test fun closeDiscardsAllLateResultsAndPublishesNothing() = runTest { checkBindings(false) {
        bindings.bind(ImageSlot.LEFT, "left"); runCurrent()
        bindings.bind(ImageSlot.RIGHT, "right"); runCurrent()
        val count = history.size
        bindings.close(); bindings.close()
        complete(0, "left-image"); source.requests[1].fail(); runCurrent()
        assertEquals(count, history.size)
        assertFailsWith<CancellationException> { bindings.bind(ImageSlot.LEFT, "later") }
        assertFailsWith<CancellationException> { bindings.unbind(ImageSlot.LEFT) }
    } }
    @Test fun parentCancellationDiscardsLateResults() = runTest {
        val owner = Job(coroutineContext[Job])
        val h = Harness(CoroutineScope(coroutineContext + owner), false)
        try {
            h.bindings.bind(ImageSlot.LEFT, "left"); runCurrent(); owner.cancel()
            val count = h.history.size
            h.complete(0, "late"); runCurrent()
            assertEquals(count, h.history.size)
            assertFailsWith<CancellationException> { h.bindings.bind(ImageSlot.RIGHT, "right") }
        } finally { h.cleanup(); owner.cancel(); runCurrent() }
    }
    @Test fun blankUrlIsRejectedWithoutDisturbingExistingBinding() = runTest { checkBindings {
        bindings.bind(ImageSlot.LEFT, "valid"); runCurrent()
        assertFailsWith<IllegalArgumentException> { bindings.bind(ImageSlot.LEFT, " ") }
        assertFalse(source.requests[0].cancelled)
        assertEquals(ImageState.Loading("valid"), shown[ImageSlot.LEFT])
    } }
}

class ReferenceImageBindingsTest : ImageBindingsContractTest() {
    override fun create(scope: CoroutineScope, source: ImageSource,
        publish: (ImageSlot, ImageState) -> Unit): ImageBindings = SolutionImageBindings(scope, source, publish)
}

@Tag("exercise")
class LearnerImageBindingsTest : ImageBindingsContractTest() {
    override fun create(scope: CoroutineScope, source: ImageSource,
        publish: (ImageSlot, ImageState) -> Unit): ImageBindings = ExerciseImageBindings(scope, source, publish)
}

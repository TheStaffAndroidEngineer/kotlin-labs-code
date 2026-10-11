package staff.android.state.examples

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.withTimeout
import staff.android.state.worked.SavedItemsStore
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield

/** Deterministic observation of the mutable-payload defect, not UI or timing code. */
fun flowExample(): List<String> = runBlocking {
    val input = sampleItems()
    val state = MutableStateFlow(LeakyState(input))
    val observedTitles = mutableListOf<String>()
    val collector = launch(start = CoroutineStart.UNDISPATCHED) {
        // Capture a String at collection time, not the mutable state reference.
        state.collect { published ->
            observedTitles.add(published.items.first().title)
        }
    }
    try {
        input[0].title = "Camera Pro"
        state.value = state.value.copy(items = state.value.items.toList())
        yield()
        listOf(
            "Titles observed at collection: $observedTitles",
            "Title read directly now: ${state.value.items.first().title}",
        )
    } finally {
        collector.cancelAndJoin()
    }
}


/** Wait for two actual observations, rather than treating a scheduler yield as proof. */
fun fixedFlowExample(): List<String> = runBlocking {
    val store = SavedItemsStore(sampleItems())
    val state = MutableStateFlow(store.snapshot())
    val before = state.value
    val observedTitles = mutableListOf<String>()
    val collector = launch(start = CoroutineStart.UNDISPATCHED) {
        state.take(2).collect { published ->
            observedTitles.add(published.items.first().title)
        }
    }
    try {
        store.rename("camera", "Camera Pro")
        state.value = store.snapshot()
        withTimeout(5_000) { collector.join() }
        listOf(
            "Titles observed at collection: $observedTitles",
            "Earlier snapshot title: ${before.items.first().title}",
            "Current title: ${state.value.items.first().title}",
        )
    } finally {
        collector.cancelAndJoin()
    }
}

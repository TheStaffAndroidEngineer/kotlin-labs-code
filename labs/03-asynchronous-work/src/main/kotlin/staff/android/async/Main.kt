package staff.android.async

import java.io.IOException
import java.util.concurrent.Executors
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import staff.android.async.preview.arrivalDemo
import staff.android.async.preview.generationDemo
import staff.android.async.preview.matchingDemo
import staff.android.async.exercise.ImageSlot
import staff.android.async.exercise.ImageSource
import staff.android.async.exercise.ImageState
import staff.android.async.solution.SolutionImageBindings

// region suspension-demo
suspend fun suspensionDemo() = coroutineScope {
    val started = CompletableDeferred<Unit>()
    val reply = CompletableDeferred<String>()
    val request = launch {
        println("request started")
        started.complete(Unit)
        val title = reply.await()
        println("reply: $title")
    }
    started.await()
    println("input handled while request waits")
    reply.complete("Glass teapot")
    request.join()
}
// endregion

suspend fun cancellationDemo(fixed: Boolean) {
    val history = mutableListOf<SearchState>()
    coroutineScope {
        val source = ManualSearchSource(cooperative = false)
        val changes = Channel<SearchState>(Channel.UNLIMITED)
        val observe: (SearchState) -> Unit = { state ->
            history += state
            check(changes.trySend(state).isSuccess)
        }
        val repaired = if (fixed) SearchController(this, source, observe) else null
        val broken = if (!fixed) CancelOnlySearch(this, source, observe) else null
        val submit: (String) -> Unit = { query ->
            if (repaired != null) repaired.submit(query) else broken!!.submit(query)
        }
        submit("tea")
        val old = source.next()
        submit("teapot")
        val latest = source.next()
        latest.succeed(SearchProduct("pot", "Glass teapot"))
        while (changes.receive() !is SearchState.Results) { /* drain loading */ }
        old.succeed(SearchProduct("tea", "Assam tea"))
        old.returned.await()
        repaired?.close()
    }
    history.forEach { println(renderSearch(it)) }
}

// region safe-search-demo
suspend fun safeSearchDemo() {
    val history = mutableListOf<SearchState>()
    coroutineScope {
        val source = ManualSearchSource(cooperative = false)
        val latestShown = CompletableDeferred<Unit>()
        val controller = SearchController(this, source) { state ->
            history += state
            if (state is SearchState.Results) latestShown.complete(Unit)
        }
        try {
            controller.submit("tea")
            val old = source.next()
            controller.submit("teapot")
            val latest = source.next()
            latest.succeed(SearchProduct("pot", "Glass teapot"))
            latestShown.await()
            old.succeed(SearchProduct("tea", "Assam tea"))
            old.returned.await()
        } finally {
            controller.close()
        }
    }
    history.forEach { println(renderSearch(it)) }
}
// endregion

suspend fun ownerCloseDemo() {
    val history = mutableListOf<SearchState>()
    coroutineScope {
        val source = ManualSearchSource(cooperative = false)
        val controller = SearchController(this, source, history::add)
        controller.submit("tea")
        val pending = source.next()
        controller.close()
        pending.succeed(SearchProduct("tea", "Assam tea"))
    }
    history.forEach { println(renderSearch(it)) }
    println("No result published after close")
}

suspend fun failureDemo() {
    val history = mutableListOf<SearchState>()
    coroutineScope {
        val failed = CompletableDeferred<Unit>()
        val source = ManualSearchSource()
        val controller = SearchController(this, source) {
            history += it
            if (it is SearchState.Failed) failed.complete(Unit)
        }
        controller.submit("tea")
        source.next().fail()
        failed.await()
        controller.close()
    }
    history.forEach { println(renderSearch(it)) }
}

// region failure-driver
suspend fun requiredFailureDemo() {
    val events = mutableListOf<String>()
    val firstStarted = CompletableDeferred<Unit>()
    try {
        requiredPair(
            first = {
                events += "first started"
                firstStarted.complete(Unit)
                try {
                    awaitCancellation()
                } finally {
                    events += "first cleaned up"
                }
            },
            second = {
                firstStarted.await()
                throw IOException("second failed")
            },
        )
    } catch (failure: IOException) {
        events += "caller caught: ${failure.message}"
    }
    events.forEach(::println)
}
// endregion

// region optional-driver
suspend fun optionalFailureDemo() {
    val panel = productPanel(
        loadTitle = { "Glass teapot" },
        loadSuggestions = { throw IOException("suggestions offline") },
    )
    println(panel)
}
// endregion

suspend fun cleanupDemo() = coroutineScope {
    val events = mutableListOf<String>()
    val request = launch(start = CoroutineStart.UNDISPATCHED) { holdLease(events) }
    request.cancelAndJoin()
    events += "join returned"
    events.forEach(::println)
}

suspend fun suspendingCleanupDemo() = coroutineScope {
    val events = mutableListOf<String>()
    val request = launch(start = CoroutineStart.UNDISPATCHED) {
        waitWithRelease { delay(1); events += "release finished" }
    }
    request.cancelAndJoin()
    events += "join returned"
    events.forEach(::println)
}

suspend fun callbackDemo() = coroutineScope {
    val started = CompletableDeferred<Unit>()
    lateinit var receivedCallback: SearchCallback
    var cancelled = false
    val api = object : LegacySearchApi {
        override fun start(query: String, callback: SearchCallback): CancelHandle {
            receivedCallback = callback
            started.complete(Unit)
            return CancelHandle { cancelled = true }
        }
    }
    val request = async { CallbackSearchSource(api).search("tea") }
    started.await()
    request.cancelAndJoin()
    receivedCallback.onSuccess(listOf(SearchProduct("tea", "Assam tea")))
    println("handle cancelled: $cancelled")
    println("request cancelled: ${request.isCancelled}")
    println("late callback did not produce a result")
}

suspend fun mainSafetyDemo() {
    val caller = Thread.currentThread()
    var worker = ""
    Executors.newSingleThreadExecutor { task -> Thread(task, "lab-io") }
        .asCoroutineDispatcher().use { io ->
            val source = MainSafeSearchSource(BlockingCatalog {
                worker = Thread.currentThread().name
                listOf(SearchProduct("tea", "Assam tea"))
            }, io)
            val result = source.search("tea")
            println("Blocking call thread: $worker")
            println("Caller resumed on original thread: ${Thread.currentThread() === caller}")
            println("Titles: ${result.map { it.title }}")
        }
}

suspend fun imageDemo() = coroutineScope {
    val source = ManualSearchSource(cooperative = false)
    val updates = Channel<Pair<ImageSlot, ImageState>>(Channel.UNLIMITED)
    val bindings = SolutionImageBindings(this, ImageSource { url ->
        source.search(url).single().title
    }) { slot, state ->
        println("$slot: $state")
        check(updates.trySend(slot to state).isSuccess)
    }
    bindings.bind(ImageSlot.LEFT, "tea-small")
    val oldLeft = source.next()
    bindings.bind(ImageSlot.RIGHT, "pot")
    val right = source.next()
    bindings.bind(ImageSlot.LEFT, "tea-large")
    val newLeft = source.next()
    right.succeed(SearchProduct("pot", "pot-image"))
    newLeft.succeed(SearchProduct("tea", "large-image"))
    var ready = 0
    while (ready < 2) if (updates.receive().second is ImageState.Ready) ready++
    oldLeft.succeed(SearchProduct("tea", "obsolete-image"))
    oldLeft.returned.await()
    bindings.close()
}

fun main(args: Array<String>) = runBlocking {
    when (args.singleOrNull() ?: "latest") {
        "arrivals" -> arrivalDemo()
        "matching" -> matchingDemo()
        "generation" -> generationDemo()
        "suspension" -> suspensionDemo()
        "cancel-only" -> cancellationDemo(fixed = false)
        "latest" -> safeSearchDemo()
        "owner-close" -> ownerCloseDemo()
        "failure" -> failureDemo()
        "required" -> requiredFailureDemo()
        "optional" -> optionalFailureDemo()
        "cleanup" -> cleanupDemo()
        "suspending-cleanup" -> suspendingCleanupDemo()
        "callback" -> callbackDemo()
        "main-safe" -> mainSafetyDemo()
        "images" -> imageDemo()
        else -> error("Unknown mode: ${args.toList()}")
    }
}

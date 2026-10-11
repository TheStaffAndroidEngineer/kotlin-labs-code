package staff.android.streams

import java.io.IOException
import java.util.concurrent.Executors
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*

// region cold-demo
suspend fun coldDemo() {
    val source = CountingSavedSource()
    val items = savedItems(source)
    items.collect { println("Header: ${it.size} saved") }
    items.collect { println("Rows: ${it.map { row -> row.title }}") }
    println("Source loads: ${source.loads}")
}
// endregion

// region shared-demo
suspend fun sharedDemo() = coroutineScope {
    val source = CountingSavedSource()
    val session = SharedSavedSession(this, { savedItems(source) })
    try {
        val header = async { session.state.filterIsInstance<SavedState.Ready>().first() }
        val rows = async { session.state.filterIsInstance<SavedState.Ready>().first() }
        println("Header: ${header.await().rows.size} saved")
        println("Rows: ${rows.await().rows.map { it.title }}")
        println("Source loads: ${source.loads}")
    } finally {
        session.close()
    }
}
// endregion

// region state-conflation-demo
suspend fun stateConflationDemo() = coroutineScope {
    val state = MutableStateFlow(0)
    val seen = mutableListOf<Int>()
    val reader = launch(start = CoroutineStart.UNDISPATCHED) {
        state.take(2).toList(seen)
    }
    state.value = 1
    state.value = 1
    state.value = 2
    reader.join()
    println("Observed states: $seen")
    println("Current state: ${state.value}")
}
// endregion

// region event-gap-demo
suspend fun eventGapDemo() {
    val transient = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val accepted = transient.tryEmit("Show receipt")
    println("Accepted without subscribers: $accepted")
    println("Replay after absence: ${transient.replayCache}")
    val retained = MutableSharedFlow<String>(replay = 1)
    retained.emit("Show receipt")
    println("First later subscriber: ${retained.first()}")
    println("Second later subscriber: ${retained.first()}")
}
// endregion

// region delivery-demo
suspend fun deliveryDemo(mode: String) = coroutineScope {
    require(mode in setOf("sequential", "buffer", "conflate"))
    val firstStarted = CompletableDeferred<Unit>()
    val releaseFirst = CompletableDeferred<Unit>()
    val producerReachedThree = CompletableDeferred<Unit>()
    val thirdAttempt = CompletableDeferred<Unit>()
    val observed = mutableListOf<Int>()
    val source = flow {
        emit(1)
        firstStarted.await()
        emit(2)
        thirdAttempt.complete(Unit)
        emit(3)
        producerReachedThree.complete(Unit)
    }
    val input = when (mode) {
        "buffer" -> source.buffer(1)
        "conflate" -> source.conflate()
        else -> source
    }
    val reader = launch {
        input.collect { value ->
            observed += value
            if (value == 1) {
                firstStarted.complete(Unit)
                releaseFirst.await()
            }
        }
    }
    firstStarted.await()
    if (mode == "conflate") producerReachedThree.await()
    if (mode == "buffer") thirdAttempt.await()
    val finished = producerReachedThree.isCompleted
    println("$mode producer finished before release: $finished")
    releaseFirst.complete(Unit)
    reader.join()
    println("$mode observed: $observed")
}
// endregion

// region latest-demo
suspend fun latestDemo() = coroutineScope {
    val started = Channel<Int>(Channel.UNLIMITED)
    val log = mutableListOf<String>()
    flow {
        for (value in 1..3) {
            emit(value)
            check(started.receive() == value)
        }
    }.collectLatest { value ->
        log += "start $value"
        started.send(value)
        try {
            if (value < 3) awaitCancellation()
            log += "finish $value"
        } finally {
            log += "cleanup $value"
        }
    }
    log.forEach(::println)
}
// endregion

// region lost-update-demo
suspend fun lostUpdateDemo(atomic: Boolean) = coroutineScope {
    val count = MutableStateFlow(0)
    val reads = Channel<Int>(2)
    val release = CompletableDeferred<Unit>()
    val jobs = List(2) {
        launch {
            val before = count.value
            reads.send(before)
            release.await()
            if (atomic) count.update { it + 1 } else count.value = before + 1
        }
    }
    println("Both read: ${reads.receive()}, ${reads.receive()}")
    release.complete(Unit)
    jobs.joinAll()
    println("${if (atomic) "Atomic" else "Broken"} count: ${count.value}")
}
// endregion

// region lifetime-demo
suspend fun lifetimeDemo() = coroutineScope {
    val subscribers = MutableStateFlow(0)
    val commands = Channel<SharingCommand>(Channel.UNLIMITED)
    val job = launch {
        SharingStarted.WhileSubscribed(1_000, 0)
            .command(subscribers).collect { commands.send(it) }
    }
    subscribers.value = 1
    println("Subscriber arrives: ${commands.receive()}")
    subscribers.value = 0
    println("After stop timeout: ${commands.receive()}")
    subscribers.value = 1
    println("Subscriber returns: ${commands.receive()}")
    job.cancelAndJoin()
}
// endregion

// region failure-demo
suspend fun failureDemo() = coroutineScope {
    var starts = 0
    val session = SharedSavedSession(this, {
        flow {
            starts += 1
            throw IOException("offline")
        }
    })
    try {
        val failure = session.state.filterIsInstance<SavedState.Failed>().first()
        println(failure.message)
        println("Source starts: $starts")
        println("Retained feed status: ${session.state.value}")
    } finally {
        session.close()
    }
}
// endregion

// region retry-demo
suspend fun retryDemo() {
    var attempts = 0
    val rows = flow {
        attempts += 1
        if (attempts == 1) throw IOException("temporary")
        emit(listOf(SavedProduct("camera", "Camera")))
    }.retryWhen { cause, attempt -> cause is IOException && attempt < 1 }
        .single()
    println("Attempts: $attempts")
    println("Rows: ${rows.map { it.title }}")
}
// endregion

// region combine-demo
suspend fun combineDemo() = coroutineScope {
    val tea = MutableStateFlow(0)
    val bags = MutableStateFlow(0)
    val seen = Channel<CartTotals>(Channel.UNLIMITED)
    val collector = launch {
        combine(tea, bags) { a, b -> CartTotals(a, b) }.collect { seen.send(it) }
    }
    println("Before: ${seen.receive()}")
    tea.value = 1
    println("Between writes: ${seen.receive()}")
    bags.value = 1
    println("After: ${seen.receive()}")
    collector.cancelAndJoin()
    val cart = AtomicCart()
    cart.addBundle()
    println("One bundle transition: ${cart.state.value}")
}
// endregion

suspend fun contextDemo() {
    val caller = Thread.currentThread()
    var upstream: Thread? = null
    Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { io ->
        titlesFromBlockingSource({
            upstream = Thread.currentThread()
            listOf(SavedProduct("camera", "Camera"))
        }, io).collect {
            println("Source used another thread: ${upstream !== caller}")
            println("Collector stayed on caller: ${Thread.currentThread() === caller}")
            println("Titles: $it")
        }
    }
}

suspend fun stockDemo() = coroutineScope {
    val stock = Stock(1)
    val decisions = List(2) { async { stock.reserve(1) } }.awaitAll()
    println("Successful reservations: ${decisions.count { it }}")
    println("Remaining: ${stock.available()}")
}

// region bounded-demo
suspend fun boundedDemo() = coroutineScope {
    var active = 0
    var peak = 0
    val started = Channel<Int>(Channel.UNLIMITED)
    val release = CompletableDeferred<Unit>()
    val completed = mutableListOf<Int>()
    val job = launch {
        processBounded(1..6, workers = 2, queueCapacity = 1) { value ->
            active += 1
            peak = maxOf(peak, active)
            started.send(value)
            try {
                release.await()
                completed += value
            } finally {
                active -= 1
            }
        }
    }
    started.receive()
    started.receive()
    println("Active while blocked: $active")
    release.complete(Unit)
    job.join()
    println("Peak active: $peak")
    println("Completed: ${completed.sorted()}")
}
// endregion

// region upload-demo
fun uploadDemo() {
    val ledger = SolutionUploadLedger()
    val photo = ledger.start("photo", 100)
    val receipt = ledger.start("receipt", 200)
    ledger.report(photo, 40)
    ledger.report(receipt, 60)
    println("Before restart: ${ledger.state.value.totalSent}")
    val replacement = ledger.start("photo", 100)
    println("After restart: ${ledger.state.value.totalSent}")
    ledger.report(photo, 90)
    println("After obsolete report: ${ledger.state.value.totalSent}")
    ledger.report(replacement, 25)
    val board = ledger.state.value
    println("Photo: ${board.files.getValue("photo").sentBytes}/100")
    println("Receipt: ${board.files.getValue("receipt").sentBytes}/200")
    println("Total sent: ${board.totalSent}/300")
}
// endregion

fun main(args: Array<String>) = runBlocking {
    when (val mode = args.firstOrNull() ?: "shared") {
        "cold" -> coldDemo()
        "shared" -> sharedDemo()
        "state" -> stateConflationDemo()
        "events" -> eventGapDemo()
        "sequential", "buffer", "conflate" -> deliveryDemo(mode)
        "latest" -> latestDemo()
        "lost" -> lostUpdateDemo(false)
        "atomic" -> lostUpdateDemo(true)
        "lifetime" -> lifetimeDemo()
        "failure" -> failureDemo()
        "retry" -> retryDemo()
        "combine" -> combineDemo()
        "context" -> contextDemo()
        "stock" -> stockDemo()
        "bounded" -> boundedDemo()
        "uploads" -> uploadDemo()
        else -> error("Unknown mode: $mode")
    }
}

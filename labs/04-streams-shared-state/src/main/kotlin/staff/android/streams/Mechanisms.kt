package staff.android.streams

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

// region sequential-pipeline
fun visibleTitles(rows: Flow<List<SavedProduct>>): Flow<List<String>> = rows
    .map { products -> products.filter { it.title.isNotBlank() } }
    .map { products -> products.map { it.title } }
// endregion

// region diagnostic-filter
fun nonEmptyCounts(rows: Flow<List<SavedProduct>>): Flow<Int> = rows
    .filter { it.isNotEmpty() }
    .map { it.size }
// endregion

// region blocking-boundary
fun titlesFromBlockingSource(
    read: () -> List<SavedProduct>,
    io: CoroutineDispatcher,
): Flow<List<String>> = flow {
    emit(read())
}.map { rows -> rows.map { it.title } }
    .flowOn(io)
// endregion

// region atomic-cart
data class CartTotals(val tea: Int = 0, val bags: Int = 0) {
    val count: Int get() = tea + bags
}

class AtomicCart {
    private val mutable = MutableStateFlow(CartTotals())
    val state: StateFlow<CartTotals> = mutable.asStateFlow()

    fun addTea() { mutable.update { it.copy(tea = it.tea + 1) } }
    fun addBag() { mutable.update { it.copy(bags = it.bags + 1) } }
    fun addBundle() {
        mutable.update { it.copy(tea = it.tea + 1, bags = it.bags + 1) }
    }
}
// endregion

// region locked-stock
class Stock(private var remaining: Int) {
    init { require(remaining >= 0) }
    private val mutex = Mutex()

    suspend fun reserve(amount: Int): Boolean = mutex.withLock {
        require(amount > 0)
        if (remaining < amount) false else {
            remaining -= amount
            true
        }
    }

    suspend fun available(): Int = mutex.withLock { remaining }
}
// endregion

// region bounded-workers
suspend fun <T> processBounded(
    items: Iterable<T>,
    workers: Int,
    queueCapacity: Int,
    process: suspend (T) -> Unit,
) = coroutineScope {
    require(workers > 0)
    require(queueCapacity in 0 until Channel.UNLIMITED)
    val queue = Channel<T>(queueCapacity)
    launch {
        try {
            for (item in items) queue.send(item)
        } finally {
            queue.close()
        }
    }
    repeat(workers) {
        launch { for (item in queue) process(item) }
    }
}
// endregion

// region permit-client
class PreviewClient(
    private val fetch: suspend (String) -> String,
    parallelism: Int,
) {
    private val permits = Semaphore(parallelism)
    suspend fun load(id: String): String = permits.withPermit { fetch(id) }
}
// endregion

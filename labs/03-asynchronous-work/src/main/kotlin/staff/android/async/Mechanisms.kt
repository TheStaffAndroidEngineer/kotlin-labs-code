package staff.android.async

import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

// region blocking-adapter
fun interface BlockingCatalog {
    fun search(query: String): List<SearchProduct>
}

class MainSafeSearchSource(
    private val catalog: BlockingCatalog,
    private val io: CoroutineDispatcher,
) : SearchSource {
    override suspend fun search(query: String): List<SearchProduct> =
        withContext(io) { catalog.search(query) }
}
// endregion

// region cooperative-cpu
suspend fun matchingTitles(
    products: List<SearchProduct>,
    query: String,
    cpu: CoroutineDispatcher,
): List<String> = withContext(cpu) {
    val matches = mutableListOf<String>()
    for (product in products) {
        currentCoroutineContext().ensureActive()
        if (product.title.contains(query, ignoreCase = true)) {
            matches += product.title
        }
    }
    matches
}
// endregion

// region required-pair
suspend fun requiredPair(
    first: suspend () -> String,
    second: suspend () -> String,
): Pair<String, String> = coroutineScope {
    val a = async { first() }
    val b = async { second() }
    a.await() to b.await()
}
// endregion

// region optional-sibling
data class ProductPanel(val title: String, val suggestions: List<String>)

suspend fun productPanel(
    loadTitle: suspend () -> String,
    loadSuggestions: suspend () -> List<String>,
): ProductPanel = supervisorScope {
    val title = async { loadTitle() }
    val suggestions = async { loadSuggestions() }
    val requiredTitle = title.await()
    val optionalSuggestions = try {
        suggestions.await()
    } catch (failure: IOException) {
        emptyList()
    }
    ProductPanel(requiredTitle, optionalSuggestions)
}
// endregion

// region lease-cleanup
suspend fun holdLease(events: MutableList<String>): Nothing {
    events += "lease acquired"
    try {
        awaitCancellation()
    } finally {
        events += "lease released"
    }
}
// endregion

// region suspending-cleanup
suspend fun waitWithRelease(release: suspend () -> Unit): Nothing {
    try {
        awaitCancellation()
    } finally {
        withContext(NonCancellable) {
            withTimeout(100) { release() }
        }
    }
}
// endregion

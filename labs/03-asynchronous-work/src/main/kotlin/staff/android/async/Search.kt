package staff.android.async

import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

// region search-contract
data class SearchProduct(val id: String, val title: String)

fun interface SearchSource {
    suspend fun search(query: String): List<SearchProduct>
}

sealed interface SearchState {
    data object Idle : SearchState
    data class Loading(val query: String) : SearchState
    data class Results(
        val query: String,
        val products: List<SearchProduct>,
    ) : SearchState
    data class Failed(val query: String, val message: String) : SearchState
}
// endregion

/**
 * Calls, coroutine resumptions, and publish are confined to one serialized lane.
 * publish must be a non-suspending observer, not a reentrant call to submit/close.
 * The source returns data; it must never publish screen state independently.
 * IOException is this fixture's expected source failure. Other failures propagate.
 */
// region search-controller
// region search-owner
class SearchController(
    parentScope: CoroutineScope,
    private val source: SearchSource,
    private val publish: (SearchState) -> Unit,
) {
    private val owner = Job(requireNotNull(parentScope.coroutineContext[Job]))
    private val scope = CoroutineScope(parentScope.coroutineContext + owner)
    private var generation = 0L
    private var current: Job? = null
    // endregion

    // region search-submit
    fun submit(query: String) {
        owner.ensureActive()
        val ticket = ++generation
        current?.cancel()
        current = null
        if (query.isBlank()) {
            publish(SearchState.Idle)
            return
        }
        publish(SearchState.Loading(query))
        current = scope.launch {
            val products = try {
                source.search(query)
            } catch (failure: IOException) {
                ensureActive()
                if (ticket == generation) {
                    publish(SearchState.Failed(query, "Search unavailable"))
                }
                return@launch
            }
            ensureActive()
            if (ticket == generation) {
                publish(SearchState.Results(query, products.toList()))
            }
        }
    }
    // endregion

    // region search-close
    fun close() {
        ++generation
        owner.cancel()
        current = null
    }
}
// endregion
// endregion

fun renderSearch(state: SearchState): String = when (state) {
    SearchState.Idle -> "Idle"
    is SearchState.Loading -> "Loading ${state.query}"
    is SearchState.Results -> "Results ${state.query}: ${state.products.map { it.title }}"
    is SearchState.Failed -> "Failed ${state.query}: ${state.message}"
}

/** Deliberately broken: cancellation requests alone do not check publication. */
// region cancel-only
class CancelOnlySearch(
    private val scope: CoroutineScope,
    private val source: SearchSource,
    private val publish: (SearchState) -> Unit,
) {
    private var current: Job? = null

    fun submit(query: String) {
        current?.cancel()
        publish(SearchState.Loading(query))
        current = scope.launch {
            val products = source.search(query)
            publish(SearchState.Results(query, products))
        }
    }
}
// endregion

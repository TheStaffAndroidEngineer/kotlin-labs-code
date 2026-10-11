package staff.android.streams

import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*

// region saved-product
data class SavedProduct(val id: String, val title: String)
// endregion

// region cold-source
class CountingSavedSource {
    var loads: Int = 0
        private set

    suspend fun load(): List<SavedProduct> {
        loads += 1
        return listOf(
            SavedProduct("camera", "Camera"),
            SavedProduct("bag", "Bag"),
        )
    }
}
// endregion

// region cold-flow
fun savedItems(source: CountingSavedSource): Flow<List<SavedProduct>> = flow {
    emit(source.load())
}
// endregion

// region saved-state
sealed interface SavedState {
    data object Idle : SavedState
    data object Loading : SavedState
    data class Ready(val rows: List<SavedProduct>) : SavedState
    data class Failed(val message: String) : SavedState
}
// endregion

// region shared-session
class SharedSavedSession(
    parentScope: CoroutineScope,
    source: () -> Flow<List<SavedProduct>>,
    stopTimeoutMillis: Long = 1_000,
    replayExpirationMillis: Long = 0,
) {
    private val parentJob = requireNotNull(parentScope.coroutineContext[Job])
    private val owner = Job(parentJob)
    private val scope = CoroutineScope(parentScope.coroutineContext + owner)

    val state: StateFlow<SavedState> = source()
        .map<List<SavedProduct>, SavedState> { SavedState.Ready(it.toList()) }
        .onStart { emit(SavedState.Loading) }
        .catch { failure ->
            if (failure is IOException) {
                emit(SavedState.Failed("Saved items unavailable"))
            } else {
                throw failure
            }
        }
        .stateIn(
            scope,
            SharingStarted.WhileSubscribed(
                stopTimeoutMillis = stopTimeoutMillis,
                replayExpirationMillis = replayExpirationMillis,
            ),
            SavedState.Idle,
        )

    fun close() { owner.cancel() }
}
// endregion

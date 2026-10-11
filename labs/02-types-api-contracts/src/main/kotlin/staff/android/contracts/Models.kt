package staff.android.contracts

// region id-model
@JvmInline
value class ProductId private constructor(val value: String) {
    companion object {
        fun parse(raw: String?): IdParseResult = when {
            raw == null -> IdParseResult.Missing
            raw.isBlank() -> IdParseResult.Blank
            else -> IdParseResult.Valid(ProductId(raw))
        }
    }
}

sealed interface IdParseResult {
    data object Missing : IdParseResult
    data object Blank : IdParseResult
    data class Valid(val id: ProductId) : IdParseResult
}
// endregion

// region product
data class SavedProduct(val id: ProductId, val title: String) {
    init { require(title.isNotBlank()) }
}
// endregion

// region failure-model
sealed interface LoadFailure {
    data object Unavailable : LoadFailure
    data class InvalidPayload(
        val field: String,
        val rowIndex: Int?,
        val problem: String
    ) : LoadFailure
}

sealed interface Outcome<out T> {
    data class Success<T>(val value: T) : Outcome<T>
    data class Failure(val reason: LoadFailure) : Outcome<Nothing>
}
// endregion

// region state-model
sealed interface Refresh {
    data object Idle : Refresh
    data object Running : Refresh
    data class Failed(val reason: LoadFailure) : Refresh
}

sealed interface LoadState {
    data object Loading : LoadState
    data class Failed(val reason: LoadFailure) : LoadState
    data class Content(
        val items: List<SavedProduct>,
        val refresh: Refresh = Refresh.Idle
    ) : LoadState
}
// endregion

// region start-load
fun startLoad(current: LoadState): LoadState = when (current) {
    is LoadState.Content -> current.copy(refresh = Refresh.Running)
    LoadState.Loading, is LoadState.Failed -> LoadState.Loading
}
// endregion

// region finish-load
fun finishLoad(
    current: LoadState,
    result: Outcome<List<SavedProduct>>
): LoadState = when (result) {
    is Outcome.Success -> LoadState.Content(result.value.toList())
    is Outcome.Failure -> when (current) {
        is LoadState.Content -> current.copy(
            refresh = Refresh.Failed(result.reason)
        )
        LoadState.Loading, is LoadState.Failed ->
            LoadState.Failed(result.reason)
    }
}
// endregion

// region render
fun render(state: LoadState): String = when (state) {
    LoadState.Loading -> "Loading saved items"
    is LoadState.Failed -> "Cannot load: ${message(state.reason)}"
    is LoadState.Content -> {
        val rows = state.items.joinToString { it.title }
            .ifEmpty { "No saved items" }
        val status = when (val refresh = state.refresh) {
            Refresh.Idle -> "up to date"
            Refresh.Running -> "refreshing"
            is Refresh.Failed -> message(refresh.reason)
        }
        "$rows | $status"
    }
}

fun message(reason: LoadFailure): String = when (reason) {
    LoadFailure.Unavailable -> "temporarily unavailable"
    is LoadFailure.InvalidPayload -> "invalid saved-items response"
}
// endregion

fun product(id: String = "camera", title: String = "Camera"): SavedProduct {
    val valid = ProductId.parse(id) as IdParseResult.Valid
    return SavedProduct(valid.id, title)
}

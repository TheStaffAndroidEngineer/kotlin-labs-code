package staff.android.contracts

// region source-sink
fun interface Source<out T> {
    fun read(): T
}

fun interface Sink<in T> {
    fun accept(value: T)
}
// endregion

// region map
fun <T, R> Outcome<T>.map(transform: (T) -> R): Outcome<R> =
    when (this) {
        is Outcome.Success -> Outcome.Success(transform(value))
        is Outcome.Failure -> this
    }
// endregion

// region inline-map
inline fun <T, R> Outcome<T>.mapInline(
    transform: (T) -> R
): Outcome<R> = when (this) {
    is Outcome.Success -> Outcome.Success(transform(value))
    is Outcome.Failure -> this
}
// endregion

// region callback-owner
class SelectionHandler(private val onSelected: (ProductId) -> Unit) {
    fun select(id: ProductId) { onSelected(id) }
}
// endregion

// region reified
inline fun <reified T> firstOfType(values: List<*>): T? =
    values.firstOrNull { it is T } as T?
// endregion

// region nonlocal-return
fun firstTitle(result: Outcome<List<SavedProduct>>): String {
    val mapped = result.mapInline { items ->
        if (items.isEmpty()) return "No saved items"
        items.first().title
    }
    return when (mapped) {
        is Outcome.Success -> mapped.value
        is Outcome.Failure -> "Cannot load"
    }
}
// endregion

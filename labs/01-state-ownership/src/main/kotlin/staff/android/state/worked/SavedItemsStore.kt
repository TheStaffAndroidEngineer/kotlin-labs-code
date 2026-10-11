package staff.android.state.worked

import staff.android.state.examples.MutableItem

// Values are shared only after mutable input has been detached at the boundary.
data class SavedItem(val id: String, val title: String, val labels: List<String>)
data class SavedItemsState(val items: List<SavedItem>)

/**
 * A single-thread-confined teaching store. It provides historical snapshots to
 * ordinary Kotlin callers honoring the read-only API. It is not a concurrency
 * primitive or an immutable collection library; mutation by casts/Java is outside
 * this API contract. Production boundaries may require stronger enforcement.
 */
class SavedItemsStore(seed: List<MutableItem>) {
    private var current = snapshotOf(seed)

    fun snapshot(): SavedItemsState = current

    // region rename
    fun rename(id: String, title: String) {
        val updatedItems = current.items.map { item ->
            if (item.id == id) item.copy(title = title) else item
        }
        current = current.copy(items = updatedItems)
    }
    // endregion rename

    fun addLabel(id: String, label: String) {
        current = current.copy(items = current.items.map { item ->
            if (item.id == id && label !in item.labels) {
                item.copy(labels = item.labels + label)
            } else item
        })
    }

    fun replaceAll(input: List<MutableItem>) {
        current = snapshotOf(input)
    }

    private fun snapshotOf(input: List<MutableItem>): SavedItemsState {
        require(input.map { it.id }.distinct().size == input.size) { "IDs must be unique" }
        val capturedItems = input.map { incoming ->
            SavedItem(
                id = incoming.id,
                title = incoming.title,
                labels = incoming.labels.toList(),
            )
        }
        return SavedItemsState(capturedItems)
    }
}


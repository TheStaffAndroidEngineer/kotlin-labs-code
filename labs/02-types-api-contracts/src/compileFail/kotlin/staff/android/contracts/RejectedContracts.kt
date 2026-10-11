package staff.android.contracts

// This source set is deliberately rejected. It is excluded from normal build/test.
// Each line marked EXPECT is a separate promised compiler barrier.
fun needsProductId(id: ProductId) = id.value
fun wrongIdentity() = needsProductId("camera") // EXPECT: String is not ProductId

fun mutableVariance() {
    val products = mutableListOf(product())
    val objects: MutableList<Any> = products // EXPECT: invariant mutable list
    objects.add("not a product")
}

interface BrokenSource<out T> {
    fun accept(value: T) // EXPECT: out T cannot be used as input
}

fun incomplete(state: LoadState): String = when (state) { // EXPECT: not exhaustive
    LoadState.Loading -> "Loading"
    is LoadState.Content -> "Content"
}

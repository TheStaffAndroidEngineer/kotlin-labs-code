package staff.android.state.examples

// Intentionally mutable input model used to reproduce the chapter's defects.
data class MutableItem(val id: String, var title: String, val labels: MutableList<String>)
data class LeakyState(val items: List<MutableItem>)

data class BodyPropertyItem(val id: String) {
    var title: String = ""
}

fun sampleItems(): MutableList<MutableItem> = mutableListOf(
    MutableItem("camera", "Camera", mutableListOf("travel")),
    MutableItem("bag", "Bag", mutableListOf("daily")),
)

fun aliasExample(): List<String> {
    val repositoryItems = sampleItems()
    val before = LeakyState(repositoryItems)
    repositoryItems[0].title = "Camera Pro"
    repositoryItems.add(MutableItem("tripod", "Tripod", mutableListOf()))
    return listOf(
        "Old snapshot title: ${before.items[0].title}",
        "Old snapshot count: ${before.items.size}",
        "Shared list: ${before.items === repositoryItems}",
    )
}

fun shallowCopyExample(): List<String> {
    val original = LeakyState(sampleItems())
    val copied = original.copy(items = original.items.toList())
    copied.items[0].labels.add("sale")
    return listOf(
        "Shared outer state: ${original === copied}",
        "Shared first item: ${original.items[0] === copied.items[0]}",
        "Original labels: ${original.items[0].labels}",
    )
}

fun equalityExample(): List<String> {
    val first = BodyPropertyItem("camera").apply { title = "Camera" }
    val second = BodyPropertyItem("camera").apply { title = "Camera Pro" }
    return listOf(
        "Same instance: ${first === second}",
        "Equal values: ${first == second}",
        "Copy title: '${first.copy().title}'",
    )
}


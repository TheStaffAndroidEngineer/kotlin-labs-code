package staff.android.state

import staff.android.state.examples.*
import staff.android.state.worked.SavedItemsStore

fun main(args: Array<String>) {
    val lines = when (args.singleOrNull() ?: "alias") {
        "references" -> { referenceDemo(); emptyList() }
        "capture" -> { capturedTitleDemo(); emptyList() }
        "alias" -> aliasExample()
        "shallow" -> shallowCopyExample()
        "equality" -> equalityExample()
        "flow" -> flowExample()
        "flow-fixed" -> fixedFlowExample()
        "fixed" -> {
            val input = sampleItems()
            val store = SavedItemsStore(input)
            val before = store.snapshot()
            input[0].labels.add("external")
            store.rename("camera", "Camera Pro")
            store.addLabel("camera", "sale")
            listOf(
                "Before: ${before.items.first()}",
                "After: ${store.snapshot().items.first()}",
                "Unchanged item shared: ${before.items[1] === store.snapshot().items[1]}",
            )
        }
        else -> error("Choose references, capture, alias, shallow, equality, flow, flow-fixed, or fixed")
    }
    lines.forEach(::println)
}


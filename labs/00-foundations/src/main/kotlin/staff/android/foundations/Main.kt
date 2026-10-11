package staff.android.foundations

fun main(args: Array<String>) {
    val demos = linkedMapOf<String, () -> Unit>(
        "empty-draft" to ::emptyDraftDemo,
        "quantity" to ::quantityDemo,
        "rules" to ::rulesDemo,
        "cancel" to ::cancelValueDemo,
        "references" to ::referencesDemo,
        "values" to ::valuesDemo,
        "copy" to ::copyDemo,
        "nullable" to ::nullableDemo,
        "catalog" to ::catalogDemo,
        "collections" to ::collectionsDemo,
        "interfaces" to ::interfacesDemo,
        "scopes" to ::scopesDemo,
    )
    val name = args.singleOrNull() ?: "quantity"
    val demo = demos[name]
    requireNotNull(demo) { "Choose one of: ${demos.keys.joinToString()}" }
    demo()
}

// region empty-draft
fun emptyDraftDemo() {
    val acceptedQuantity = 2
    val draftText = ""
    val candidate = draftText.toIntOrNull()
    println("accepted=$acceptedQuantity")
    println("candidate=$candidate")
}
// endregion empty-draft

// region quantity
fun quantityDemo() {
    var acceptedQuantity = 2
    var draftText = ""
    println("empty: accepted=$acceptedQuantity")
    draftText = "3"
    val candidate = draftText.toIntOrNull()
    println("typed=$candidate; accepted=$acceptedQuantity")
    // This branch represents pressing Confirm.
    if (candidate != null) acceptedQuantity = candidate
    println("confirmed: accepted=$acceptedQuantity")
}
// endregion quantity

// region quantity-rule
fun nextQuantity(
    current: Int,
    increase: Boolean,
    limit: Int = 9,
): Int {
    require(limit >= 0)
    require(current in 0..limit)
    return when {
        increase && current < limit -> current + 1
        !increase && current > 0 -> current - 1
        else -> current
    }
}
// endregion quantity-rule

// region rule-calls
fun rulesDemo() {
    println(nextQuantity(current = 2, increase = true))
    println(nextQuantity(current = 8, increase = true))
    println(nextQuantity(current = 9, increase = true))
    println(nextQuantity(current = 0, increase = false))
}
// endregion rule-calls

// region cancel-value
fun cancelValueDemo() {
    val acceptedQuantity = 2
    var draftQuantity = acceptedQuantity
    draftQuantity = 3
    println("accepted=$acceptedQuantity; draft=$draftQuantity")
    draftQuantity = acceptedQuantity
    println("after Cancel=$draftQuantity")
}
// endregion cancel-value

// region references
class MutableSelection(val productId: String, var quantity: Int)

fun referencesDemo() {
    val accepted = MutableSelection("tea", 2)
    val draft = accepted
    draft.quantity = 3
    println("accepted=${accepted.quantity}")
    println(accepted === draft)
}
// endregion references

// region values
data class Selection(val productId: String, val quantity: Int)

fun valuesDemo() {
    val accepted = Selection("tea", 2)
    val unchanged = accepted.copy()
    val draft = accepted.copy(quantity = 3)
    println(accepted == unchanged)
    println(accepted === unchanged)
    println("accepted=${accepted.quantity}; draft=${draft.quantity}")
}
// endregion values

// region shallow-copy
data class GiftSelection(
    val selection: Selection,
    val giftOptions: MutableList<String>,
)

fun copyDemo() {
    val accepted = GiftSelection(
        Selection("tea", 2), mutableListOf("wrap"),
    )
    val draft = accepted.copy(
        selection = accepted.selection.copy(quantity = 3),
    )
    draft.giftOptions.add("note")
    println(accepted.giftOptions)
    println(accepted.giftOptions === draft.giftOptions)
}
// endregion shallow-copy

// region parse-quantity
fun parseQuantity(raw: String?, maximum: Int = 9): Int? {
    require(maximum >= 0)
    val text = raw?.trim() ?: return null
    val quantity = text.toIntOrNull() ?: return null
    if (quantity !in 0..maximum) return null
    return quantity
}

fun nullableDemo() {
    println(parseQuantity(" 3 "))
    println(parseQuantity("three"))
    println(parseQuantity(null))
    println(parseQuantity("0"))
}
// endregion parse-quantity

// region product
data class Product(
    val id: String,
    val title: String,
    val stock: Int,
)
// endregion product

// region catalog
fun catalogDemo() {
    val catalog = listOf(
        Product("tea", "Tea", 4),
        Product("cup", "Cup", 0),
    )
    val available = catalog.filter { product -> product.stock > 0 }
    val titles = available.map { it.title }
    println(titles)
    println(catalog.size)
}
// endregion catalog

// region collection-alias
fun collectionsDemo() {
    val editable = mutableListOf("wrap")
    val readOnly: List<String> = editable
    val membershipCopy = editable.toList()
    editable.add("note")
    println(readOnly)
    println(membershipCopy)
}
// endregion collection-alias

// region interfaces
interface Catalog {
    fun find(id: String): Product?
}

class MemoryCatalog(products: List<Product>) : Catalog {
    private val byId = products.associateBy { it.id }
    override fun find(id: String): Product? = byId[id]
}

fun titleFor(catalog: Catalog, id: String): String {
    val product = catalog.find(id)
    return product?.title ?: "Unavailable"
}
// endregion interfaces

fun interfacesDemo() {
    val catalog: Catalog = MemoryCatalog(
        listOf(Product("tea", "Tea", 4)),
    )
    println(titleFor(catalog, "tea"))
    println(titleFor(catalog, "absent"))
}

// region scope-functions
fun Product.displayLabel(): String = "$title ($stock available)"

fun scopesDemo() {
    val catalog: Catalog = MemoryCatalog(
        listOf(Product("tea", "Tea", 4)),
    )
    val label = catalog.find("tea")?.let { product ->
        product.displayLabel()
    } ?: "Unavailable"
    val draft = MutableSelection("tea", 2).apply {
        quantity = 3
    }
    println(label)
    println(draft.quantity)
}
// endregion scope-functions

package staff.android.state.examples

// Small, complete demonstrations used before collections or Flow enter the chapter.
data class Product(var title: String)

fun referenceDemo() {
    val product = Product("Camera")
    val before = product
    product.title = "Camera Pro"
    println(before.title)
}

fun capturedTitleDemo() {
    val product = Product("Camera")
    val titleBefore = product.title
    product.title = "Camera Pro"
    println(titleBefore)
    println(product.title)
}

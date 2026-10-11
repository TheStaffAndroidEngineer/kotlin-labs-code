package staff.android.contracts

import java.io.IOException

// region capture-java
fun loadSaved(
    source: LegacySavedSource
): Outcome<List<SavedProduct>> {
    val rows: List<LegacySavedProduct?>? = try {
        source.read()
    } catch (failure: IOException) {
        return Outcome.Failure(LoadFailure.Unavailable)
    }
    return validateRows(rows)
}
// endregion

// region validate-row
fun validateRow(
    row: LegacySavedProduct?,
    index: Int
): Outcome<SavedProduct> {
    if (row == null) return invalid("row", index, "missing")
    val rawId: String? = row.id
    val rawTitle: String? = row.title
    val id = when (val parsed = ProductId.parse(rawId)) {
        IdParseResult.Missing -> return invalid("id", index, "missing")
        IdParseResult.Blank -> return invalid("id", index, "blank")
        is IdParseResult.Valid -> parsed.id
    }
    val title = rawTitle ?: return invalid("title", index, "missing")
    if (title.isBlank()) return invalid("title", index, "blank")
    return Outcome.Success(SavedProduct(id, title))
}

private fun invalid(
    field: String,
    index: Int?,
    problem: String
): Outcome.Failure = Outcome.Failure(
    LoadFailure.InvalidPayload(field, index, problem)
)
// endregion

// region validate-rows
fun validateRows(
    rows: List<LegacySavedProduct?>?
): Outcome<List<SavedProduct>> {
    if (rows == null) return invalid("items", null, "missing")
    val accepted = mutableListOf<SavedProduct>()
    val seen = mutableSetOf<ProductId>()
    for ((index, row) in rows.withIndex()) {
        when (val result = validateRow(row, index)) {
            is Outcome.Failure -> return result
            is Outcome.Success -> {
                val product = result.value
                if (!seen.add(product.id)) {
                    return invalid("id", index, "duplicate")
                }
                accepted.add(product)
            }
        }
    }
    return Outcome.Success(accepted.toList())
}
// endregion

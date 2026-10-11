package staff.android.contracts

// region lossy-empty
// Deliberately lossy example; do not use as the adapter contract.
fun rowsOrEmpty(result: Outcome<List<SavedProduct>>): List<SavedProduct> =
    when (result) {
        is Outcome.Success -> result.value
        is Outcome.Failure -> emptyList()
    }
// endregion

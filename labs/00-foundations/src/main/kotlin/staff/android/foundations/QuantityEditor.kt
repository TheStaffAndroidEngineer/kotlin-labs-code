package staff.android.foundations

/**
 * A framework-free worked model for the section's product rule.
 * All calls are sequential. This class does not implement Android UI,
 * persistence, stock reservation or concurrent state updates.
 */
class QuantityEditor(initialQuantity: Int, private val maximum: Int = 9) {
    init {
        require(maximum >= 0)
        require(initialQuantity in 0..maximum)
    }

    var acceptedQuantity: Int = initialQuantity
        private set

    var draftText: String = initialQuantity.toString()

    fun confirm(): Boolean {
        val candidate = parseQuantity(draftText, maximum) ?: return false
        acceptedQuantity = candidate
        return true
    }

    fun cancel() {
        draftText = acceptedQuantity.toString()
    }
}

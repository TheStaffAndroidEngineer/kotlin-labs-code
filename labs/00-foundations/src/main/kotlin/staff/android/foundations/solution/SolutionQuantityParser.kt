package staff.android.foundations.solution

import staff.android.foundations.exercise.QuantityParser

class SolutionQuantityParser : QuantityParser {
    override fun parse(raw: String?, packSize: Int, maximum: Int): Int? {
        require(packSize > 0)
        require(maximum >= 0)
        val text = raw?.trim() ?: return null
        val quantity = text.toIntOrNull() ?: return null
        if (quantity !in 0..maximum) return null
        if (quantity % packSize != 0) return null
        return quantity
    }
}

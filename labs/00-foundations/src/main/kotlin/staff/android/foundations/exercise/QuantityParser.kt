package staff.android.foundations.exercise

interface QuantityParser {
    /**
     * Null means invalid input. Zero removes the item.
     * A positive quantity must be an exact pack multiple and <= maximum.
     * Whitespace around the integer is allowed; overflow is invalid.
     * packSize > 0 and maximum >= 0 are caller preconditions.
     */
    fun parse(raw: String?, packSize: Int, maximum: Int): Int?
}

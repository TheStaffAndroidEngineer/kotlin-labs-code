package staff.android.foundations.exercise

/** Deliberately incomplete. Edit this class; preserve the public contract. */
class ExerciseQuantityParser : QuantityParser {
    override fun parse(raw: String?, packSize: Int, maximum: Int): Int? {
        return raw?.toIntOrNull()?.coerceIn(0, maximum)
    }
}

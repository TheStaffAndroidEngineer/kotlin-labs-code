package staff.android.foundations

import kotlin.test.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import staff.android.foundations.exercise.*
import staff.android.foundations.solution.SolutionQuantityParser

abstract class QuantityParserContract {
    protected abstract fun parser(): QuantityParser

    @Test fun `whole packs are accepted without rounding`() {
        assertEquals(6, parser().parse("6", 3, 10))
        assertEquals(9, parser().parse("9", 3, 10))
        assertNull(parser().parse("4", 3, 10))
        assertNull(parser().parse("10", 3, 10))
    }

    @Test fun `zero remains an explicit removal command`() {
        assertEquals(0, parser().parse("0", 3, 10))
        assertEquals(0, parser().parse("0", 3, 0))
    }

    @Test fun `invalid quantities are rejected rather than clamped`() {
        assertNull(parser().parse("-3", 3, 10))
        assertNull(parser().parse("12", 3, 10))
    }

    @Test fun `whitespace is accepted around a valid integer`() {
        assertEquals(6, parser().parse(" 6 ", 3, 10))
        assertEquals(6, parser().parse("+6", 3, 10))
    }

    @Test fun `missing malformed and overflowing input is invalid`() {
        for (raw in listOf(null, "", "  ", "six", "6.0", "2147483648")) {
            assertNull(parser().parse(raw, 3, 10), "Accepted $raw")
        }
    }

    @Test fun `invalid policy parameters fail independently of input`() {
        assertFailsWith<IllegalArgumentException> {
            parser().parse(null, 0, 10)
        }
        assertFailsWith<IllegalArgumentException> {
            parser().parse("3", -3, 10)
        }
        assertFailsWith<IllegalArgumentException> {
            parser().parse(null, 3, -1)
        }
    }

    @Test fun `zero is the only value when no full pack fits`() {
        assertEquals(0, parser().parse("0", 5, 3))
        assertNull(parser().parse("3", 5, 3))
        assertNull(parser().parse("5", 5, 3))
    }
}

class ReferenceQuantityParserTest : QuantityParserContract() {
    override fun parser(): QuantityParser = SolutionQuantityParser()
}

@Tag("exercise")
class ExerciseQuantityParserTest : QuantityParserContract() {
    override fun parser(): QuantityParser = ExerciseQuantityParser()
}

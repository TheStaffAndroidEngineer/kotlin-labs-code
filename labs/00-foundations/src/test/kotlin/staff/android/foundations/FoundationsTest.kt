package staff.android.foundations

import kotlin.test.*
import org.junit.jupiter.api.Test

class FoundationsTest {
    @Test fun `quantity cannot cross either valid boundary`() {
        assertEquals(9, nextQuantity(9, true))
        assertEquals(0, nextQuantity(0, false))
        assertEquals(3, nextQuantity(2, true, 3))
        assertEquals(1, nextQuantity(2, false))
        assertEquals(Int.MAX_VALUE, nextQuantity(
            Int.MAX_VALUE, true, Int.MAX_VALUE,
        ))
    }

    @Test fun `invalid caller state is not silently repaired`() {
        assertFailsWith<IllegalArgumentException> { nextQuantity(-1, true) }
        assertFailsWith<IllegalArgumentException> { nextQuantity(10, false) }
        assertFailsWith<IllegalArgumentException> { nextQuantity(0, true, -1) }
    }

    @Test fun `boundary parsing preserves zero and rejects invalid values`() {
        assertEquals(0, parseQuantity("0"))
        assertEquals(2, parseQuantity(" 2 "))
        for (raw in listOf(null, "", "two", "-1", "10", "2147483648")) {
            assertNull(parseQuantity(raw), "Unexpected acceptance of $raw")
        }
    }

    @Test fun `value replacement leaves the earlier selection unchanged`() {
        val original = Selection("tea", 2)
        val changed = original.copy(quantity = 3)
        assertEquals(Selection("tea", 2), original)
        assertEquals(3, changed.quantity)
        assertNotEquals(original, changed)
        assertNotSame(original, original.copy())
    }

    @Test fun `catalog lookup distinguishes an absent product`() {
        val input = mutableListOf(Product("tea", "Tea", 4))
        val catalog = MemoryCatalog(input)
        input.clear()
        assertEquals("Tea", titleFor(catalog, "tea"))
        assertEquals("Unavailable", titleFor(catalog, "absent"))
        assertNull(catalog.find("absent"))
    }
}

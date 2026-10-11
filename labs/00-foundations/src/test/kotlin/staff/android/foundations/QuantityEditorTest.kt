package staff.android.foundations

import kotlin.test.*
import org.junit.jupiter.api.Test

class QuantityEditorTest {
    @Test fun `deleting a digit neither commits nor removes the item`() {
        val editor = QuantityEditor(2)
        editor.draftText = ""
        assertEquals(2, editor.acceptedQuantity)
        assertFalse(editor.confirm())
        assertEquals(2, editor.acceptedQuantity)
        assertEquals("", editor.draftText)
    }

    @Test fun `typing three becomes accepted only after confirmation`() {
        val editor = QuantityEditor(2)
        editor.draftText = ""
        editor.draftText = "3"
        assertEquals(2, editor.acceptedQuantity)
        assertTrue(editor.confirm())
        assertEquals(3, editor.acceptedQuantity)
    }

    @Test fun `explicit zero removes only on confirmation`() {
        val editor = QuantityEditor(2)
        editor.draftText = "0"
        assertEquals(2, editor.acceptedQuantity)
        assertTrue(editor.confirm())
        assertEquals(0, editor.acceptedQuantity)
    }

    @Test fun `cancel discards the draft before confirmation`() {
        val editor = QuantityEditor(2)
        editor.draftText = "3"
        editor.cancel()
        assertEquals(2, editor.acceptedQuantity)
        assertEquals("2", editor.draftText)
    }

    @Test fun `rejected text stays available for correction`() {
        val editor = QuantityEditor(2, maximum = 4)
        for (draft in listOf("five", "-1", "5", "2147483648")) {
            editor.draftText = draft
            assertFalse(editor.confirm())
            assertEquals(2, editor.acceptedQuantity)
            assertEquals(draft, editor.draftText)
        }
    }
}

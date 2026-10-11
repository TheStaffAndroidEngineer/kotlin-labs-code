package staff.android.state

import kotlin.test.*
import org.junit.jupiter.api.Test
import staff.android.state.examples.*
import staff.android.state.worked.SavedItemsStore

class OwnershipTest {
    @Test fun `a read-only alias changes when its mutable owner changes`() {
        val input = sampleItems()
        val old = LeakyState(input)
        input[0].title = "Changed"
        input.clear()
        assertTrue(old.items.isEmpty())
    }

    @Test fun `copying the collection preserves element and nested aliases`() {
        val original = LeakyState(sampleItems())
        val copied = original.copy(items = original.items.toList())
        assertNotSame(original, copied)
        assertSame(original.items.first(), copied.items.first())
        copied.items.first().labels.add("sale")
        assertEquals(listOf("travel", "sale"), original.items.first().labels)
    }

    @Test fun `body properties do not participate in generated data class equality or copying`() {
        val a = BodyPropertyItem("camera").apply { title = "Old" }
        val b = BodyPropertyItem("camera").apply { title = "New" }
        assertNotSame(a, b)
        assertEquals(a, b)
        assertEquals("", a.copy().title)
    }

    @Test fun `store detaches mutable input at every nested mutable boundary`() {
        val input = sampleItems()
        val store = SavedItemsStore(input)
        input[0].title = "External"
        input[0].labels.add("external")
        input.clear()
        val first = store.snapshot().items.first()
        assertEquals(2, store.snapshot().items.size)
        assertEquals("Camera", first.title)
        assertEquals(listOf("travel"), first.labels)
    }

    @Test fun `transitions preserve old states and can share unchanged values`() {
        val store = SavedItemsStore(sampleItems())
        val before = store.snapshot()
        store.rename("camera", "Camera Pro")
        store.addLabel("camera", "sale")
        val after = store.snapshot()
        assertEquals("Camera", before.items.first().title)
        assertEquals(listOf("travel"), before.items.first().labels)
        assertEquals("Camera Pro", after.items.first().title)
        assertEquals(listOf("travel", "sale"), after.items.first().labels)
        assertNotEquals(before, after)
        assertSame(before.items[1], after.items[1])
    }

    @Test fun `replacement detaches input and does not overwrite previous snapshots`() {
        val store = SavedItemsStore(sampleItems())
        val before = store.snapshot()
        val replacement = mutableListOf(MutableItem("book", "Book", mutableListOf("learn")))
        store.replaceAll(replacement)
        replacement.first().labels.clear()
        replacement.clear()
        assertEquals(listOf("camera", "bag"), before.items.map { it.id })
        assertEquals(listOf("learn"), store.snapshot().items.single().labels)
    }

    @Test fun `unknown ID and repeated label are no-ops by value`() {
        val store = SavedItemsStore(sampleItems())
        val before = store.snapshot()
        store.rename("missing", "Ignored")
        store.addLabel("camera", "travel")
        assertEquals(before, store.snapshot())
    }

    @Test fun `duplicate IDs are rejected`() {
        val input = sampleItems()
        assertFailsWith<IllegalArgumentException> { SavedItemsStore(listOf(input[0], input[0])) }
    }
}


package staff.android.contracts

import java.io.IOException
import kotlin.test.*
import org.junit.jupiter.api.Test

class ContractsTest {
    @Test fun `missing blank and valid IDs remain distinct`() {
        assertEquals(IdParseResult.Missing, ProductId.parse(null))
        assertEquals(IdParseResult.Blank, ProductId.parse(" \t"))
        assertEquals(" camera ", assertIs<IdParseResult.Valid>(ProductId.parse(" camera ")).id.value)
    }

    @Test fun `title invariant rejects invalid internal construction`() {
        assertFailsWith<IllegalArgumentException> { product(title = " ") }
    }

    @Test fun `null Java collection is failure while empty is success`() {
        val failure = assertIs<Outcome.Failure>(loadSaved(LegacySavedSource { null }))
        assertEquals(LoadFailure.InvalidPayload("items", null, "missing"), failure.reason)
        assertEquals(Outcome.Success(emptyList()), loadSaved(LegacySavedSource { emptyList() }))
    }

    @Test fun `null Java row is rejected with its index`() {
        val result = validateRows(listOf(LegacySavedProduct("a", "A"), null))
        assertEquals(Outcome.Failure(LoadFailure.InvalidPayload("row", 1, "missing")), result)
    }

    @Test fun `null Java ID is distinguished from blank`() {
        assertEquals(Outcome.Failure(LoadFailure.InvalidPayload("id", 0, "missing")), validateRow(LegacySavedProduct(null, "A"), 0))
        assertEquals(Outcome.Failure(LoadFailure.InvalidPayload("id", 0, "blank")), validateRow(LegacySavedProduct(" ", "A"), 0))
    }

    @Test fun `null Java title is distinguished from blank`() {
        assertEquals(Outcome.Failure(LoadFailure.InvalidPayload("title", 0, "missing")), validateRow(LegacySavedProduct("a", null), 0))
        assertEquals(Outcome.Failure(LoadFailure.InvalidPayload("title", 0, "blank")), validateRow(LegacySavedProduct("a", "\t"), 0))
    }

    @Test fun `duplicate ID rejects entire batch even if title differs`() {
        val result = validateRows(listOf(LegacySavedProduct("a", "A"), LegacySavedProduct("a", "Changed")))
        assertEquals(Outcome.Failure(LoadFailure.InvalidPayload("id", 1, "duplicate")), result)
    }

    @Test fun `successful boundary preserves order and exact strings`() {
        val result = assertIs<Outcome.Success<List<SavedProduct>>>(validateRows(listOf(LegacySavedProduct(" b ", " B "), LegacySavedProduct("a", "A"))))
        assertEquals(listOf(" b ", "a"), result.value.map { it.id.value })
        assertEquals(listOf(" B ", "A"), result.value.map { it.title })
    }

    @Test fun `boundary captures mutable source collection`() {
        val incoming = mutableListOf<LegacySavedProduct?>(LegacySavedProduct("a", "A"))
        val accepted = assertIs<Outcome.Success<List<SavedProduct>>>(validateRows(incoming))
        incoming.clear()
        assertEquals("A", accepted.value.single().title)
    }

    @Test fun `IO failure maps to unavailable`() {
        assertEquals(Outcome.Failure(LoadFailure.Unavailable), loadSaved(LegacySavedSource { throw IOException("private detail") }))
    }

    @Test fun `programming failure is not disguised as unavailable`() {
        assertFailsWith<IllegalStateException> {
            loadSaved(LegacySavedSource { error("programming defect") })
        }
    }

    @Test fun `first failure has no content and retry returns loading`() {
        val failed = finishLoad(LoadState.Loading, Outcome.Failure(LoadFailure.Unavailable))
        assertEquals(LoadState.Failed(LoadFailure.Unavailable), failed)
        assertEquals(LoadState.Loading, startLoad(failed))
    }

    @Test fun `refresh preserves prior rows and earlier snapshot`() {
        val before = LoadState.Content(listOf(product()))
        val running = assertIs<LoadState.Content>(startLoad(before))
        val failed = assertIs<LoadState.Content>(finishLoad(running, Outcome.Failure(LoadFailure.Unavailable)))
        assertEquals(Refresh.Idle, before.refresh)
        assertEquals(Refresh.Running, running.refresh)
        assertEquals(Refresh.Failed(LoadFailure.Unavailable), failed.refresh)
        assertEquals(before.items, failed.items)
        assertEquals("Camera | temporarily unavailable", render(failed))
    }

    @Test fun `retry clears earlier failure but retains content`() {
        val failed = LoadState.Content(listOf(product()), Refresh.Failed(LoadFailure.Unavailable))
        val retry = assertIs<LoadState.Content>(startLoad(failed))
        assertEquals(Refresh.Running, retry.refresh)
        assertEquals(failed.items, retry.items)
    }

    @Test fun `valid empty success replaces previous content and error`() {
        val before = LoadState.Content(listOf(product()), Refresh.Failed(LoadFailure.Unavailable))
        val after = finishLoad(startLoad(before), Outcome.Success(emptyList()))
        assertEquals(LoadState.Content(emptyList()), after)
        assertEquals("No saved items | up to date", render(after))
    }

    @Test fun `publication captures success list and preserves old snapshot`() {
        val rows = mutableListOf(product())
        val state = assertIs<LoadState.Content>(finishLoad(LoadState.Loading, Outcome.Success(rows)))
        rows.clear()
        assertEquals("Camera", state.items.single().title)
    }

    @Test fun `malformed response keeps content through full call chain`() {
        val initial = finishLoad(LoadState.Loading, loadSaved(LegacySavedSource { listOf(LegacySavedProduct("camera", "Camera")) }))
        val result = loadSaved(LegacySavedSource { listOf(LegacySavedProduct("camera", null)) })
        assertEquals("Camera | invalid saved-items response", render(finishLoad(startLoad(initial), result)))
    }

    @Test fun `map transforms success once and preserves failure`() {
        var calls = 0
        val success: Outcome<Int> = Outcome.Success(2)
        val failure: Outcome<Int> = Outcome.Failure(LoadFailure.Unavailable)
        assertEquals(Outcome.Success("2"), success.map { calls++; it.toString() })
        assertSame<Any>(failure, failure.map { calls++; it.toString() })
        assertEquals(1, calls)
    }

    @Test fun `transform defect propagates rather than masquerading as load failure`() {
        assertFailsWith<IllegalArgumentException> {
            Outcome.Success(2).map<Int, String> { throw IllegalArgumentException("bug") }
        }
    }

    @Test fun `covariant source and contravariant sink preserve permitted operations`() {
        val source: Source<SavedProduct> = Source { product() }
        val broad: Source<Any> = source
        assertIs<SavedProduct>(broad.read())
        val output = mutableListOf<Any>()
        val anySink: Sink<Any> = Sink { output.add(it) }
        val specific: Sink<SavedProduct> = anySink
        specific.accept(product())
        assertEquals(listOf<Any>(product()), output)
    }

    @Test fun `inline early return and failure paths complete as documented`() {
        assertEquals("No saved items", firstTitle(Outcome.Success(emptyList())))
        assertEquals("Camera", firstTitle(Outcome.Success(listOf(product()))))
        assertEquals("Cannot load", firstTitle(Outcome.Failure(LoadFailure.Unavailable)))
    }

    @Test fun `stored callback runs only on explicit selection`() {
        val selected = mutableListOf<ProductId>()
        val handler = SelectionHandler { selected.add(it) }
        assertTrue(selected.isEmpty())
        handler.select(product().id)
        assertEquals(listOf(product().id), selected)
    }

    @Test fun `reified inspection sees outer type and absence`() {
        assertEquals("Camera", firstOfType<String>(listOf(7, "Camera")))
        assertNull(firstOfType<String>(listOf(7)))
        // Nested generic element types are erased: this is a List, not proof of List<String>.
        val outer: List<*>? = firstOfType<List<String>>(listOf(listOf(7)))
        assertEquals(7, outer?.single())
    }
}

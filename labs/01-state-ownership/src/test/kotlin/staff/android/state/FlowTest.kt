package staff.android.state

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.test.*
import org.junit.jupiter.api.Test
import staff.android.state.examples.*
import staff.android.state.worked.SavedItemsStore

class FlowTest {
    @Test fun `mutating payload and assigning an equal copy does not produce a new observation`() = runBlocking {
        val input = sampleItems()
        val state = MutableStateFlow(LeakyState(input))
        val observed = mutableListOf<String>()
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            state.collect { observed.add(it.items.first().title) }
        }
        try {
            input[0].title = "Camera Pro"
            state.value = state.value.copy(items = state.value.items.toList())
            yield()
            assertEquals(listOf("Camera"), observed)
            assertEquals("Camera Pro", state.value.items.first().title)
        } finally { collector.cancelAndJoin() }
    }

    @Test fun `replacing a changed value lets an active collector observe the new title`() {
        assertEquals(
            listOf(
                "Titles observed at collection: [Camera, Camera Pro]",
                "Earlier snapshot title: Camera",
                "Current title: Camera Pro",
            ),
            fixedFlowExample(),
        )
    }
}

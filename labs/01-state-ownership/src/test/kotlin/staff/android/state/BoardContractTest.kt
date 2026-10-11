package staff.android.state

import kotlin.test.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import staff.android.state.exercise.*
import staff.android.state.solution.SolutionTaskBoard

abstract class BoardContract {
    protected abstract fun create(seed: List<IncomingTask>): TaskBoard

    private fun seed() = mutableListOf(
        IncomingTask("inspect", "Inspect trace", mutableListOf("Asha", "Dev")),
        IncomingTask("report", "Write report", mutableListOf("Mina")),
    )

    @Test fun `external mutation cannot rewrite imported state`() {
        val input = seed()
        val board = create(input)
        input.first().title = "Rewritten"
        input.first().assignees.clear()
        input.clear()
        val state = board.snapshot()
        assertEquals(2, state.tasks.size)
        assertEquals("Inspect trace", state.tasks.first().title)
        assertEquals(listOf("Asha", "Dev"), state.tasks.first().assignees)
    }

    @Test fun `completion creates a new value without rewriting history`() {
        val board = create(seed())
        val old = board.snapshot()
        board.setDone("inspect", true)
        assertFalse(old.tasks.first().done)
        assertTrue(board.snapshot().tasks.first().done)
        assertFalse(board.snapshot().tasks[1].done)
        assertNotEquals(old, board.snapshot())
    }

    @Test fun `new command inputs cannot mutate state later`() {
        val board = create(seed())
        val incoming = mutableListOf("Jo", "Sam", "Jo")
        board.replaceAssignees("inspect", incoming)
        incoming.clear()
        assertEquals(listOf("Jo", "Sam", "Jo"), board.snapshot().tasks.first().assignees)
    }

    @Test fun `later assignments preserve earlier snapshots`() {
        val board = create(seed())
        val old = board.snapshot()
        board.replaceAssignees("inspect", listOf("Jo"))
        assertEquals(listOf("Asha", "Dev"), old.tasks.first().assignees)
        assertEquals(listOf("Jo"), board.snapshot().tasks.first().assignees)
    }

    @Test fun `unknown IDs are no-ops and empty boards are supported`() {
        val board = create(seed())
        val old = board.snapshot()
        board.setDone("absent", true)
        board.replaceAssignees("absent", listOf("Jo"))
        assertEquals(old, board.snapshot())
        val empty = create(emptyList())
        empty.setDone("absent", true)
        assertTrue(empty.snapshot().tasks.isEmpty())
    }

    @Test fun `same value update preserves equality`() {
        val board = create(seed())
        val old = board.snapshot()
        board.setDone("inspect", false)
        board.replaceAssignees("inspect", listOf("Asha", "Dev"))
        assertEquals(old, board.snapshot())
    }

    @Test fun `duplicate IDs are rejected`() {
        val item = seed().first()
        assertFailsWith<IllegalArgumentException> { create(listOf(item, item)) }
    }
}

class ReferenceBoardContractTest : BoardContract() {
    override fun create(seed: List<IncomingTask>): TaskBoard = SolutionTaskBoard(seed)
}

@Tag("exercise")
class ExerciseBoardContractTest : BoardContract() {
    override fun create(seed: List<IncomingTask>): TaskBoard = ExerciseTaskBoard(seed)
}

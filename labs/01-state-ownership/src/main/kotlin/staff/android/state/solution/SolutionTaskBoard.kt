package staff.android.state.solution

import staff.android.state.exercise.*

/** Reference implementation. Read after attempting the independent exercise. */
class SolutionTaskBoard(seed: List<IncomingTask>) : TaskBoard {
    init { require(seed.map { it.id }.distinct().size == seed.size) { "IDs must be unique" } }
    private var current = BoardState(seed.map {
        TaskValue(it.id, it.title, it.assignees.toList(), it.done)
    })

    override fun snapshot(): BoardState = current

    override fun setDone(id: String, done: Boolean) {
        current = current.copy(tasks = current.tasks.map {
            if (it.id == id) it.copy(done = done) else it
        })
    }

    override fun replaceAssignees(id: String, assignees: List<String>) {
        val captured = assignees.toList()
        current = current.copy(tasks = current.tasks.map {
            if (it.id == id) it.copy(assignees = captured) else it
        })
    }
}


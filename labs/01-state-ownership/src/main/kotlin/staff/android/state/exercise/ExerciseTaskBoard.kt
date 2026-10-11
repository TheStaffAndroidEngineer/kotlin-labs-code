package staff.android.state.exercise

/** Intentionally defective starter. Repair this file; keep the public contract. */
class ExerciseTaskBoard(seed: List<IncomingTask>) : TaskBoard {
    private val tasks = seed.map {
        TaskValue(it.id, it.title, it.assignees, it.done)
    }.toMutableList()

    override fun snapshot(): BoardState = BoardState(tasks)

    override fun setDone(id: String, done: Boolean) {
        val index = tasks.indexOfFirst { it.id == id }
        if (index >= 0) tasks[index] = tasks[index].copy(done = done)
    }

    override fun replaceAssignees(id: String, assignees: List<String>) {
        val index = tasks.indexOfFirst { it.id == id }
        if (index >= 0) tasks[index] = tasks[index].copy(assignees = assignees)
    }
}


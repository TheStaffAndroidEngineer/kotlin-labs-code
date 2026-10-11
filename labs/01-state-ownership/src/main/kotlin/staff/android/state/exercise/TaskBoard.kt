package staff.android.state.exercise

data class IncomingTask(
    val id: String,
    var title: String,
    val assignees: MutableList<String>,
    var done: Boolean = false,
)
data class TaskValue(val id: String, val title: String, val assignees: List<String>, val done: Boolean)
data class BoardState(val tasks: List<TaskValue>)

/**
 * Single-threaded calls; callers honor read-only types.
 * IDs must be unique at construction. Unknown IDs are no-ops.
 * Each returned state is a historical snapshot. Caller-owned inputs must not
 * remain writable aliases into it. Assignee order and duplicates are preserved.
 */
interface TaskBoard {
    fun snapshot(): BoardState
    fun setDone(id: String, done: Boolean)
    fun replaceAssignees(id: String, assignees: List<String>)
}


package staff.android.async.solution

import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import staff.android.async.exercise.ImageBindings
import staff.android.async.exercise.ImageSlot
import staff.android.async.exercise.ImageSource
import staff.android.async.exercise.ImageState

// region image-solution
// region image-solution-owner
class SolutionImageBindings(
    parentScope: CoroutineScope,
    private val source: ImageSource,
    private val publish: (ImageSlot, ImageState) -> Unit,
) : ImageBindings {
    private val owner = Job(requireNotNull(parentScope.coroutineContext[Job]))
    private val scope = CoroutineScope(parentScope.coroutineContext + owner)
    private var nextTicket = 0L
    private val tickets = mutableMapOf<ImageSlot, Long>()
    private val jobs = mutableMapOf<ImageSlot, Job>()

    // endregion

    // region image-solution-bind
    override fun bind(slot: ImageSlot, url: String) {
        owner.ensureActive()
        require(url.isNotBlank())
        val ticket = ++nextTicket
        tickets[slot] = ticket
        jobs.remove(slot)?.cancel()
        publish(slot, ImageState.Loading(url))
        jobs[slot] = scope.launch {
            val image = try {
                source.load(url)
            } catch (failure: IOException) {
                ensureActive()
                if (tickets[slot] == ticket) {
                    publish(slot, ImageState.Failed(url))
                }
                return@launch
            }
            ensureActive()
            if (tickets[slot] == ticket) {
                publish(slot, ImageState.Ready(url, image))
            }
        }
    }

    // endregion

    // region image-solution-close
    override fun unbind(slot: ImageSlot) {
        owner.ensureActive()
        tickets.remove(slot)
        jobs.remove(slot)?.cancel()
        publish(slot, ImageState.Empty)
    }

    override fun close() {
        tickets.clear()
        owner.cancel()
        jobs.clear()
    }
}
// endregion
// endregion

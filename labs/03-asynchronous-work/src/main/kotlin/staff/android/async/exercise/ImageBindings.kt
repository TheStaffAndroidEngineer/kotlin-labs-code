package staff.android.async.exercise

import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

// region image-contract
enum class ImageSlot { LEFT, RIGHT }

fun interface ImageSource {
    suspend fun load(url: String): String
}

sealed interface ImageState {
    data object Empty : ImageState
    data class Loading(val url: String) : ImageState
    data class Ready(val url: String, val imageToken: String) : ImageState
    data class Failed(val url: String) : ImageState
}

interface ImageBindings {
    fun bind(slot: ImageSlot, url: String)
    fun unbind(slot: ImageSlot)
    fun close()
}
// endregion

/** Deliberately defective learner checkpoint. Save attempts before replacing it. */
// region image-starter
class ExerciseImageBindings(
    parentScope: CoroutineScope,
    private val source: ImageSource,
    private val publish: (ImageSlot, ImageState) -> Unit,
) : ImageBindings {
    private val owner = Job(requireNotNull(parentScope.coroutineContext[Job]))
    private val scope = CoroutineScope(parentScope.coroutineContext + owner)
    private var current: Job? = null

    override fun bind(slot: ImageSlot, url: String) {
        owner.ensureActive()
        require(url.isNotBlank())
        current?.cancel()
        publish(slot, ImageState.Loading(url))
        current = scope.launch {
            val image = try {
                source.load(url)
            } catch (failure: IOException) {
                publish(slot, ImageState.Failed(url))
                return@launch
            }
            publish(slot, ImageState.Ready(url, image))
        }
    }

    override fun unbind(slot: ImageSlot) {
        owner.ensureActive()
        publish(slot, ImageState.Empty)
    }

    override fun close() {
        owner.cancel()
    }
}
// endregion

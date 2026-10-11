package staff.android.streams

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet

// region upload-model
data class UploadTicket(val id: String, val generation: Long)
data class UploadProgress(
    val ticket: UploadTicket,
    val totalBytes: Long,
    val sentBytes: Long = 0,
)
data class UploadBoard(
    val generation: Long = 0,
    val files: Map<String, UploadProgress> = emptyMap(),
) {
    val totalSent: Long get() = files.values.sumOf { it.sentBytes }
}

interface UploadLedger {
    val state: StateFlow<UploadBoard>
    fun start(id: String, totalBytes: Long): UploadTicket
    fun report(ticket: UploadTicket, sentBytes: Long)
    fun remove(id: String)
}
// endregion

// region upload-starter
class ExerciseUploadLedger : UploadLedger {
    private val mutable = MutableStateFlow(UploadBoard())
    override val state: StateFlow<UploadBoard> = mutable.asStateFlow()

    override fun start(id: String, totalBytes: Long): UploadTicket {
        val before = mutable.value
        val ticket = UploadTicket(id, before.generation + 1)
        val entry = UploadProgress(ticket, totalBytes)
        mutable.value = UploadBoard(ticket.generation, mapOf(id to entry))
        return ticket
    }

    override fun report(ticket: UploadTicket, sentBytes: Long) {
        val before = mutable.value
        val old = before.files[ticket.id] ?: return
        mutable.value = before.copy(
            files = before.files + (ticket.id to old.copy(sentBytes = sentBytes)),
        )
    }

    override fun remove(id: String) {
        mutable.value = UploadBoard()
    }
}
// endregion

// region upload-solution-start
class SolutionUploadLedger : UploadLedger {
    private val mutable = MutableStateFlow(UploadBoard())
    override val state: StateFlow<UploadBoard> = mutable.asStateFlow()

    override fun start(id: String, totalBytes: Long): UploadTicket {
        require(id.isNotBlank())
        require(totalBytes in 1..MAX_FILE_BYTES)
        val after = mutable.updateAndGet { before ->
            require(id in before.files || before.files.size < MAX_FILES)
            check(before.generation < Long.MAX_VALUE)
            val ticket = UploadTicket(id, before.generation + 1)
            before.copy(
                generation = ticket.generation,
                files = before.files + (id to UploadProgress(ticket, totalBytes)),
            )
        }
        return after.files.getValue(id).ticket
    }
// endregion

// region upload-solution-report
    override fun report(ticket: UploadTicket, sentBytes: Long) {
        mutable.update { before ->
            val old = before.files[ticket.id]
            if (old == null || old.ticket != ticket) {
                before
            } else {
                require(sentBytes in 0..old.totalBytes)
                if (sentBytes <= old.sentBytes) before else {
                    before.copy(
                        files = before.files + (ticket.id to old.copy(sentBytes = sentBytes)),
                    )
                }
            }
        }
    }
// endregion

// region upload-solution-remove
    override fun remove(id: String) {
        mutable.update { before -> before.copy(files = before.files - id) }
    }

    companion object {
        const val MAX_FILES = 100
        const val MAX_FILE_BYTES = 1_000_000_000_000L
    }
}
// endregion

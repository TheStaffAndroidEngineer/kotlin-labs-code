package staff.android.contracts.exercise

// region upload-model
data class UploadDraft(
    val id: String?,
    val localPath: String?,
    val uploadedReceipt: String?
)

enum class UploadProblem {
    MissingId, BlankId, BlankReceipt, MissingPath, BlankPath
}

data class UploadRequest(val reportId: String, val localPath: String) {
    init {
        require(reportId.isNotBlank())
        require(localPath.isNotBlank())
    }
}

sealed interface UploadDecision {
    data class Invalid(val problem: UploadProblem) : UploadDecision
    data class Ready(val request: UploadRequest) : UploadDecision
    data class AlreadyUploaded(val receipt: String) : UploadDecision {
        init { require(receipt.isNotBlank()) }
    }
}

fun interface UploadPlanner {
    fun decide(draft: UploadDraft): UploadDecision
}
// endregion

// region upload-starter
class ExerciseUploadPlanner : UploadPlanner {
    override fun decide(draft: UploadDraft): UploadDecision =
        UploadDecision.Ready(
            UploadRequest(draft.id.orEmpty(), draft.localPath.orEmpty())
        )
}
// endregion

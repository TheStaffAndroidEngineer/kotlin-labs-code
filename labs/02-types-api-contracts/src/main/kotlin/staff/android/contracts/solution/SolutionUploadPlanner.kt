package staff.android.contracts.solution

import staff.android.contracts.exercise.*

// region upload-solution
class SolutionUploadPlanner : UploadPlanner {
    override fun decide(draft: UploadDraft): UploadDecision {
        val id = draft.id
            ?: return UploadDecision.Invalid(UploadProblem.MissingId)
        if (id.isBlank()) {
            return UploadDecision.Invalid(UploadProblem.BlankId)
        }
        val receipt = draft.uploadedReceipt
        if (receipt != null) {
            return if (receipt.isBlank()) {
                UploadDecision.Invalid(UploadProblem.BlankReceipt)
            } else {
                UploadDecision.AlreadyUploaded(receipt)
            }
        }
        val path = draft.localPath
            ?: return UploadDecision.Invalid(UploadProblem.MissingPath)
        if (path.isBlank()) {
            return UploadDecision.Invalid(UploadProblem.BlankPath)
        }
        return UploadDecision.Ready(UploadRequest(id, path))
    }
}
// endregion

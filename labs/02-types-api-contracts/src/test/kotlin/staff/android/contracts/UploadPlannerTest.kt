package staff.android.contracts

import kotlin.test.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import staff.android.contracts.exercise.*
import staff.android.contracts.solution.SolutionUploadPlanner

abstract class UploadPlannerContract {
    abstract fun planner(): UploadPlanner

    @Test fun `missing ID wins even with receipt`() {
        assertEquals(UploadDecision.Invalid(UploadProblem.MissingId), planner().decide(UploadDraft(null, null, "receipt")))
    }
    @Test fun `blank ID wins even with receipt`() {
        assertEquals(UploadDecision.Invalid(UploadProblem.BlankId), planner().decide(UploadDraft("\t", "/file", "receipt")))
    }
    @Test fun `blank receipt is invalid and cannot become a new upload`() {
        assertEquals(UploadDecision.Invalid(UploadProblem.BlankReceipt), planner().decide(UploadDraft("r", "/file", " ")))
    }
    @Test fun `blank receipt precedes missing path`() {
        assertEquals(UploadDecision.Invalid(UploadProblem.BlankReceipt), planner().decide(UploadDraft("r", null, "")))
    }
    @Test fun `receipt permits absent local file`() {
        assertEquals(UploadDecision.AlreadyUploaded("receipt"), planner().decide(UploadDraft("r", null, "receipt")))
    }
    @Test fun `receipt permits blank local path and preserves receipt`() {
        assertEquals(UploadDecision.AlreadyUploaded(" receipt "), planner().decide(UploadDraft("r", " ", " receipt ")))
    }
    @Test fun `missing receipt requires path`() {
        assertEquals(UploadDecision.Invalid(UploadProblem.MissingPath), planner().decide(UploadDraft("r", null, null)))
    }
    @Test fun `blank path rejected`() {
        assertEquals(UploadDecision.Invalid(UploadProblem.BlankPath), planner().decide(UploadDraft("r", " ", null)))
    }
    @Test fun `ready strings preserved without trimming`() {
        assertEquals(UploadDecision.Ready(UploadRequest(" r ", " /file ")), planner().decide(UploadDraft(" r ", " /file ", null)))
    }
    @Test fun `decision deterministic and does not alter draft`() {
        val input = UploadDraft("r", "/file", null)
        val expected = UploadDecision.Ready(UploadRequest("r", "/file"))
        assertEquals(expected, planner().decide(input))
        assertEquals(expected, planner().decide(input))
        assertEquals(UploadDraft("r", "/file", null), input)
    }
}

class ReferenceUploadPlannerTest : UploadPlannerContract() {
    override fun planner(): UploadPlanner = SolutionUploadPlanner()
}

@Tag("exercise")
class LearnerUploadPlannerTest : UploadPlannerContract() {
    override fun planner(): UploadPlanner = ExerciseUploadPlanner()
}

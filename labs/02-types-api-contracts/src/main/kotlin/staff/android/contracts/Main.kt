package staff.android.contracts

import java.io.IOException
import staff.android.contracts.preview.flagsDemo
import staff.android.contracts.preview.flagsFixedDemo
import staff.android.contracts.preview.previewDemo
import staff.android.contracts.exercise.UploadDraft
import staff.android.contracts.solution.SolutionUploadPlanner

fun statesDemo() {
    // region states-demo
    var state: LoadState = LoadState.Loading
    println(render(state))
    val firstRows = listOf(product())
    state = finishLoad(state, Outcome.Success(firstRows))
    println(render(state))
    state = startLoad(state)
    println(render(state))
    state = finishLoad(state, Outcome.Failure(LoadFailure.Unavailable))
    println(render(state))
    state = startLoad(state)
    println(render(state))
    state = finishLoad(state, Outcome.Success(emptyList()))
    println(render(state))
    // endregion
}

fun firstFailureDemo() {
    // region first-failure-demo
    val failed = finishLoad(
        LoadState.Loading,
        Outcome.Failure(LoadFailure.Unavailable)
    )
    println(render(failed))
    println(render(startLoad(failed)))
    // endregion
}

fun boundaryDemo() {
    // region boundary-demo
    val missing = LegacySavedSource { null }
    val empty = LegacySavedSource { emptyList() }
    val broken = LegacySavedSource {
        listOf(LegacySavedProduct("camera", null))
    }
    val offline = LegacySavedSource { throw IOException("unreachable") }
    for (source in listOf(missing, empty, broken, offline)) {
        println(loadSaved(source))
    }
    // endregion
}

fun idsDemo() {
    // region ids-demo
    for (raw in listOf(null, " ", "camera", " camera ")) {
        println(ProductId.parse(raw))
    }
    // endregion
}

fun boundaryStateDemo() {
    // region boundary-state-demo
    val valid = LegacySavedSource {
        listOf(LegacySavedProduct("camera", "Camera"))
    }
    var state = finishLoad(LoadState.Loading, loadSaved(valid))
    println(render(state))
    val malformed = LegacySavedSource {
        listOf(LegacySavedProduct("camera", null))
    }
    state = startLoad(state)
    state = finishLoad(state, loadSaved(malformed))
    println(render(state))
    state = startLoad(state)
    state = finishLoad(state, loadSaved(LegacySavedSource { emptyList() }))
    println(render(state))
    // endregion
}

fun varianceDemo() {
    // region variance-demo
    val products: Source<SavedProduct> = Source { product() }
    val objects: Source<Any> = products
    println(objects.read() is SavedProduct)
    val received = mutableListOf<Any>()
    val anySink: Sink<Any> = Sink { received.add(it) }
    val productSink: Sink<SavedProduct> = anySink
    productSink.accept(product())
    println(received.size)
    // endregion
}

fun mapDemo() {
    // region map-demo
    var calls = 0
    val titles: (List<SavedProduct>) -> List<String> = { rows ->
        calls += 1
        rows.map { it.title }
    }
    val loaded: Outcome<List<SavedProduct>> =
        Outcome.Success(listOf(product()))
    val failed: Outcome<List<SavedProduct>> =
        Outcome.Failure(LoadFailure.Unavailable)
    println(loaded.map(titles))
    println(failed.map(titles))
    println("Transform calls: $calls")
    // endregion
}

fun inlineDemo() {
    // region inline-demo
    println(firstTitle(Outcome.Success(emptyList())))
    println(firstTitle(Outcome.Success(listOf(product()))))
    println(firstTitle(Outcome.Failure(LoadFailure.Unavailable)))
    val mixed: List<Any> = listOf(7, "Camera")
    println(firstOfType<String>(mixed))
    // endregion
}

fun uploadDemo() {
    // region upload-demo
    val planner = SolutionUploadPlanner()
    println(planner.decide(UploadDraft("r1", null, "receipt-7")))
    println(planner.decide(UploadDraft("r1", "/draft.jpg", null)))
    println(planner.decide(UploadDraft("r1", "/draft.jpg", " ")))
    // endregion
}

fun main(args: Array<String>) {
    when (args.singleOrNull() ?: "states") {
        "flags" -> flagsDemo()
        "flags-fixed" -> flagsFixedDemo()
        "preview" -> previewDemo()
        "states" -> statesDemo()
        "first-failure" -> firstFailureDemo()
        "boundary" -> boundaryDemo()
        "boundary-state" -> boundaryStateDemo()
        "ids" -> idsDemo()
        "variance" -> varianceDemo()
        "map" -> mapDemo()
        "inline" -> inlineDemo()
        "upload" -> uploadDemo()
        else -> error("Unknown demo; see the lab guide.")
    }
}

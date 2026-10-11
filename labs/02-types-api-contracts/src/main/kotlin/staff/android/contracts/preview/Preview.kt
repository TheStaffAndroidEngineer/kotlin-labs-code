package staff.android.contracts.preview

// region flags
data class FlagState(
    val titles: List<String>,
    val isLoading: Boolean,
    val hasError: Boolean,
)

fun startRefresh(state: FlagState): FlagState =
    state.copy(isLoading = true)

fun failRefresh(state: FlagState): FlagState =
    state.copy(hasError = true)
// endregion

// region flags-fixed
fun failRefreshFixed(state: FlagState): FlagState =
    state.copy(isLoading = false, hasError = true)
// endregion

// region preview-model
sealed interface Refresh {
    data object Idle : Refresh
    data object Running : Refresh
    data class Failed(val message: String) : Refresh
}

data class ContentPreview(
    val titles: List<String>,
    val refresh: Refresh,
)
// endregion

fun flagsDemo() {
    // region flags-demo
    val shown = FlagState(listOf("Camera"), false, false)
    val requesting = startRefresh(shown)
    val failed = failRefresh(requesting)
    println(failed)
    // endregion
}

fun flagsFixedDemo() {
    val shown = FlagState(listOf("Camera"), false, false)
    val requesting = startRefresh(shown)
    println(failRefreshFixed(requesting))
}

fun previewDemo() {
    // region preview-demo
    val shown = ContentPreview(listOf("Camera"), Refresh.Idle)
    val requesting = shown.copy(refresh = Refresh.Running)
    val failed = requesting.copy(
        refresh = Refresh.Failed("Could not update"),
    )
    println(failed.titles)
    println(failed.refresh)
    // endregion
}

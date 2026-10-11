package staff.android.async.preview

// region preview-state
data class SearchState(
    val query: String,
    val titles: List<String>,
)

fun receiveResult(
    current: SearchState,
    titles: List<String>,
): SearchState = current.copy(titles = titles.toList())
// endregion

fun arrivalDemo() {
    // region preview-arrivals
    val afterTea = SearchState("tea", emptyList())
    val afterTeapot = afterTea.copy(query = "teapot")
    val afterNewReply = receiveResult(afterTeapot, listOf("Glass teapot"))
    val afterOldReply = receiveResult(afterNewReply, listOf("Assam tea"))
    println("Query: ${afterOldReply.query}")
    println("Results: ${afterOldReply.titles}")
    // endregion
}

// region query-match
data class SearchReply(val query: String, val titles: List<String>)

fun acceptMatching(
    current: SearchState,
    reply: SearchReply,
): SearchState = if (reply.query == current.query) {
    current.copy(titles = reply.titles.toList())
} else {
    current
}
// endregion

fun matchingDemo() {
    // region query-match-driver
    val current = SearchState("teapot", emptyList())
    val afterNewReply = acceptMatching(
        current, SearchReply("teapot", listOf("Glass teapot")),
    )
    val afterOldReply = acceptMatching(
        afterNewReply, SearchReply("tea", listOf("Assam tea")),
    )
    println("Query: ${afterOldReply.query}")
    println("Results: ${afterOldReply.titles}")
    // endregion
}

// region generation-model
data class SearchAttempt(val generation: Long, val query: String)
data class AttemptReply(val attempt: SearchAttempt, val titles: List<String>)

fun acceptCurrent(
    current: SearchAttempt,
    shown: List<String>,
    reply: AttemptReply,
): List<String> = if (reply.attempt == current) reply.titles.toList() else shown
// endregion

fun generationDemo() {
    // region generation-driver
    val firstTea = SearchAttempt(1, "tea")
    val teapot = SearchAttempt(2, "teapot")
    val current = SearchAttempt(3, "tea")
    val newer = AttemptReply(current, listOf("Fresh tea"))
    val older = AttemptReply(firstTea, listOf("Assam tea"))
    val shown = acceptCurrent(current, emptyList(), newer)
    println("Attempts: $firstTea -> $teapot -> $current")
    println("Query-only accepts old: ${older.attempt.query == current.query}")
    println("Generation result: ${acceptCurrent(current, shown, older)}")
    // endregion
}

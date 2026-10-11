package staff.android.async

import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.suspendCancellableCoroutine

/** One serialized test lane; controlled callbacks, never real network traffic. */
class ManualSearchSource(private val cooperative: Boolean = true) : SearchSource {
    class Request(
        val query: String,
        private val success: (List<SearchProduct>) -> Unit,
        private val failure: (IOException) -> Unit,
    ) {
        val returned = CompletableDeferred<Unit>()
        var cancelled = false
            internal set
        var completed = false
            private set
        fun succeed(vararg products: SearchProduct) {
            check(!completed) { "One terminal callback per request" }
            completed = true
            success(products.toList())
        }
        fun fail(message: String = "offline") {
            check(!completed) { "One terminal callback per request" }
            completed = true
            failure(IOException(message))
        }
    }

    val requests = mutableListOf<Request>()
    private val arrivals = Channel<Request>(Channel.UNLIMITED)
    suspend fun next(): Request = arrivals.receive()

    override suspend fun search(query: String): List<SearchProduct> {
        var registered: Request? = null
        try {
            return if (cooperative) {
                suspendCancellableCoroutine { continuation ->
                    val request = Request(query, { continuation.resume(it) },
                        { continuation.resumeWithException(it) })
                    registered = request
                    requests += request
                    continuation.invokeOnCancellation { request.cancelled = true }
                    check(arrivals.trySend(request).isSuccess)
                }
            } else {
                // Deliberately cancellation-unaware dependency for an adversarial lab.
                suspendCoroutine { continuation ->
                    val request = Request(query, { continuation.resume(it) },
                        { continuation.resumeWithException(it) })
                    registered = request
                    requests += request
                    check(arrivals.trySend(request).isSuccess)
                }
            }
        } finally {
            registered?.returned?.complete(Unit)
        }
    }
}

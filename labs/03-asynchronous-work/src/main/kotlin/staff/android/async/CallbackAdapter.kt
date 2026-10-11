package staff.android.async

import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

// region callback-contract
fun interface CancelHandle {
    fun cancel()
}

interface SearchCallback {
    fun onSuccess(products: List<SearchProduct>)
    fun onFailure(failure: IOException)
}

interface LegacySearchApi {
    fun start(query: String, callback: SearchCallback): CancelHandle
}
// endregion

/**
 * start returns a handle even if its single terminal callback ran inline.
 * At most one success OR failure callback is allowed, including after cancel.
 * start/cancel must be thread-safe; cancel is fast, idempotent and nonthrowing.
 * It must release registration resources even if delivery already finished.
 * Normal terminal delivery releases its registration resources without cancel.
 * The result contains owned values, not handles requiring close.
 */
// region callback-adapter
class CallbackSearchSource(private val api: LegacySearchApi) : SearchSource {
    override suspend fun search(query: String): List<SearchProduct> =
        suspendCancellableCoroutine { continuation ->
            val callback = object : SearchCallback {
                override fun onSuccess(products: List<SearchProduct>) {
                    continuation.resume(products.toList())
                }

                override fun onFailure(failure: IOException) {
                    continuation.resumeWithException(failure)
                }
            }
            val handle = api.start(query, callback)
            continuation.invokeOnCancellation { handle.cancel() }
        }
}
// endregion

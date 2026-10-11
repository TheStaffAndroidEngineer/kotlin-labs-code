@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package staff.android.async

import java.io.IOException
import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.Test

class CallbackAdapterTest {
    private class Api : LegacySearchApi {
        lateinit var callback: SearchCallback
        var cancelCount = 0
        override fun start(query: String, callback: SearchCallback): CancelHandle {
            this.callback = callback
            return CancelHandle { cancelCount++ }
        }
    }
    @Test fun successfulCallbackResumesWithOwnedRows() = runTest {
        val api = Api(); val result = async { CallbackSearchSource(api).search("tea") }; runCurrent()
        val rows = mutableListOf(SearchProduct("tea", "Tea"))
        api.callback.onSuccess(rows); rows.clear()
        assertEquals(listOf(SearchProduct("tea", "Tea")), result.await())
        assertEquals(0, api.cancelCount)
    }
    @Test fun expectedCallbackErrorPropagates() = runTest {
        supervisorScope {
            val api = Api(); val result = async { CallbackSearchSource(api).search("tea") }; runCurrent()
            api.callback.onFailure(IOException("offline"))
            assertFailsWith<IOException> { result.await() }
        }
    }
    @Test fun cancellationCancelsHandleAndLateCallbackCannotReturnAValue() = runTest {
        val api = Api(); val result = async { CallbackSearchSource(api).search("tea") }; runCurrent()
        result.cancelAndJoin(); api.callback.onSuccess(emptyList()); runCurrent()
        assertEquals(1, api.cancelCount)
        assertFailsWith<CancellationException> { result.await() }
    }
    @Test fun cancellationAfterResumeButBeforeDispatchWins() = runTest {
        val api = Api(); var accepted = false
        val result = launch { CallbackSearchSource(api).search("tea"); accepted = true }; runCurrent()
        api.callback.onSuccess(emptyList())
        result.cancel(); runCurrent()
        assertFalse(accepted)
        assertEquals(1, api.cancelCount)
    }
    @Test fun callbackMayCompleteInlineDuringRegistration() = runTest {
        val api = object : LegacySearchApi {
            override fun start(query: String, callback: SearchCallback): CancelHandle {
                callback.onSuccess(emptyList())
                return CancelHandle { }
            }
        }
        assertEquals(emptyList(), CallbackSearchSource(api).search("tea"))
    }
    @Test fun cancellationBeforeHandleIsReturnedStillCancelsIt() = runTest {
        val owner = Job(coroutineContext[Job]); var cancelled = false
        val api = object : LegacySearchApi {
            override fun start(query: String, callback: SearchCallback): CancelHandle {
                owner.cancel()
                return CancelHandle { cancelled = true }
            }
        }
        val request = CoroutineScope(coroutineContext + owner).async { CallbackSearchSource(api).search("tea") }
        runCurrent()
        assertTrue(cancelled)
        assertFailsWith<CancellationException> { request.await() }
    }
}

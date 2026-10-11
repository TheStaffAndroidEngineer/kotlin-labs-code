package staff.android.runtime

import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.util.concurrent.Executor
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.*

abstract class PresenceContract {
    abstract fun create(source: PresenceSource, ui: Executor): PresenceBinding

    @Test fun inlineInitialCallbackWaitsForUi() {
        val source = ControlledPresenceSource(Presence(0, false))
        val ui = ManualQueue()
        val shown = mutableListOf<Presence>()
        create(source, ui).attach(PresenceSink { shown += it })
        assertTrue(shown.isEmpty())
        ui.drain()
        assertEquals(listOf(Presence(0, false)), shown)
    }
    @Test fun callbackUsesUiLane() {
        val source = ControlledPresenceSource()
        val ui = ManualQueue()
        val threads = mutableListOf<Thread>()
        create(source, ui).attach(PresenceSink { threads += Thread.currentThread() })
        thread { source.emit(0, Presence(1, true)) }.join()
        ui.drain()
        assertEquals(listOf(Thread.currentThread()), threads)
    }
    @Test fun versionsCannotRegress() {
        val source = ControlledPresenceSource()
        val ui = ManualQueue()
        val shown = mutableListOf<Presence>()
        create(source, ui).attach(PresenceSink { shown += it })
        source.emit(0, Presence(2, true)); source.emit(0, Presence(1, false))
        ui.drain()
        assertEquals(listOf(Presence(2, true)), shown)
    }
    @Test fun conflictingDuplicateIgnored() {
        val source = ControlledPresenceSource()
        val ui = ManualQueue()
        val shown = mutableListOf<Presence>()
        create(source, ui).attach(PresenceSink { shown += it })
        source.emit(0, Presence(2, true)); source.emit(0, Presence(2, false))
        ui.drain()
        assertEquals(listOf(Presence(2, true)), shown)
    }
    @Test fun increasingReportsAreAccepted() {
        val source = ControlledPresenceSource()
        val ui = ManualQueue()
        val shown = mutableListOf<Presence>()
        create(source, ui).attach(PresenceSink { shown += it })
        source.emit(0, Presence(0, false)); source.emit(0, Presence(1, true))
        ui.drain()
        assertEquals(listOf(Presence(0, false), Presence(1, true)), shown)
    }
    @Test fun pendingCallbackCannotReachDetachedSink() {
        val source = ControlledPresenceSource()
        val ui = ManualQueue()
        val shown = mutableListOf<Presence>()
        val binding = create(source, ui)
        binding.attach(PresenceSink { shown += it })
        source.emit(0, Presence(1, true)); binding.detach(); ui.drain()
        assertTrue(shown.isEmpty())
    }
    @Test fun lateCallbackCannotReachDetachedSink() {
        val source = ControlledPresenceSource()
        val ui = ManualQueue()
        val shown = mutableListOf<Presence>()
        val binding = create(source, ui)
        binding.attach(PresenceSink { shown += it }); binding.detach()
        source.emit(0, Presence(1, true)); ui.drain()
        assertTrue(shown.isEmpty())
    }
    @Test fun detachClosesExactlyOnce() {
        val source = ControlledPresenceSource()
        val binding = create(source, ManualQueue())
        binding.attach(PresenceSink {}); binding.detach(); binding.detach()
        assertEquals(0, source.activeCount)
        assertEquals(1, source.closeCalls)
    }
    @Test fun replacingSinkClosesOldSubscription() {
        val source = ControlledPresenceSource()
        val binding = create(source, ManualQueue())
        binding.attach(PresenceSink {}); binding.attach(PresenceSink {})
        assertEquals(1, source.activeCount)
        assertEquals(1, source.closeCalls)
    }
    @Test fun oldSessionCannotReachNewSink() {
        val source = ControlledPresenceSource()
        val ui = ManualQueue()
        val old = mutableListOf<Presence>()
        val new = mutableListOf<Presence>()
        val binding = create(source, ui)
        binding.attach(PresenceSink { old += it })
        source.emit(0, Presence(9, false))
        binding.attach(PresenceSink { new += it })
        source.emit(0, Presence(10, false)); source.emit(1, Presence(0, true))
        ui.drain()
        assertTrue(old.isEmpty())
        assertEquals(listOf(Presence(0, true)), new)
    }
    @Test fun newSessionRestartsVersionDomain() {
        val source = ControlledPresenceSource()
        val ui = ManualQueue()
        val shown = mutableListOf<Presence>()
        val binding = create(source, ui)
        binding.attach(PresenceSink { shown += it })
        source.emit(0, Presence(99, false)); ui.drain()
        binding.attach(PresenceSink { shown += it })
        source.emit(1, Presence(0, true)); ui.drain()
        assertEquals(listOf(Presence(99, false), Presence(0, true)), shown)
    }
    @Test fun concurrentCallbacksPreserveIncreasingAcceptedVersions() {
        val source = ControlledPresenceSource()
        val ui = ManualQueue()
        val shown = mutableListOf<Presence>()
        create(source, ui).attach(PresenceSink { shown += it })
        val start = CountDownLatch(1)
        val workers = (0L..31L).map { version ->
            thread { assertTrue(start.await(5, TimeUnit.SECONDS)); source.emit(0, Presence(version, true)) }
        }
        start.countDown(); workers.forEach { it.join() }; ui.drain()
        assertEquals(31L, shown.last().version)
        assertTrue(shown.zipWithNext().all { (a,b) -> a.version < b.version })
    }
    @Test fun sinkMayDetachDuringDelivery() {
        val source = ControlledPresenceSource()
        val ui = ManualQueue()
        val binding = create(source, ui)
        var calls = 0
        binding.attach(PresenceSink { calls++; binding.detach() })
        source.emit(0, Presence(0, false)); source.emit(0, Presence(1, true)); ui.drain()
        assertEquals(1, calls)
        assertEquals(0, source.activeCount)
    }
    @Test fun closeMayDeliverAlreadyInFlightCallback() {
        lateinit var listener: (Presence) -> Unit
        val source = object : PresenceSource {
            override fun subscribe(callback: (Presence) -> Unit): Registration {
                listener = callback
                return Registration { listener(Presence(7, true)) }
            }
        }
        val ui = ManualQueue()
        val shown = mutableListOf<Presence>()
        val binding = create(source, ui)
        binding.attach(PresenceSink { shown += it }); binding.detach(); ui.drain()
        assertTrue(shown.isEmpty())
    }
}

class ReferencePresenceTest : PresenceContract() {
    override fun create(source: PresenceSource, ui: Executor) = ReferencePresenceBinding(source, ui)
}
@Tag("exercise")
class LearnerPresenceTest : PresenceContract() {
    override fun create(source: PresenceSource, ui: Executor) = LearnerPresenceBinding(source, ui)
}

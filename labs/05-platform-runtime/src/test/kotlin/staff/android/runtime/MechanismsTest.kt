package staff.android.runtime

import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.*

class MechanismsTest {
    @Test fun queuedProbeRunsAfterClick() {
        assertEquals(listOf("Click returned: Available", "Queued probe: Available"), queueDemo())
    }
    @Test fun nestedPostingDoesNotInvokeInline() {
        val queue = ManualQueue()
        val events = mutableListOf<Int>()
        queue.execute { events += 1; queue.execute { events += 3 }; events += 2 }
        queue.drain()
        assertEquals(listOf(1,2,3), events)
    }
    @Test fun volatileReadWriteSequenceLosesUpdate() { assertEquals(1, lostIncrement()) }
    @Test fun atomicIncrementPreservesBoth() { assertEquals(2, atomicIncrement()) }
    @Test fun publicationRejectsObsoleteAndDuplicate() {
        val stock = StockPublication()
        assertTrue(stock.publish(StockSnapshot(2, 8)))
        assertFalse(stock.publish(StockSnapshot(1, 4)))
        assertFalse(stock.publish(StockSnapshot(2, 10)))
        assertEquals(StockSnapshot(2, 8), stock.read())
    }
    @Test fun publicationRejectsInvalidInput(): Unit {
        assertFailsWith<IllegalArgumentException> { StockPublication().publish(StockSnapshot(1, -1)) }
    }
    @Test fun publicationRemainsOneAggregateAcrossThreads() {
        val stock = StockPublication()
        val start = CountDownLatch(1)
        val workers = (1L..24L).map { version ->
            thread { check(start.await(5, TimeUnit.SECONDS)); stock.publish(StockSnapshot(version, version.toInt())) }
        }
        start.countDown()
        repeat(1000) { val snapshot = stock.read(); assertEquals(snapshot.revision.toInt(), snapshot.available) }
        workers.forEach { it.join() }
        assertEquals(StockSnapshot(24,24), stock.read())
    }
    @Test fun rebuildCreatesDifferentRowIdentities() {
        val rows = listOf(RowValue(1,"Camera"), RowValue(2,"Bag"))
        val rebuilt = rebuildRows(rows)
        assertEquals(rows, rebuilt)
        assertTrue(rows.indices.all { rows[it] !== rebuilt[it] })
    }
    @Test fun editSharesUnchangedImmutableRows() {
        val rows = listOf(RowValue(1,"Camera"), RowValue(2,"Bag"))
        val edited = replaceOne(rows,2,"Travel bag")
        assertSame(rows[0], edited[0]); assertNotSame(rows[1], edited[1])
        assertEquals("Bag", rows[1].title); assertEquals("Travel bag", edited[1].title)
    }
}

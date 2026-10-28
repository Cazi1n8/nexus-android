package br.com.nexus.assistant.gestures

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.ConcurrentLinkedQueue

class LatestFrameDeliveryTest {
    @Test fun `busy UI keeps one queued callback and receives newest observation`() {
        val queue = ConcurrentLinkedQueue<Runnable>()
        val delivered = mutableListOf<Int>()
        val delivery = LatestFrameDelivery<Int>({ queue.add(it) }, { delivered.add(it) })
        repeat(10_000) { delivery.offer(it) }
        assertEquals(1, queue.size)
        queue.remove().run()
        assertEquals(listOf(9_999), delivered)
        assertTrue(queue.isEmpty())
    }

    @Test fun `frame arriving inside consumer is delivered once next turn`() {
        val queue = ConcurrentLinkedQueue<Runnable>()
        val delivered = mutableListOf<Int>()
        lateinit var delivery: LatestFrameDelivery<Int>
        delivery = LatestFrameDelivery({ queue.add(it) }) { value ->
            delivered.add(value)
            if (value == 1) { delivery.offer(2); delivery.offer(3) }
        }
        delivery.offer(1)
        queue.remove().run()
        assertEquals(1, queue.size)
        queue.remove().run()
        assertEquals(listOf(1, 3), delivered)
        assertTrue(queue.isEmpty())
    }

    @Test fun `stop discards pending and future observations without calling consumer`() {
        val queue = ConcurrentLinkedQueue<Runnable>()
        val delivery = LatestFrameDelivery<Int>({ queue.add(it) }) { fail("Stopped camera delivered a frame") }
        delivery.offer(1)
        delivery.close()
        delivery.offer(2)
        queue.remove().run()
        assertTrue(queue.isEmpty())
    }

    @Test fun `parallel producers still queue only one latest callback`() {
        val queue = ConcurrentLinkedQueue<Runnable>()
        val result = mutableListOf<Int>()
        val delivery = LatestFrameDelivery<Int>({ queue.add(it) }, { result.add(it) })
        val threads = List(4) { producer -> Thread { repeat(1_000) { delivery.offer(producer * 1_000 + it) } } }
        threads.forEach(Thread::start); threads.forEach(Thread::join)
        assertEquals(1, queue.size)
        delivery.offer(4_001)
        queue.remove().run()
        assertEquals(listOf(4_001), result)
    }

    @Test fun `camera clock offset is arbitrary and stale backlog is bounded`() {
        val timeline = CameraFrameTimeline()
        assertEquals(0L, timeline.begin(1_000_000L, 7_001_000_000L)?.excessQueueDelayMs)
        assertEquals(0L, timeline.begin(51_000_000L, 7_051_000_000L)?.excessQueueDelayMs)
        assertNull(timeline.begin(51_000_000L, 7_052_000_000L))
        assertNull(timeline.begin(41_000_000L, 7_053_000_000L))
        assertNull(timeline.begin(101_000_000L, 7_402_000_000L))
        assertEquals(0L, timeline.begin(451_000_000L, 7_451_000_000L)?.excessQueueDelayMs)
    }

    @Test fun `queue estimate reports excess only and can recover a better offset`() {
        val timeline = CameraFrameTimeline()
        assertEquals(0L, timeline.begin(1_000_000L, 8_001_000_000L)?.excessQueueDelayMs)
        assertEquals(100L, timeline.begin(51_000_000L, 8_151_000_000L)?.excessQueueDelayMs)
        assertEquals(0L, timeline.begin(101_000_000L, 8_051_000_000L)?.excessQueueDelayMs)
        assertEquals(50L, timeline.begin(151_000_000L, 8_151_000_000L)?.excessQueueDelayMs)
    }
}

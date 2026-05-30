package io.github.grepsedawk.civdiscord.paper.bridge

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class BridgeOutboundQueueTest {

    private class FakeClock(var now: Long = 0L) {
        fun read(): Long = now
    }

    private class CapturingLogger : Logger by LoggerFactory.getLogger("capture") {
        val warns = mutableListOf<String>()
        override fun warn(msg: String) {
            warns += msg
        }
    }

    @Test
    fun `drain flushes every queued frame in FIFO order`() {
        val clock = FakeClock()
        val queue = BridgeOutboundQueue(clock = clock::read)
        queue.enqueue("civdiscord:bridge", "Test", byteArrayOf(1))
        queue.enqueue("civdiscord:bridge", "Test", byteArrayOf(2))
        queue.enqueue("civdiscord:bridge", "Test", byteArrayOf(3))

        val delivered = mutableListOf<Byte>()
        val count = queue.drain { frame -> delivered.add(frame.bytes[0]) }

        count shouldBe 3
        delivered shouldBe listOf<Byte>(1, 2, 3)
        queue.size shouldBe 0
    }

    @Test
    fun `drain drops frames older than the TTL and delivers fresh ones`() {
        val clock = FakeClock(now = 1_000)
        val queue = BridgeOutboundQueue(maxAgeMs = 30_000, clock = clock::read)
        queue.enqueue("civdiscord:bridge", "SnitchHit", byteArrayOf(0xAA.toByte()))

        clock.now = 1_000 + 30_001
        queue.enqueue("civdiscord:bridge", "SnitchHit", byteArrayOf(0xBB.toByte()))

        val delivered = mutableListOf<Byte>()
        val count = queue.drain { frame -> delivered.add(frame.bytes[0]) }

        count shouldBe 1
        delivered shouldBe listOf<Byte>(0xBB.toByte())
    }

    @Test
    fun `drain with maxFrames stops after the cap and leaves the rest queued`() {
        val queue = BridgeOutboundQueue()
        repeat(100) { i ->
            queue.enqueue("civdiscord:bridge", "Test", byteArrayOf(i.toByte()))
        }

        val delivered = mutableListOf<Byte>()
        val count = queue.drain(maxFrames = 10) { frame -> delivered.add(frame.bytes[0]) }

        count shouldBe 10
        delivered.size shouldBe 10
        queue.size shouldBe 90
        delivered.first() shouldBe 0.toByte()
        delivered.last() shouldBe 9.toByte()
    }

    @Test
    fun `isEmpty reflects queue state`() {
        val queue = BridgeOutboundQueue()
        queue.isEmpty() shouldBe true
        queue.enqueue("civdiscord:bridge", "Test", byteArrayOf(1))
        queue.isEmpty() shouldBe false
        queue.drain { }
        queue.isEmpty() shouldBe true
    }

    @Test
    fun `enqueue past the cap drops the oldest frame so size never exceeds the cap`() {
        val queue = BridgeOutboundQueue(maxQueue = 200)
        repeat(250) { i ->
            queue.enqueue("civdiscord:bridge", "Test", byteArrayOf(i.toByte()))
        }

        queue.size shouldBe 200

        val delivered = mutableListOf<Byte>()
        queue.drain { frame -> delivered.add(frame.bytes[0]) }

        delivered.size shouldBe 200
        delivered.first() shouldBe 50.toByte()
        delivered.last() shouldBe 249.toByte()
    }

    @Test
    fun `drain summary names payload types that were cap-evicted`() {
        val log = CapturingLogger()
        val queue = BridgeOutboundQueue(maxQueue = 2, log = log)
        queue.enqueue("civdiscord:bridge", "SnitchHit", byteArrayOf(1))
        queue.enqueue("civdiscord:bridge", "SnitchHit", byteArrayOf(2))
        queue.enqueue("civdiscord:bridge", "StatusRequest", byteArrayOf(3))
        queue.enqueue("civdiscord:bridge", "StatusRequest", byteArrayOf(4))

        queue.drain { }

        log.warns.size shouldBe 1
        log.warns.first() shouldContain "SnitchHit=2"
        queue.evictionCount() shouldBe 2L
    }

    @Test
    fun `drain summary names payload types that went stale`() {
        val log = CapturingLogger()
        val clock = FakeClock(now = 1_000)
        val queue = BridgeOutboundQueue(maxAgeMs = 1_000, clock = clock::read, log = log)
        queue.enqueue("civdiscord:bridge", "SnitchHit", byteArrayOf(1))
        queue.enqueue("civdiscord:bridge", "ChatToMc", byteArrayOf(2))

        clock.now = 1_000 + 5_000
        queue.drain { }

        log.warns.size shouldBe 1
        log.warns.first() shouldContain "SnitchHit=1"
        log.warns.first() shouldContain "ChatToMc=1"
        queue.staleDropCount() shouldBe 2L
    }

    @Test
    fun `drain stays silent when nothing is dropped`() {
        val log = CapturingLogger()
        val queue = BridgeOutboundQueue(log = log)
        queue.enqueue("civdiscord:bridge", "SnitchHit", byteArrayOf(1))

        queue.drain { }

        log.warns shouldBe emptyList()
    }
}

package io.github.grepsedawk.civdiscord.paper.bridge

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class BridgeOutboundQueueTest {

    private class FakeClock(var now: Long = 0L) {
        fun read(): Long = now
    }

    @Test
    fun `drain flushes every queued frame in FIFO order`() {
        val clock = FakeClock()
        val queue = BridgeOutboundQueue(clock = clock::read)
        queue.enqueue("civdiscord:bridge", byteArrayOf(1))
        queue.enqueue("civdiscord:bridge", byteArrayOf(2))
        queue.enqueue("civdiscord:bridge", byteArrayOf(3))

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
        queue.enqueue("civdiscord:bridge", byteArrayOf(0xAA.toByte()))

        clock.now = 1_000 + 30_001
        queue.enqueue("civdiscord:bridge", byteArrayOf(0xBB.toByte()))

        val delivered = mutableListOf<Byte>()
        val count = queue.drain { frame -> delivered.add(frame.bytes[0]) }

        count shouldBe 1
        delivered shouldBe listOf<Byte>(0xBB.toByte())
    }

    @Test
    fun `drain with maxFrames stops after the cap and leaves the rest queued`() {
        val queue = BridgeOutboundQueue()
        repeat(100) { i ->
            queue.enqueue("civdiscord:bridge", byteArrayOf(i.toByte()))
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
        queue.enqueue("civdiscord:bridge", byteArrayOf(1))
        queue.isEmpty() shouldBe false
        queue.drain { }
        queue.isEmpty() shouldBe true
    }

    @Test
    fun `enqueue past the cap drops the oldest frame so size never exceeds the cap`() {
        val queue = BridgeOutboundQueue(maxQueue = 200)
        repeat(250) { i ->
            queue.enqueue("civdiscord:bridge", byteArrayOf(i.toByte()))
        }

        queue.size shouldBe 200

        val delivered = mutableListOf<Byte>()
        queue.drain { frame -> delivered.add(frame.bytes[0]) }

        delivered.size shouldBe 200
        delivered.first() shouldBe 50.toByte()
        delivered.last() shouldBe 249.toByte()
    }
}

package io.github.grepsedawk.civdiscord.paper.bridge

import java.util.concurrent.ConcurrentLinkedDeque

/**
 * Plugin messaging requires a Player as the carrier. When the Paper server is empty we hold
 * outbound frames until the next join. Bounded so a long player-less stretch can't OOM, and
 * TTL-gated so we don't replay stale snitch hits hours later.
 */
class BridgeOutboundQueue(
    private val maxQueue: Int = DEFAULT_MAX_QUEUE,
    private val maxAgeMs: Long = DEFAULT_MAX_AGE_MS,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    data class Frame(val channel: String, val bytes: ByteArray, val enqueuedAt: Long) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Frame) return false
            return channel == other.channel &&
                bytes.contentEquals(other.bytes) &&
                enqueuedAt == other.enqueuedAt
        }

        override fun hashCode(): Int {
            var result = channel.hashCode()
            result = 31 * result + bytes.contentHashCode()
            result = 31 * result + enqueuedAt.hashCode()
            return result
        }
    }

    private val deque = ConcurrentLinkedDeque<Frame>()

    val size: Int get() = deque.size

    fun enqueue(channel: String, bytes: ByteArray) {
        while (deque.size >= maxQueue) deque.pollFirst()
        deque.add(Frame(channel, bytes, clock()))
    }

    /**
     * Drains up to [maxFrames] frames in FIFO order. Stale frames (older than maxAgeMs) are
     * dropped silently and count against the cap; fresh frames are handed to [send]. Returns
     * the number of frames actually delivered.
     */
    fun drain(maxFrames: Int = Int.MAX_VALUE, send: (Frame) -> Unit): Int {
        val now = clock()
        var delivered = 0
        var processed = 0
        while (processed < maxFrames) {
            val frame = deque.pollFirst() ?: break
            processed++
            if (now - frame.enqueuedAt > maxAgeMs) continue
            send(frame)
            delivered++
        }
        return delivered
    }

    fun isEmpty(): Boolean = deque.isEmpty()

    companion object {
        const val DEFAULT_MAX_QUEUE = 200
        const val DEFAULT_MAX_AGE_MS = 30_000L
    }
}

package io.github.grepsedawk.civdiscord.paper.bridge

import org.slf4j.Logger
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicLong

/**
 * Plugin messaging requires a Player as the carrier. When the Paper server is empty we hold
 * outbound frames until the next join. Bounded so a long player-less stretch can't OOM, and
 * TTL-gated so we don't replay stale snitch hits hours later.
 *
 * Drops (cap-hit eviction + stale TTL) are tracked by payload type so a per-drain summary can
 * surface that a SnitchHit was lost — materially worse than losing a StatusRequest.
 */
class BridgeOutboundQueue(
    private val maxQueue: Int = DEFAULT_MAX_QUEUE,
    private val maxAgeMs: Long = DEFAULT_MAX_AGE_MS,
    private val clock: () -> Long = System::currentTimeMillis,
    private val log: Logger? = null,
) {

    data class Frame(
        val channel: String,
        val payloadType: String,
        val bytes: ByteArray,
        val enqueuedAt: Long,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Frame) return false
            return channel == other.channel &&
                payloadType == other.payloadType &&
                bytes.contentEquals(other.bytes) &&
                enqueuedAt == other.enqueuedAt
        }

        override fun hashCode(): Int {
            var result = channel.hashCode()
            result = 31 * result + payloadType.hashCode()
            result = 31 * result + bytes.contentHashCode()
            result = 31 * result + enqueuedAt.hashCode()
            return result
        }
    }

    private val deque = ConcurrentLinkedDeque<Frame>()
    private val evictedByType = mutableMapOf<String, Long>()
    private val totalEvicted = AtomicLong(0)
    private val totalStale = AtomicLong(0)

    val size: Int get() = deque.size

    fun enqueue(channel: String, payloadType: String, bytes: ByteArray) {
        while (deque.size >= maxQueue) {
            val dropped = deque.pollFirst() ?: break
            synchronized(evictedByType) {
                evictedByType[dropped.payloadType] = (evictedByType[dropped.payloadType] ?: 0) + 1
            }
            totalEvicted.incrementAndGet()
        }
        deque.add(Frame(channel, payloadType, bytes, clock()))
    }

    /**
     * Drains up to [maxFrames] frames in FIFO order. Stale frames (older than maxAgeMs) are
     * dropped and count against the cap; fresh frames are handed to [send]. Returns the number
     * of frames actually delivered. Emits a single summary line if anything was dropped since
     * the previous drain — cap-hit evictions and TTL drops folded together with payload-type
     * histogram so ops can see whether a SnitchHit was lost vs a StatusRequest.
     */
    fun drain(maxFrames: Int = Int.MAX_VALUE, send: (Frame) -> Unit): Int {
        val now = clock()
        var delivered = 0
        var processed = 0
        val staleByType = mutableMapOf<String, Long>()
        while (processed < maxFrames) {
            val frame = deque.pollFirst() ?: break
            processed++
            if (now - frame.enqueuedAt > maxAgeMs) {
                staleByType[frame.payloadType] = (staleByType[frame.payloadType] ?: 0) + 1
                totalStale.incrementAndGet()
                continue
            }
            send(frame)
            delivered++
        }
        emitDropSummary(staleByType)
        return delivered
    }

    private fun emitDropSummary(staleByType: Map<String, Long>) {
        val l = log ?: return
        val evicted = synchronized(evictedByType) {
            if (evictedByType.isEmpty()) emptyMap() else evictedByType.toMap().also { evictedByType.clear() }
        }
        if (evicted.isEmpty() && staleByType.isEmpty()) return
        val merged = (evicted.keys + staleByType.keys).associateWith { type ->
            (evicted[type] ?: 0) + (staleByType[type] ?: 0)
        }
        val total = merged.values.sum()
        val breakdown = merged.entries
            .sortedByDescending { it.value }
            .joinToString(", ") { "${it.key}=${it.value}" }
        l.warn("Bridge outbound queue dropped $total frame(s) [$breakdown] (cap=$maxQueue ttl=${maxAgeMs}ms)")
    }

    fun isEmpty(): Boolean = deque.isEmpty()

    /** Cumulative cap-hit evictions since start; for ops scraping. */
    fun evictionCount(): Long = totalEvicted.get()

    /** Cumulative stale-drop count since start; for ops scraping. */
    fun staleDropCount(): Long = totalStale.get()

    companion object {
        const val DEFAULT_MAX_QUEUE = 200
        const val DEFAULT_MAX_AGE_MS = 30_000L
    }
}

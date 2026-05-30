package io.github.grepsedawk.civdiscord.core.bridge

import org.slf4j.Logger
import java.util.concurrent.atomic.AtomicLong

/**
 * Emits warn-level lines no more often than [intervalNanos], rolling suppressed events into the
 * next emission as a "(suppressed N since last)" suffix. Per-instance: one budget per call site,
 * so HMAC-verify failures and unknown-payload drops don't starve each other.
 *
 * Designed for the bridge's silent-drop paths — HMAC mismatch, unknown payload type, queue
 * eviction summaries — where per-frame logging would flood ops during raid bursts but total
 * silence hides config skew (mismatched secret.key, schema drift across a rolling deploy).
 */
class RateLimitedLogger(
    private val log: Logger,
    private val intervalNanos: Long,
    private val nanoClock: () -> Long = System::nanoTime,
) {
    private val lastEmitNanos = AtomicLong(Long.MIN_VALUE)
    private val suppressed = AtomicLong(0)
    private val total = AtomicLong(0)

    /** Returns total count of events observed (emitted + suppressed). */
    fun count(): Long = total.get()

    fun warn(message: String) {
        total.incrementAndGet()
        val now = nanoClock()
        while (true) {
            val last = lastEmitNanos.get()
            if (last != Long.MIN_VALUE && now - last < intervalNanos) {
                suppressed.incrementAndGet()
                return
            }
            if (lastEmitNanos.compareAndSet(last, now)) {
                val sup = suppressed.getAndSet(0)
                if (sup > 0) log.warn("$message (suppressed $sup since last)") else log.warn(message)
                return
            }
        }
    }
}

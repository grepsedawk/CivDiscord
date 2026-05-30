package io.github.grepsedawk.civdiscord.velocity.chat

import java.util.concurrent.ConcurrentHashMap

/**
 * Per-Discord-user token bucket for Discord→MC chat.
 *
 * Keyed on Discord user id (not channel) so cross-channel floods can't bypass.
 * Buckets refill at [tokensPerSecond] and cap at [capacity]. [tryAcquire] returns
 * false when the caller should silently drop — never throws, never logs the user,
 * since visible feedback would let an attacker probe the limit.
 */
class ChatRateLimiter(
    private val capacity: Double,
    private val tokensPerSecond: Double,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private data class Bucket(var tokens: Double, var lastRefillMs: Long)

    private val buckets = ConcurrentHashMap<Long, Bucket>()

    fun tryAcquire(discordId: Long): Boolean {
        val now = clock()
        val bucket = buckets.computeIfAbsent(discordId) { Bucket(capacity, now) }
        synchronized(bucket) {
            val elapsedMs = now - bucket.lastRefillMs
            if (elapsedMs > 0) {
                bucket.tokens = (bucket.tokens + elapsedMs * tokensPerSecond / 1000.0).coerceAtMost(capacity)
                bucket.lastRefillMs = now
            }
            if (bucket.tokens >= 1.0) {
                bucket.tokens -= 1.0
                return true
            }
            return false
        }
    }
}

package io.github.grepsedawk.civdiscord.core.auth

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class LinkAttemptLimiter(
    private val maxFailuresPerWindow: Int = 5,
    private val windowMillis: Long = 5 * 60 * 1000,
    private val cooldownMillis: Long = 10 * 60 * 1000,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private data class State(
        var windowStart: Long,
        val failures: AtomicInteger,
        var cooldownUntil: Long,
    )

    private val states = ConcurrentHashMap<Long, State>()

    fun isLockedOut(discordId: Long): Boolean {
        val now = clock()
        val s = states[discordId] ?: return false
        return s.cooldownUntil > now
    }

    fun recordFailure(discordId: Long): Boolean {
        val now = clock()
        val s =
            states.computeIfAbsent(discordId) {
                State(windowStart = now, failures = AtomicInteger(0), cooldownUntil = 0L)
            }
        synchronized(s) {
            if (now - s.windowStart > windowMillis) {
                s.windowStart = now
                s.failures.set(0)
            }
            val count = s.failures.incrementAndGet()
            if (count >= maxFailuresPerWindow) {
                s.cooldownUntil = now + cooldownMillis
                return true
            }
            return false
        }
    }

    fun recordSuccess(discordId: Long) {
        states.remove(discordId)
    }
}

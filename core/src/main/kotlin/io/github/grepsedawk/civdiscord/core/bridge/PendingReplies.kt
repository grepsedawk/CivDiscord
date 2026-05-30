package io.github.grepsedawk.civdiscord.core.bridge

import java.util.concurrent.ConcurrentHashMap

/**
 * Id-correlated in-flight requests with TTL and a hard entry cap.
 *
 * Use one instance per (carrier, TTL) — e.g. one for player link replies (5min),
 * one for player status replies (30s), one for Discord console replies (14min).
 *
 * [onExpire] runs once per entry dropped by [sweep] — Velocity uses it to edit the
 * Discord interaction hook with a timeout message. [resolve] does NOT fire it
 * (resolve = success).
 *
 * When [maxEntries] is reached, [remember] silently drops the new entry. The cap
 * exists so a flood of unanswered requests can't OOM the JVM; legitimate flows
 * sweep faster than they fill.
 */
class PendingReplies<C : Any>(
    private val ttlMillis: Long,
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
    private val clock: () -> Long = System::currentTimeMillis,
    private val onExpire: (C) -> Unit = {},
) {
    private data class Entry<C>(val carrier: C, val registeredAt: Long)

    private val map = ConcurrentHashMap<String, Entry<C>>()

    fun remember(id: String, carrier: C) {
        if (map.size >= maxEntries) return
        map[id] = Entry(carrier, clock())
    }

    fun resolve(id: String): C? {
        val entry = map.remove(id) ?: return null
        return if (entry.registeredAt + ttlMillis > clock()) entry.carrier else null
    }

    fun sweep() {
        val cutoff = clock() - ttlMillis
        val it = map.entries.iterator()
        while (it.hasNext()) {
            val (_, entry) = it.next()
            if (entry.registeredAt <= cutoff) {
                it.remove()
                runCatching { onExpire(entry.carrier) }
            }
        }
    }

    fun size(): Int = map.size

    companion object {
        const val DEFAULT_MAX_ENTRIES: Int = 10_000
    }
}

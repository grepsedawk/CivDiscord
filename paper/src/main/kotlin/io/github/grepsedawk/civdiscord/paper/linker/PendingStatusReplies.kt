package io.github.grepsedawk.civdiscord.paper.linker

import io.github.grepsedawk.civdiscord.core.bridge.Payload
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** Entries expire after [ttlMillis] so a dropped StatusReply can't leak memory. */
class PendingStatusReplies(
    private val ttlMillis: Long = DEFAULT_TTL_MS,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private data class Pending(val target: UUID, val registeredAt: Long)

    private val map = ConcurrentHashMap<String, Pending>()

    fun remember(id: String, player: UUID) {
        map[id] = Pending(player, clock())
    }

    fun resolve(reply: Payload.StatusReply): UUID? {
        val entry = map.remove(reply.id) ?: return null
        return if (entry.registeredAt + ttlMillis > clock()) entry.target else null
    }

    fun sweep() {
        val cutoff = clock() - ttlMillis
        map.entries.removeIf { it.value.registeredAt <= cutoff }
    }

    companion object {
        val DEFAULT_TTL_MS: Long = TimeUnit.SECONDS.toMillis(30)
    }
}

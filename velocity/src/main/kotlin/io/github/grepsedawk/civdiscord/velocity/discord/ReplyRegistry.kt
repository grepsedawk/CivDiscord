package io.github.grepsedawk.civdiscord.velocity.discord

import io.github.grepsedawk.civdiscord.core.bridge.Payload
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

/**
 * Correlates broadcast-style request/reply pairs by `id`. Callers `register(id)`
 * before sending the request, then await the returned future. Inbound replies
 * are routed in via `complete(payload)`. Unmatched replies are dropped silently
 * — late arrivals after a timeout are normal and not errors.
 */
class ReplyRegistry {
    private val pending = ConcurrentHashMap<String, CompletableFuture<Payload>>()

    fun register(id: String): CompletableFuture<Payload> {
        val future = CompletableFuture<Payload>()
        pending[id] = future
        return future
    }

    fun complete(payload: Payload) {
        val id = idOf(payload) ?: return
        pending.remove(id)?.complete(payload)
    }

    fun discard(id: String) {
        pending.remove(id)
    }

    private fun idOf(p: Payload): String? = when (p) {
        is Payload.NameLayerReply -> p.id
        else -> null
    }
}

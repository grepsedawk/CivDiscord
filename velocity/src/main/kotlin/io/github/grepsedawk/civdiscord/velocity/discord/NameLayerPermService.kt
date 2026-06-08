package io.github.grepsedawk.civdiscord.velocity.discord

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Result of a NameLayer permission lookup. UNKNOWN means the check could not run
 * (DB down, timeout, pool exhausted) — it is NOT a denial. Callers that delete
 * state on denial must treat UNKNOWN as "keep the state and try again later",
 * otherwise a momentary DB blip silently destroys relay bindings.
 */
enum class PermCheck { ALLOWED, DENIED, UNKNOWN }

/**
 * What a relay path should do with a binder whose READ_CHAT was just checked.
 * Centralizes the one safe mapping of [PermCheck] to an action so no call site
 * can re-derive it and fold UNKNOWN back into UNBIND (the original data-loss bug).
 */
enum class RelayDecision { RELAY, SKIP, UNBIND }

open class NameLayerPermService(
    private val lookup: (UUID, String, String) -> PermCheck,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val ttlMillis: Long = 60_000L,
    // A failed lookup is cached only briefly: long enough to stop a per-message
    // flood from hammering the 3-connection pool during an outage, short enough
    // that relays resume within seconds of the DB recovering.
    private val unknownTtlMillis: Long = 5_000L,
) {
    private data class CacheEntry(val value: PermCheck, val expires: Long)

    private val permCache = ConcurrentHashMap<String, CacheEntry>()

    open fun check(mcUuid: UUID, group: String, perm: String): PermCheck {
        val key = "$mcUuid|$group|$perm"
        cached(key)?.let { return it }
        val result = lookup(mcUuid, group, perm)
        val ttl = if (result == PermCheck.UNKNOWN) unknownTtlMillis else ttlMillis
        permCache[key] = CacheEntry(result, nowMillis() + ttl)
        return result
    }

    open fun hasPerm(mcUuid: UUID, group: String, perm: String): Boolean = check(mcUuid, group, perm) == PermCheck.ALLOWED

    /**
     * Decide whether a relay whose binder is [binderUuid] should still carry chat
     * for [group]. A null binder (the Discord account unlinked) or a confirmed
     * DENIED both mean the binding is genuinely gone → UNBIND. An UNKNOWN check
     * (DB unreachable) keeps the binding and skips just this message.
     */
    fun relayReadDecision(binderUuid: UUID?, group: String): RelayDecision {
        if (binderUuid == null) return RelayDecision.UNBIND
        return when (check(binderUuid, group, READ_CHAT)) {
            PermCheck.ALLOWED -> RelayDecision.RELAY
            PermCheck.DENIED -> RelayDecision.UNBIND
            PermCheck.UNKNOWN -> RelayDecision.SKIP
        }
    }

    private fun cached(key: String): PermCheck? {
        val hit = permCache[key] ?: return null
        if (hit.expires < nowMillis()) {
            permCache.remove(key)
            return null
        }
        return hit.value
    }

    companion object {
        const val READ_CHAT = "READ_CHAT"
        const val SNITCH_NOTIFICATIONS = "SNITCH_NOTIFICATIONS"
        const val SNITCH_IMMUNE = "SNITCH_IMMUNE"
    }
}

package io.github.grepsedawk.civdiscord.velocity.discord

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

open class NameLayerPermService(
    private val lookup: (UUID, String, String) -> Boolean,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val ttlMillis: Long = 60_000L,
) {
    private data class CacheEntry(val value: Boolean, val expires: Long)

    private val permCache = ConcurrentHashMap<String, CacheEntry>()

    open fun hasPerm(mcUuid: UUID, group: String, perm: String): Boolean {
        val key = "$mcUuid|$group|$perm"
        cached(key)?.let { return it }
        val result = lookup(mcUuid, group, perm)
        permCache[key] = CacheEntry(result, nowMillis() + ttlMillis)
        return result
    }

    private fun cached(key: String): Boolean? {
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
    }
}

package io.github.grepsedawk.civdiscord.core.patreon

class TierRoleMap(private val map: Map<String, Long>) {
    fun roleForTier(tier: String?): Long? = tier?.let(map::get)

    fun allRoleIds(): Set<Long> = map.values.toSet()
}

package io.github.grepsedawk.civdiscord.core.patreon

import io.github.grepsedawk.civdiscord.core.db.PatreonTierDao

class PatreonSync(
    private val client: PatreonClient,
    private val tiers: PatreonTierDao,
    private val map: TierRoleMap,
) {
    data class RoleDelta(
        val discordId: Long,
        val addRole: Long?,
        val removeRoles: Set<Long>,
    )

    fun runOnce(): List<RoleDelta> {
        val previous = tiers.findAll()
        val snapshot = client.fetchCurrentTiers()
        val deltas = mutableListOf<RoleDelta>()
        for ((discordId, tier) in snapshot) {
            val add = map.roleForTier(tier)
            val remove = map.allRoleIds() - listOfNotNull(add).toSet()
            deltas.add(RoleDelta(discordId, add, remove))
        }
        val droppedOff = previous.keys - snapshot.keys
        for (discordId in droppedOff) {
            deltas.add(RoleDelta(discordId, addRole = null, removeRoles = map.allRoleIds()))
        }
        tiers.replaceSnapshot(snapshot, toRemove = droppedOff)
        return deltas
    }
}

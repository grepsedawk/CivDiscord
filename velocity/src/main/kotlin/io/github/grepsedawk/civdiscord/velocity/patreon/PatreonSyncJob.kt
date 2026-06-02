package io.github.grepsedawk.civdiscord.velocity.patreon

import io.github.grepsedawk.civdiscord.core.patreon.PatreonSync

class PatreonSyncJob(
    private val sync: PatreonSync,
    private val homeGuildId: Long,
    private val addRole: (guildId: Long, discordId: Long, roleId: Long) -> Unit,
    private val removeRole: (guildId: Long, discordId: Long, roleId: Long) -> Unit,
) {
    fun tick() {
        for (delta in sync.runOnce()) {
            delta.addRole?.let { addRole(homeGuildId, delta.discordId, it) }
            for (r in delta.removeRoles) removeRole(homeGuildId, delta.discordId, r)
        }
    }
}

package io.github.grepsedawk.civdiscord.velocity.auth

import io.github.grepsedawk.civdiscord.core.db.GuildDao

class RoleGranter(
    private val guilds: GuildDao,
    private val isMemberOf: (guildId: Long, discordId: Long) -> Boolean,
    private val grant: (guildId: Long, discordId: Long, roleId: Long) -> Unit,
) {
    /** Iterate every known guild; grant its auth_role if configured and user is a member. */
    fun grantAllForLinkedUser(discordId: Long) {
        for (g in guilds.all()) {
            val role = g.authRoleId ?: continue
            if (!isMemberOf(g.guildId, discordId)) continue
            grant(g.guildId, discordId, role)
        }
    }

    /** Single-guild grant — used by MemberAddListener for a just-joined member. */
    fun grantForGuild(
        guildId: Long,
        discordId: Long,
    ) {
        val g = guilds.find(guildId) ?: return
        val role = g.authRoleId ?: return
        grant(g.guildId, discordId, role)
    }
}

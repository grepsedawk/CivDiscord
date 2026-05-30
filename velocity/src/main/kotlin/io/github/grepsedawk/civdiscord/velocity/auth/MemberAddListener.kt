package io.github.grepsedawk.civdiscord.velocity.auth

import io.github.grepsedawk.civdiscord.core.db.BindingDao
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import org.slf4j.LoggerFactory

class MemberAddListener(
    private val bindings: BindingDao,
    private val grant: (guildId: Long, discordId: Long) -> Unit,
) : ListenerAdapter() {
    private val log = LoggerFactory.getLogger(MemberAddListener::class.java)

    override fun onGuildMemberJoin(event: GuildMemberJoinEvent) {
        val discordId = event.user.idLong
        val guildId = event.guild.idLong
        try {
            if (bindings.findByDiscordId(discordId) == null) {
                log.debug("Member join in guild {} from unlinked user {}", guildId, discordId)
                return
            }
            log.info("Linked user {} joined guild {} — granting auth role", discordId, guildId)
            grant(guildId, discordId)
        } catch (t: Throwable) {
            log.warn("MemberAddListener failed for user={} guild={}", discordId, guildId, t)
        }
    }
}

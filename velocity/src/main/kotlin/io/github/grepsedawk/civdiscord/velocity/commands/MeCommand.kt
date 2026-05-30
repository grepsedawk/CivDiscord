package io.github.grepsedawk.civdiscord.velocity.commands

import io.github.grepsedawk.civdiscord.core.db.BindingDao
import io.github.grepsedawk.civdiscord.core.db.GuildDao
import io.github.grepsedawk.civdiscord.core.db.PatreonTierDao
import io.github.grepsedawk.civdiscord.core.text.MarkdownSafe
import io.github.grepsedawk.civdiscord.velocity.auth.RoleGranter
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import java.util.concurrent.Executor

class MeCommand(
    private val bindings: BindingDao,
    private val tiers: PatreonTierDao,
    private val guildsDao: GuildDao,
    private val granter: RoleGranter,
    private val syncExecutor: Executor,
) {
    fun handle(event: SlashCommandInteractionEvent) {
        val id = event.user.idLong
        val b = bindings.findByDiscordId(id)
        val tier = tiers.get(id)
        val msg = buildString {
            if (b == null) {
                append("You are not linked yet. Run `/discord link` in-game to start.")
            } else {
                append("**MC**: `${MarkdownSafe.code(b.mcName)}` (`${b.mcUuid}`)\n")
                append("**Linked at**: <t:${b.linkedAt / 1000}:f>\n")
                tier?.let { append("**Patreon tier**: $it\n") }
                val perGuild = renderPerGuildRoles(event, id)
                if (perGuild.isNotEmpty()) {
                    append("**Discord roles**:\n")
                    append(perGuild)
                }
            }
        }.trimEnd()
        event.reply(msg).setEphemeral(true).queue()
        if (b != null) {
            syncExecutor.execute { granter.grantAllForLinkedUser(id) }
        }
    }

    private fun renderPerGuildRoles(event: SlashCommandInteractionEvent, discordId: Long): String = buildString {
        for (g in guildsDao.all()) {
            val guild = event.jda.getGuildById(g.guildId) ?: continue
            val member = guild.getMemberById(discordId) ?: continue
            val roleNames = member.roles.map { MarkdownSafe.code(it.name) }
            val display = if (roleNames.isEmpty()) "(none)" else roleNames.joinToString(", ")
            append("• `${MarkdownSafe.code(guild.name)}`: $display\n")
        }
    }
}

package io.github.grepsedawk.civdiscord.velocity.commands

import io.github.grepsedawk.civdiscord.core.db.GuildDao
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import org.slf4j.LoggerFactory

class AdminGuildCommand(private val guilds: GuildDao) {
    private val log = LoggerFactory.getLogger(AdminGuildCommand::class.java)

    fun handle(event: SlashCommandInteractionEvent) {
        val guildId =
            event.guild?.idLong ?: run {
                event.reply("This command must be used in a guild.").setEphemeral(true).queue()
                return
            }
        when (event.subcommandName) {
            "auth-role" -> {
                val roleOpt =
                    event.getOption("role") ?: run {
                        event.reply("Missing role.").setEphemeral(true).queue()
                        return
                    }
                val role =
                    runCatching { roleOpt.asRole }.getOrNull() ?: run {
                        event.reply("Unknown role.").setEphemeral(true).queue()
                        return
                    }
                val rejection = rejectionReason(event, role, guildId)
                if (rejection != null) {
                    event.reply(rejection).setEphemeral(true).queue()
                    return
                }
                log.info(
                    "admin guild auth-role by actor={} guild={} value={}",
                    event.user.idLong,
                    guildId,
                    role.idLong,
                )
                val updated = guilds.setAuthRole(guildId, role.idLong)
                if (updated == 0) {
                    event.reply("This guild isn't tracked yet. Try again in a moment.").setEphemeral(true).queue()
                } else {
                    event.reply("Auth role set to <@&${role.idLong}>.").setEphemeral(true).queue()
                }
            }
            "view" -> {
                log.info("admin guild view by actor={} guild={}", event.user.idLong, guildId)
                val g = guilds.find(guildId)
                val authRole = g?.authRoleId?.let { "<@&$it>" } ?: "(unset)"
                event.reply("Guild config:\n- Auth role: $authRole").setEphemeral(true).queue()
            }
            else -> event.reply("Unknown subcommand: ${event.subcommandName}.").setEphemeral(true).queue()
        }
    }

    private fun rejectionReason(event: SlashCommandInteractionEvent, role: Role, guildId: Long): String? {
        if (role.isPublicRole || role.idLong == guildId) {
            return "Role @everyone cannot be used as an auth role."
        }
        if (role.isManaged) {
            return "Role @${role.name} is managed (bot/integration role) — refusing to use as auth role."
        }
        val selfMember = event.guild?.selfMember
        if (selfMember != null && !selfMember.canInteract(role)) {
            return "Role @${role.name} is above the bot's highest role — refusing to use as auth role."
        }
        val dangerous = listOf(
            Permission.ADMINISTRATOR,
            Permission.MANAGE_SERVER,
            Permission.MANAGE_ROLES,
            Permission.MANAGE_CHANNEL,
            Permission.BAN_MEMBERS,
            Permission.KICK_MEMBERS,
        )
        val offending = dangerous.firstOrNull { role.permissions.contains(it) }
        if (offending != null) {
            return "Role @${role.name} bears dangerous permission ${offending.name} — refusing to use as auth role."
        }
        return null
    }
}

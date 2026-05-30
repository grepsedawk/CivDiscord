package io.github.grepsedawk.civdiscord.velocity.commands

import io.github.grepsedawk.civdiscord.core.admin.AdminService
import io.github.grepsedawk.civdiscord.core.text.MarkdownSafe
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import org.slf4j.LoggerFactory

class AdminUserCommand(private val service: AdminService) {
    private val log = LoggerFactory.getLogger(AdminUserCommand::class.java)

    fun handle(event: SlashCommandInteractionEvent) {
        val target = event.getOption("discord-user")?.asLong ?: run {
            event.reply("Missing discord-user.").setEphemeral(true).queue()
            return
        }
        val sub = event.subcommandName
        log.info("admin user {} by actor={} target={}", sub, event.user.idLong, target)
        when (sub) {
            "view" -> view(event, target)
            "unlink" -> unlink(event, target)
            else -> event.reply("Unknown subcommand: $sub.").setEphemeral(true).queue()
        }
    }

    private fun view(e: SlashCommandInteractionEvent, id: Long) {
        val b = service.viewBinding(id)
        val msg = if (b == null) {
            "<@$id> is not linked."
        } else {
            "<@$id> → MC `${MarkdownSafe.code(b.mcName)}` (`${b.mcUuid}`), linked <t:${b.linkedAt / 1000}:f>"
        }
        e.reply(msg).setEphemeral(true).queue()
    }

    private fun unlink(e: SlashCommandInteractionEvent, id: Long) {
        when (service.forceUnlink(id)) {
            AdminService.UnlinkResult.Unlinked -> e.reply("Unlinked <@$id>.").setEphemeral(true).queue()
            AdminService.UnlinkResult.NotLinked -> e.reply("<@$id> was not linked.").setEphemeral(true).queue()
        }
    }
}
